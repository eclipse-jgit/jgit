/*
 * Copyright (C) 2018, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static java.text.MessageFormat.format;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE;
import static org.apache.sshd.sftp.SftpModuleProperties.SFTP_CHANNEL_OPEN_TIMEOUT;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.io.functors.IOFunction;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.CopyMode;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpException;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile;
import org.eclipse.jgit.internal.transport.sshd.AuthenticationCanceledException;
import org.eclipse.jgit.internal.transport.sshd.AuthenticationLogger;
import org.eclipse.jgit.internal.transport.sshd.JGitSshClient;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.FtpChannel;
import org.eclipse.jgit.transport.RemoteSession2;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link org.eclipse.jgit.transport.RemoteSession
 * RemoteSession} based on Apache MINA sshd.
 *
 * @since 5.2
 */
public class SshdSession implements RemoteSession2 {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshdSession.class);

	private static final Pattern SHORT_SSH_FORMAT = Pattern
			.compile("[-\\w.]+(?:@[-\\w.]+)?(?::\\d+)?"); //$NON-NLS-1$

	private static final int MAX_DEPTH = 10;

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
			session = connect(uri, Collections.emptyList(),
					future -> notifyCloseListeners(), timeout, MAX_DEPTH);
		} catch (IOException e) {
			disconnect(e);
			throw e;
		}
	}

	private ClientSession connect(URIish target, List<URIish> jumps,
			SshFutureListener<CloseFuture> listener, Duration timeout,
			int depth) throws IOException {
		if (--depth < 0) {
			throw new IOException(
					format(SshdText.get().proxyJumpAbort, target));
		}
		HostConfigEntry hostConfig = getHostConfig(target.getUser(),
				target.getHost(), target.getPort());
		String host = hostConfig.getHostName();
		int port = hostConfig.getPort();
		List<URIish> hops = determineHops(jumps, hostConfig, target.getHost());
		ClientSession resultSession = null;
		ClientSession proxySession = null;
		PortForwardingTracker portForward = null;
		AuthenticationLogger authLog = null;
		try {
			if (!hops.isEmpty()) {
				URIish hop = hops.remove(0);
				if (LOG.isDebugEnabled()) {
					LOG.debug("Connecting to jump host {}", hop); //$NON-NLS-1$
				}
				proxySession = connect(hop, hops, null, timeout, depth);
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
			int timeoutInSec = OpenSshConfigFile.timeSpec(
					hostConfig.getProperty(SshConstants.CONNECT_TIMEOUT));
			resultSession = connect(hostConfig, context,
					timeoutInSec > 0 ? Duration.ofSeconds(timeoutInSec)
							: timeout);
			if (proxySession != null) {
				final PortForwardingTracker tracker = portForward;
				final ClientSession pSession = proxySession;
				resultSession.addCloseFutureListener(future -> {
					IoUtils.closeQuietly(tracker);
					String sessionName = pSession.toString();
					try {
						pSession.close();
					} catch (IOException e) {
						LOG.error(format(
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
			authLog = new AuthenticationLogger(resultSession);
			resultSession.auth().verify(resultSession.getAuthTimeout());
			return resultSession;
		} catch (IOException e) {
			close(portForward, e);
			close(proxySession, e);
			close(resultSession, e);
			if (e instanceof SshException && ((SshException) e)
					.getDisconnectCode() == SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE) {
				String message = format(SshdText.get().loginDenied, host,
						Integer.toString(port));
				throw new TransportException(target,
						withAuthLog(message, authLog), e);
			} else if (e instanceof SshException && e
					.getCause() instanceof AuthenticationCanceledException) {
				String message = e.getCause().getMessage();
				throw new TransportException(target,
						withAuthLog(message, authLog), e.getCause());
			}
			throw e;
		} finally {
			if (authLog != null) {
				authLog.clear();
			}
		}
	}

	private String withAuthLog(String message, AuthenticationLogger authLog) {
		if (authLog != null) {
			String log = String.join(System.lineSeparator(), authLog.getLog());
			if (!log.isEmpty()) {
				return message + System.lineSeparator() + log;
			}
		}
		return message;
	}

	private ClientSession connect(HostConfigEntry config,
			AttributeRepository context, Duration timeout)
			throws IOException {
		ConnectFuture connected = client.connect(config, context, null);
		long timeoutMillis = timeout.toMillis();
		if (timeoutMillis <= 0) {
			connected = connected.verify();
		} else {
			connected = connected.verify(timeoutMillis);
		}
		return connected.getSession();
	}

	private void close(Closeable toClose, Throwable error) {
		if (toClose != null) {
			try {
				toClose.close();
			} catch (IOException e) {
				error.addSuppressed(e);
			}
		}
	}

	private HostConfigEntry getHostConfig(String username, String host,
			int port) throws IOException {
		HostConfigEntry entry = client.getHostConfigEntryResolver()
				.resolveEffectiveHost(host, port, null, username, null, null);
		if (entry == null) {
			if (SshdSocketAddress.isIPv6Address(host)) {
				return new HostConfigEntry("", host, port, username); //$NON-NLS-1$
			}
			return new HostConfigEntry(host, host, port, username);
		}
		return entry;
	}

	private List<URIish> determineHops(List<URIish> currentHops,
			HostConfigEntry hostConfig, String host) throws IOException {
		if (currentHops.isEmpty()) {
			String jumpHosts = hostConfig.getProperty(SshConstants.PROXY_JUMP);
			if (!StringUtils.isEmptyOrNull(jumpHosts)
					&& !SshConstants.NONE.equals(jumpHosts)) {
				try {
					return parseProxyJump(jumpHosts);
				} catch (URISyntaxException e) {
					throw new IOException(
							format(SshdText.get().configInvalidProxyJump, host,
									jumpHosts),
							e);
				}
			}
		}
		return currentHops;
	}

	private List<URIish> parseProxyJump(String proxyJump)
			throws URISyntaxException {
		String[] hops = proxyJump.split(","); //$NON-NLS-1$
		List<URIish> result = new LinkedList<>();
		for (String hop : hops) {
			// There shouldn't be any whitespace, but let's be lenient
			hop = hop.trim();
			if (SHORT_SSH_FORMAT.matcher(hop).matches()) {
				// URIish doesn't understand the short SSH format
				// user@host:port, only user@host:path
				hop = SshConstants.SSH_SCHEME + "://" + hop; //$NON-NLS-1$
			}
			URIish to = new URIish(hop);
			if (!SshConstants.SSH_SCHEME.equalsIgnoreCase(to.getScheme())) {
				throw new URISyntaxException(hop,
						SshdText.get().configProxyJumpNotSsh);
			} else if (!StringUtils.isEmptyOrNull(to.getPath())) {
				throw new URISyntaxException(hop,
						SshdText.get().configProxyJumpWithPath);
			}
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
		return exec(commandName, Collections.emptyMap(), timeout);
	}

	@Override
	public Process exec(String commandName, Map<String, String> environment,
			int timeout) throws IOException {
		@SuppressWarnings("resource")
		ChannelExec exec = session.createExecChannel(commandName, null,
				environment);
		if (timeout <= 0) {
			try {
				exec.open().verify();
			} catch (IOException | RuntimeException e) {
				exec.close(true);
				throw e;
			}
		} else {
			try {
				exec.open().verify(TimeUnit.SECONDS.toMillis(timeout));
			} catch (IOException | RuntimeException e) {
				exec.close(true);
				throw new IOException(format(SshdText.get().sshCommandTimeout,
						commandName, Integer.valueOf(timeout)), e);
			}
		}
		return new SshdExecProcess(exec, commandName);
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

		private final String commandName;

		public SshdExecProcess(ChannelExec channel, String commandName) {
			this.channel = channel;
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
			if (waitFor(-1L, TimeUnit.MILLISECONDS)) {
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
				channel.close(false);
			}
		}
	}

	private class SshdFtpChannel implements FtpChannel {

		private SftpClient ftp;

		/** Current working directory. */
		private String cwd = ""; //$NON-NLS-1$

		@Override
		public void connect(int timeout, TimeUnit unit) throws IOException {
			if (timeout <= 0) {
				// This timeout must not be null!
				SFTP_CHANNEL_OPEN_TIMEOUT.set(session,
						Duration.ofMillis(Long.MAX_VALUE));
			} else {
				SFTP_CHANNEL_OPEN_TIMEOUT.set(session,
						Duration.ofMillis(unit.toMillis(timeout)));
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

		private <T> T map(IOFunction<Void, T> op) throws IOException {
			try {
				return op.apply(null);
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
			cwd = map(x -> ftp.canonicalPath(absolute(path)));
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
			return map(x -> {
				List<DirEntry> result = new ArrayList<>();
				for (SftpClient.DirEntry remote : ftp.readDir(absolute(path))) {
					result.add(new DirEntry() {

						@Override
						public String getFilename() {
							return remote.getFilename();
						}

						@Override
						public long getModifiedTime() {
							return remote.getAttributes().getModifyTime()
									.toMillis();
						}

						@Override
						public boolean isDirectory() {
							return remote.getAttributes().isDirectory();
						}

					});
				}
				return result;
			});
		}

		@Override
		public void rmdir(String path) throws IOException {
			map(x -> {
				ftp.rmdir(absolute(path));
				return null;
			});

		}

		@Override
		public void mkdir(String path) throws IOException {
			map(x -> {
				ftp.mkdir(absolute(path));
				return null;
			});
		}

		@Override
		public InputStream get(String path) throws IOException {
			return map(x -> ftp.read(absolute(path)));
		}

		@Override
		public OutputStream put(String path) throws IOException {
			return map(x -> ftp.write(absolute(path)));
		}

		@Override
		public void rm(String path) throws IOException {
			map(x -> {
				ftp.remove(absolute(path));
				return null;
			});
		}

		@Override
		public void rename(String from, String to) throws IOException {
			map(x -> {
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
