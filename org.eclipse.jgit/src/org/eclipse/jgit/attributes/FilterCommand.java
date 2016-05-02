/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.attributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An abstraction for JGit's builtin implementations for hooks and filters.
 * Instead of spawning an external processes to start a filter/hook and to pump
 * data from/to stdin/stdout these builtin commmands may be used. They are
 * constructed by {@link FilterCommandFactory}.
 *
 * @since 4.6
 */
public abstract class FilterCommand {
	/**
	 * The {@link InputStream} this command should read from
	 */
	protected InputStream in;

	/**
	 * The {@link OutputStream} this command should write to
	 */
	protected OutputStream out;

	/**
	 * @param in
	 *            The {@link InputStream} this command should read from
	 * @param out
	 *            The {@link OutputStream} this command should write to
	 */
	public FilterCommand(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	/**
	 * Execute the command. The command is supposed to read data from
	 * {@link #in} and to write the result to {@link #out}. It returns the
	 * number of bytes it read from {@link #in}. It should be called in a loop
	 * until it returns -1 signaling that the {@link InputStream} is completely
	 * processed.
	 *
	 * @return the number of bytes read from the {@link InputStream} or -1. -1
	 *         means that the {@link InputStream} is completely processed.
	 * @throws IOException
	 *             when {@link IOException} occured while reading from
	 *             {@link #in} or writing to {@link #out}
	 *
	 */
	public abstract int run() throws IOException;
}
