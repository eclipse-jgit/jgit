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

import static org.eclipse.jgit.transport.SshConstants.ENV_SSH_AUTH_SOCKET;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.common.SshException;
import org.eclipse.jgit.transport.sshd.agent.AbstractConnector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * JRE-based implementation of communication through a Unix domain socket.
 * Support added in JRE 16 with <a href="https://openjdk.org/jeps/380">JEP
 * 380</a>.
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

	private final UnixDomainSocketAddress socketAddress;

	private SocketChannel client;

	private AtomicBoolean connected = new AtomicBoolean();

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
		this.socketAddress = UnixDomainSocketAddress.of(file);
	}

	@Override
	public boolean connect() throws IOException {
		client = SocketChannel.open(StandardProtocolFamily.UNIX);
		client.bind(socketAddress);
		connected.set(true);
		return true;
	}

	@Override
	public synchronized void close() throws IOException {
		client.close();
	}

	@Override
	public byte[] rpc(byte command, byte[] message) throws IOException {
		prepareMessage(command, message);
		if (!connected.get()) {
			// No translation, internal error
			throw new IllegalStateException("Not connected to SSH agent"); //$NON-NLS-1$
		}
		writeFully(message);
		// Now receive the reply
		byte[] lengthBuf = new byte[4];
		readFully(lengthBuf);
		int length = toLength(command, lengthBuf);
		byte[] payload = new byte[length];
		readFully(payload);
		return payload;
	}

	private void writeFully(byte[] message) throws IOException {
		ByteBuffer byteBuffer = ByteBuffer.wrap(message);
		client.write(byteBuffer);
	}

	private void readFully(byte[] data) throws IOException {
		ByteBuffer byteBuffer = ByteBuffer.wrap(data);
		int offset = client.read(byteBuffer);
		if (offset < data.length) {
			throw new SshException(
					MessageFormat.format(Texts.get().msgShortRead,
							Integer.toString(data.length),
							Integer.toString(offset),
							Integer.toString(offset)));
		}
	}
}
