/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import static org.eclipse.jgit.internal.ketch.KetchReplica.State.AHEAD;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.DIVERGENT;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.LAGGING;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.UNKNOWN;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * A helper to check if a {@link KetchReplica} is ahead or behind the leader.
 */
class LagCheck implements AutoCloseable {
	private final KetchReplica replica;
	private final Repository repo;
	private RevWalk rw;
	private ObjectId remoteId;

	LagCheck(KetchReplica replica, Repository repo) {
		this.replica = replica;
		this.repo = repo;
		initRevWalk();
	}

	private void initRevWalk() {
		if (rw != null) {
			rw.close();
		}

		rw = new RevWalk(repo);
		rw.setRetainBody(false);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (rw != null) {
			rw.close();
			rw = null;
		}
	}

	ObjectId getRemoteId() {
		return remoteId;
	}

	KetchReplica.State check(ObjectId acceptId, ReceiveCommand acceptCmd) {
		remoteId = acceptId;
		if (remoteId == null) {
			// Nothing advertised by the replica, value is unknown.
			return UNKNOWN;
		}

		if (AnyObjectId.isEqual(remoteId, ObjectId.zeroId())) {
			// Replica does not have the txnAccepted reference.
			return LAGGING;
		}

		try {
			RevCommit remote;
			try {
				remote = parseRemoteCommit(acceptCmd.getRefName());
			} catch (RefGoneException gone) {
				// Replica does not have the txnAccepted reference.
				return LAGGING;
			} catch (MissingObjectException notFound) {
				// Local repository does not know this commit so it cannot
				// be including the replica's log.
				return DIVERGENT;
			}

			RevCommit head = rw.parseCommit(acceptCmd.getNewId());
			if (rw.isMergedInto(remote, head)) {
				return LAGGING;
			}

			// TODO(sop) Check term to see if my leader was deposed.
			if (rw.isMergedInto(head, remote)) {
				return AHEAD;
			}
			return DIVERGENT;
		} catch (IOException err) {
			KetchReplica.log.error(String.format(
					"Cannot compare %s", //$NON-NLS-1$
					acceptCmd.getRefName()), err);
			return UNKNOWN;
		}
	}

	private RevCommit parseRemoteCommit(String refName)
			throws IOException, MissingObjectException, RefGoneException {
		try {
			return rw.parseCommit(remoteId);
		} catch (MissingObjectException notLocal) {
			// Fall through and try to acquire the object by fetching it.
		}

		ReplicaFetchRequest fetch = new ReplicaFetchRequest(
				Collections.singleton(refName),
				Collections.<ObjectId> emptySet());
		try {
			replica.blockingFetch(repo, fetch);
		} catch (IOException fetchErr) {
			KetchReplica.log.error(String.format(
					"Cannot fetch %s (%s) from %s", //$NON-NLS-1$
					remoteId.abbreviate(8).name(), refName,
					replica.describeForLog()), fetchErr);
			throw new MissingObjectException(remoteId, OBJ_COMMIT);
		}

		Map<String, Ref> adv = fetch.getRefs();
		if (adv == null) {
			throw new MissingObjectException(remoteId, OBJ_COMMIT);
		}

		Ref ref = adv.get(refName);
		if (ref == null || ref.getObjectId() == null) {
			throw new RefGoneException();
		}

		initRevWalk();
		remoteId = ref.getObjectId();
		return rw.parseCommit(remoteId);
	}

	private static class RefGoneException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
