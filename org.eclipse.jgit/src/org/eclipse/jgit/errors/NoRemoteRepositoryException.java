/*
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
 * Indicates a remote repository does not exist.
 */
public class NoRemoteRepositoryException extends TransportException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an exception indicating a repository does not exist.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 */
	public NoRemoteRepositoryException(URIish uri, String s) {
		super(uri, s);
	}

	/**
	 * Constructs an exception indicating a repository does not exist.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 * @param cause
	 *            root cause exception
	 */
	public NoRemoteRepositoryException(URIish uri, String s, Throwable cause) {
		super(uri, s, cause);
	}
}
