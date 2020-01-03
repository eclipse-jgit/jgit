/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefsMap() {
		return advertisedRefs;
	}

	/** {@inheritDoc} */
	@Override
	public final Collection<Ref> getRefs() {
		return advertisedRefs.values();
	}

	/** {@inheritDoc} */
	@Override
	public final Ref getRef(String name) {
		return advertisedRefs.get(name);
	}

	/** {@inheritDoc} */
	@Override
	public String getMessages() {
		return messageWriter != null ? messageWriter.toString() : ""; //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 *
	 * User agent advertised by the remote server.
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

	/** {@inheritDoc} */
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
	protected void available(Map<String, Ref> all) {
		advertisedRefs = Collections.unmodifiableMap(all);
	}

	/**
	 * Helper method for ensuring one-operation per connection. Check whether
	 * operation was already marked as started, and mark it as started.
	 *
	 * @throws org.eclipse.jgit.errors.TransportException
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
