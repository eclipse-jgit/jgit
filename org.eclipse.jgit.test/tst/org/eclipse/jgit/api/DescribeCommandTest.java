/*
 * Copyright (C) 2011, GitHub Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class DescribeCommandTest extends RepositoryTestCase {

	@Test
	public void testDescribeCommandFirstCommit() throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		RevCommit initialCommit = git.commit().setMessage("initial commit")
				.call();

		git.tag()
				.setName("TEST")
				.setObjectId(initialCommit)
				.setTagger(
						new PersonIdent("Test Tagger", "tagger@example.coml"))
				.setMessage("Tag to test DescribeCommand").call();

		{
			String actual = git.describe().call();
			String expected = "TEST";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDescribeCommandLaterCommit() throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		RevCommit initialCommit = git.commit().setMessage("initial commit")
				.call();

		git.tag()
				.setName("TEST")
				.setObjectId(initialCommit)
				.setTagger(
						new PersonIdent("Test Tagger", "tagger@example.coml"))
				.setMessage("Tag to test DescribeCommand").call();

		writeTrashFile("Test1.txt", "Hello world!");
		git.add().addFilepattern("Test1.txt").call();
		RevCommit secondCommit = git.commit().setMessage("new commit").call();

		String defaultAbbreviationLength = Integer.toString(DescribeCommand
				.getDefaultAbbreviationLength());
		String actual = git.describe().setObjectId(secondCommit).call();
		String expectedRegex = "TEST-1-g[0-9a-f]{" + defaultAbbreviationLength
				+ "}";
		assertTrue("Describe String matches expected regex",
				actual.matches(expectedRegex));
	}

	@Test
	public void testDescribeCommandVariableAbbreviationLength()
			throws Exception {
		Git git = Git.wrap(db);

		/*
		 * XXX: Technically, this test might fail intermittently, only very
		 * rarely, if the repo created to test happened to have objects which
		 * conflict by more than 2 letters (as then, the abbreviate method might
		 * return more than 2 letters even when called with a length of 2).
		 */
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		RevCommit initialCommit = git.commit().setMessage("initial commit")
				.call();

		git.tag()
				.setName("TEST")
				.setObjectId(initialCommit)
				.setTagger(
						new PersonIdent("Test Tagger", "tagger@example.coml"))
				.setMessage("Tag to test DescribeCommand").call();

		writeTrashFile("Test1.txt", "Hello world!");
		git.add().addFilepattern("Test1.txt").call();
		RevCommit secondCommit = git.commit().setMessage("new commit").call();

		{
			// special case testing abbrev = 0, should just return the tag name
			DescribeCommand dc = git.describe().setAbbrev(0)
					.setObjectId(secondCommit);
			String actual = dc.call();
			String expected = "TEST";
			assertEquals(expected, actual);
		}

		int lengthsToTest[] = { 2, 5, 8, 16, 32, 40 };
		for (int length : lengthsToTest) {
			DescribeCommand dc = git.describe().setAbbrev(length)
					.setObjectId(secondCommit);
			String actual = dc.call();
			String expectedRegex = "TEST-1-g[0-9a-f]{"
					+ Integer.toString(length) + "}";
			assertTrue("Describe String matches expected regex",
					actual.matches(expectedRegex));

		}
	}

	@Test
	public void testDescribeThrowsWithInvalidArgs() throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("initial commit").call();
		try {
			DescribeCommand dc = git.describe();
			dc.setAbbrev(1).call();
			throw new IllegalStateException(
					"Expected throw when given invalid arguments");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testDescribeCommandAfterMerge() throws Exception {
        /*
         * This test confirms that describe behaves the same as cgit's describe.
         *
         * For the following tree:
         *      * E
         *    / |
         *    * | D
         *    * | C
         *    | * B
         *     \|
         *      * A (TEST)
         *
         * If commit A is labeled as "TEST", then git-describe returns the following:
         * A: TEST
         * B: TEST-1-...
         * C: TEST-1-...
         * D: TEST-2-...
         * E: TEST-4-...
         *
         * NOTE: eclipse auto-format ruins this comment.  Please do not commit edits
         * which ruin this comment.
         */

		Git git = Git.wrap(db);

		writeTrashFile("File_A.txt", "Hello world");
		git.add().addFilepattern("File_A.txt").call();
		RevCommit commitA = git.commit().setMessage("Commit A")
				.call();
		git.branchCreate().setName("A").call();

		git.tag()
				.setName("TEST")
				.setObjectId(commitA)
				.setTagger(
						new PersonIdent("Test Tagger", "tagger@example.coml"))
				.setMessage("Tag to test DescribeCommand").call();

		git.branchCreate().setName("B").call();
		git.checkout().setName("B").call();
		writeTrashFile("File_B.txt", "Hello world");
		git.add().addFilepattern("File_B.txt").call();
		RevCommit secondCommit = git.commit().setMessage("Commit B").call();

		git.checkout().setName("A").call();

		git.branchCreate().setName("C").call();
		git.checkout().setName("C").call();
		writeTrashFile("File_C.txt", "Hello world");
		git.add().addFilepattern("File_C.txt").call();
		RevCommit thirdCommit = git.commit().setMessage("Commit C").call();

		git.branchCreate().setName("D").call();
		git.checkout().setName("D").call();
		writeTrashFile("File_D.txt", "Hello world");
		git.add().addFilepattern("File_D.txt").call();
		RevCommit fourthCommit = git.commit().setMessage("Commit D").call();

		git.checkout().setName("B").call();

		git.branchCreate().setName("E").call();
		git.checkout().setName("E").call();
		MergeResult mr = git.merge().include(fourthCommit).call();

		RevWalk w = new RevWalk(db);
		RevCommit fifthCommit = w.parseCommit(mr.getNewHead());
		w.release();

		String defaultAbbreviationLength = Integer.toString(DescribeCommand
				.getDefaultAbbreviationLength());

		{
			String actual = git.describe().setObjectId(secondCommit).call();
			String expectedRegex = "TEST-1-g[0-9a-f]{"
					+ defaultAbbreviationLength + "}";
			assertTrue("Describe String matches expected regex",
					actual.matches(expectedRegex));
		}
		{
			String actual = git.describe().setObjectId(thirdCommit).call();
			String expectedRegex = "TEST-1-g[0-9a-f]{"
					+ defaultAbbreviationLength + "}";
			assertTrue("Describe String matches expected regex",
					actual.matches(expectedRegex));
		}
		{
			String actual = git.describe().setObjectId(fourthCommit).call();
			String expectedRegex = "TEST-2-g[0-9a-f]{"
					+ defaultAbbreviationLength + "}";
			assertTrue("Describe String matches expected regex",
					actual.matches(expectedRegex));
		}
		{
			String actual = git.describe().setObjectId(fifthCommit).call();
			String expectedRegex = "TEST-4-g[0-9a-f]{"
					+ defaultAbbreviationLength
				+ "}";
			assertTrue("Describe String matches expected regex",
					actual.matches(expectedRegex));
		}
		/*
		 * TODO: test that when there are multiple tags, the newest is picked.
		 * The tag must be at least 1 second newer for this test and no matter
		 * what I did (thread.sleep, etc) the second tag always had the same
		 * time as the first tag, making that test difficult to write.
		 */



	}
}
