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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Upload (Fetch) request in the V1 protocol.
 *
 * @since 5.2
 */
final class FetchV1Request {

	final Set<ObjectId> wantsIds;

	final int depth;

	final Set<ObjectId> clientShallowCommits;

	final long filterBlobLimit;

	final Set<String> options;

	FetchV1Request(Set<ObjectId> wantsIds, int depth,
			Set<ObjectId> clientShallowCommits, long filterBlobLimit,
			Set<String> options) {

		this.wantsIds = wantsIds;
		this.depth = depth;
		this.clientShallowCommits = clientShallowCommits;
		this.filterBlobLimit = filterBlobLimit;
		this.options = options;
	}

	Set<ObjectId> getWantsIds() {
		return wantsIds;
	}

	int getDepth() {
		return depth;
	}

	Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	long getFilterBlobLimit() {
		return filterBlobLimit;
	}

	Set<String> getOptions() {
		return options;
	}

	static final class Builder {

		int depth;

		Set<ObjectId> wantsIds = new HashSet<>();

		Set<ObjectId> clientShallowCommits = new HashSet<>();

		long filterBlobLimit = -1;

		Set<String> options = new HashSet<>();

		/**
		 * @param objectId
		 *            from a "want" line in a fetch request
		 * @return the builder
		 */
		Builder addWantsIds(ObjectId objectId) {
			this.wantsIds.add(objectId);
			return this;
		}

		/**
		 * @param d
		 *            from a "deepen" line in the fetch request
		 * @return the builder
		 */
		Builder setDepth(int d) {
			this.depth = d;
			return this;
		}

		/**
		 * @param shallowOid
		 *            from a "shallow" line in the fetch request
		 * @return the builder
		 */
		Builder addClientShallowCommit(ObjectId shallowOid) {
			this.clientShallowCommits.add(shallowOid);
			return this;
		}

		/**
		 * @param opts
		 *            options appended in the first line of the request
		 * @return the builder
		 */
		Builder addAllOptions(Set<String> opts) {
			this.options.addAll(opts);
			return this;
		}

		/**
		 * @param filterBlobLimit
		 *            set in a "filter" line
		 * @return the builder
		 */
		Builder setFilterBlobLimit(long filterBlobLimit) {
			this.filterBlobLimit = filterBlobLimit;
			return this;
		}

		FetchV1Request build() {
			return new FetchV1Request(wantsIds, depth, clientShallowCommits,
					filterBlobLimit, options);
		}
	}
}
