/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.errors.TransportException;

/**
 * Abstract class for creating connections capable of running external commands.
 * <p>
 * See JschConnection and ExtConnection inside {@link TransportGitSsh} for
 * example implementations.
 *
 */
public abstract class RemoteCommandConnection {
	final TransportGitSsh fTransport;

	/**
	 * @param transport
	 */
	public RemoteCommandConnection(TransportGitSsh transport) {
		fTransport = transport;
	}

	/**
	 * Start command execution only. Execution does not have to complete until
	 * close is called.
	 *
	 * @param commandName
	 * @param uri
	 *            necessary for creating a TransportException
	 * @throws TransportException
	 */
	public abstract void exec(String commandName, URIish uri)
			throws TransportException;

	/**
	 * Create a connection, if necessary.
	 * 
	 * @param uri
	 *            necessary for creating a TransportException
	 * @throws TransportException
	 */
	public abstract void connect(URIish uri) throws TransportException;

	/**
	 * @return InputStream
	 * @throws IOException
	 */
	public abstract InputStream getInputStream() throws IOException;

	/**
	 * @return OutputStream
	 * @throws IOException
	 */
	public abstract OutputStream getOutputStream() throws IOException;

	/**
	 * @return InputStream
	 * @throws IOException
	 */
	public abstract InputStream getErrorStream() throws IOException;

	/**
	 * Note that this is called after close
	 *
	 * @return exit status
	 */
	public abstract int getExitStatus();

	/**
	 * Complete command execution (cancel if necessary) and set the exit status.
	 */
	public abstract void close();
}
