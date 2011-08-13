/*
 * Copyright (C) 2011, Google Inc.
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
import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

/** Caches pack streams for reuse on identical requests. */
public interface UploadPackCache {
	/**
	 * Locate and reuse a cached result.
	 *
	 * @param out
	 *            pack stream to copy the pack onto, if the entire pack was
	 *            found in the cache.
	 * @param capabilities
	 *            capabilities supported by the client that impact the resulting
	 *            pack format.
	 * @param wants
	 *            list of objects the client wants to obtain.
	 * @param haves
	 *            list of objects the client already has.
	 * @return true if the cache reused a pack; false if the cache does not have
	 *         an entry for this request.
	 * @throws IOException
	 *             the pack cannot be copied to the output and the request must
	 *             be aborted.
	 */
	public boolean sendFromCache(OutputStream out,
			Set<String> capabilities,
			Collection<? extends ObjectId> wants,
			Collection<? extends ObjectId> haves) throws IOException;

	/**
	 * Create a cache entry.
	 *
	 * @param capabilities
	 *            capabilities supported by the client that impact the resulting
	 *            pack format.
	 * @param wants
	 *            list of objects the client wants to obtain.
	 * @param haves
	 *            list of objects the client already has.
	 * @return stream to write to the cache. This stream will be written to in
	 *         parallel with the current client and must not block too long
	 *         during write. If the cache does not wish to store this entry, it
	 *         should return null.
	 */
	public OutputStream newEntry(
			Set<String> capabilities,
			Collection<? extends ObjectId> wants,
			Collection<? extends ObjectId> haves);

	/**
	 * Finish a cache entry that was previously started.
	 *
	 * @param cacheOut
	 *            stream originally created by saveToCache.
	 * @param success
	 *            true if the cache entry was fully written; false if it failed
	 *            and should be discarded.
	 */
	public void finishEntry(OutputStream cacheOut, boolean success);
}
