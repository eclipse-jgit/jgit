/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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
 * Base implementation of connectivity checker. Will check that all commits
 * which pack is referencing are reachable. if
 * baseReceivePack.isCheckReferencedObjectsAreReachable() is set it will also
 * check that blobs are reachable as well.
 *
 * @since 5.6
 */
public class BaseConnectivityChecker implements ConnectivityChecker {
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
