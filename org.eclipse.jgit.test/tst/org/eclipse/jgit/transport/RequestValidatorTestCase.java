/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UploadPack.RequestValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public abstract class RequestValidatorTestCase {

	private RevCommit reachableCommit;

	private RevCommit tipAdvertisedCommit;

	private RevCommit tipUnadvertisedCommit;

	private RevCommit unreachableCommit;

	private RevBlob reachableBlob;

	private RevBlob unreachableBlob;

	private InMemoryRepository repo;

	protected abstract RequestValidator createValidator();

	@Before
	public void setUp() throws Exception {
		repo = new InMemoryRepository(new DfsRepositoryDescription());
		try (TestRepository<InMemoryRepository> git = new TestRepository<>(
				repo)) {
			reachableBlob = git.blob("foo");
			reachableCommit = git
					.commit(git.tree(git.file("foo", reachableBlob)));
			tipAdvertisedCommit = git.commit(reachableCommit);
			git.update("advertised", tipAdvertisedCommit);

			tipUnadvertisedCommit = git.commit(reachableCommit);
			git.update("unadvertised", tipUnadvertisedCommit);

			unreachableBlob = git.blob("unreachableFoo");
			unreachableCommit = git
					.commit(git.tree(git.file("foo", unreachableBlob)));
		}
	}

	/**
	 * @return true if a commit reachable from a visible tip (but not directly
	 *         the tip) is valid
	 */
	protected abstract boolean isReachableCommitValid();

	/** @return true if a commit not reachable from any tip is valid */
	protected abstract boolean isUnreachableCommitValid();

	/**
	 * @return true if the commit directly pointed by an advertised ref is valid
	 */
	protected abstract boolean isAdvertisedTipValid();

	/**
	 * @return true if the object directly pointed by a non-advertised ref is
	 *         valid
	 */
	protected abstract boolean isUnadvertisedTipCommitValid();

	// UploadPack doesn't allow to ask for blobs when there is no
	// bitmap. Test both cases separately.
	/**
	 * @return true if a reachable blob is valid (and the repo has bitmaps)
	 */
	protected abstract boolean isReachableBlobValid_withBitmaps();

	/**
	 * @return true if a reachable blob is valid (and the repo does NOT have
	 *         bitmaps)
	 */
	protected abstract boolean isReachableBlobValid_withoutBitmaps();

	/**
	 * @return true if a blob unreachable from any tip is valid
	 */
	protected abstract boolean isUnreachableBlobValid();

	@Test
	public void validateReachableCommitWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(reachableCommit));
		if (!isReachableCommitValid()) {
			assertTransportException(r,
					"want " + reachableCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateReachableCommitWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(reachableCommit));
		if (!isReachableCommitValid()) {
			assertTransportException(r,
					"want " + reachableCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateAdvertisedTipWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(tipAdvertisedCommit));
		if (!isAdvertisedTipValid()) {
			assertTransportException(r,
					"want " + tipAdvertisedCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateAdvertisedTipWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(tipAdvertisedCommit));
		if (!isAdvertisedTipValid()) {
			assertTransportException(r,
					"want " + tipAdvertisedCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnadvertisedTipWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(tipUnadvertisedCommit));
		if (!isUnadvertisedTipCommitValid()) {
			assertTransportException(r,
					"want " + tipUnadvertisedCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnadvertisedTipWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(tipUnadvertisedCommit));
		if (!isUnadvertisedTipCommitValid()) {
			assertTransportException(r,
					"want " + tipUnadvertisedCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnreachableCommitWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(unreachableCommit));
		if (!isUnreachableCommitValid()) {
			assertTransportException(r,
					"want " + unreachableCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnreachableCommitWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(unreachableCommit));
		if (!isUnreachableCommitValid()) {
			assertTransportException(r,
					"want " + unreachableCommit.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateReachableBlobWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(reachableBlob));
		if (!isReachableBlobValid_withBitmaps()) {
			assertTransportException(r,
					"want " + reachableBlob.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateReachableBlobWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(reachableBlob));
		if (!isReachableBlobValid_withoutBitmaps()) {
			assertTransportException(r,
					"want " + reachableBlob.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnreachableBlobWithBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(unreachableBlob));
		if (!isUnreachableBlobValid()) {
			assertTransportException(r,
					"want " + unreachableBlob.name() + " not valid");
			return;
		}
		r.run();
	}

	@Test
	public void validateUnreachableBlobWithoutBitmaps() throws Throwable {
		ThrowingRunnable r = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(unreachableBlob));
		if (!isUnreachableBlobValid()) {
			assertTransportException(r,
					"want " + unreachableBlob.name() + " not valid");
			return;
		}
		r.run();
	}

	private void assertTransportException(ThrowingRunnable r,
			String messageContent) throws AssertionError {
		Exception e = assertThrows(TransportException.class, r);
		assertTrue(e.getMessage().contains(messageContent));
	}

	private UploadPack getUploadPack(Repository repository) throws IOException {
		UploadPack uploadPack = new UploadPack(repository);

		Ref advertisedRef = repo.getRefDatabase().findRef("advertised");
		Map<String, Ref> advertisedRefs = new HashMap<>();
		advertisedRefs.put(advertisedRef.getName(), advertisedRef);

		uploadPack.setAdvertisedRefs(advertisedRefs);
		return uploadPack;
	}

	private Repository getRepoWithBitmaps() throws IOException {
		new DfsGarbageCollector(repo).pack(null);
		repo.scanForRepoChanges();
		return repo;
	}

	private Repository getRepoWithoutBitmaps() {
		return repo;
	}
}
