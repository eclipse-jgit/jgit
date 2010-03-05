/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.WorkDirCheckout;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "merges two development histories")
class Merge extends TextBuiltin {

	@Option(name = "--strategy", aliases = { "-s" }, usage = "Use the given "
			+ "merge strategy. Can be supplied more than once to specify them "
			+ "in the order they should be tried. If there is no -s option, "
			+ "the simple-two-way-in-core strategy is used. Currently the "
			+ "following strategies are supported: ours, theirs, "
			+ "simple-two-way-in-core")
	private String strategyName;

	private MergeStrategy mergeStrategy = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE;

	@Argument(required = true)
	private String ref;

	@Override
	protected void run() throws Exception {
		GitIndex index = db.getIndex();
		Commit newHeadCommit;
		StringBuilder refLogMessage = new StringBuilder("merge ");

		// determine the merge strategy
		if (strategyName != null) {
			mergeStrategy = MergeStrategy.get(strategyName);
			if (mergeStrategy == null)
				throw die("unknown merge strategy (" + strategyName
						+ "specified.");
		}

		// determine the current HEAD
		final Ref head = db.getRef(Constants.HEAD);
		if (head == null || !head.isSymbolic())
			throw die("Cannot merge into detached HEAD");

		// determine the other revision we want to merge with HEAD
		final ObjectId src = db.resolve(ref + "^{commit}");
		if (src == null)
			throw die(String.format(
					"%s does not exist or is not referring to a commit", ref));
		refLogMessage.append(ref + ": ");

		// Check for FAST_FORWARD, ALREADY_UP_TO_DATE
		RevWalk revWalk = new RevWalk(db);
		RevCommit headCommit = revWalk.lookupCommit(head.getObjectId());
		RevCommit srcCommit = revWalk.lookupCommit(src);
		if (revWalk.isMergedInto(srcCommit, headCommit)) {
			// ALREADY_UP_TO_DATE: nothing to be done
			out.write("already up-to-date");
			return;
		}
		if (revWalk.isMergedInto(headCommit, srcCommit)) {
			// FAST_FORWARD detected: skip doing a real merge but only update
			// HEAD
			newHeadCommit = db.mapCommit(srcCommit);
			out.write("updating " + head.getLeaf().getObjectId().getName()
					+ ".." + srcCommit.getName() + "\nFast forward");
			refLogMessage.append("Fast forward");
		} else {
			// do a real merge
			Merger merger = mergeStrategy.newMerger(db);
			boolean merge = merger.merge(new ObjectId[] { head.getObjectId(),
					src });

			if (!merge)
				throw die("merge failed");
			// commit the merge
			String commitMessage = "Merge " + getRefType(ref) + " '" + ref
					+ "'";
			PersonIdent myIdent = new PersonIdent(db);
			final Commit mergeCommit = new Commit(db);
			mergeCommit.setTreeId(merger.getResultTreeId());
			mergeCommit
					.setParentIds(new ObjectId[] { head.getObjectId(), src });
			mergeCommit.setAuthor(myIdent);
			mergeCommit.setCommitter(myIdent);
			mergeCommit.setMessage(commitMessage);
			newHeadCommit = mergeCommit;
			mergeCommit.setCommitId(merger.getObjectWriter().writeCommit(
					mergeCommit));
			refLogMessage.append("Merge made by " + mergeStrategy.getName()
					+ ".");
		}
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD);
		refUpdate.setNewObjectId(newHeadCommit.getCommitId());
		refUpdate.setRefLogMessage(refLogMessage.toString(), false);
		if (refUpdate.update() == RefUpdate.Result.LOCK_FAILURE) {
			throw new IOException("Failed to update " + refUpdate.getName()
					+ " to commit " + newHeadCommit.getCommitId() + ".");
		}

		// update the index.
		// I am just overwriting the index. This is (if at all) only safe if the
		// index was clean (was matching exactly HEAD state) before the merge
		index.readTree(newHeadCommit.getTree());
		index.write();

		// checkout the index
		File workDir = db.getWorkDir();
		if (workDir != null) {
			WorkDirCheckout workDirCheckout = new WorkDirCheckout(db, workDir,
					index, newHeadCommit.getTree());
			workDirCheckout.setFailOnConflict(true);
			workDirCheckout.checkout();
		}
	}

	private String getRefType(String revstr) throws IOException {
		Ref ref = db.getRef(revstr);
		if (ref != null && ref.getName().startsWith("refs/heads"))
			return "branch";
		return "commit";
	}
}
