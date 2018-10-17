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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Fetch request in the V0/V1 protocol.
 */
final class FetchV0Request extends FetchRequest {

	FetchV0Request(@NonNull Set<ObjectId> wantIds, int depth,
			@NonNull Set<ObjectId> clientShallowCommits, long filterBlobLimit,
			@NonNull Set<String> clientCapabilities, @Nullable String agent) {
		super(wantIds, depth, clientShallowCommits, filterBlobLimit,
				clientCapabilities, 0, Collections.emptyList(), agent);
	}

	static final class Builder {

		int depth;

		final Set<ObjectId> wantIds = new HashSet<>();

		final Set<ObjectId> clientShallowCommits = new HashSet<>();

		long filterBlobLimit = -1;

		final Set<String> clientCaps = new HashSet<>();

		String agent;

		/**
		 * @param objectId
		 *            object id received in a "want" line
		 * @return this builder
		 */
		Builder addWantId(ObjectId objectId) {
			wantIds.add(objectId);
			return this;
		}

		/**
		 * @param d
		 *            depth set in a "deepen" line
		 * @return this builder
		 */
		Builder setDepth(int d) {
			depth = d;
			return this;
		}

		/**
		 * @param shallowOid
		 *            object id received in a "shallow" line
		 * @return this builder
		 */
		Builder addClientShallowCommit(ObjectId shallowOid) {
			clientShallowCommits.add(shallowOid);
			return this;
		}

		/**
		 * @param clientCapabilities
		 *            client capabilities sent by the client in the first want
		 *            line of the request
		 * @return this builder
		 */
		Builder addClientCapabilities(Collection<String> clientCapabilities) {
			for (String cap: clientCapabilities) {
				// TODO(ifrade): Do this is done on parse time
				if (cap.startsWith("agent=")) { //$NON-NLS-1$
					agent = cap.substring("agent=".length()); //$NON-NLS-1$
				} else {
					clientCaps.add(cap);
				}
			}
			return this;
		}

		/**
		 * @param clientAgent
		 *            agent line sent by the client in the request body
		 * @return this builder
		 */
		Builder setAgent(String clientAgent) {
			agent = clientAgent;
			return this;
		}

		/**
		 * @param filterBlobLim
		 *            blob limit set in a "filter" line
		 * @return this builder
		 */
		Builder setFilterBlobLimit(long filterBlobLim) {
			filterBlobLimit = filterBlobLim;
			return this;
		}

		FetchV0Request build() {
			return new FetchV0Request(wantIds, depth, clientShallowCommits,
					filterBlobLimit, clientCaps, agent);
		}

	}
}
