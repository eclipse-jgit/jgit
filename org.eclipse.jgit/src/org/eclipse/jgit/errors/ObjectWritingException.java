/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Cannot store an object in the object database. This is a serious
 * error that users need to be made aware of.
 */
public class ObjectWritingException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an ObjectWritingException with the specified detail message.
	 *
	 * @param s message
	 */
	public ObjectWritingException(String s) {
		super(s);
	}

	/**
	 * Constructs an ObjectWritingException with the specified detail message.
	 *
	 * @param s message
	 * @param cause root cause exception
	 */
	public ObjectWritingException(String s, Throwable cause) {
		super(s);
		initCause(cause);
	}
}
