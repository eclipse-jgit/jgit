/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.client.subsystem.sftp.SftpClient.CloseableHandle;
import org.apache.sshd.client.subsystem.sftp.SftpClient.CopyMode;
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.subsystem.sftp.SftpException;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.JGitSshClient;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.FtpChannel;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link RemoteSession} based on Apache MINA sshd.
 *
 * @since 5.2
 */
public class SshdSession implements RemoteSession {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshdSession.class);

	private static final Pattern SHORT_SSH_FORMAT = Pattern
			.compile("[-\\w.]+(?:@[-\\w.]+)?(?::\\d+)?"); //$NON-NLS-1$

	private final CopyOnWriteArrayList<SessionCloseListener> listeners = new CopyOnWriteArrayList<>();

	private final URIish uri;

	private SshClient client;

	private ClientSession session;

	SshdSession(URIish uri, Supplier<SshClient> clientFactory) {
		this.uri = uri;
		this.client = clientFactory.get();
	}

	void connect(Duration timeout) throws IOException {
		if (!client.isStarted()) {
			client.start();
		}
		try {
			long timeLimit = timeout.toMillis();
			long remainingTime[] = { timeLimit };
			session = connect(uri, Collections.emptyList(),
					future -> notifyCloseListeners(), remainingTime,
					timeLimit > 0);
		} catch (IOException e) {
			disconnect(e);
			throw e;
		}
	}

	@SuppressWarnings("resource")
	private ClientSession connect(URIish target, List<URIish> jumps,
			SshFutureListener<CloseFuture> listener, long[] timeout,
			boolean useTimeout) throws IOException {
		ClientSession resultSession = null;
		ClientSession proxySession = null;
		PortForwardingTracker portForward = null;
		try {
			List<URIish> hops = jumps;
			String username = target.getUser();
			String host = target.getHost();
			int port = target.getPort();
			HostConfigEntry hostConfig = getHostConfig(username, host, port);
			username = hostConfig.getUsername();
			host = hostConfig.getHostName();
			port = hostConfig.getPort();
			if (hops.isEmpty()) {
				String jumpHosts = hostConfig
						.getProperty(SshConstants.PROXY_JUMP);
				if (!StringUtils.isEmptyOrNull(jumpHosts)) {
					try {
						hops = parseProxyJump(jumpHosts);
					} catch (URISyntaxException e) {
						throw new IOException(MessageFormat.format(
								SshdText.get().configInvalidProxyJump,
								target.getHost(), jumpHosts), e);
					}
				}
			}

			if (!hops.isEmpty()) {
				URIish hop = hops.remove(0);
				if (LOG.isDebugEnabled()) {
					LOG.debug("Connecting to jump host {}", hop); //$NON-NLS-1$
				}
				proxySession = connect(hop, hops, null, timeout, useTimeout);

			}
			AttributeRepository context = null;
			if (proxySession != null) {
				SshdSocketAddress remoteAddress = new SshdSocketAddress(host,
						port);
				portForward = proxySession.createLocalPortForwardingTracker(
						SshdSocketAddress.LOCALHOST_ADDRESS, remoteAddress);
				// We must connect to the locally bound address, not the one
				// from the host config.
				context = AttributeRepository.ofKeyValuePair(
						JGitSshClient.LOCAL_FORWARD_ADDRESS,
						portForward.getBoundAddress());
			}
			if (!useTimeout) {
				resultSession = client.connect(hostConfig, context, null)
						.verify().getSession();
			} else {
				long start = System.currentTimeMillis();
				resultSession = client.connect(hostConfig, context, null)
						.verify(timeout[0]).getSession();
				timeout[0] -= System.currentTimeMillis() - start;
				if (timeout[0] <= 0) {
					// A very small grace period in case we have to connect
					// other sessions yet. DefaultSshFuture.await0() throws an
					// exception if the timeout ever becomes negative.
					timeout[0] = 10;
				}
			}
			if (proxySession != null) {
				final PortForwardingTracker tracker = portForward;
				final ClientSession pSession = proxySession;
				resultSession.addCloseFutureListener(future -> {
					IoUtils.closeQuietly(tracker);
					String sessionName = pSession.toString();
					try {
						pSession.close();
					} catch (IOException e) {
						LOG.error(MessageFormat.format(
								SshdText.get().sshProxySessionCloseFailed,
								sessionName), e);
					}
				});
				portForward = null;
				proxySession = null;
			}
			if (listener != null) {
				resultSession.addCloseFutureListener(listener);
			}
			// Authentication timeout is by default 2 minutes.
			resultSession.auth().verify(resultSession.getAuthTimeout());
			return resultSession;
		} catch (IOException e) {
			if (portForward != null) {
				IoUtils.closeQuietly(portForward);
			}
			if (proxySession != null) {
				proxySession.close();
			}
			if (resultSession != null) {
				resultSession.close();
			}
			throw e;
		}
	}

	private HostConfigEntry getHostConfig(String username, String host,
			int port) throws IOException {
		HostConfigEntry entry = client.getHostConfigEntryResolver()
				.resolveEffectiveHost(host, port, null, username, null);
		if (entry == null) {
			if (SshdSocketAddress.isIPv6Address(host)) {
				return new HostConfigEntry("", host, port, username); //$NON-NLS-1$
			}
			return new HostConfigEntry(host, host, port, username);
		}
		return entry;
	}

	private List<URIish> parseProxyJump(String proxyJump)
			throws URISyntaxException {
		String[] hops = proxyJump.split(","); //$NON-NLS-1$
		List<URIish> result = new LinkedList<>();
		for (String hop : hops) {
			if (SHORT_SSH_FORMAT.matcher(hop).matches()) {
				// URIish doesn't understand the short SSH format
				// user@host:port, only user@host:path
				hop = "ssh://" + hop; //$NON-NLS-1$
			}
			URIish to = new URIish(hop);
			result.add(to);
		}
		return result;
	}

	/**
	 * Adds a {@link SessionCloseListener} to this session. Has no effect if the
	 * given {@code listener} is already registered with this session.
	 *
	 * @param listener
	 *            to add
	 */
	public void addCloseListener(@NonNull SessionCloseListener listener) {
		listeners.addIfAbsent(listener);
	}

	/**
	 * Removes the given {@code listener}; has no effect if the listener is not
	 * currently registered with this session.
	 *
	 * @param listener
	 *            to remove
	 */
	public void removeCloseListener(@NonNull SessionCloseListener listener) {
		listeners.remove(listener);
	}

	private void notifyCloseListeners() {
		for (SessionCloseListener l : listeners) {
			try {
				l.sessionClosed(this);
			} catch (RuntimeException e) {
				LOG.warn(SshdText.get().closeListenerFailed, e);
			}
		}
	}

	@Override
	public Process exec(String commandName, int timeout) throws IOException {
		@SuppressWarnings("resource")
		ChannelExec exec = session.createExecChannel(commandName);
		long timeoutMillis = TimeUnit.SECONDS.toMillis(timeout);
		try {
			if (timeout <= 0) {
				exec.open().verify();
			} else {
				long start = System.nanoTime();
				exec.open().verify(timeoutMillis);
				timeoutMillis -= TimeUnit.NANOSECONDS
						.toMillis(System.nanoTime() - start);
			}
		} catch (IOException | RuntimeException e) {
			exec.close(true);
			throw e;
		}
		if (timeout > 0 && timeoutMillis <= 0) {
			// We have used up the whole timeout for opening the channel
			exec.close(true);
			throw new InterruptedIOException(
					format(SshdText.get().sshCommandTimeout, commandName,
							Integer.valueOf(timeout)));
		}
		return new SshdExecProcess(exec, commandName, timeoutMillis);
	}

	/**
	 * Obtain an {@link FtpChannel} to perform SFTP operations in this
	 * {@link SshdSession}.
	 */
	@Override
	@NonNull
	public FtpChannel getFtpChannel() {
		return new SshdFtpChannel();
	}

	@Override
	public void disconnect() {
		disconnect(null);
	}

	private void disconnect(Throwable reason) {
		try {
			if (session != null) {
				session.close();
				session = null;
			}
		} catch (IOException e) {
			if (reason != null) {
				reason.addSuppressed(e);
			} else {
				LOG.error(SshdText.get().sessionCloseFailed, e);
			}
		} finally {
			client.stop();
			client = null;
		}
	}

	private static class SshdExecProcess extends Process {

		private final ChannelExec channel;

		private final long timeoutMillis;

		private final String commandName;

		public SshdExecProcess(ChannelExec channel, String commandName,
				long timeoutMillis) {
			this.channel = channel;
			this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : -1L;
			this.commandName = commandName;
		}

		@Override
		public OutputStream getOutputStream() {
			return channel.getInvertedIn();
		}

		@Override
		public InputStream getInputStream() {
			return channel.getInvertedOut();
		}

		@Override
		public InputStream getErrorStream() {
			return channel.getInvertedErr();
		}

		@Override
		public int waitFor() throws InterruptedException {
			if (waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
				return exitValue();
			}
			return -1;
		}

		@Override
		public boolean waitFor(long timeout, TimeUnit unit)
				throws InterruptedException {
			long millis = timeout >= 0 ? unit.toMillis(timeout) : -1L;
			return channel
					.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), millis)
					.contains(ClientChannelEvent.CLOSED);
		}

		@Override
		public int exitValue() {
			Integer exitCode = channel.getExitStatus();
			if (exitCode == null) {
				throw new IllegalThreadStateException(
						format(SshdText.get().sshProcessStillRunning,
								commandName));
			}
			return exitCode.intValue();
		}

		@Override
		public void destroy() {
			if (channel.isOpen()) {
				channel.close(true);
			}
		}
	}

	/**
	 * Helper interface like {@link Supplier}, but possibly raising an
	 * {@link IOException}.
	 *
	 * @param <T>
	 *            return type
	 */
	@FunctionalInterface
	private interface FtpOperation<T> {

		T call() throws IOException;

	}

	private class SshdFtpChannel implements FtpChannel {

		private SftpClient ftp;

		/** Current working directory. */
		private String cwd = ""; //$NON-NLS-1$

		@Override
		public void connect(int timeout, TimeUnit unit) throws IOException {
			if (timeout <= 0) {
				session.getProperties().put(
						SftpClient.SFTP_CHANNEL_OPEN_TIMEOUT,
						Long.valueOf(Long.MAX_VALUE));
			} else {
				session.getProperties().put(
						SftpClient.SFTP_CHANNEL_OPEN_TIMEOUT,
						Long.valueOf(unit.toMillis(timeout)));
			}
			ftp = SftpClientFactory.instance().createSftpClient(session);
			try {
				cd(cwd);
			} catch (IOException e) {
				ftp.close();
			}
		}

		@Override
		public void disconnect() {
			try {
				ftp.close();
			} catch (IOException e) {
				LOG.error(SshdText.get().ftpCloseFailed, e);
			}
		}

		@Override
		public boolean isConnected() {
			return session.isAuthenticated() && ftp.isOpen();
		}

		private String absolute(String path) {
			if (path.isEmpty()) {
				return cwd;
			}
			// Note: there is no path injection vulnerability here. If
			// path has too many ".." components, we rely on the server
			// catching it and returning an error.
			if (path.charAt(0) != '/') {
				if (cwd.charAt(cwd.length() - 1) == '/') {
					return cwd + path;
				}
				return cwd + '/' + path;
			}
			return path;
		}

		private <T> T map(FtpOperation<T> op) throws IOException {
			try {
				return op.call();
			} catch (IOException e) {
				if (e instanceof SftpException) {
					throw new FtpChannel.FtpException(e.getLocalizedMessage(),
							((SftpException) e).getStatus(), e);
				}
				throw e;
			}
		}

		@Override
		public void cd(String path) throws IOException {
			cwd = map(() -> ftp.canonicalPath(absolute(path)));
			if (cwd.isEmpty()) {
				cwd += '/';
			}
		}

		@Override
		public String pwd() throws IOException {
			return cwd;
		}

		@Override
		public Collection<DirEntry> ls(String path) throws IOException {
			return map(() -> {
				List<DirEntry> result = new ArrayList<>();
				try (CloseableHandle handle = ftp.openDir(absolute(path))) {
					AtomicReference<Boolean> atEnd = new AtomicReference<>(
							Boolean.FALSE);
					while (!atEnd.get().booleanValue()) {
						List<SftpClient.DirEntry> chunk = ftp.readDir(handle,
								atEnd);
						if (chunk == null) {
							break;
						}
						for (SftpClient.DirEntry remote : chunk) {
							result.add(new DirEntry() {

								@Override
								public String getFilename() {
									return remote.getFilename();
								}

								@Override
								public long getModifiedTime() {
									return remote.getAttributes()
											.getModifyTime().toMillis();
								}

								@Override
								public boolean isDirectory() {
									return remote.getAttributes().isDirectory();
								}

							});
						}
					}
				}
				return result;
			});
		}

		@Override
		public void rmdir(String path) throws IOException {
			map(() -> {
				ftp.rmdir(absolute(path));
				return null;
			});

		}

		@Override
		public void mkdir(String path) throws IOException {
			map(() -> {
				ftp.mkdir(absolute(path));
				return null;
			});
		}

		@Override
		public InputStream get(String path) throws IOException {
			return map(() -> ftp.read(absolute(path)));
		}

		@Override
		public OutputStream put(String path) throws IOException {
			return map(() -> ftp.write(absolute(path)));
		}

		@Override
		public void rm(String path) throws IOException {
			map(() -> {
				ftp.remove(absolute(path));
				return null;
			});
		}

		@Override
		public void rename(String from, String to) throws IOException {
			map(() -> {
				String src = absolute(from);
				String dest = absolute(to);
				try {
					ftp.rename(src, dest, CopyMode.Atomic, CopyMode.Overwrite);
				} catch (UnsupportedOperationException e) {
					// Older server cannot do POSIX rename...
					if (!src.equals(dest)) {
						delete(dest);
						ftp.rename(src, dest);
					}
				}
				return null;
			});
		}
	}
}
