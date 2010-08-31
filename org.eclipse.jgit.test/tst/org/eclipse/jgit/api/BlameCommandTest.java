/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
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

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.jgit.blame.BlameEntry;
import org.eclipse.jgit.blame.Range;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileTreeEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class BlameCommandTest extends RepositoryTestCase {

	public void testBlameEmptyPath() throws Exception {
		Git git = new Git(db);
		try {
			git.blame().call();
			fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	public void testBlameNullPath() throws Exception {
		Git git = new Git(db);
		try {
			git.blame().setPath(null).call();
			fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	public void testSimpleBlame() throws Exception {
		Git git = new Git(db);
		writeTrashFile("test.txt", "content");
		git.add().addFilepattern("test.txt").call();
		RevCommit commit = git.commit().setAuthor(author).setMessage("").call();
		Iterable<BlameEntry> entries = git.blame().setPath("test.txt").call();
		BlameEntry blame = entries.iterator().next();
		assertEquals(commit, blame.suspect.getCommit());
		assertEquals(0, blame.suspectStart);
		// TODO why 2 and not 1 ??
		assertEquals(2, blame.originalRange.getLength());
	}

	public void testBlameCallableOnce() throws Exception {
		Git git = new Git(db);
		writeTrashFile("test.txt", "content");
		git.add().addFilepattern("test.txt").call();
		git.commit().setAuthor(author).setMessage("").call();
		BlameCommand blame = git.blame().setPath("test.txt");
		blame.call();
		try {
			blame.call();
			fail("Command should not be callable anymore");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			blame.setPath("foo");
			fail("Command should not be callable anymore");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testBlameTwoCommits() throws Exception {
		Git git = new Git(db);
		writeTrashFile("test.txt", "some\ncontent");
		git.add().addFilepattern("test.txt").call();
		RevCommit firstCommit = git.commit().setAuthor(author)
				.setMessage("first")
				.call();

		writeTrashFile("test.txt", "some\nthing");
		git.add().addFilepattern("test.txt").call();
		RevCommit secondCommit = git.commit().setAuthor(author)
				.setMessage("second")
				.call();

		Iterable<BlameEntry> entries = git.blame().setPath("test.txt").call();
		BlameEntry blame = entries.iterator().next();
		Iterator<BlameEntry> iterator = entries.iterator();
		blame = iterator.next();
		assertEquals(firstCommit, blame.suspect.getCommit());
		assertEquals(0, blame.suspectStart);
		blame = iterator.next();
		assertEquals(secondCommit, blame.suspect.getCommit());
		assertEquals(1, blame.suspectStart);
	}

	public void testBlameStartCommit() throws Exception {
		Git git = new Git(db);
		writeTrashFile("test.txt", "some\ncontent");
		git.add().addFilepattern("test.txt").call();
		RevCommit firstCommit = git.commit().setAuthor(author)
				.setMessage("first").call();

		writeTrashFile("test.txt", "some\nthing");
		git.add().addFilepattern("test.txt").call();
		RevCommit secondCommit = git.commit().setAuthor(author)
				.setMessage("second").call();

		writeTrashFile("test.txt", "some\nthing else");
		git.add().addFilepattern("test.txt").call();
		git.commit().setAuthor(author)
				.setMessage("second").call();

		Iterable<BlameEntry> entries = git.blame().setPath("test.txt")
				.setStartCommit(secondCommit).call();
		Iterator<BlameEntry> iterator = entries.iterator();
		BlameEntry blame = iterator.next();
		assertEquals(firstCommit, blame.suspect.getCommit());
		assertEquals(0, blame.suspectStart);
		blame = iterator.next();
		assertEquals(secondCommit, blame.suspect.getCommit());
		assertEquals(1, blame.suspectStart);
	}

	private static final String ENCODING = "UTF-8";

	public void testBlameSimple() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = a + "b 1\nb 2\nb 3";
		String c = "c 1\nc 2\nc 3\n" + b;
		String versions[] = new String[] { a, b, c };
		ObjectId[] commits = new ObjectId[3];
		int i = 0;
		File file = new File(repo.getWorkTree(), "test.txt");
		Git git = new Git(repo);
		for (String version : versions) {

			PrintWriter writer = new PrintWriter(file);
			writer.print(version);
			writer.close();

			git.add().addFilepattern("test.txt").call();
			ObjectId commitId = git.commit().setAuthor(author)
					.setCommitter(committer).setMessage("test022\n").call()
					.getId();

			commits[i] = commitId;
			i++;
		}
		ObjectId headId = repo.resolve("HEAD");
		RevCommit latestCommit = new RevWalk(repo).parseCommit(headId);

		RevWalk revWalk = new RevWalk(repo);
		RevCommit commit = revWalk.parseCommit(latestCommit.getId());
		Iterable<BlameEntry> blame = git.blame().setPath("test.txt")
				.setStartCommit(commit).call();
		Iterator<BlameEntry> iterator = blame.iterator();

		ObjectId[] expectedCommits = new ObjectId[] { commits[2], commits[0],
				commits[1] };
		Range[] expectedRanges = new Range[] { new Range(0, 3),
				new Range(3, 3), new Range(6, 3) };
		int[] expectedSuspectStarts = new int[] { 0, 0, 3 };

		for (i = 0; i < expectedCommits.length; i++) {
			BlameEntry blameEntry = iterator.next();
			assertTrue(blameEntry.guilty);
			assertEquals(expectedCommits[i], blameEntry.suspect.getCommit());
			assertEquals(expectedRanges[i], blameEntry.originalRange);
			assertEquals(expectedSuspectStarts[i], blameEntry.suspectStart);
		}
	}

	public void testBlameHEAD() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = a + "b 1\nb 2\nb 3";
		String c = "c 1\nc 2\nc 3\n" + b;
		String versions[] = new String[] { a, b, c };
		ObjectId[] commits = new ObjectId[3];
		int i = 0;
		File file = new File(repo.getWorkTree(), "test.txt");
		Git git = new Git(repo);
		for (String version : versions) {

			PrintWriter writer = new PrintWriter(file);
			writer.print(version);
			writer.close();

			git.add().addFilepattern("test.txt").call();
			ObjectId commitId = git.commit().setAuthor(author)
					.setCommitter(committer).setMessage("test022\n").call()
					.getId();

			commits[i] = commitId;
			i++;
		}

		Iterable<BlameEntry> blame = git.blame().setPath("test.txt").call();
		Iterator<BlameEntry> iterator = blame.iterator();

		ObjectId[] expectedCommits = new ObjectId[] { commits[2], commits[0],
				commits[1] };
		Range[] expectedRanges = new Range[] { new Range(0, 3),
				new Range(3, 3), new Range(6, 3) };
		int[] expectedSuspectStarts = new int[] { 0, 0, 3 };
		for (i = 0; i < expectedCommits.length; i++) {
			BlameEntry blameEntry = iterator.next();
			assertTrue(blameEntry.guilty);
			assertEquals(expectedCommits[i], blameEntry.suspect.getCommit());
			assertEquals(expectedRanges[i], blameEntry.originalRange);
			assertEquals(expectedSuspectStarts[i], blameEntry.suspectStart);
		}
	}

	public void testBlamePasstroughParent() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = a + "b 1\nb 2\nb 3";
		String c = "c 1\nc 2\nc 3\n" + b;
		String versions[] = new String[] { a, b, c };
		ObjectId[] commits = new ObjectId[3];
		int i = 0;
		File file = new File(repo.getWorkTree(), "test.txt");
		Git git = new Git(repo);
		for (String version : versions) {

			PrintWriter writer = new PrintWriter(file);
			writer.print(version);
			writer.close();

			git.add().addFilepattern("test.txt").call();
			ObjectId commitId = git.commit().setAuthor(author)
					.setCommitter(committer).setMessage("test022\n").call()
					.getId();

			commits[i] = commitId;
			i++;
		}
		// empty commits between change and HEAD
		git.commit().setAuthor(author).setCommitter(committer)
				.setMessage("test022\n").call().getId();

		Iterable<BlameEntry> blame = git.blame().setPath("test.txt").call();
		Iterator<BlameEntry> iterator = blame.iterator();

		ObjectId[] expectedCommits = new ObjectId[] { commits[2], commits[0],
				commits[1] };
		Range[] expectedRanges = new Range[] { new Range(0, 3),
				new Range(3, 3), new Range(6, 3) };
		int[] expectedSuspectStarts = new int[] { 0, 0, 3 };
		for (i = 0; i < expectedCommits.length; i++) {
			BlameEntry blameEntry = iterator.next();
			assertTrue(blameEntry.guilty);
			assertEquals(expectedCommits[i], blameEntry.suspect.getCommit());
			assertEquals(expectedRanges[i], blameEntry.originalRange);
			assertEquals(expectedSuspectStarts[i], blameEntry.suspectStart);
		}
	}

	public void testMerge() throws Exception {
		// Classic diamond merge ( dots just to keep formatting)
		// .... a
		// ... / \
		// . ba . ac
		// ... \ /
		// ... bac

		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\nb 2\nb 3\n";
		String c = "c 1\nc 2\nc 3\n";

		String authors[] = new String[] { "a", "ba", "ac", "bac" };
		String versions[] = new String[] { a, b + a, a + c, b + a + c };
		// used to find commit ids
		String parentCommitIdAuthors[] = new String[] { "", "a", "a", "ba,ac" };
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		for (String version : versions) {
			ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
					version.getBytes(ENCODING));
			final Tree tree = new Tree(repo);
			tree.addEntry(new FileTreeEntry(tree, id,
					"test".getBytes(ENCODING), false));
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();
			commit.setAuthor(new PersonIdent(authors[i], "", 0, 0));
			commit.setCommitter(new PersonIdent(authors[i], "", 0, 0));
			commit.setMessage("test022\n");
			commit.setTreeId(treeId);
			ArrayList<ObjectId> parentIds = new ArrayList<ObjectId>();
			for (String commitAuthor : parentCommitIdAuthors[i].split(",")) {
				if (commitAuthor.length() > 0) {
					parentIds.add(commitMap.get(commitAuthor));
				}
			}
			commit.setParentIds(parentIds.toArray(new ObjectId[0]));
			ObjectId commitId = newInserter.insert(commit);
			newInserter.flush();
			commitMap.put(authors[i], commitId);
			commitIds.add(commitId);
			i++;
		}
		RevCommit latestCommit = new RevWalk(repo).parseCommit(commitMap
				.get("bac"));
		Git git = new Git(repo);
		Iterable<BlameEntry> blame = git.blame().setPath("test")
				.setStartCommit(latestCommit).call();
		Iterator<BlameEntry> iterator = blame.iterator();

		ObjectId[] expectedCommitIds = new ObjectId[] { commitMap.get("ba"),
				commitMap.get("a"), commitMap.get("ac") };
		Range[] expectedRanges = new Range[] { new Range(0, 3),
				new Range(3, 3), new Range(6, 3) };
		int[] expectedSuspectStarts = new int[] { 0, 0, 3 };
		for (i = 0; i < expectedCommitIds.length; i++) {
			BlameEntry blameEntry = iterator.next();
			assertTrue(blameEntry.guilty);
			assertEquals(expectedCommitIds[i], blameEntry.suspect.getCommit()
					.copy());
			assertEquals(expectedRanges[i], blameEntry.originalRange);
			assertEquals("entry " + i, expectedSuspectStarts[i],
					blameEntry.suspectStart);
		}
	}

	public void testMergeWithModifications() throws Exception {
		// Classic diamond merge ( dots just to keep formatting)
		// .... a
		// ... / \
		// . ba . ac
		// ... \ /
		// .. bxayc

		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\nb 2\nb 3\n";
		String c = "c 1\nc 2\nc 3\n";
		String x = "x 1\nx 2\nx 3\n";
		String y = "y 1\ny 2\ny 3\n";

		String authors[] = new String[] { "a", "ba", "ac", "bxayc" };
		String versions[] = new String[] { a, b + a, a + c, b + x + a + y + c };
		// used to find commit ids
		String parentCommitIdAuthors[] = new String[] { "", "a", "a", "ba,ac" };
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		ObjectInserter inserter = repo.getObjectDatabase().newInserter();
		for (String version : versions) {
			ObjectId id = inserter.insert(Constants.OBJ_BLOB,
					version.getBytes(ENCODING));
			final Tree tree = new Tree(repo);
			tree.addEntry(new FileTreeEntry(tree, id,
					"test".getBytes(ENCODING), false));
			ObjectId treeId = inserter
					.insert(Constants.OBJ_TREE, tree.format());

			final CommitBuilder commit = new CommitBuilder();
			commit.setAuthor(new PersonIdent(authors[i], "", 0, 0));
			commit.setCommitter(new PersonIdent(authors[i], "", 0, 0));
			commit.setMessage("test022\n");
			commit.setTreeId(treeId);
			ArrayList<ObjectId> parentIds = new ArrayList<ObjectId>();
			for (String commitAuthor : parentCommitIdAuthors[i].split(",")) {
				if (commitAuthor.length() > 0) {
					parentIds.add(commitMap.get(commitAuthor));
				}
			}
			commit.setParentIds(parentIds.toArray(new ObjectId[0]));
			ObjectId commitId = inserter.insert(commit);
			commitMap.put(authors[i], commitId);
			commitIds.add(commitId);
			i++;
		}
		RevCommit latestCommit = new RevWalk(repo).parseCommit(commitMap
				.get("bxayc"));

		Git git = new Git(repo);
		Iterable<BlameEntry> blame = git.blame().setPath("test")
				.setStartCommit(latestCommit).call();
		Iterator<BlameEntry> iterator = blame.iterator();

		ObjectId[] expectedCommitIds = new ObjectId[] { commitMap.get("ba"),
				commitMap.get("bxayc"), commitMap.get("a"),
				commitMap.get("bxayc"), commitMap.get("ac") };
		Range[] expectedRanges = new Range[] { new Range(0, 3),
				new Range(3, 3), new Range(6, 3), new Range(9, 3),
				new Range(12, 3) };
		int[] expectedSuspectStarts = new int[] { 0, 3, 0, 9, 3 };
		for (i = 0; i < expectedCommitIds.length; i++) {
			BlameEntry blameEntry = iterator.next();
			assertTrue(blameEntry.guilty);
			assertEquals(expectedCommitIds[i], blameEntry.suspect.getCommit()
					.copy());
			assertEquals(expectedRanges[i], blameEntry.originalRange);
			assertEquals("entry " + i, expectedSuspectStarts[i],
					blameEntry.suspectStart);
		}
	}

}
