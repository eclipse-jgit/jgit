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
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal TCP forwarder for the anonymous git protocol with a single routing
 * listener and a simple proxy loop.
 *
 * @since 7.7
 */
public class GitForwarder implements AutoCloseable {
	private static final Logger LOG = LoggerFactory
			.getLogger(GitForwarder.class);

	private static final int SOCKET_TIMEOUT_MS = 30_000;

	private final InetSocketAddress listenOn;

	private final RoutingListener routingListener;

	private final ExecutorService acceptExecutor = Executors
			.newSingleThreadExecutor();

	private final ExecutorService workerPool;

	private final ServerSocket serverSocket;

	/**
	 * Creates a forwarder that listens on the given host and port.
	 *
	 * @param listenOn
	 *            address to bind
	 * @param listener
	 *            routing listener
	 * @param workerPool
	 *            executor used for handling connections
	 * @throws IOException
	 *             on failure to bind to the socket or on writing/reading
	 *             to/from the socket
	 */
	public GitForwarder(@NonNull InetSocketAddress listenOn,
			@NonNull RoutingListener listener,
			@NonNull ExecutorService workerPool) throws IOException {
		this.listenOn = Objects.requireNonNull(listenOn, "listenOn"); //$NON-NLS-1$
		this.routingListener = Objects.requireNonNull(listener, "listener"); //$NON-NLS-1$
		this.workerPool = Objects.requireNonNull(workerPool, "workerPool"); //$NON-NLS-1$

		serverSocket = new ServerSocket();
		startInternal();
	}

	private void startInternal() throws IOException {
		try {
			serverSocket.bind(listenOn);
			acceptExecutor.execute(this::acceptLoop);
		} catch (IOException e) {
			LOG.error(MessageFormat.format(
					JGitText.get().forwarderFailedToStart, listenOn), e);
			throw e;
		}
	}

	private void acceptLoop() {
		while (!serverSocket.isClosed()) {
			try {
				Socket clientSocket = serverSocket.accept();
				workerPool.execute(() -> {
					try {
						handleConnection(new RouteRequest(clientSocket,
								clientSocket.getInetAddress().getHostAddress(),
								listenOn));
					} catch (IOException e) {
						LOG.error(MessageFormat.format(JGitText
								.get().forwarderFailedToParseCommandInfo,
								clientSocket.getInetAddress()), e);
					} finally {
						closeQuietly(clientSocket);
					}
				});
			} catch (IOException e) {
				// Ignore transient accept errors while running.
				// This could happen when client closes abruptly just doing a
				// heartbeat check.
			}
		}
	}

	private void handleConnection(RouteRequest request) {
		RouteResponse response = routingListener.onConnect(request);
		if (response == null) {
			return;
		}

		LOG.info(JGitText.get().forwarderIncomingConnection, request.sourceIp(),
				request.commandInfo().command, request.commandInfo().repo,
				request.commandInfo().host, request.listenAddr(),
				response.destination());

		Socket upstreamSocket = new Socket();
		try {
			upstreamSocket.connect(response.destination(), SOCKET_TIMEOUT_MS);
		} catch (IOException e) {
			LOG.error(MessageFormat.format(
					JGitText.get().forwarderFailedConnection,
					upstreamSocket.getInetAddress()), e);
			routingListener.onConnectException(request, e);
		}

		proxyBidirectional(request.socket(), upstreamSocket,
				request.commandInfo().recordedPrefix,
				e -> routingListener.afterOpenException(request, response, e));
		routingListener.onClose(request, response);
	}

	private void proxyBidirectional(Socket in, Socket out, byte[] prefix,
			Consumer<Exception> onException) {
		CountDownLatch latch = new CountDownLatch(1);
		Future<?> forwarderFuture = workerPool
				.submit(() -> forward(in, out, prefix, latch, onException));
		Future<?> responderFuture = workerPool
				.submit(() -> forward(out, in, null, latch, onException));

		try {
			forwarderFuture.get();
			responderFuture.get();

			latch.await();
		} catch (InterruptedException | ExecutionException e) {
			LOG.debug(MessageFormat.format(JGitText.get().forwarderFailedToPipe,
					in.getInetAddress(), out.getInetAddress()), e);
			onException.accept((Exception) e.getCause());
		} finally {
			forwarderFuture.cancel(true);
			responderFuture.cancel(true);
			closeQuietly(in);
			closeQuietly(out);
		}
	}

	private void forward(Socket source, Socket target, @Nullable byte[] prefix,
			CountDownLatch latch, Consumer<Exception> onException) {
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
			// When latch is counted down, the exception should have been
			// recorded by other the thread.
			// Avoid duplicate.
			if (latch.getCount() > 0) {
				LOG.error(MessageFormat.format(
						JGitText.get().forwarderFailedToPipe,
						source.getInetAddress(), target.getInetAddress()), e);
				onException.accept(e);
			}
		} finally {
			latch.countDown();
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
		try {
			serverSocket.close();
		} catch (IOException e) {
			LOG.debug(MessageFormat.format(
					JGitText.get().forwarderFailedToCloseSocket,
					serverSocket.getInetAddress()), e);
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
				LOG.debug(MessageFormat.format(
						JGitText.get().forwarderFailedToCloseSocket,
						serverSocket.getInetAddress()), e);
			}
		}
	}
}
