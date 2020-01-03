/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.transport.URIish;

/**
 * Indicates a protocol error has occurred while fetching/pushing objects.
 */
public class PackProtocolException extends TransportException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an PackProtocolException with the specified detail message
	 * prefixed with provided URI.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message, which may be shown to an end-user.
	 */
	public PackProtocolException(URIish uri, String s) {
		super(uri + ": " + s); //$NON-NLS-1$
	}

	/**
	 * Constructs an PackProtocolException with the specified detail message
	 * prefixed with provided URI.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message, which may be shown to an end-user.
	 * @param cause
	 *            root cause exception
	 */
	public PackProtocolException(final URIish uri, final String s,
			final Throwable cause) {
		this(uri + ": " + s, cause); //$NON-NLS-1$
	}

	/**
	 * Constructs an PackProtocolException with the specified detail message.
	 *
	 * @param s
	 *            message, which may be shown to an end-user.
	 */
	public PackProtocolException(String s) {
		super(s);
	}

	/**
	 * Constructs an PackProtocolException with the specified detail message.
	 *
	 * @param s
	 *            message, which may be shown to an end-user.
	 * @param cause
	 *            root cause exception
	 */
	public PackProtocolException(String s, Throwable cause) {
		super(s);
		initCause(cause);
	}
}
