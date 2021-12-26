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

import java.io.IOException;

import org.eclipse.jgit.transport.sshd.agent.AbstractConnector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;

/**
 * A connector using Pageant's shared memory IPC mechanism.
 */
public class PageantConnector extends AbstractConnector {

	/**
	 * {@link ConnectorDescriptor} for the {@link PageantConnector}.
	 */
	public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor() {

		@Override
		public String getIdentityAgent() {
			// This must be an absolute Windows path name to avoid that
			// OpenSshConfigFile treats it as a relative path name. Use an UNC
			// name on localhost, like for pipes.
			return "\\\\.\\pageant"; //$NON-NLS-1$
		}

		@Override
		public String getDisplayName() {
			return Texts.get().pageant;
		}
	};

	private final PageantLibrary lib;

	/**
	 * Creates a new {@link PageantConnector}.
	 */
	public PageantConnector() {
		super(); // Use default maximum message size
		this.lib = new PageantLibrary();
	}

	@Override
	public boolean connect() throws IOException {
		return lib.isPageantAvailable();
	}

	@Override
	public void close() throws IOException {
		// Nothing to do
	}

	@Override
	public byte[] rpc(byte command, byte[] message) throws IOException {
		try (PageantLibrary.Pipe pipe = lib
				.createPipe(getClass().getSimpleName(),
						getMaximumMessageLength())) {
			prepareMessage(command, message);
			pipe.send(message);
			byte[] lengthBuf = new byte[4];
			pipe.receive(lengthBuf);
			int length = toLength(command, lengthBuf);
			byte[] payload = new byte[length];
			pipe.receive(payload);
			return payload;
		}
	}
}
