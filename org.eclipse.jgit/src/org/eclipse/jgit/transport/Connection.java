/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.lib.Ref;

/**
 * Represent connection for operation on a remote repository.
 * <p>
 * Currently all operations on remote repository (fetch and push) provide
 * information about remote refs. Every connection is able to be closed and
 * should be closed - this is a connection client responsibility.
 *
 * @see Transport
 */
public interface Connection extends AutoCloseable {
	/**
	 * Get the complete map of refs advertised as available for fetching or
	 * pushing.
	 *
	 * @return available/advertised refs: map of refname to ref. Never null. Not
	 *         modifiable. The collection can be empty if the remote side has no
	 *         refs (it is an empty/newly created repository).
	 */
	public Map<String, Ref> getRefsMap();

	/**
	 * Get the complete list of refs advertised as available for fetching or
	 * pushing.
	 * <p>
	 * The returned refs may appear in any order. If the caller needs these to
	 * be sorted, they should be copied into a new array or List and then sorted
	 * by the caller as necessary.
	 *
	 * @return available/advertised refs. Never null. Not modifiable. The
	 *         collection can be empty if the remote side has no refs (it is an
	 *         empty/newly created repository).
	 */
	public Collection<Ref> getRefs();

	/**
	 * Get a single advertised ref by name.
	 * <p>
	 * The name supplied should be valid ref name. To get a peeled value for a
	 * ref (aka <code>refs/tags/v1.0^{}</code>) use the base name (without
	 * the <code>^{}</code> suffix) and look at the peeled object id.
	 *
	 * @param name
	 *            name of the ref to obtain.
	 * @return the requested ref; null if the remote did not advertise this ref.
	 */
	public Ref getRef(final String name);

	/**
	 * Close any resources used by this connection.
	 * <p>
	 * If the remote repository is contacted by a network socket this method
	 * must close that network socket, disconnecting the two peers. If the
	 * remote repository is actually local (same system) this method must close
	 * any open file handles used to read the "remote" repository.
	 * <p>
	 * If additional messages were produced by the remote peer, these should
	 * still be retained in the connection instance for {@link #getMessages()}.
	 * <p>
	 * {@code AutoClosable.close()} declares that it throws {@link Exception}.
	 * Implementers shouldn't throw checked exceptions. This override narrows
	 * the signature to prevent them from doing so.
	 */
	@Override
	public void close();

	/**
	 * Get the additional messages, if any, returned by the remote process.
	 * <p>
	 * These messages are most likely informational or error messages, sent by
	 * the remote peer, to help the end-user correct any problems that may have
	 * prevented the operation from completing successfully. Application UIs
	 * should try to show these in an appropriate context.
	 * <p>
	 * The message buffer is available after {@link #close()} has been called.
	 * Prior to closing the connection, the message buffer may be empty.
	 *
	 * @return the messages returned by the remote, most likely terminated by a
	 *         newline (LF) character. The empty string is returned if the
	 *         remote produced no additional messages.
	 */
	public String getMessages();

	/**
	 * User agent advertised by the remote server.
	 *
	 * @return agent (version of Git) running on the remote server. Null if the
	 *         server does not advertise this version.
	 * @since 4.0
	 */
	public String getPeerUserAgent();
}
