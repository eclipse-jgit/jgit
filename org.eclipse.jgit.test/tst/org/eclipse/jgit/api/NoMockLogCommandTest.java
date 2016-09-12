/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Assert;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests for LogCommand which don't use MockSystemReader. Some tests which rely
 * heavily on the modification date of persisted files are hard to express with
 * a MockSystemReader in place. But such a reader is registered by our standard
 * test baseclass LocalDiskRepositoryTestCase.
 */
@RunWith(Theories.class)
public class NoMockLogCommandTest {
	@DataPoints
	public static boolean[] sleep = { true, false };

	@Theory
	/**
	 * Create a side branch which derived from master^2 and which contains one
	 * own commit. Merge master into side branch and then execute "git log
	 * side..master" which should return no commit
	 *
	 * <pre>
	 * <code>
	 *   * (HEAD -> side) Merge commit ...
	 *   |\
	 *   | * (master) master3
	 *   | * master2
	 *   * | side1
	 *   |/
	 *   * Initial commit
	 *   </code>
	 * </pre>
	 */
	public void testLogOnMergeCommit(boolean sleepBeforeMerge)
			throws Exception {
		// Create a Git repository
		Path folder = Files.createTempDirectory(
				"JGitTest_" + NoMockLogCommandTest.class.getName());
		Git git = null;
		try {
			git = Git.init().setBare(false).setDirectory(folder.toFile())
					.call();
			Repository repository = git.getRepository();

			// Add an initial commit
			git.commit().setMessage("Initial commit").call();

			// Create a new branch 'side' and add a commit to it
			git.checkout().setCreateBranch(true).setName("side").call();
			git.commit().setMessage("side1").call();

			// Checkout 'master' and add two commits
			git.checkout().setName(Constants.MASTER).call();
			git.commit().setMessage("master2").call();
			RevCommit add2 = git.commit().setMessage("master3").call();

			// If this delay is commented out -- test fails and
			// 'behind' is equal to "the number of commits to master - 1".
			if (sleepBeforeMerge) {
				Thread.sleep(1000);
			}

			// Checkout branch 'side' and merge 'master' to it
			git.checkout().setName("side").call();
			ObjectId mergeId = git.merge()
					.include(repository.resolve(Constants.MASTER))
					.setStrategy(MergeStrategy.RECURSIVE).call().getNewHead();

			RevWalk walk = new RevWalk(repository);
			walk.markStart(walk.lookupCommit(add2.getId()));
			walk.markUninteresting(walk.lookupCommit(mergeId));
			Assert.assertNull(
					"Found an unexpected commit. sleepBeforeMerge was set to: "
							+ sleepBeforeMerge,
					walk.next());
		} finally {
			if (git != null)
				git.close();
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir,
						IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}