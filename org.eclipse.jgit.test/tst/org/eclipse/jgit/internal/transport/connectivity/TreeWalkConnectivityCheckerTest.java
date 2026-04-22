/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.transport.connectivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ConnectivityChecker.ConnectivityCheckInfo;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

/** Tests for {@link TreeWalkConnectivityChecker}. */
public class TreeWalkConnectivityCheckerTest {
	@Rule
	public MockitoRule rule = MockitoJUnit.rule();

	private TestRepository<InMemoryRepository> tr;

	private TreeWalkConnectivityChecker checker;

	private ConnectivityCheckInfo info;

	private Set<ObjectId> haves;

	private CountingProgressMonitor pm = new CountingProgressMonitor();

	private static class CountingProgressMonitor implements ProgressMonitor {
		private long objectsCheckedCount = 0;

		@Override
		public void start(int totalTasks) {
			// noop
		}

		@Override
		public void beginTask(String title, int totalWork) {
			// noop
		}

		@Override
		public void update(int completed) {
			objectsCheckedCount += completed;
		}

		@Override
		public void endTask() {
			// noop
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public void showDuration(boolean show) {
			// noop
		}

		public long getObjectsCheckedCount() {
			return objectsCheckedCount;
		}

		public void reset() {
			objectsCheckedCount = 0;
		}
	}

	@Mock
	private PackParser parser;

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("test")));
		checker = new TreeWalkConnectivityChecker();
		info = new ConnectivityCheckInfo();
		info.setRepository(tr.getRepository());
		info.setWalk(tr.getRevWalk());
		info.setParser(parser);
		when(parser.needNewObjectIds()).thenReturn(true);
		haves = new HashSet<>();
		pm.reset();
	}

	@Test
	public void testFailureMissingParent() throws Exception {
		ObjectId missingParentId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567");

		RevCommit newCommit = createCommitWithParent(missingParentId);

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit);

		// MOE thrown within RevWalk.next() when emitting newCommit, count isn't
		// updated
		runCheckAndExpectMissingObject(missingParentId, 0);
	}

	@Test
	public void testFailureMissingParentChain() throws Exception {
		ObjectId missingParentId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567");

		RevCommit commit1 = createCommitWithParent(missingParentId);
		RevCommit commit2 = createCommitWithParent(commit1);

		setupSingleReceiveCommand(ObjectId.zeroId(), commit2.getId());
		mockNewPackObjects(commit1, commit2);

		runCheckAndExpectMissingObject(missingParentId, 1);
	}

	@Test
	public void testFailureMissingParentMultipleBranches() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		ObjectId missingParentId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567");

		RevCommit newCommit1 = tr.commit().parent(base).create();
		RevCommit newCommit2 = createCommitWithParent(missingParentId);

		ReceiveCommand cmd1 = new ReceiveCommand(base, newCommit1,
				"refs/heads/branch1");
		ReceiveCommand cmd2 = new ReceiveCommand(ObjectId.zeroId(), newCommit2,
				"refs/heads/branch2");

		info.setCommands(Arrays.asList(cmd1, cmd2));

		mockNewPackObjects(newCommit1, newCommit2);

		runCheckAndExpectMissingObject(missingParentId, 1);
	}

	@Test
	public void testFailureMissingBlob() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		// Create a missing blob ID that is not in DB or pack
		ObjectId missingBlobId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567");
		RevBlob missingBlob = tr.getRevWalk().lookupBlob(missingBlobId);

		RevCommit newCommit = tr.commit().parent(base).add("foo", missingBlob)
				.create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		runCheckAndExpectMissingObject(missingBlobId, 4);
	}

	@Test
	public void testFailureMissingSubtree() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		ObjectId missingTreeId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234568");

		RevBlob blob = tr.blob("content");

		TreeFormatter formatter = new TreeFormatter();
		formatter.append("file", FileMode.REGULAR_FILE, blob);
		formatter.append("dir", FileMode.TREE, missingTreeId);

		try (ObjectInserter inserter = tr.getRepository().newObjectInserter()) {
			ObjectId rootTreeId = inserter.insert(formatter);

			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(rootTreeId);
			cb.setAuthor(new PersonIdent("Author", "author@example.com"));
			cb.setCommitter(
					new PersonIdent("Committer", "committer@example.com"));
			cb.setMessage("Commit with missing subtree");
			cb.setParentIds(base.getId());

			ObjectId commitId = inserter.insert(cb);
			inserter.flush();

			RevCommit newCommit = tr.getRevWalk().parseCommit(commitId);

			setupSingleReceiveCommand(base.getId(), newCommit.getId());
			mockNewPackObjects(newCommit, newCommit.getTree(), blob);

			runCheckAndExpectMissingObject(missingTreeId, 4);
		}
	}

	@Test
	public void testFailureUnreachableParentCommit() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevCommit secretCommit = tr.commit().create();
		RevCommit newCommit = tr.commit().parent(secretCommit).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		runCheckAndExpectMissingObject(secretCommit.getId(), 3);
	}

	@Test
	public void testMultipleBranchesUnreachableParent() throws Exception {
		// base2 & new2 have newer timestamps
		RevCommit base1 = tr.commit().create();
		RevCommit new1 = tr.commit().parent(base1).create();
		RevCommit base2 = tr.commit().create();
		RevCommit new2 = tr.commit().parent(base2).create();

		tr.branch("refs/heads/base1").update(base1);
		haves.add(base1);

		ReceiveCommand cmd1 = new ReceiveCommand(base1, new1, "refs/heads/b1");
		ReceiveCommand cmd2 = new ReceiveCommand(base2, new2, "refs/heads/b2");

		info.setCommands(Arrays.asList(cmd1, cmd2));
		mockNewPackObjects(new1, new1.getTree(), new2, new2.getTree());

		runCheckAndExpectMissingObject(base2, 5);

		// Verify order doesn't matter - reset state and swap order
		setUp();

		// base2 & new2 have newer timestamps
		base1 = tr.commit().create();
		new1 = tr.commit().parent(base1).create();
		base2 = tr.commit().create();
		new2 = tr.commit().parent(base2).create();

		tr.branch("refs/heads/base2").update(base2);
		haves.add(base2);

		cmd1 = new ReceiveCommand(base1, new1, "refs/heads/b1");
		cmd2 = new ReceiveCommand(base2, new2, "refs/heads/b2");

		info.setCommands(Arrays.asList(cmd2, cmd1));
		mockNewPackObjects(new1, new1.getTree(), new2, new2.getTree());

		runCheckAndExpectMissingObject(base1, 5);
	}

	@Test
	public void testThinPackFailureUnreachableDeltaBase() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob deltafiledBlob = tr.blob("simulated delta blob");
		RevBlob missingBase = tr.getRevWalk().lookupBlob(ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567"));
		RevCommit newCommit = tr.commit().parent(base)
				.add("foo", deltafiledBlob).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit);
		mockNewPackObjects(newCommit, newCommit.getTree(), deltafiledBlob);

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(missingBase);
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndExpectMissingObject(missingBase.getId(), 2);
	}

	@Test
	public void testSuccessMatchingParent() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		// Create a new commit in the pack that explicitly matches the base tree
		ObjectId newCommitId = tr.unparsedCommit(1, base.getTree(),
				base.getId());
		RevCommit newCommit = tr.getRevWalk().parseCommit(newCommitId);

		setupSingleReceiveCommand(base.getId(), newCommitId);
		mockNewPackObjects(newCommit);
		runCheckAndAssertCount(4);
	}

	@Test
	public void testSuccessWithDifferentBlob() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob blob = tr.blob("hello");
		RevCommit newCommit = tr.commit().parent(base).add("foo", blob)
				.create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree(), blob);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testSuccessNoReachabilityCheckWhenParentsInHaves()
			throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevCommit currentHaves = base;
		for (int i = 0; i < 250; i++) {
			currentHaves = tr.commit().parent(currentHaves).create();
			haves.add(currentHaves.getId());
		}

		RevBlob blob = tr.blob("hello");
		RevCommit newCommit = tr.commit().parent(base).add("foo", blob)
				.create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit);

		runCheckAndAssertCount(4);
	}

	/**
	 * Test with a large flat tree to measure objects checked.
	 *
	 * <pre>{@code
	 * [Existing DB]               [Received Pack]
	 *      base <------------------- newCommit
	 *        |                          |
	 *      root_tree                 root_tree (differs)
	 *        |                          |
	 *        +- file0                   +- file0 (differs)
	 *        +- file1                   +- file1 (differs)
	 *        +- file2                   +- file2 (differs)
	 *        +- file3                   +- file3 (differs)
	 *        +- file4                   +- file4 (differs)
	 *        +- file5                   +- file5 (shared)
	 *        +- ...                     +- ...
	 *        `- file99                  `- file99 (shared)
	 * }</pre>
	 */
	@Test
	public void testLargeFlatTree() throws Exception {
		TestRepository.CommitBuilder cb = tr.commit();
		for (int i = 0; i < 100; i++) {
			cb.add("file" + i, tr.blob("content" + i));
		}
		RevCommit base = cb.create();
		haves.add(base.getId());

		TestRepository.CommitBuilder cbNew = tr.commit().parent(base);
		List<ObjectId> allNewObjects = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			RevBlob b = tr.blob("new content " + i);
			cbNew.add("file" + i, b);
			allNewObjects.add(b);
		}
		RevCommit newCommit = cbNew.create();
		allNewObjects.add(newCommit);
		allNewObjects.add(newCommit.getTree());

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(allNewObjects);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testGitlink() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		ObjectId submoduleCommitId = ObjectId
				.fromString("0123456789abcdef0123456789abcdef01234567");

		TreeFormatter formatter = new TreeFormatter();
		formatter.append("submodule", FileMode.GITLINK, submoduleCommitId);

		try (ObjectInserter inserter = tr.getRepository().newObjectInserter()) {
			ObjectId rootTreeId = inserter.insert(formatter);

			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(rootTreeId);
			cb.setAuthor(new PersonIdent("Author", "author@example.com"));
			cb.setCommitter(
					new PersonIdent("Committer", "committer@example.com"));
			cb.setMessage("Commit with gitlink");
			cb.setParentIds(base.getId());

			ObjectId commitId = inserter.insert(cb);
			inserter.flush();

			RevCommit newCommit = tr.getRevWalk().parseCommit(commitId);

			setupSingleReceiveCommand(base.getId(), newCommit.getId());
			mockNewPackObjects(newCommit, newCommit.getTree());

			// Should not fail gitlink identifier not being in the local
			// database
			runCheckAndAssertCount(4);
		}
	}

	@Test
	public void testZeroParents() throws Exception {
		RevBlob blob = tr.blob("content");
		RevCommit rootCommit = tr.commit().add("file1", blob).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), rootCommit.getId());
		mockNewPackObjects(rootCommit, rootCommit.getTree(), blob);

		runCheckAndAssertCount(2);
	}

	@Test
	public void testMultipleParents() throws Exception {
		RevCommit parent1 = tr.commit().add("file1", tr.blob("content1"))
				.create();
		RevCommit parent2 = tr.commit().add("file2", tr.blob("content2"))
				.create();
		haves.add(parent1.getId());
		haves.add(parent2.getId());

		RevCommit mergeCommit = tr.commit().parent(parent1).parent(parent2)
				.add("file3", tr.blob("content3")).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), mergeCommit.getId());
		mockNewPackObjects(mergeCommit, mergeCommit.getTree());

		runCheckAndAssertCount(6);
	}

	@Test
	public void testChainedCommitsInPack() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevCommit c1 = tr.commit().parent(base).create();
		RevCommit c2 = tr.commit().parent(c1).create();

		setupSingleReceiveCommand(base.getId(), c2.getId());
		mockNewPackObjects(c1, c2, c1.getTree(), c2.getTree());

		runCheckAndAssertCount(7);
	}

	@Test
	public void testSuccessReachableParentCommit() throws Exception {
		RevCommit base = tr.commit().create();
		RevCommit reachableCommit = tr.commit().parent(base).create();
		haves.add(reachableCommit.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		runCheckAndAssertCount(5);
	}

	@Test
	public void testSuccessFallbackToFullRefDatabase() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevCommit newRootCommit = tr.commit().create();
		tr.branch("refs/heads/unconnected").update(newRootCommit);

		RevCommit newCommit = tr.commit().parent(newRootCommit).create();

		info.setCommands(Collections.singletonList(new ReceiveCommand(
				ObjectId.zeroId(), newCommit.getId(), "refs/heads/master")));
		mockNewPackObjects(newCommit, newCommit.getTree());
		runCheckAndAssertCount(5);
	}

	@Test
	public void testCheckReachabilityWithBlobInHaves() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob blob = tr.blob("blob content");
		haves.add(blob.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		runCheckAndAssertCount(4);
	}

	@Test
	public void testCheckReachabilityWithAnnotatedTagInHaves()
			throws Exception {
		RevCommit base = tr.commit().create();

		RevTag tag = tr.tag("my-tag", base);
		haves.add(tag.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());
		runCheckAndAssertCount(5);
	}

	@Test
	public void testCheckReachabilityWithBlobInRefs() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob blob = tr.blob("blob content");
		tr.update("refs/tags/my-blob", blob);

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());
		runCheckAndAssertCount(4);
	}

	@Test
	public void testCheckReachabilityWithAnnotatedTagInRefs() throws Exception {
		RevCommit base = tr.commit().create();

		RevTag tag = tr.tag("my-tag", base);
		tr.update("refs/tags/my-tag", tag);

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(ObjectId.zeroId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());
		runCheckAndAssertCount(4);
	}

	@Test
	public void testSuccessWithDeletion() throws Exception {
		RevBlob blob = tr.blob("hello");
		RevCommit base = tr.commit().add("foo", blob).create();
		haves.add(base.getId());

		// Create a new commit that deletes "foo"
		RevCommit newCommit = tr.commit().parent(base).rm("foo").create();

		info.setCommands(Collections.singletonList(new ReceiveCommand(
				base.getId(), newCommit.getId(), "refs/heads/master")));

		mockNewPackObjects(newCommit, newCommit.getTree());

		runCheckAndAssertCount(4);
	}

	@Test
	public void testDeleteCommandIgnored() throws Exception {
		RevCommit base = tr.commit().create();
		tr.branch("refs/heads/master").update(base);

		ReceiveCommand cmd = new ReceiveCommand(base.getId(), ObjectId.zeroId(),
				"refs/heads/master", ReceiveCommand.Type.DELETE);

		info.setCommands(Collections.singletonList(cmd));

		runCheckAndAssertCount(0);
	}

	@Test
	public void testMultipleCommands() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob blob1 = tr.blob("content1");
		RevCommit newCommit1 = tr.commit().parent(base).add("foo1", blob1)
				.create();

		RevBlob blob2 = tr.blob("content2");
		RevCommit newCommit2 = tr.commit().parent(base).add("foo2", blob2)
				.create();

		ReceiveCommand cmd1 = new ReceiveCommand(base.getId(),
				newCommit1.getId(), "refs/heads/branch1");
		ReceiveCommand cmd2 = new ReceiveCommand(base.getId(),
				newCommit2.getId(), "refs/heads/branch2");

		info.setCommands(Arrays.asList(cmd1, cmd2));

		mockNewPackObjects(newCommit1, newCommit1.getTree(), blob1, newCommit2,
				newCommit2.getTree(), blob2);

		runCheckAndAssertCount(7);
	}

	@Test
	public void testThinPackSuccess() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob baseBlob = tr.blob("base blob content");
		haves.add(baseBlob.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(baseBlob.getId());
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testThinPackWithBlobInHaves() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob baseBlob = tr.blob("base blob content");

		RevBlob unrelatedBlob = tr.blob("unrelated blob content");
		haves.add(unrelatedBlob.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(baseBlob.getId());
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testThinPackWithSignedTagInHaves() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob baseBlob = tr.blob("base blob content");

		RevTag tag = tr.tag("my-tag", base);
		haves.add(tag.getId());

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(baseBlob.getId());
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testThinPackWithBlobInRefs() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob baseBlob = tr.blob("base blob content");

		RevBlob unrelatedBlob = tr.blob("unrelated blob content");
		tr.update("refs/tags/my-blob", unrelatedBlob);

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(baseBlob.getId());
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testThinPackWithAnnotatedTagInRefs() throws Exception {
		RevCommit base = tr.commit().create();
		haves.add(base.getId());

		RevBlob baseBlob = tr.blob("base blob content");

		RevTag tag = tr.tag("my-tag", base);
		tr.update("refs/tags/my-tag", tag);

		RevCommit newCommit = tr.commit().parent(base).create();

		setupSingleReceiveCommand(base.getId(), newCommit.getId());
		mockNewPackObjects(newCommit, newCommit.getTree());

		ObjectIdSubclassMap<ObjectId> baseObjectIds = new ObjectIdSubclassMap<>();
		baseObjectIds.add(baseBlob.getId());
		when(parser.getBaseObjectIds()).thenReturn(baseObjectIds);
		info.setCheckObjects(true);

		runCheckAndAssertCount(4);
	}

	@Test
	public void testMultipleCommandsDifferentParents() throws Exception {
		RevCommit base1 = tr.commit().create();
		RevCommit base2 = tr.commit().create();
		tr.branch("refs/heads/base1").update(base1);
		tr.branch("refs/heads/base2").update(base2);
		haves.add(base1.getId());
		haves.add(base2.getId());

		RevBlob blob1 = tr.blob("content1");
		RevCommit newCommit1 = tr.commit().parent(base1).add("foo1", blob1)
				.create();

		RevBlob blob2 = tr.blob("content2");
		RevCommit newCommit2 = tr.commit().parent(base2).add("foo2", blob2)
				.create();

		ReceiveCommand cmd1 = new ReceiveCommand(base1.getId(),
				newCommit1.getId(), "refs/heads/branch1");
		ReceiveCommand cmd2 = new ReceiveCommand(base2.getId(),
				newCommit2.getId(), "refs/heads/branch2");

		info.setCommands(Arrays.asList(cmd1, cmd2));

		mockNewPackObjects(newCommit1, newCommit1.getTree(), blob1, newCommit2,
				newCommit2.getTree(), blob2);

		runCheckAndAssertCount(8);
	}

	/**
	 * Test case with a deep and wide tree structure. Modifying a file 5 levels
	 * deep should only check the trees along that path.
	 *
	 * <pre>{@code
	 * [Existing DB]               [Received Pack]
	 *      base <------------------- newCommit
	 *        |                          |
	 *      root_tree                 root_tree (differs)
	 *        |                          |
	 *        +- A                       +- A (differs)
	 *        |  +- C                    |  +- C (differs)
	 *        |  |  +- I                 |  |  +- I (differs)
	 *        |  |  |  +- J              |  |  |  +- J (differs)
	 *        |  |  |  |  +- K           |  |  |  |  +- K (differs)
	 *        |  |  |  |  |  +- L        |  |  |  |  |  +- L (differs)
	 *        |  |  |  |  |  |  +- M     |  |  |  |  |  |  +- M (differs)
	 *        |  |  |  |  |  |  |  `- 1  |  |  |  |  |  |  |  `- 1 (differs)
	 *        |  +- D                    |  +- D (shared)
	 *        |     `- 2                 |     `- 2 (shared)
	 *        +- B                       +- B (shared)
	 *           +- E                    |  +- E (shared)
	 *           |  `- 3                 |     `- 3 (shared)
	 *           +- F                    |  +- F (shared)
	 *           |  `- 4                 |     `- 4 (shared)
	 *           +- G                    |  +- G (shared)
	 *           |  `- 5                 |     `- 5 (shared)
	 *           +- H                    |  +- H (shared)
	 *              `- 6                 |     `- 6 (shared)
	 * }</pre>
	 */
	@Test
	public void testDeepTreeSuccess() throws Exception {
		// Create base with deep and wide structure
		TestRepository.CommitBuilder cb = tr.commit();
		cb.add("A/C/I/J/K/L/M/1", tr.blob("content1"));
		cb.add("A/D/2", tr.blob("content2"));
		cb.add("B/E/3", tr.blob("content3"));
		cb.add("B/F/4", tr.blob("content4"));
		cb.add("B/G/5", tr.blob("content5"));
		cb.add("B/H/6", tr.blob("content6"));
		RevCommit base = cb.create();
		haves.add(base.getId());

		RevCommit currentHaves = base;
		for (int i = 0; i < 250; i++) {
			currentHaves = tr.commit().parent(currentHaves).create();
			haves.add(currentHaves.getId());
		}

		// Create new commit modifying file at end of deep path
		RevBlob newBlob = tr.blob("new content");
		RevCommit newCommit = tr.commit().parent(base)
				.add("A/C/I/J/K/L/M/000", newBlob).create();

		info.setCommands(Collections.singletonList(new ReceiveCommand(
				base.getId(), newCommit.getId(), "refs/heads/master")));

		List<ObjectId> allNewObjects = new ArrayList<>();
		allNewObjects.add(newCommit);
		allNewObjects.add(newCommit.getTree());
		allNewObjects.add(newBlob);
		try (TreeWalk tw = new TreeWalk(tr.getRepository())) {
			tw.setRecursive(false);
			tw.addTree(newCommit.getTree());
			String[] pathSegments = { "A", "C", "I", "J", "K", "L", "M" };
			for (String segment : pathSegments) {
				while (tw.next()) {
					if (tw.getNameString().equals(segment)) {
						allNewObjects.add(tw.getObjectId(0));
						tw.enterSubtree();
						break;
					}
				}
			}
		}
		mockNewPackObjects(allNewObjects);

		runCheckAndAssertCount(18);
	}

	/**
	 * Compare the efficiency of the TreeWalk checker vs. iterative vs. full
	 * checkers. Initial JFR results on this test:
	 * <ul>
	 * <li>TreeWalk Checker: 22 checked objects, 25 JFR allocations</li>
	 * <li>Iterative Checker: 14 checked objects, 41 JFR allocations</li>
	 * <li>Full Checker: 264 checked objects, 147 JFR allocations</li>
	 * </ul>
	 *
	 * <pre>{@code
	 * [Existing DB]               [Received Pack]
	 *      base <------------------- newCommit
	 *        |                          |
	 *      root_tree                 root_tree (differs)
	 *        |                          |
	 *        +- A                       +- A (differs)
	 *        |  +- C                    |  +- C (differs)
	 *        |  |  +- I                 |  |  +- I (differs)
	 *        |  |  |  +- J              |  |  |  +- J (differs)
	 *        |  |  |  |  +- K           |  |  |  |  +- K (differs)
	 *        |  |  |  |  |  +- L        |  |  |  |  |  +- L (differs)
	 *        |  |  |  |  |  |  +- M     |  |  |  |  |  |  +- M (differs)
	 *        |  |  |  |  |  |  |  `- 1  |  |  |  |  |  |  |  `- 1 (differs)
	 *        |  +- D                    |  +- D (shared)
	 *        |     `- 2                 |     `- 2 (shared)
	 *        +- B                       +- B (differs)
	 *           +- E                    |  +- E (shared)
	 *           |  `- 3                 |     `- 3 (shared)
	 *           +- F                    |  +- F (shared)
	 *           |  `- 4                 |     `- 4 (shared)
	 *           +- G                    |  +- G (shared)
	 *           |  `- 5                 |     `- 5 (shared)
	 *           +- H                    |  +- H (differs)
	 *              `- 6                 |     `- 6 (differs)
	 * }</pre>
	 */
	@Test
	public void testDeepTreePerformance() throws Exception {
		// Create base with deep and wide structure
		TestRepository.CommitBuilder cb = tr.commit();
		cb.add("A/C/I/J/K/L/M/1", tr.blob("content1"));
		cb.add("A/D/2", tr.blob("content2"));
		cb.add("B/E/3", tr.blob("content3"));
		cb.add("B/F/4", tr.blob("content4"));
		cb.add("B/G/5", tr.blob("content5"));
		cb.add("B/H/6", tr.blob("content6"));

		RevCommit base = cb.create();
		haves.add(base.getId());
		RevCommit currentHaves = base;
		for (int i = 0; i < 250; i++) {
			currentHaves = tr.commit().parent(currentHaves).create();
			haves.add(currentHaves.getId());
		}

		// Create new commit modifying file at end of deep path
		RevBlob newBlob = tr.blob("new content");
		RevCommit newCommit = tr.commit().parent(base)
				.add("A/C/I/J/K/L/M/1", newBlob).add("B/H/6", newBlob).create();

		info.setCommands(Collections.singletonList(new ReceiveCommand(
				base.getId(), newCommit.getId(), "refs/heads/new_change")));

		ObjectIdSubclassMap<ObjectId> newObjectIds = new ObjectIdSubclassMap<>();
		newObjectIds.add(newCommit);
		newObjectIds.add(newCommit.getTree());
		newObjectIds.add(newBlob);
		try (TreeWalk tw = new TreeWalk(tr.getRepository())) {
			tw.setRecursive(false);
			tw.addTree(newCommit.getTree());
			String[] pathSegments = { "A", "C", "I", "J", "K", "L", "M" };
			for (String segment : pathSegments) {
				while (tw.next()) {
					if (tw.getNameString().equals(segment)) {
						newObjectIds.add(tw.getObjectId(0));
						tw.enterSubtree();
						break;
					}
				}
			}
		}
		try (TreeWalk tw = new TreeWalk(tr.getRepository())) {
			tw.setRecursive(false);
			tw.addTree(newCommit.getTree());
			while (tw.next()) {
				if (tw.getNameString().equals("B")) {
					newObjectIds.add(tw.getObjectId(0));
					tw.enterSubtree();
					while (tw.next()) {
						if (tw.getNameString().equals("H")) {
							newObjectIds.add(tw.getObjectId(0));
							break;
						}
					}
					break;
				}
			}
		}
		when(parser.getNewObjectIds()).thenReturn(newObjectIds);

		TreeWalkConnectivityChecker scChecker = new TreeWalkConnectivityChecker();

		long scJfrCount = 0;
		try (Recording r = new Recording()) {
			r.enable("jdk.ObjectAllocationInNewTLAB");
			r.enable("jdk.ObjectAllocationOutsideTLAB");
			r.start();

			for (int i = 0; i < 1000; i++) {
				try (RevWalk rw = new RevWalk(info.getRepository())) {
					info.setWalk(rw);
					pm.reset();
					scChecker.checkConnectivity(info, haves, pm);
				}
			}

			r.stop();
			Path p = Files.createTempFile("sc_jfr", ".jfr");
			r.dump(p);
			try (RecordingFile file = new RecordingFile(p)) {
				while (file.hasMoreEvents()) {
					file.readEvent();
					scJfrCount++;
				}
			}
			Files.delete(p);
		}
		long scCount = pm.getObjectsCheckedCount();

		// Run IterativeConnectivityChecker
		IterativeConnectivityChecker icChecker = new IterativeConnectivityChecker(
				new FullConnectivityChecker());

		long icJfrCount = 0;
		try (jdk.jfr.Recording r = new jdk.jfr.Recording()) {
			r.enable("jdk.ObjectAllocationInNewTLAB");
			r.enable("jdk.ObjectAllocationOutsideTLAB");
			r.start();

			for (int i = 0; i < 1000; i++) {
				try (RevWalk rw = new RevWalk(info.getRepository())) {
					info.setWalk(rw);
					pm.reset();
					icChecker.checkConnectivity(info, haves, pm);
				}
			}

			r.stop();
			Path p = Files.createTempFile("ic_jfr_deep", ".jfr");
			r.dump(p);
			try (jdk.jfr.consumer.RecordingFile file = new jdk.jfr.consumer.RecordingFile(
					p)) {
				while (file.hasMoreEvents()) {
					jdk.jfr.consumer.RecordedEvent event = file.readEvent();
					if (event.getEventType().getName()
							.startsWith("jdk.ObjectAllocation")) {
						icJfrCount++;
					}
				}
			}
			Files.delete(p);
		}
		long icCount = pm.getObjectsCheckedCount();

		// Run FullConnectivityChecker
		FullConnectivityChecker fcChecker = new FullConnectivityChecker();

		long fcJfrCount = 0;
		try (Recording r = new Recording()) {
			r.enable("jdk.ObjectAllocationInNewTLAB");
			r.enable("jdk.ObjectAllocationOutsideTLAB");
			r.start();

			for (int i = 0; i < 1000; i++) {
				try (RevWalk rw = new RevWalk(info.getRepository())) {
					info.setWalk(rw);
					pm.reset();
					fcChecker.checkConnectivity(info, haves, pm);
				}
			}

			r.stop();
			Path p = Files.createTempFile("fc_jfr", ".jfr");
			r.dump(p);
			try (RecordingFile file = new RecordingFile(p)) {
				while (file.hasMoreEvents()) {
					file.readEvent();
					fcJfrCount++;
				}
			}
			Files.delete(p);
		}
		long fcCount = pm.getObjectsCheckedCount();

		System.out.println("TreeWalk Checked Objects: " + scCount);
		System.out.println("TreeWalk JFR allocations: " + scJfrCount);
		System.out.println("Iterative Checked Objects: " + icCount);
		System.out.println("Iterative JFR allocations: " + icJfrCount);
		System.out.println("Full Checked Objects: " + fcCount);
		System.out.println("Full JFR allocations: " + fcJfrCount);

		assertEquals(22, scCount);
		assertEquals(14, icCount);
		assertEquals(264, fcCount);

		assertTrue(
				"TreeWalkConnectivityChecker should produce fewer JFR allocation events. Expected "
						+ scJfrCount + " < " + fcJfrCount,
				scJfrCount < fcJfrCount);
	}

	private void setupSingleReceiveCommand(ObjectId oldId, ObjectId newId) {
		info.setCommands(Collections.singletonList(
				new ReceiveCommand(oldId, newId, "refs/heads/master")));
	}

	private void mockNewPackObjects(ObjectId... ids) {
		mockNewPackObjects(Arrays.asList(ids));
	}

	private void mockNewPackObjects(Collection<? extends ObjectId> ids) {
		ObjectIdSubclassMap<ObjectId> map = new ObjectIdSubclassMap<>();
		for (ObjectId id : ids) {
			map.add(id);
		}
		when(parser.getNewObjectIds()).thenReturn(map);
	}

	private void runCheckAndAssertCount(int expectedCount) throws IOException {
		checker.checkConnectivity(info, haves, pm);
		assertEquals(expectedCount, pm.getObjectsCheckedCount());
	}

	private void runCheckAndExpectMissingObject(ObjectId expectedMissingId,
			int expectedCount) throws IOException {
		try {
			checker.checkConnectivity(info, haves, pm);
			fail("Expected MissingObjectException");
		} catch (MissingObjectException e) {
			assertEquals(expectedMissingId, e.getObjectId());
			assertEquals(expectedCount, pm.getObjectsCheckedCount());
		}
	}

	private RevCommit createCommitWithParent(ObjectId parentId)
			throws Exception {
		CommitBuilder cb = new CommitBuilder();
		cb.setTreeId(tr.tree().getId());
		cb.setParentId(parentId);
		cb.setAuthor(new PersonIdent("Author", "author@example.com",
				tr.getInstant(), tr.getTimeZoneId()));
		cb.setCommitter(new PersonIdent("Committer", "committer@example.com",
				tr.getInstant(), tr.getTimeZoneId()));
		cb.setMessage("Commit with specific parent");

		try (ObjectInserter ins = tr.getRepository().newObjectInserter()) {
			ObjectId id = ins.insert(cb);
			ins.flush();
			return tr.getRevWalk().parseCommit(id);
		}
	}
}
