/*
 * Copyright (C) 2010, Google Inc.
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

import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;

/**
 * Base helper class for implementing operations connections.
 *
 * @see BasePackConnection
 * @see BaseFetchConnection
 */
public abstract class BaseConnection implements Connection {
	private Map<String, Ref> advertisedRefs = Collections.emptyMap();

	private String peerUserAgent;

	private boolean startedOperation;

	private Writer messageWriter;

	@Override
	public Map<String, Ref> getRefsMap() {
		return advertisedRefs;
	}

	@Override
	public final Collection<Ref> getRefs() {
		return advertisedRefs.values();
	}

	@Override
	public final Ref getRef(final String name) {
		return advertisedRefs.get(name);
	}

	@Override
	public String getMessages() {
		return messageWriter != null ? messageWriter.toString() : ""; //$NON-NLS-1$
	}

	/**
	 * User agent advertised by the remote server.
	 *
	 * @return agent (version of Git) running on the remote server. Null if the
	 *         server does not advertise this version.
	 * @since 4.0
	 */
	@Override
	public String getPeerUserAgent() {
		return peerUserAgent;
	}

	/**
	 * Remember the remote peer's agent.
	 *
	 * @param agent
	 *            remote peer agent string.
	 * @since 4.0
	 */
	protected void setPeerUserAgent(String agent) {
		peerUserAgent = agent;
	}

	@Override
	public abstract void close();

	/**
	 * Denote the list of refs available on the remote repository.
	 * <p>
	 * Implementors should invoke this method once they have obtained the refs
	 * that are available from the remote repository.
	 *
	 * @param all
	 *            the complete list of refs the remote has to offer. This map
	 *            will be wrapped in an unmodifiable way to protect it, but it
	 *            does not get copied.
	 */
	protected void available(final Map<String, Ref> all) {
		advertisedRefs = Collections.unmodifiableMap(all);
	}

	/**
	 * Helper method for ensuring one-operation per connection. Check whether
	 * operation was already marked as started, and mark it as started.
	 *
	 * @throws TransportException
	 *             if operation was already marked as started.
	 */
	protected void markStartedOperation() throws TransportException {
		if (startedOperation)
			throw new TransportException(
					JGitText.get().onlyOneOperationCallPerConnectionIsSupported);
		startedOperation = true;
	}

	/**
	 * Get the writer that buffers messages from the remote side.
	 *
	 * @return writer to store messages from the remote.
	 */
	protected Writer getMessageWriter() {
		if (messageWriter == null)
			setMessageWriter(new StringWriter());
		return messageWriter;
	}

	/**
	 * Set the writer that buffers messages from the remote side.
	 *
	 * @param writer
	 *            the writer that messages will be delivered to. The writer's
	 *            {@code toString()} method should be overridden to return the
	 *            complete contents.
	 */
	protected void setMessageWriter(Writer writer) {
		if (messageWriter != null)
			throw new IllegalStateException(JGitText.get().writerAlreadyInitialized);
		messageWriter = writer;
	}
}
