/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.errors;

import java.io.IOException;

import org.eclipse.jgit.transport.URIish;

/**
 * Indicates a protocol error has occurred while fetching/pushing objects.
 */
public class TransportException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an TransportException with the specified detail message
	 * prefixed with provided URI.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 */
	public TransportException(URIish uri, String s) {
		super(uri.setPass(null) + ": " + s); //$NON-NLS-1$
	}

	/**
	 * Constructs an TransportException with the specified detail message
	 * prefixed with provided URI.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 * @param cause
	 *            root cause exception
	 */
	public TransportException(final URIish uri, final String s,
			final Throwable cause) {
		this(uri.setPass(null) + ": " + s, cause); //$NON-NLS-1$
	}

	/**
	 * Constructs an TransportException with the specified detail message.
	 *
	 * @param s
	 *            message
	 */
	public TransportException(String s) {
		super(s);
	}

	/**
	 * Constructs an TransportException with the specified detail message.
	 *
	 * @param s
	 *            message
	 * @param cause
	 *            root cause exception
	 */
	public TransportException(String s, Throwable cause) {
		super(s);
		initCause(cause);
	}
}
