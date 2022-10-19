/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.transport.connectivity;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ConnectivityChecker;
import org.eclipse.jgit.transport.ConnectivityChecker.ConnectivityCheckInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IterativeConnectivityCheckerTest {

	private ObjectId branchHeadObjectId;

	private ObjectId openRewiewObjectId;

	private ObjectId newCommitObjectId;
	private ObjectId otherHaveObjectId = ObjectId
			.fromString("DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");

	private Set<ObjectId> advertisedHaves;

	@Mock
	private ConnectivityChecker connectivityCheckerDelegate;

	@Mock
	private ProgressMonitor pm;

	@Mock
	private PackParser parser;

	private RevCommit branchHeadCommitObject;
	private RevCommit openReviewCommitObject;
	private RevCommit newCommitObject;

	private ConnectivityCheckInfo connectivityCheckInfo;
	private IterativeConnectivityChecker connectivityChecker;

	private TestRepository tr;

	@BeforeEach
	public void setUp() throws Exception {
		tr = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("test")));
		connectivityChecker = new IterativeConnectivityChecker(
				connectivityCheckerDelegate);
		connectivityCheckInfo = new ConnectivityCheckInfo();
		connectivityCheckInfo.setParser(parser);
		connectivityCheckInfo.setRepository(tr.getRepository());
		connectivityCheckInfo.setWalk(tr.getRevWalk());

		branchHeadCommitObject = tr.commit().create();
		branchHeadObjectId = branchHeadCommitObject.getId();

		openReviewCommitObject = tr.commit().create();
		openRewiewObjectId = openReviewCommitObject.getId();

		advertisedHaves = wrap(branchHeadObjectId, openRewiewObjectId,
				otherHaveObjectId);
	}

	@Test
	void testSuccessfulNewBranchBasedOnOld() throws Exception {
		createNewCommit(branchHeadCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo,
				wrap(branchHeadObjectId /* as direct parent */),
				pm);
	}

	@Test
	void testSuccessfulNewBranchBasedOnOldWithTip() throws Exception {
		createNewCommit(branchHeadCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.setForcedHaves(wrap(openRewiewObjectId));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo,
				wrap(branchHeadObjectId /* as direct parent */,
						openRewiewObjectId),
				pm);
	}

	@Test
	void testSuccessfulNewBranchMerge() throws Exception {
		createNewCommit(branchHeadCommitObject, openReviewCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo,
				wrap(branchHeadObjectId /* as direct parent */,
						openRewiewObjectId),
				pm);
	}

	@Test
	void testSuccessfulNewBranchBasedOnNewWithTip() throws Exception {
		createNewCommit();
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.setForcedHaves(wrap(openRewiewObjectId));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo, wrap(openRewiewObjectId), pm);
	}

	@Test
	void testSuccessfulPushOldBranch() throws Exception {
		createNewCommit(branchHeadCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(pushOldBranchCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo, wrap(branchHeadObjectId /* as direct parent */),
				pm);
	}

	@Test
	void testSuccessfulPushOldBranchMergeCommit() throws Exception {
		createNewCommit(branchHeadCommitObject, openReviewCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(pushOldBranchCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate).checkConnectivity(
				connectivityCheckInfo,
				wrap(branchHeadObjectId /* as direct parent */,
						openRewiewObjectId),
				pm);
	}


	@Test
	void testNoChecksIfCantFindSubset() throws Exception {
		createNewCommit();
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate)
				.checkConnectivity(connectivityCheckInfo, advertisedHaves, pm);
	}

	@Test
	void testReiterateInCaseNotSuccessful() throws Exception {
		createNewCommit(branchHeadCommitObject);
		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		doThrow(new MissingObjectException(branchHeadCommitObject,
				Constants.OBJ_COMMIT)).when(connectivityCheckerDelegate)
				.checkConnectivity(connectivityCheckInfo,
						wrap(branchHeadObjectId /* as direct parent */), pm);

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate)
				.checkConnectivity(connectivityCheckInfo, advertisedHaves, pm);
	}

	@Test
	void testDependOnGrandparent() throws Exception {
		RevCommit grandparent = tr.commit(new RevCommit[]{});
		RevCommit parent = tr.commit(grandparent);
		createNewCommit(parent);

		branchHeadCommitObject = tr.commit(grandparent);
		branchHeadObjectId = branchHeadCommitObject.getId();
		tr.getRevWalk().dispose();

		connectivityCheckInfo.setCommands(
				Collections.singletonList(createNewBrachCommand()));

		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);

		verify(connectivityCheckerDelegate)
				.checkConnectivity(connectivityCheckInfo, advertisedHaves, pm);
	}

	private static Set<ObjectId> wrap(ObjectId... objectIds) {
		return new HashSet<>(Arrays.asList(objectIds));
	}

	private ReceiveCommand createNewBrachCommand() {
		return new ReceiveCommand(ObjectId.zeroId(), newCommitObjectId,
				"totally/a/new/branch");
	}

	private ReceiveCommand pushOldBranchCommand() {
		return new ReceiveCommand(branchHeadObjectId, newCommitObjectId,
				"push/to/an/old/branch");
	}

	private void createNewCommit(RevCommit... parents) throws Exception {
		newCommitObject = tr.commit(parents);
		newCommitObjectId = newCommitObject.getId();
	}

}
