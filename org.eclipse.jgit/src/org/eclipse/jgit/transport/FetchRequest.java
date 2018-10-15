/*
 * Copyright (C) 2018, Google LLC.
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

import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Common fields between v0/v1/v2 fetch requests.
 */
abstract class FetchRequest {

	final Set<ObjectId> wantIds;

	final int depth;

	final Set<ObjectId> clientShallowCommits;

	final long filterBlobLimit;

	final Set<String> clientCapabilities;

	/**
	 * Initialize the common fields of a fetch request.
	 *
	 * @param wantIds
	 *            list of want ids
	 * @param depth
	 *            how deep to go in the tree
	 * @param clientShallowCommits
	 *            commits the client has without history
	 * @param filterBlobLimit
	 *            to exclude blobs on certain conditions
	 * @param clientCapabilities
	 *            capabilities sent in the request
	 */
	FetchRequest(Set<ObjectId> wantIds, int depth,
			Set<ObjectId> clientShallowCommits, long filterBlobLimit,
			Set<String> clientCapabilities) {
		if (wantIds == null || clientShallowCommits == null
				|| clientCapabilities == null) {
			throw new NullPointerException();
		}

		this.wantIds = wantIds;
		this.depth = depth;
		this.clientShallowCommits = clientShallowCommits;
		this.filterBlobLimit = filterBlobLimit;
		this.clientCapabilities = clientCapabilities;
	}

	/**
	 * @return object ids in the "want" (and "want-ref") lines of the request
	 */
	@NonNull
	Set<ObjectId> getWantIds() {
		return wantIds;
	}

	/**
	 * @return the depth set in a "deepen" line. 0 by default.
	 */
	int getDepth() {
		return depth;
	}

	/**
	 * Shallow commits the client already has.
	 *
	 * These are sent by the client in "shallow" request lines.
	 *
	 * @return set of commits the client has declared as shallow.
	 */
	@NonNull
	Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	/**
	 * @return the blob limit set in a "filter" line (-1 if not set)
	 */
	long getFilterBlobLimit() {
		return filterBlobLimit;
	}

	/**
	 * Capabilities that the client wants enabled from the server.
	 *
	 * Capabilities are options that tune the expected response from the server,
	 * like "thin-pack", "no-progress" or "ofs-delta". This list should be a
	 * subset of the capabilities announced by the server in its first response.
	 *
	 * These options are listed and well-defined in the git protocol
	 * specification.
	 *
	 * @return capabilities sent by the client
	 */
	@NonNull
	Set<String> getClientCapabilities() {
		return clientCapabilities;
	}
}