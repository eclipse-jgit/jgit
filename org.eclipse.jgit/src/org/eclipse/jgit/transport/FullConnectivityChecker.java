/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

/**
 * Implementation of connectivity checker which will check that all commits
 * which pack is referencing are reachable. if
 * baseReceivePack.isCheckReferencedObjectsAreReachable() is set it will also
 * check that blobs are reachable as well.
 *
 * @since 5.6
 */
final class FullConnectivityChecker implements ConnectivityChecker {
	@Override
	public void checkConnectivity(BaseReceivePack baseReceivePack,
			Set<ObjectId> haves, ProgressMonitor monitor)
			throws MissingObjectException, IOException {
		monitor.beginTask(JGitText.get().countingObjects,
				ProgressMonitor.UNKNOWN);
		try (ObjectWalk ow = new ObjectWalk(baseReceivePack.db)) {
			if (!markStartAndKnownNodes(baseReceivePack, ow, haves,
					monitor)) {
				return;
			}
			checkCommitTree(baseReceivePack, ow, monitor);
			checkObjects(baseReceivePack, ow, monitor);
		} finally {
			monitor.endTask();
		}
	}

	/**
	 * @param baseReceivePack
	 * @param ow
	 *            Walk which can also check blobs.
	 * @param haves
	 *            Set of references known for client.
	 * @param monitor
	 *            Monitor to publish progress to.
	 * @return true if at least one new node was marked.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 */
	private boolean markStartAndKnownNodes(BaseReceivePack baseReceivePack,
			ObjectWalk ow,
			Set<ObjectId> haves, ProgressMonitor monitor)
			throws IOException {
		boolean markTrees = baseReceivePack
				.isCheckReferencedObjectsAreReachable()
				&& !baseReceivePack.parser.getBaseObjectIds().isEmpty();
		if (baseReceivePack.isCheckReferencedObjectsAreReachable()) {
			ow.sort(RevSort.TOPO);
			if (!baseReceivePack.parser.getBaseObjectIds().isEmpty()) {
				ow.sort(RevSort.BOUNDARY, true);
			}
		}
		boolean hasInteresting = false;

		for (ReceiveCommand cmd : baseReceivePack.getAllCommands()) {
			if (cmd.getResult() != Result.NOT_ATTEMPTED) {
				continue;
			}
			if (cmd.getType() == ReceiveCommand.Type.DELETE) {
				continue;
			}
			if (haves.contains(cmd.getNewId())) {
				continue;
			}
			ow.markStart(ow.parseAny(cmd.getNewId()));
			monitor.update(1);
			hasInteresting = true;
		}
		if (!hasInteresting) {
			return false;
		}
		for (ObjectId have : haves) {
			RevObject o = ow.parseAny(have);
			ow.markUninteresting(o);
			monitor.update(1);

			if (markTrees) {
				o = ow.peel(o);
				if (o instanceof RevCommit) {
					o = ((RevCommit) o).getTree();
				}
				if (o instanceof RevTree) {
					ow.markUninteresting(o);
				}
				monitor.update(1);
			}
		}
		return true;
	}

	/**
	 * @param baseReceivePack
	 * @param ow
	 *            Walk which can also check blobs.
	 * @param monitor
	 *            Monitor to publish progress to.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 *
	 */
	private void checkObjects(BaseReceivePack baseReceivePack, ObjectWalk ow,
			ProgressMonitor monitor) throws IOException {
		RevObject o;

		while ((o = ow.nextObject()) != null) {
			monitor.update(1);
			if (o.has(RevFlag.UNINTERESTING)) {
				continue;
			}

			if (baseReceivePack.isCheckReferencedObjectsAreReachable()) {
				if (baseReceivePack.parser.getNewObjectIds().contains(o)) {
					continue;
				}
				throw new MissingObjectException(o, o.getType());

			}

			if (o instanceof RevBlob
					&& !baseReceivePack.db.getObjectDatabase().has(o)) {
				throw new MissingObjectException(o, Constants.TYPE_BLOB);
			}
		}

		if (baseReceivePack.isCheckReferencedObjectsAreReachable()) {
			for (ObjectId id : baseReceivePack.parser.getBaseObjectIds()) {
				o = ow.parseAny(id);
				if (!o.has(RevFlag.UNINTERESTING)) {
					throw new MissingObjectException(o, o.getType());
				}
			}
		}
	}

	/**
	 * @param baseReceivePack
	 * @param ow
	 *            Walk which can also check blobs.
	 * @param monitor
	 *            Monitor to publish progress to.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 */
	private void checkCommitTree(BaseReceivePack baseReceivePack, ObjectWalk ow,
			ProgressMonitor monitor) throws IOException {
		RevCommit c;
		while ((c = ow.next()) != null) {
			monitor.update(1);
			if (baseReceivePack.isCheckReferencedObjectsAreReachable()
					&& !c.has(RevFlag.UNINTERESTING) //
					&& !baseReceivePack.parser.getNewObjectIds().contains(c)) {
				throw new MissingObjectException(c, Constants.TYPE_COMMIT);
			}
		}
	}
}
