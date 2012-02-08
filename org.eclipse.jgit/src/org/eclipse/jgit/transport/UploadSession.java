/*
 * Copyright (C) 2012, Google Inc.
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

import java.util.Map;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackWriter;

/**
 * Interface for handling a session's worth of upload data.
 * <p>
 * This comprises everything used by {@link UploadPack} that does not involve
 * talking to the wire directly.
 */
public interface UploadSession {
	/** Policy the server uses to validate client requests */
	public static enum RequestPolicy {
		/** Client may only ask for objects the server advertised a reference for. */
		ADVERTISED,
		/** Client may ask for any commit reachable from a reference. */
		REACHABLE_COMMIT,
		/** Client may ask for any SHA-1 in the repository. */
		ANY;
	}

	/** @return the repository this upload is reading from. */
	public Repository getRepository();

	/** @return the RevWalk instance used by this connection. */
	public RevWalk getRevWalk();

	/** @return all refs which were advertised to the client. */
	public Map<String, Ref> getAdvertisedRefs();

	/**
	 * Explicitly set advertised refs.
	 * <p>
	 * May be called by the configured {@link #getAdvertisedRefsHook()}. If called
	 * with {@code null} or not called at all by the hook, all refs are
	 * advertised.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            UploadPack instance. If null, all refs are advertised.
	 */
	public void setAdvertisedRefs(Map<String, Ref> allRefs);

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout();

	/**
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public boolean isBiDirectionalPipe();

	/** @return policy used by the service to validate client requests. */
	public RequestPolicy getRequestPolicy();

	/** @return the hook used while advertising the refs to the client */
	public AdvertiseRefsHook getAdvertisedRefsHook();

	/** @return the configured upload hook. */
	public PreUploadHook getPreUploadHook();

	/** @return the configured logger. */
	public UploadPackLogger getLogger();

	/**
	 * Get the PackWriter's statistics if a pack was sent to the client.
	 *
	 * @return statistics about pack output, if a pack was sent. Null if no pack
	 *         was sent, such as during the negotation phase of a smart HTTP
	 *         connection, or if the client was already up-to-date.
	 */
	public PackWriter.Statistics getPackStatistics();
}
