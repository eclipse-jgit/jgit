/*
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
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
package org.eclipse.jgit.merge;

import static org.junit.Assert.assertFalse;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class ResolveMergerTest extends RepositoryTestCase {

	@Test
	public void failingPathsShouldNotResultInOKReturnValue() throws Exception {
		File folder1 = new File(db.getWorkTree(), "folder1");
		FileUtils.mkdir(folder1);
		File file = new File(folder1, "file1.txt");
		write(file, "folder1--file1.txt");
		file = new File(folder1, "file2.txt");
		write(file, "folder1--file2.txt");

		Git git = new Git(db);
		git.add().addFilepattern(folder1.getName()).call();
		RevCommit base = git.commit().setMessage("adding folder").call();

		recursiveDelete(folder1);
		git.rm().addFilepattern("folder1/file1.txt")
				.addFilepattern("folder1/file2.txt").call();
		RevCommit other = git.commit()
				.setMessage("removing folders on 'other'").call();

		git.checkout().setName(base.name()).call();

		file = new File(db.getWorkTree(), "unrelated.txt");
		write(file, "unrelated");

		git.add().addFilepattern("unrelated").call();
		RevCommit head = git.commit().setMessage("Adding another file").call();

		// Untracked file to cause failing path for delete() of folder1
		file = new File(folder1, "file3.txt");
		write(file, "folder1--file3.txt");

		ResolveMerger merger = new ResolveMerger(db, false);
		merger.setCommitNames(new String[] { "BASE", "HEAD", "other" });
		merger.setWorkingTreeIterator(new FileTreeIterator(db));
		boolean ok = merger.merge(head.getId(), other.getId());

		assertFalse(merger.getFailingPaths().isEmpty());
		assertFalse(ok);
	}

	@Test
	public void checkForCorrectIndex() throws Exception {
		File f;
		Git git = Git.wrap(db);

		// Create initial content
		f = writeTrashFiles(true, "orig", "orig", "1\n2\n3", "orig", "orig");
		fsTick(f);
		git.add().addFilepattern(".").call();
		RevCommit firstCommit = git.commit().setMessage("initial commit")
				.call();
		Assert.assertEquals("[0, mode:100644, time:t0, length:4, content:orig]"
				+ "[1, mode:100644, time:t1, length:4, content:orig]"
				+ "[2, mode:100644, time:t2, length:5, content:1\n2\n3]"
				+ "[3, mode:100644, time:t3, length:4, content:orig]"
				+ "[4, mode:100644, time:t4, length:4, content:orig]",
				indexState(LENGTH | MOD_TIME | SMUDGE | CONTENT));

		// Do modifications on the master branch. This should touch only "0",
		// "2 and "3"
		f = writeTrashFiles(true, "master", null, "1master\n2\n3", "master",
				null);
		fsTick(f);
		git.add().addFilepattern(".").call();
		RevCommit masterCommit = git.commit().setMessage("master commit")
				.call();
		Assert.assertEquals(
				"[0, mode:100644, time:t2, length:6, content:master]"
						+ "[1, mode:100644, time:t0, length:4, content:orig]"
						+ "[2, mode:100644, time:t3, length:11, content:1master\n2\n3]"
						+ "[3, mode:100644, time:t4, length:6, content:master]"
						+ "[4, mode:100644, time:t1, length:4, content:orig]",
				indexState(LENGTH | MOD_TIME | SMUDGE | CONTENT));

		// Checkout a side branch. This should touch only "0", "2 and "3"
		git.checkout().setCreateBranch(true).setStartPoint(firstCommit)
				.setName("side").call();
		// This checkout may have populated worktree and index so fast that we
		// may have smudged entries now. Check that we have the right content
		// and then rewrite the index to get rid of smudged state
		Assert.assertEquals("[0, mode:100644, content:orig]" //
				+ "[1, mode:100644, content:orig]" //
				+ "[2, mode:100644, content:1\n2\n3]" //
				+ "[3, mode:100644, content:orig]" //
				+ "[4, mode:100644, content:orig]", //
				indexState(CONTENT));
		f = writeTrashFiles(true, "orig", "orig", "1\n2\n3", "orig", "orig");
		fsTick(f);
		git.add().addFilepattern(".").call();

		// Do modifications on the side branch. Touch only "1", "2 and "3"
		f = writeTrashFiles(true, null, "side", "1\n2\n3side", "side", null);
		fsTick(f);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("side commit").call();
		Assert.assertEquals("[0, mode:100644, time:t0, length:4, content:orig]"
				+ "[1, mode:100644, time:t2, length:4, content:side]"
				+ "[2, mode:100644, time:t3, length:9, content:1\n2\n3side]"
				+ "[3, mode:100644, time:t4, length:4, content:side]"
				+ "[4, mode:100644, time:t1, length:4, content:orig]",
				indexState(LENGTH | MOD_TIME | SMUDGE | CONTENT));

		// merge master and side. Should only touch "2" and "3"
		git.merge().include(masterCommit).call();
		Assert.assertEquals(
				"[0, mode:100644, content:master]" //
						+ "[1, mode:100644, content:side]" //
						+ "[2, mode:100644, content:1master\n2\n3side\n]" //
						+ "[3, mode:100644, stage:1, content:orig][3, mode:100644, stage:2, content:side][3, mode:100644, stage:3, content:master]" //
						+ "[4, mode:100644, content:orig]", //
				indexState(CONTENT));

		// Check that we have at least correct lastmodification timestamps.
		DirCache dc=db.readDirCache();
		Assert.assertTrue(db.getIndexFile().lastModified() >= dc.getEntry("2")
				.getLastModified());
		Assert.assertTrue(dc.getEntry("2").getLastModified() > dc.getEntry("1")
				.getLastModified());
		Assert.assertTrue(dc.getEntry("1").getLastModified() > dc.getEntry("0")
				.getLastModified());
		Assert.assertTrue(dc.getEntry("0").getLastModified() > dc.getEntry("4")
				.getLastModified());
	}
}
