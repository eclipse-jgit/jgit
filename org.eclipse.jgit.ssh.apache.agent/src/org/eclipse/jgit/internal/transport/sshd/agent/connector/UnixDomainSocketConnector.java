/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent.connector;

import static org.eclipse.jgit.internal.transport.sshd.agent.connector.Sockets.AF_UNIX;
import static org.eclipse.jgit.internal.transport.sshd.agent.connector.Sockets.DEFAULT_PROTOCOL;
import static org.eclipse.jgit.internal.transport.sshd.agent.connector.Sockets.SOCK_STREAM;
import static org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixSockets.FD_CLOEXEC;
import static org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixSockets.F_SETFD;
import static org.eclipse.jgit.transport.SshConstants.ENV_SSH_AUTH_SOCKET;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.common.SshException;
import org.eclipse.jgit.transport.sshd.agent.AbstractConnector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.platform.unix.LibCAPI;

/**
 * JNA-based implementation of communication through a Unix domain socket.
 */
public class UnixDomainSocketConnector extends AbstractConnector {

	/**
	 * {@link ConnectorDescriptor} for the {@link UnixDomainSocketConnector}.
	 */
	public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor() {

		@Override
		public String getIdentityAgent() {
			return ENV_SSH_AUTH_SOCKET;
		}

		@Override
		public String getDisplayName() {
			return Texts.get().unixDefaultAgent;
		}
	};

	private static final Logger LOG = LoggerFactory
			.getLogger(UnixDomainSocketConnector.class);

	private static UnixSockets library;

	private static boolean libraryLoaded = false;

	private static synchronized UnixSockets getLibrary() {
		if (!libraryLoaded) {
			libraryLoaded = true;
			try {
				library = Native.load(UnixSockets.LIBRARY_NAME, UnixSockets.class);
			} catch (Exception | UnsatisfiedLinkError
					| NoClassDefFoundError e) {
				LOG.error(Texts.get().logErrorLoadLibrary, e);
			}
		}
		return library;
	}

	private final String socketFile;

	private AtomicBoolean connected = new AtomicBoolean();

	private volatile int socketFd = -1;

	/**
	 * Creates a new instance.
	 *
	 * @param socketFile
	 *            to use; if {@code null} or empty, use environment variable
	 *            SSH_AUTH_SOCK
	 */
	public UnixDomainSocketConnector(String socketFile) {
		super();
		String file = socketFile;
		if (StringUtils.isEmptyOrNull(file)
				|| ENV_SSH_AUTH_SOCKET.equals(file)) {
			file = SystemReader.getInstance().getenv(ENV_SSH_AUTH_SOCKET);
		}
		this.socketFile = file;
	}

	@Override
	public boolean connect() throws IOException {
		if (StringUtils.isEmptyOrNull(socketFile)) {
			return false;
		}
		int fd = socketFd;
		synchronized (this) {
			if (connected.get()) {
				return true;
			}
			UnixSockets sockets = getLibrary();
			if (sockets == null) {
				return false;
			}
			try {
				fd = sockets.socket(AF_UNIX, SOCK_STREAM, DEFAULT_PROTOCOL);
				// OS X apparently doesn't have SOCK_CLOEXEC, so we can't set it
				// atomically. Set it via fcntl, which exists on all systems
				// we're interested in.
				sockets.fcntl(fd, F_SETFD, FD_CLOEXEC);
				Sockets.SockAddr sockAddr = new Sockets.SockAddr(socketFile,
						StandardCharsets.UTF_8);
				sockets.connect(fd, sockAddr, sockAddr.size());
				connected.set(true);
			} catch (LastErrorException e) {
				if (fd >= 0) {
					try {
						sockets.close(fd);
					} catch (LastErrorException e1) {
						e.addSuppressed(e1);
					}
				}
				throw new IOException(MessageFormat
						.format(Texts.get().msgConnectFailed, socketFile), e);
			}
		}
		socketFd = fd;
		return connected.get();
	}

	@Override
	public synchronized void close() throws IOException {
		int fd = socketFd;
		if (connected.getAndSet(false) && fd >= 0) {
			socketFd = -1;
			try {
				getLibrary().close(fd);
			} catch (LastErrorException e) {
				throw new IOException(MessageFormat.format(
						Texts.get().msgCloseFailed, Integer.toString(fd)), e);
			}
		}
	}

	@Override
	public byte[] rpc(byte command, byte[] message) throws IOException {
		prepareMessage(command, message);
		int fd = socketFd;
		if (!connected.get() || fd < 0) {
			// No translation, internal error
			throw new IllegalStateException("Not connected to SSH agent"); //$NON-NLS-1$
		}
		writeFully(fd, message);
		// Now receive the reply
		byte[] lengthBuf = new byte[4];
		readFully(fd, lengthBuf);
		int length = toLength(command, lengthBuf);
		byte[] payload = new byte[length];
		readFully(fd, payload);
		return payload;
	}

	private void writeFully(int fd, byte[] message) throws IOException {
		int toWrite = message.length;
		try {
			byte[] buf = message;
			while (toWrite > 0) {
				int written = getLibrary()
						.write(fd, buf, new LibCAPI.size_t(buf.length))
						.intValue();
				if (written < 0) {
					throw new IOException(
							MessageFormat.format(Texts.get().msgSendFailed,
									Integer.toString(message.length),
									Integer.toString(toWrite)));
				}
				toWrite -= written;
				if (written > 0 && toWrite > 0) {
					buf = Arrays.copyOfRange(buf, written, buf.length);
				}
			}
		} catch (LastErrorException e) {
			throw new IOException(
					MessageFormat.format(Texts.get().msgSendFailed,
							Integer.toString(message.length),
							Integer.toString(toWrite)),
					e);
		}
	}

	private void readFully(int fd, byte[] data) throws IOException {
		int n = 0;
		int offset = 0;
		while (offset < data.length
				&& (n = read(fd, data, offset, data.length - offset)) > 0) {
			offset += n;
		}
		if (offset < data.length) {
			throw new SshException(
					MessageFormat.format(Texts.get().msgShortRead,
							Integer.toString(data.length),
							Integer.toString(offset), Integer.toString(n)));
		}
	}

	private int read(int fd, byte[] buffer, int offset, int length)
			throws IOException {
		try {
			LibCAPI.size_t toRead = new LibCAPI.size_t(length);
			if (offset == 0) {
				return getLibrary().read(fd, buffer, toRead).intValue();
			}
			byte[] data = new byte[length];
			int read = getLibrary().read(fd, data, toRead).intValue();
			if (read > 0) {
				System.arraycopy(data, 0, buffer, offset, read);
			}
			return read;
		} catch (LastErrorException e) {
			throw new IOException(
					MessageFormat.format(Texts.get().msgReadFailed,
							Integer.toString(length)),
					e);
		}
	}
}
