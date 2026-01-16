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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.PacketLineIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal TCP forwarder with a single routing listener and a simple proxy loop.
 *
 * @since 7.6
 */
public class GitForwarder implements AutoCloseable {
	/**
	 * Listener invoked for connection lifecycle events.
	 *
	 * @since 7.6
	 */
	public interface RoutingListener {
		/**
		 * Called when a client connects.
		 *
		 * @param request
		 *            request information
		 * @return routing response
		 */
		@Nullable
		RouteResponse onConnect(RouteRequest request);

		/**
		 * Called when a connection closes.
		 *
		 * @param request
		 *            request information
		 * @param response
		 *            routing response
		 */
		void onClose(RouteRequest request, @Nullable RouteResponse response);

		/**
		 * Called when the connection could not be established.
		 *
		 * @param request
		 *            request information
		 * @param error
		 *            exception encountered while handling the connection
		 */
		void onException(RouteRequest request, IOException error);

		/**
		 * Called when an exception occurs after a route was opened.
		 *
		 * @param request
		 *            request information
		 * @param response
		 *            routing response
		 * @param error
		 *            exception encountered while proxying
		 */
		void onOpenException(RouteRequest request,
				@Nullable RouteResponse response, Exception error);
	}

	/**
	 * Simple fixed-route listener for stubbing.
	 *
	 * @since 7.6
	 */
	public static final class FixedRouteListener implements RoutingListener {
		final RouteResponse response;

		/**
		 * @param destinationHost
		 *            destination host
		 * @param destinationPort
		 *            destination port
		 */
		public FixedRouteListener(@NonNull String destinationHost, int destinationPort) {
			this.response = new RouteResponse(destinationHost, destinationPort);
		}

		@Override
		public RouteResponse onConnect(RouteRequest request) {
			return response;
		}

		@Override
		public void onClose(RouteRequest request, @Nullable RouteResponse routeResponse) { }

		@Override
		public void onException(RouteRequest request, IOException error) {
			// No-op
		}

		@Override
		public void onOpenException(RouteRequest request,
				@Nullable RouteResponse routeResponse, Exception error) {
			// No-op
		}
	}

	/**
	 * Input stream that records bytes read from the delegate.
	 *
	 * @since 7.6
	 */
	public static final class RecordingInputStream extends InputStream {
		final InputStream delegate;
		final ByteArrayOutputStream recorded = new ByteArrayOutputStream();

		/**
		 * @param delegate
		 *            stream to wrap
		 */
		public RecordingInputStream(InputStream delegate) {
			this.delegate = delegate;
		}

		@Override
		public int read() throws IOException {
			int b = delegate.read();
			if (b >= 0) {
				recordByte((byte) b);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = delegate.read(b, off, len);
			if (read > 0) {
				recordBytes(b, off, read);
			}
			return read;
		}

		private void recordByte(byte b) {
			recorded.write(b);
		}

		private void recordBytes(byte[] b, int off, int len) {
			recorded.write(b, off, len);
		}

		private byte[] getRecordedBytes() {
			return recorded.toByteArray();
		}
	}

	/**
	 * Parsed command metadata from the first pkt-line.
	 *
	 * @since 7.6
	 */
	public static final class CommandInfo {
		static final String NUL_NUL = "\0\0"; //$NON-NLS-1$
		final String project;
		final String command;
		final String host;
		final Collection<String> extraParameters;
		final byte[] recordedPrefix;

		/**
		 * Parse the first pkt-line from the client and expose command metadata.
		 *
		 * @param client client socket providing the pkt-line
		 * @throws IOException if reading the pkt-line fails
		 */
		public CommandInfo(Socket client) throws IOException {
			RecordingInputStream stream = new RecordingInputStream(client.getInputStream());
			String line = new PacketLineIn(stream).readStringRaw();
			this.recordedPrefix = stream.getRecordedBytes();

			int nulnul = line.indexOf(NUL_NUL);
			extraParameters = nulnul != -1 ? Arrays.asList(line.substring(nulnul + 2).split("\0")) : List.of(); //$NON-NLS-1$

			String resolvedCommand = null;
			String resolvedArgument = null;
			for (String candidate : new String[] { "git-upload-pack ", //$NON-NLS-1$
					"git-receive-pack " }) { //$NON-NLS-1$
				String argumentValue = parseCommandArgument(line, candidate);
				if (argumentValue != null) {
					resolvedCommand = candidate.trim();
					resolvedArgument = argumentValue;
					break;
				}
			}
			this.command = resolvedCommand;
			if (resolvedArgument != null) {
				String stripped = stripLeadingSlashes(resolvedArgument);
				this.project = stripped.isEmpty() ? null : stripped;
			} else {
				this.project = null;
			}
			this.host = parseHostFromLine(line);
		}

		/**
		 * @param line
		 *            packet line payload
		 * @return requested host, or {@code null} if not present
		 */
		@Nullable
		public static String parseHostFromLine(String line) {
			String marker = "host="; //$NON-NLS-1$
			int idx = line.indexOf(marker);
			if (idx < 0) {
				return null;
			}

			int start = idx + marker.length();
			int end = line.indexOf('\0', start);
			if (end < 0) {
				return null;
			}

			return line.substring(start, end);
		}

		/**
		 * Extract the command argument from a git service line.
		 * <p>
		 * Example input: "git-receive-pack /example/repo.git\0"
		 * returns "example/repo.git".
		 *
		 * @param line packet line payload
		 * @param command command prefix to search
		 * @return command argument, or {@code null} if not found
		 */
		@Nullable
		public static String parseCommandArgument(String line, String command) {
			int idx = line.indexOf(command);
			if (idx < 0) {
				return null;
			}

			String rest = line.substring(idx + command.length());

			int end = rest.indexOf('\0');
			if (end < 0) {
				return null;
			}

			return rest.substring(0, end);
		}

		/**
		 * @param value
		 *            value to normalize
		 * @return value without leading slashes
		 */
		public static String stripLeadingSlashes(String value) {
			int start = 0;
			while (start < value.length() && value.charAt(start) == '/') {
				start++;
			}
			return start == 0 ? value : value.substring(start);
		}
	}

	static final Logger LOG = LoggerFactory.getLogger(GitForwarder.class);
	final String listenHost;
	final int listenPort;
	final RoutingListener routingListener;
	final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
	final ExecutorService workerPool;
	volatile ServerSocket serverSocket;

	/**
	 * Creates a forwarder that listens on the given host and 9418 port.
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
	 * Creates a forwarder that listens on the given host and port.
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
			request = new RouteRequest(commandInfo,
				clientSocket.getInetAddress().getHostAddress(), listenHost,
				listenPort);
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
				upstreamSocket.connect(new InetSocketAddress(response.destinationHost, response.destinationPort));
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

	private void forward(Socket source, Socket target, @Nullable byte[] prefix,
			AtomicBoolean done, Supplier<Thread> peerThread, Consumer<Exception> onException) {
		try (InputStream in = source.getInputStream();
				OutputStream out = target.getOutputStream()) {
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
			onException.accept(e);
		} finally {
			if (done.compareAndSet(false, true)) {
				peerThread.get().interrupt();
				closeQuietly(source);
				closeQuietly(target);
			}
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
				// Ignore
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

	/**
	 * Route request metadata.
	 *
	 * @param commandInfo parsed command info, if available
	 * @param sourceIp source IP address, if available
	 * @param listenHost listen host
	 * @param listenPort listen port
	 *
	 * @since 7.6
	 */
	public record RouteRequest(
		@NonNull CommandInfo commandInfo,
		@NonNull String sourceIp,
		@NonNull String listenHost,
		int listenPort) {
	}

	/**
	 * Route response from the listener.
	 *
	 * @param destinationHost destination host
	 * @param destinationPort destination port
	 *
	 * @since 7.6
	 */
	public record RouteResponse(@NonNull String destinationHost, int destinationPort) {}

	/**
	 * Runs a simple TCP forwarder on localhost:9419 with a fixed destination to
	 * localhost:9418.
	 *
	 * @param args
	 *            listenHost listenPort destHost destPort
	 * @throws Exception
	 *             if the forwarder cannot be started
	 */
	public static void main(String[] args) throws Exception {
		GitForwarder forwarder = new GitForwarder(
				"localhost", 9419, new FixedRouteListener("localhost", 9418), Executors.newCachedThreadPool()); //$NON-NLS-1$ //$NON-NLS-2$
		CountDownLatch latch = new CountDownLatch(1);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			forwarder.close();
			latch.countDown();
		}));
		latch.await();
	}
}
