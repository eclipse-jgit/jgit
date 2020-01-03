/*
 * Copyright (C) 2011-2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates a transport service may not continue execution.
 *
 * @since 2.0
 */
public class ServiceMayNotContinueException extends IOException {
	private static final int FORBIDDEN = 403;
	private static final long serialVersionUID = 1L;

	private final int statusCode;
	private boolean output;

	/**
	 * Initialize with no message.
	 */
	public ServiceMayNotContinueException() {
		// Do not set a message.
		statusCode = FORBIDDEN;
	}

	/**
	 * <p>Constructor for ServiceMayNotContinueException.</p>
	 *
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 */
	public ServiceMayNotContinueException(String msg) {
		super(msg);
		statusCode = FORBIDDEN;
	}

	/**
	 * <p>Constructor for ServiceMayNotContinueException.</p>
	 *
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 * @param statusCode
	 *            the HTTP status code.
	 * @since 4.5
	 */
	public ServiceMayNotContinueException(String msg, int statusCode) {
		super(msg);
		this.statusCode = statusCode;
	}

	/**
	 * <p>Constructor for ServiceMayNotContinueException.</p>
	 *
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 * @param cause
	 *            the cause of the exception.
	 * @since 3.2
	 */
	public ServiceMayNotContinueException(String msg, Throwable cause) {
		super(msg, cause);
		statusCode = FORBIDDEN;
	}

	/**
	 * <p>Constructor for ServiceMayNotContinueException.</p>
	 *
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 * @param cause
	 *            the cause of the exception.
	 * @param statusCode
	 *            the HTTP status code.
	 * @since 4.5
	 */
	public ServiceMayNotContinueException(
			String msg, Throwable cause, int statusCode) {
		super(msg, cause);
		this.statusCode = statusCode;
	}

	/**
	 * Initialize with an "internal server error" message and a cause.
	 *
	 * @param cause
	 *            the cause of the exception.
	 * @since 3.2
	 */
	public ServiceMayNotContinueException(Throwable cause) {
		this(JGitText.get().internalServerError, cause);
	}

	/**
	 * Whether the message was already output to the client.
	 *
	 * @return {@code true} if the message was already output to the client.
	 */
	public boolean isOutput() {
		return output;
	}

	/**
	 * Mark this message has being sent to the client.
	 */
	public void setOutput() {
		output = true;
	}

	/**
	 * Get status code
	 *
	 * @return true if the message was already output to the client.
	 * @since 4.5
	 */
	public int getStatusCode() {
		return statusCode;
	}
}
