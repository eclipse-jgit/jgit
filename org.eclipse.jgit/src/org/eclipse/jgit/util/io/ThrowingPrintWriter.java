/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
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
package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.eclipse.jgit.util.SystemReader;

/**
 * An alternative PrintWriter that doesn't catch exceptions.
 *
 * @since 2.2
 */
public class ThrowingPrintWriter extends Writer {

	private final Writer out;

	private final String LF;

	/**
	 * Construct a JGitPrintWriter
	 *
	 * @param out
	 *            the underlying {@link java.io.Writer}
	 */
	public ThrowingPrintWriter(Writer out) {
		this.out = out;
		LF = AccessController
				.doPrivileged((PrivilegedAction<String>) () -> SystemReader
						.getInstance().getProperty("line.separator") //$NON-NLS-1$
				);
	}

	/** {@inheritDoc} */
	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		out.write(cbuf, off, len);
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		out.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		out.close();
	}

	/**
	 * Print a string and terminate with a line feed.
	 *
	 * @param s
	 *            a {@link java.lang.String} object.
	 * @throws java.io.IOException
	 */
	public void println(String s) throws IOException {
		print(s + LF);
	}

	/**
	 * Print a platform dependent new line
	 *
	 * @throws java.io.IOException
	 */
	public void println() throws IOException {
		print(LF);
	}

	/**
	 * Print a char
	 *
	 * @param value
	 *            a char.
	 * @throws java.io.IOException
	 */
	public void print(char value) throws IOException {
		print(String.valueOf(value));
	}

	/**
	 * Print an int as string
	 *
	 * @param value
	 *            an int.
	 * @throws java.io.IOException
	 */
	public void print(int value) throws IOException {
		print(String.valueOf(value));
	}

	/**
	 * Print a long as string
	 *
	 * @param value
	 *            a long.
	 * @throws java.io.IOException
	 */
	public void print(long value) throws IOException {
		print(String.valueOf(value));
	}

	/**
	 * Print a short as string
	 *
	 * @param value
	 *            a short.
	 * @throws java.io.IOException
	 */
	public void print(short value) throws IOException {
		print(String.valueOf(value));
	}

	/**
	 * Print a formatted message according to
	 * {@link java.lang.String#format(String, Object...)}.
	 *
	 * @param fmt
	 *            a {@link java.lang.String} object.
	 * @param args
	 *            objects.
	 * @throws java.io.IOException
	 */
	public void format(String fmt, Object... args) throws IOException {
		print(String.format(fmt, args));
	}

	/**
	 * Print an object's toString representations
	 *
	 * @param any
	 *            an object.
	 * @throws java.io.IOException
	 */
	public void print(Object any) throws IOException {
		out.write(String.valueOf(any));
	}
}
