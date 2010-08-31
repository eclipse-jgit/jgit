/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2009, Manuel Woelker <manuel.woelker+github@gmail.com>
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
package org.eclipse.jgit.blame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

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

public class RenameModifiedSearchStrategyTest extends RepositoryTestCase {
	private static final String ENCODING = "UTF-8";

	public void testPerfectMatch() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String filenames[] = new String[] { "first", "second" };
		String versions[] = new String[] { a, a };
		ObjectId lastCommitId = null;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		for (String version : versions) {
			ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
					version.getBytes(ENCODING));
			final Tree tree = new Tree(repo);
			tree.addEntry(new FileTreeEntry(tree, id, filenames[i]
					.getBytes(ENCODING), false));
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();

			commit.setAuthor(new PersonIdent("", "", 0, 0));
			commit.setCommitter(new PersonIdent("", "", 0, 0));
			commit.setMessage("test");
			commit.setTreeId(treeId);
			if (lastCommitId != null) {
				commit.setParentIds(new ObjectId[] { lastCommitId });
			}
			ObjectId commitId = newInserter.insert(commit);
			commitMap.put(filenames[i], commitId);
			lastCommitId = commitId;
			commitIds.add(commitId);
			i++;
		}
		RevWalk revWalk = new RevWalk(repo);
		RevCommit latestCommit = revWalk.parseCommit(lastCommitId);
		RenameModifiedSearchStrategy strategy = new RenameModifiedSearchStrategy();
		Origin[] origins = strategy.findOrigins(new Origin(repo, latestCommit,
				"second"));
		List<Origin> actual = Arrays.asList(origins);
		RevCommit firstCommit = revWalk.parseCommit(commitMap.get("first"));
		List<Origin> expected = Arrays.asList(new Origin(repo, firstCommit,
				"first"));
		assertEquals(expected, actual);
	}

	public void testSimilar() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\n";
		String filenames[] = new String[] { "first", "second" };
		String versions[] = new String[] { a, a + b };
		ObjectId lastCommitId = null;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		for (String version : versions) {
			ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
					version.getBytes(ENCODING));
			final Tree tree = new Tree(repo);
			tree.addEntry(new FileTreeEntry(tree, id, filenames[i]
					.getBytes(ENCODING), false));
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();

			commit.setAuthor(new PersonIdent("", "", 0, 0));
			commit.setCommitter(new PersonIdent("", "", 0, 0));
			commit.setMessage("test");
			commit.setTreeId(treeId);
			if (lastCommitId != null) {
				commit.setParentIds(new ObjectId[] { lastCommitId });
			}
			ObjectId commitId = newInserter.insert(commit);
			commitMap.put(filenames[i], commitId);
			lastCommitId = commitId;
			commitIds.add(commitId);
			i++;
		}
		RevWalk revWalk = new RevWalk(repo);
		RevCommit latestCommit = revWalk.parseCommit(lastCommitId);
		RenameModifiedSearchStrategy strategy = new RenameModifiedSearchStrategy();
		Origin[] origins = strategy.findOrigins(new Origin(repo, latestCommit,
				"second"));
		List<Origin> actual = Arrays.asList(origins);
		RevCommit firstCommit = revWalk.parseCommit(commitMap.get("first"));
		List<Origin> expected = Arrays.asList(new Origin(repo, firstCommit,
				"first"));
		assertEquals(expected, actual);
	}

	public void testTwoSimilar() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\n";
		String c = "c 1\n";
		String x = "x 1\n";
		String filenames[][] = new String[][] {
				new String[] { "firstb", "firstc" }, new String[] { "second" } };
		String versions[][] = new String[][] { new String[] { a + b, a + c },
				new String[] { a + x } };
		ObjectId lastCommitId = null;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		for (String[] version : versions) {
			final Tree tree = new Tree(repo);
			int j = 0;
			for (String file : version) {
				ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
						file.getBytes(ENCODING));
				tree.addEntry(new FileTreeEntry(tree, id, filenames[i][j]
						.getBytes(ENCODING), false));
				j++;
			}
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();

			commit.setAuthor(new PersonIdent("", "", 0, 0));
			commit.setCommitter(new PersonIdent("", "", 0, 0));
			commit.setMessage("test");
			commit.setTreeId(treeId);
			if (lastCommitId != null) {
				commit.setParentIds(new ObjectId[] { lastCommitId });
			}
			ObjectId commitId = newInserter.insert(commit);
			commitMap.put(filenames[i][0], commitId);
			lastCommitId = commitId;
			commitIds.add(commitId);
			i++;
		}
		RevWalk revWalk = new RevWalk(repo);
		RevCommit latestCommit = revWalk.parseCommit(lastCommitId);
		RenameModifiedSearchStrategy strategy = new RenameModifiedSearchStrategy();
		Origin[] origins = strategy.findOrigins(new Origin(repo, latestCommit,
				"second"));
		List<Origin> actual = Arrays.asList(origins);
		RevCommit firstCommit = revWalk.parseCommit(commitMap.get("firstb"));
		List<Origin> expected = Arrays.asList(new Origin(repo, firstCommit,
				"firstb"), new Origin(repo, firstCommit, "firstc"));
		assertEquals(expected, actual);
	}

	public void testUnsimilar() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\nb 2\nb 3\n";
		String filenames[] = new String[] { "first", "second" };
		String versions[] = new String[] { a, b };
		ObjectId lastCommitId = null;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		for (String version : versions) {
			ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
					version.getBytes(ENCODING));
			final Tree tree = new Tree(repo);
			tree.addEntry(new FileTreeEntry(tree, id, filenames[i]
					.getBytes(ENCODING), false));
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();

			commit.setAuthor(new PersonIdent("", "", 0, 0));
			commit.setCommitter(new PersonIdent("", "", 0, 0));
			commit.setMessage("test");
			commit.setTreeId(treeId);
			if (lastCommitId != null) {
				commit.setParentIds(new ObjectId[] { lastCommitId });
			}
			ObjectId commitId = newInserter.insert(commit);
			commitMap.put(filenames[i], commitId);
			lastCommitId = commitId;
			commitIds.add(commitId);
			i++;
		}
		RevWalk revWalk = new RevWalk(repo);
		RevCommit latestCommit = revWalk.parseCommit(lastCommitId);
		RenameModifiedSearchStrategy strategy = new RenameModifiedSearchStrategy();
		Origin[] origins = strategy.findOrigins(new Origin(repo, latestCommit,
				"second"));
		List<Origin> actual = Arrays.asList(origins);
		List<Origin> expected = new LinkedList<Origin>();
		assertEquals(expected, actual);
	}

	public void testCopyNotFound() throws Exception {
		Repository repo = createWorkRepository();
		String a = "a 1\na 2\na 3\n";
		String b = "b 1\n";
		String filenames[][] = new String[][] { new String[] { "first" },
				new String[] { "first_copy", "first" } };
		String versions[][] = new String[][] { new String[] { a },
				new String[] { a + b, a } };
		ObjectId lastCommitId = null;
		ObjectInserter newInserter = repo.getObjectDatabase().newInserter();
		ArrayList<ObjectId> commitIds = new ArrayList<ObjectId>();
		HashMap<String, ObjectId> commitMap = new HashMap<String, ObjectId>();
		int i = 0;
		for (String[] version : versions) {
			final Tree tree = new Tree(repo);
			int j = 0;
			for (String file : version) {
				ObjectId id = newInserter.insert(Constants.OBJ_BLOB,
						file.getBytes(ENCODING));
				tree.addEntry(new FileTreeEntry(tree, id, filenames[i][j]
						.getBytes(ENCODING), false));
				j++;
			}
			ObjectId treeId = newInserter.insert(Constants.OBJ_TREE,
					tree.format());

			final CommitBuilder commit = new CommitBuilder();

			commit.setAuthor(new PersonIdent("", "", 0, 0));
			commit.setCommitter(new PersonIdent("", "", 0, 0));
			commit.setMessage("test");
			commit.setTreeId(treeId);
			if (lastCommitId != null) {
				commit.setParentIds(new ObjectId[] { lastCommitId });
			}
			ObjectId commitId = newInserter.insert(commit);
			commitMap.put(filenames[i][0], commitId);
			lastCommitId = commitId;
			commitIds.add(commitId);
			i++;
		}
		RevWalk revWalk = new RevWalk(repo);
		RevCommit latestCommit = revWalk.parseCommit(lastCommitId);
		RenameModifiedSearchStrategy strategy = new RenameModifiedSearchStrategy();
		Origin[] origins = strategy.findOrigins(new Origin(repo, latestCommit,
				"first_copy"));
		List<Origin> actual = Arrays.asList(origins);
		List<Origin> expected = new LinkedList<Origin>();
		assertEquals(expected, actual);
	}

}
