package org.eclipse.jgit.transport;

import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

abstract class FetchRequest {

	final Set<ObjectId> wantsIds;

	final int depth;

	final Set<ObjectId> clientShallowCommits;

	final long filterBlobLimit;

	final Set<String> options;

	FetchRequest(Set<ObjectId> wantsIds, int depth,
			Set<ObjectId> clientShallowCommits, long filterBlobLimit,
			Set<String> options) {
		this.wantsIds = wantsIds;
		this.depth = depth;
		this.clientShallowCommits = clientShallowCommits;
		this.filterBlobLimit = filterBlobLimit;
		this.options = options;
	}

	/**
	 * @return object ids in the "want" (and "want-ref") lines of the request
	 */
	protected Set<ObjectId> getWantsIds() {
		return wantsIds;
	}

	/**
	 * @return the depth set in a "deepen" line. 0 by default.
	 */
	protected int getDepth() {
		return depth;
	}

	/**
	 * Shallow commits the client already has.
	 *
	 * These are sent by the client in "shallow" request lines.
	 *
	 * @return set of commits the client has declared as shallow.
	 */
	protected Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	/**
	 * @return the blob limit set in a "filter" line (-1 if not set)
	 */
	protected long getFilterBlobLimit() {
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
	protected Set<String> getOptions() {
		return options;
	}

}