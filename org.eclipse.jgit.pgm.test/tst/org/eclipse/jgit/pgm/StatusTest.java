/*
 * Copyright (C) 2012, François Rey <eclipse.org_@_francois_._rey_._name>
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

import static org.junit.Assert.assertArrayEquals;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class StatusTest extends CLIRepositoryTestCase {
	@Test
	public void testStatus() throws Exception {
		Git git = new Git(db);
		// Write all files
		writeTrashFile("tracked", "tracked");
		writeTrashFile("stagedNew", "stagedNew");
		writeTrashFile("stagedModified", "stagedModified");
		writeTrashFile("stagedDeleted", "stagedDeleted");
		writeTrashFile("trackedModified", "trackedModified");
		writeTrashFile("trackedDeleted", "trackedDeleted");
		writeTrashFile("untracked", "untracked");
		// Test untracked
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Untracked files:", //
						"# \tstagedDeleted", //
						"# \tstagedModified", //
						"# \tstagedNew", //
						"# \ttracked", //
						"# \ttrackedDeleted", //
						"# \ttrackedModified", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 7 untracked.", //
						"" //
				}, execute("git status")); //
		// Add to index
		git.add().addFilepattern("tracked").call();
		git.add().addFilepattern("stagedModified").call();
		git.add().addFilepattern("stagedDeleted").call();
		git.add().addFilepattern("trackedModified").call();
		git.add().addFilepattern("trackedDeleted").call();
		// Test staged count
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Changes to be committed:", //
						"# \tnew file:   stagedDeleted", //
						"# \tnew file:   stagedModified", //
						"# \tnew file:   tracked", //
						"# \tnew file:   trackedDeleted", //
						"# \tnew file:   trackedModified", //
						"# ", //
						"# Untracked files:", //
						"# \tstagedNew", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 5 to commit, 0 not staged, 0 unmerged, 2 untracked.", //
						"" //
				}, execute("git status")); //
		// Commit
		git.commit().setMessage("initial commit")
				.call();
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Untracked files:", //
						"# \tstagedNew", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 2 untracked.", //
						"" //
				}, execute("git status")); //
		// Make some changes and stage them
		writeTrashFile("stagedModified", "stagedModified modified");
		deleteTrashFile("stagedDeleted");
		writeTrashFile("trackedModified", "trackedModified modified");
		deleteTrashFile("trackedDeleted");
		git.add().addFilepattern("stagedModified").call();
		git.rm().addFilepattern("stagedDeleted").call();
		git.add().addFilepattern("stagedNew").call();
		// Test staged/not-staged status
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Changes to be committed:", //
						"# \tnew file:   stagedNew", //
						"# \tmodified:   stagedModified", //
						"# \tdeleted:    stagedDeleted", //
						"# ", //
						"# Changes not staged for commit:", //
						"# \tmodified:   trackedModified", //
						"# \tdeleted:    trackedDeleted", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 3 to commit, 2 not staged, 0 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		// Create unmerged file
		writeTrashFile("unmerged", "unmerged");
		git.add().addFilepattern("unmerged").call();
		// Commit pending changes
		git.add().addFilepattern("trackedModified").call();
		git.rm().addFilepattern("trackedDeleted").call();
		git.commit().setMessage("commit before branching").call();
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		// Checkout new branch
		git.checkout().setCreateBranch(true).setName("test").call();
		// Test branch status
		assertArrayEquals(new String[] { // git status output
						"# On branch test", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		// Commit change and checkout master again
		writeTrashFile("unmerged", "changed in test branch");
		git.add().addFilepattern("unmerged").call();
		RevCommit testBranch = git.commit()
				.setMessage("changed unmerged in test branch").call();
		assertArrayEquals(new String[] { // git status output
						"# On branch test", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		git.checkout().setName("master").call();
		// Change the same file and commit
		writeTrashFile("unmerged", "changed in master branch");
		git.add().addFilepattern("unmerged").call();
		git.commit().setMessage("changed unmerged in master branch").call();
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 0 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		// Merge test branch into master
		git.merge().include(testBranch.getId()).call();
		// Test unmerged status
		assertArrayEquals(new String[] { // git status output
						"# On branch master", //
						"# ", //
						"# Unmerged paths:", //
						"# \tunmerged", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 1 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
		// Test detached head
		String commitId = db.getRef(Constants.MASTER).getObjectId().name();
		git.checkout().setName(commitId).call();
		assertArrayEquals(new String[] { // git status output
						"# Not currently on any branch.", //
						"# ", //
						"# Unmerged paths:", //
						"# \tunmerged", //
						"# ", //
						"# Untracked files:", //
						"# \tuntracked", //
						"# ", //
						"# Summary: 0 to commit, 0 not staged, 1 unmerged, 1 untracked.", //
						"" //
				}, execute("git status")); //
	}
}
