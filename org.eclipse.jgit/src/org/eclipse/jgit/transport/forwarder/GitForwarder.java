/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.forwarder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal TCP forwarder with a single routing listener and a simple proxy loop.
 *
 * @since 7.7
 */
public class GitForwarder implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger(GitForwarder.class);
	private final String listenHost;
	private final int listenPort;
	private final RoutingListener routingListener;
	private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
	private final ExecutorService workerPool;
	private volatile ServerSocket serverSocket;

	/**
	 * Creates a forwarder that listens to on the given host and 9418 port.
	 *
	 * @param listenHost
	 *            host to bind
	 * @param listener
	 *            routing listener
	 */
	public GitForwarder(@NonNull String listenHost, @NonNull RoutingListener listener) {
		this(listenHost, 9418, listener, Executors.newCachedThreadPool());
  }

	/**
	 * Creates a forwarder that listens to on the given host and port.
	 *
	 * @param listenHost host to bind
	 * @param listenPort port to bind
	 * @param listener routing listener
	 * @param workerPool executor used for handling connections
	 */
	public GitForwarder(@NonNull String listenHost, int listenPort,
			@NonNull RoutingListener listener, @NonNull ExecutorService workerPool) {
		this.listenHost = Objects.requireNonNull(listenHost, "listenHost"); //$NON-NLS-1$
		this.listenPort = listenPort;
		this.routingListener = Objects.requireNonNull(listener, "listener"); //$NON-NLS-1$
		this.workerPool = Objects.requireNonNull(workerPool, "workerPool"); //$NON-NLS-1$

		startInternal();
	}

	private void startInternal() {
		try {
			serverSocket = new ServerSocket();
			serverSocket.bind(new InetSocketAddress(listenHost, listenPort));
			acceptExecutor.execute(this::acceptLoop);
		} catch (IOException e) {
			LOG.error("Failed to start forwarder", e); //$NON-NLS-1$
			throw new RuntimeException(e);
		}
	}

	private void acceptLoop() {
		while (true) {
			try {
				Socket clientSocket = serverSocket.accept();
				workerPool.execute(() -> {
					try {
						handleConnection(clientSocket);
					} finally {
						closeQuietly(clientSocket);
					}
				});
			} catch (IOException e) {
				// Ignore transient accept errors while running.
			}
		}
	}

	private void handleConnection(Socket clientSocket) {
		final RouteRequest request;
		try {
			CommandInfo commandInfo = new CommandInfo(clientSocket);
			request = new RouteRequest(
							commandInfo,
							clientSocket.getInetAddress().getHostAddress(),
							listenHost,
							listenPort
						);
			LOG.info("Incoming connection from {} for {} {} (host={}) on {}:{}", //$NON-NLS-1$
					request.sourceIp(),
					commandInfo.command,
					commandInfo.project,
					commandInfo.host,
					request.listenHost(),
					request.listenPort());
		} catch (IOException e) {
			LOG.error("Failed to parse command info", e); //$NON-NLS-1$
			return;
		}

		RouteResponse response = null;
		boolean opened = false;
		try (Socket client = clientSocket) {
			response = routingListener.onConnect(request);
			if (response == null) {
				return;
			}
			try (Socket upstreamSocket = new Socket()) {
				upstreamSocket.connect(new InetSocketAddress(response.destinationHost(), response.destinationPort()));
				opened = true;
				final RouteResponse finalResponse = response; // to avoid compile error on lambda below.
				proxyBidirectional(
						client,
						upstreamSocket,
						request.commandInfo().recordedPrefix,
						e -> routingListener.onOpenException(request, finalResponse, e)
				);
			}
		} catch (IOException e) {
			LOG.error("Failed connection", e); //$NON-NLS-1$
			if (opened) {
				routingListener.onOpenException(request, response, e);
			} else {
				routingListener.onException(request, e);
			}
		} finally {
			routingListener.onClose(request, response);
		}
	}

	private void proxyBidirectional(Socket in, Socket out, byte[] prefix, Consumer<Exception> onException) {
		AtomicBoolean done = new AtomicBoolean(false);
		AtomicReference<Thread> forwarder = new AtomicReference<>();
		AtomicReference<Thread> responder = new AtomicReference<>();

		forwarder.setPlain(new Thread(
				() -> forward(in, out, prefix, done, responder::getPlain, onException)));
		responder.setPlain(new Thread(
				() -> forward(out, in, null, done, forwarder::getPlain, onException)));

		forwarder.getPlain().setDaemon(true);
		responder.getPlain().setDaemon(true);
		forwarder.getPlain().start();
		responder.getPlain().start();

		try {
			forwarder.getPlain().join();
			responder.getPlain().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.debug("Interrupted while waiting for threads to join", e); //$NON-NLS-1$
		}
	}

	private void forward(
			Socket source,
			Socket target,
			@Nullable byte[] prefix,
			AtomicBoolean done,
			Supplier<Thread> peerThread,
			Consumer<Exception> onException) {
		try {
			InputStream in = source.getInputStream();
			OutputStream out = target.getOutputStream();

			if (prefix != null && prefix.length > 0) {
				out.write(prefix);
				out.flush();
			}
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
				out.flush();
			}
		} catch (IOException e) {
			if(!done.get() && !Thread.currentThread().isInterrupted()) {
				LOG.error("Failed connection", e); //$NON-NLS-1$
				onException.accept(e);
			}
		} finally {
			if (done.compareAndSet(false, true)) {
				peerThread.get().interrupt();
				closeQuietly(source);
				closeQuietly(target);
			}
		}
	}

	@Override
	public void close() {
		close(1, TimeUnit.SECONDS);
	}

	/**
	 * Closes the forwarder, waiting up to the given timeout for shutdown.
	 *
	 * @param timeout
	 *            time to wait
	 * @param unit
	 *            timeout unit
	 */
	public void close(long timeout, TimeUnit unit) {
		if (serverSocket != null) {
			try {
				serverSocket.close();
			} catch (IOException e) {
				LOG.debug("Failed to close server socket cleanly", e); //$NON-NLS-1$
			}
		}
		acceptExecutor.shutdown();
		workerPool.shutdown();
		try {
			acceptExecutor.awaitTermination(timeout, unit);
			workerPool.awaitTermination(timeout, unit);
		} catch (InterruptedException e) {
			acceptExecutor.shutdownNow();
			workerPool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void closeQuietly(Socket socket) {
		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				LOG.debug("Failed to close client socket cleanly", e); //$NON-NLS-1$
			}
		}
	}
}
