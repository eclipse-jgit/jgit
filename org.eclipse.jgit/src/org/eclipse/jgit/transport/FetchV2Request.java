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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.ObjectId;

/**
 * fetch protocol v2 request.
 *
 * <p>
 * This is used as an input to {@link ProtocolV2Hook}.
 *
 * @since 5.1
 */
public final class FetchV2Request {
	private final List<ObjectId> peerHas;

	private final TreeMap<String, ObjectId> wantedRefs;

	private final Set<ObjectId> wantsIds;

	private final Set<ObjectId> clientShallowCommits;

	private final int shallowSince;

	private final List<String> shallowExcludeRefs;

	private final int depth;

	private final long filterBlobLimit;

	private final Set<String> options;

	private FetchV2Request(List<ObjectId> peerHas,
			TreeMap<String, ObjectId> wantedRefs, Set<ObjectId> wantsIds,
			Set<ObjectId> clientShallowCommits, int shallowSince,
			List<String> shallowExcludeRefs, int depth, long filterBlobLimit,
			Set<String> options) {
		this.peerHas = peerHas;
		this.wantedRefs = wantedRefs;
		this.wantsIds = wantsIds;
		this.clientShallowCommits = clientShallowCommits;
		this.shallowSince = shallowSince;
		this.shallowExcludeRefs = shallowExcludeRefs;
		this.depth = depth;
		this.filterBlobLimit = filterBlobLimit;
		this.options = options;
	}

	/**
	 * @return object ids in the "have" lines of the request
	 */
	@NonNull
	List<ObjectId> getPeerHas() {
		return this.peerHas;
	}

	/**
	 * @return list of references in the "want-ref" lines of the request
	 */
	@NonNull
	Map<String, ObjectId> getWantedRefs() {
		return this.wantedRefs;
	}

	/**
	 * @return object ids in the "want" (and "want-ref") lines of the request
	 */
	@NonNull
	Set<ObjectId> getWantsIds() {
		return wantsIds;
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
	 * The value in a "deepen-since" line in the request, indicating the
	 * timestamp where to stop fetching/cloning.
	 *
	 * @return timestamp where to stop the shallow fetch/clone. Defaults to 0 if
	 *         not set in the request
	 */
	int getShallowSince() {
		return shallowSince;
	}

	/**
	 * @return the refs in "deepen-not" lines in the request.
	 */
	@NonNull
	List<String> getShallowExcludeRefs() {
		return shallowExcludeRefs;
	}

	/**
	 * @return the depth set in a "deepen" line. 0 by default.
	 */
	int getDepth() {
		return depth;
	}

	/**
	 * @return the blob limit set in a "filter" line (-1 if not set)
	 */
	long getFilterBlobLimit() {
		return filterBlobLimit;
	}

	/**
	 * Options that tune the expected response from the server, like
	 * "thin-pack", "no-progress" or "ofs-delta"
	 *
	 * These are options listed and well-defined in the git protocol
	 * specification
	 *
	 * @return options found in the request lines
	 */
	@NonNull
	Set<String> getOptions() {
		return options;
	}

	/** @return A builder of {@link FetchV2Request}. */
	static Builder builder() {
		return new Builder();
	}


	/** A builder for {@link FetchV2Request}. */
	static final class Builder {
		List<ObjectId> peerHas = new ArrayList<>();

		TreeMap<String, ObjectId> wantedRefs = new TreeMap<>();

		Set<ObjectId> wantsIds = new HashSet<>();

		Set<ObjectId> clientShallowCommits = new HashSet<>();

		List<String> shallowExcludeRefs = new ArrayList<>();

		Set<String> options = new HashSet<>();

		int depth;

		int shallowSince;

		long filterBlobLimit = -1;

		private Builder() {
		}

		/**
		 * @param objectId
		 *            from a "have" line in a fetch request
		 * @return the builder
		 */
		Builder addPeerHas(ObjectId objectId) {
			peerHas.add(objectId);
			return this;
		}

		/**
		 * From a "want-ref" line in a fetch request
		 *
		 * @param refName
		 *            reference name
		 * @param oid
		 *            object id
		 * @return the builder
		 */
		Builder addWantedRef(String refName, ObjectId oid) {
			wantedRefs.put(refName, oid);
			return this;
		}

		/**
		 * @param option
		 *            fetch request lines acting as options
		 * @return the builder
		 */
		Builder addOption(String option) {
			options.add(option);
			return this;
		}

		/**
		 * @param objectId
		 *            from a "want" line in a fetch request
		 * @return the builder
		 */
		Builder addWantsIds(ObjectId objectId) {
			wantsIds.add(objectId);
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
		 * @param d
		 *            from a "deepen" line in the fetch request
		 * @return the builder
		 */
		Builder setDepth(int d) {
			this.depth = d;
			return this;
		}

		/**
		 * @return depth set in the request (via a "deepen" line). Defaulting to
		 *         0 if not set.
		 */
		int getDepth() {
			return this.depth;
		}

		/**
		 * @return if there has been any "deepen not" line in the request
		 */
		boolean hasShallowExcludeRefs() {
			return shallowExcludeRefs.size() > 0;
		}

		/**
		 * @param shallowExcludeRef reference in a "deepen not" line
		 * @return the builder
		 */
		Builder addShallowExcludeRefs(String shallowExcludeRef) {
			this.shallowExcludeRefs.add(shallowExcludeRef);
			return this;
		}

		/**
		 * @param value
		 *            shallow since value received in a "deepen since" line
		 * @return the builder
		 */
		Builder setShallowSince(int value) {
			this.shallowSince = value;
			return this;
		}

		/**
		 * @return shallow since value, sent before in a "deepen since" line. 0
		 *         by default.
		 */
		int getShallowSince() {
			return this.shallowSince;
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

		/**
		 * @return Initialized fetch request
		 */
		FetchV2Request build() {
			return new FetchV2Request(peerHas, wantedRefs, wantsIds,
					clientShallowCommits, shallowSince, shallowExcludeRefs,
					depth, filterBlobLimit, options);
		}
	}
}
