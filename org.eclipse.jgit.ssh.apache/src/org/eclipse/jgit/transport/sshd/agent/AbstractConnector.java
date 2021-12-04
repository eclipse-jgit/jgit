/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd.agent;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Objects;

import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.eclipse.jgit.internal.transport.sshd.SshdText;

/**
 * Provides some utility methods for implementing {@link Connector}s.
 *
 * @since 6.0
 */
public abstract class AbstractConnector implements Connector {

	// A somewhat sane lower bound for the maximum reply length
	private static final int MIN_REPLY_LENGTH = 8 * 1024;

	/**
	 * Default maximum reply length. 256kB is the OpenSSH limit.
	 */
	protected static final int DEFAULT_MAX_REPLY_LENGTH = 256 * 1024;

	private final int maxReplyLength;

	/**
	 * Creates a new instance using the {@link #DEFAULT_MAX_REPLY_LENGTH}.
	 */
	protected AbstractConnector() {
		this(DEFAULT_MAX_REPLY_LENGTH);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param maxReplyLength
	 *            maximum number of payload bytes we're ready to accept
	 */
	protected AbstractConnector(int maxReplyLength) {
		if (maxReplyLength < MIN_REPLY_LENGTH) {
			throw new IllegalArgumentException(
					"Maximum payload length too small"); //$NON-NLS-1$
		}
		this.maxReplyLength = maxReplyLength;
	}

	/**
	 * Retrieves the maximum message length this {@link AbstractConnector} is
	 * configured for.
	 *
	 * @return the maximum message length
	 */
	protected int getMaximumMessageLength() {
		return this.maxReplyLength;
	}

	/**
	 * Prepares a message for sending by inserting the command and message
	 * length.
	 *
	 * @param command
	 *            SSH agent command the request is for
	 * @param message
	 *            about to be sent, including the 5 spare bytes at the front
	 * @throws IllegalArgumentException
	 *             if {@code message} has less than 5 bytes
	 */
	protected void prepareMessage(byte command, byte[] message)
			throws IllegalArgumentException {
		Objects.requireNonNull(message);
		if (message.length < 5) {
			// No translation; internal error
			throw new IllegalArgumentException("Message buffer for " //$NON-NLS-1$
					+ SshAgentConstants.getCommandMessageName(command)
					+ " must have at least 5 bytes; have only " //$NON-NLS-1$
					+ message.length);
		}
		BufferUtils.putUInt(message.length - 4, message);
		message[4] = command;
	}

	/**
	 * Checks the received length of a reply.
	 *
	 * @param command
	 *            SSH agent command the reply is for
	 * @param length
	 *            length as received: number of payload bytes
	 * @return the length as an {@code int}
	 * @throws IOException
	 *             if the length is invalid
	 */
	protected int toLength(byte command, byte[] length)
			throws IOException {
		long l = BufferUtils.getUInt(length);
		if (l <= 0 || l > maxReplyLength - 4) {
			throw new SshException(MessageFormat.format(
					SshdText.get().sshAgentReplyLengthError,
					Long.toString(l),
					SshAgentConstants.getCommandMessageName(command)));
		}
		return (int) l;
	}
}
