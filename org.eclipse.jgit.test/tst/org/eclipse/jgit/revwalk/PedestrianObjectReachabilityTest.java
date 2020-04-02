/*
 * Copyright (C) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.junit.Before;
import org.junit.Test;

public class PedestrianObjectReachabilityTest
		extends LocalDiskRepositoryTestCase {

	PedestrianObjectReachabilityChecker getChecker(
			TestRepository<FileRepository> repository)
			throws Exception {
		return new PedestrianObjectReachabilityChecker(
				repository.getRevWalk().toObjectWalkWithSameObjects());
	}

	private TestRepository<FileRepository> repo;

	private AddressableRevCommit baseCommit;

	private AddressableRevCommit branchACommit;

	private AddressableRevCommit branchBCommit;

	private AddressableRevCommit mergeCommit;

	/** {@inheritDoc} */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository db = createWorkRepository();
		repo = new TestRepository<>(db);
		prepareRepo();
	}

	@Test
	public void blob_in_base_reachable_from_branches() throws Exception {
		PedestrianObjectReachabilityChecker checker = getChecker(repo);

		RevObject baseBlob = baseCommit.blob;
		assertReachable("reachable from one branch", checker.areAllReachable(
				Arrays.asList(baseBlob), Stream.of(branchACommit.commit)));
		assertReachable("reachable from another branch",
				checker.areAllReachable(
				Arrays.asList(baseBlob), Stream.of(branchBCommit.commit)));
	}

	@Test
	public void blob_reachable_from_owning_commit() throws Exception {
		PedestrianObjectReachabilityChecker checker = getChecker(repo);

		RevObject branchABlob = branchACommit.blob;
		assertReachable("reachable from itself",
				checker.areAllReachable(Arrays.asList(branchABlob),
						Stream.of(branchACommit.commit)));
	}

	@Test
	public void blob_in_branch_reachable_from_merge() throws Exception {
		PedestrianObjectReachabilityChecker checker = getChecker(repo);

		RevObject branchABlob = branchACommit.blob;
		assertReachable("reachable from merge", checker.areAllReachable(
				Arrays.asList(branchABlob), Stream.of(mergeCommit.commit)));
	}

	@Test
	public void blob_unreachable_from_earlier_commit() throws Exception {
		PedestrianObjectReachabilityChecker checker = getChecker(repo);

		RevObject branchABlob = branchACommit.blob;
		assertUnreachable("unreachable from earlier commit",
				checker.areAllReachable(Arrays.asList(branchABlob),
						Stream.of(baseCommit.commit)));
	}

	@Test
	public void blob_unreachable_from_parallel_branch() throws Exception {
		PedestrianObjectReachabilityChecker checker = getChecker(repo);

		RevObject branchABlob = branchACommit.blob;
		assertUnreachable("unreachable from another branch",
				checker.areAllReachable(Arrays.asList(branchABlob),
						Stream.of(branchBCommit.commit)));
	}

	private void prepareRepo()
			throws Exception {
		baseCommit = createCommit("base");
		branchACommit = createCommit("branchA", baseCommit);
		branchBCommit = createCommit("branchB", baseCommit);
		mergeCommit = createCommit("merge", branchACommit, branchBCommit);

		// Bitmaps are generated from references
		// TODO(ifrade): These are not needed now, but will be when we use these
		// tests with the bitmap implementation.
		repo.update("refs/heads/a", branchACommit.commit);
		repo.update("refs/heads/b", branchBCommit.commit);
		repo.update("refs/heads/merge", mergeCommit.commit);
	}

	private AddressableRevCommit createCommit(String blobPath,
			AddressableRevCommit... parents) throws Exception {
		RevBlob blob = repo.blob(blobPath + " content");
		CommitBuilder commitBuilder = repo.commit();
		for (int i = 0; i < parents.length; i++) {
			commitBuilder.parent(parents[i].commit);
		}
		commitBuilder.add(blobPath, blob);

		RevCommit commit = commitBuilder.create();
		return new AddressableRevCommit(commit, blob);
	}

	// Pair of commit and blob inside it
	static class AddressableRevCommit {
		RevCommit commit;

		RevBlob blob;

		AddressableRevCommit(RevCommit commit, RevBlob blob) {
			this.commit = commit;
			this.blob = blob;
		}
	}

	private static void assertReachable(String msg,
			Optional<RevObject> result) {
		assertFalse(msg, result.isPresent());
	}

	private static void assertUnreachable(String msg,
			Optional<RevObject> result) {
		assertTrue(msg, result.isPresent());
	}

}
