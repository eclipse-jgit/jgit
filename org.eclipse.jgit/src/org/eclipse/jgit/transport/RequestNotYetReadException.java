/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

/**
 * Indicates that a client request has not yet been read from the wire.
 *
 * @since 2.0
 */
public class RequestNotYetReadException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	/**
	 * Initialize with no message.
	 */
	public RequestNotYetReadException() {
		// Do not set a message.
	}

	/**
	 * <p>Constructor for RequestNotYetReadException.</p>
	 *
	 * @param msg
	 *            a message explaining the state. This message should not
	 *            be shown to an end-user.
	 */
	public RequestNotYetReadException(String msg) {
		super(msg);
	}
}
