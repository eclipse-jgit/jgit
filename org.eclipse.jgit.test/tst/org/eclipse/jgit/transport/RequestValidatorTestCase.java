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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(reachableCommit));
		if (!isReachableCommitValid()) {
			assertTransportException(c,
					"want " + reachableCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateReachableCommitWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(reachableCommit));
		if (!isReachableCommitValid()) {
			assertTransportException(c,
					"want " + reachableCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateAdvertisedTipWithBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(tipAdvertisedCommit));
		if (!isAdvertisedTipValid()) {
			assertTransportException(c,
					"want " + tipAdvertisedCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateAdvertisedTipWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(tipAdvertisedCommit));
		if (!isAdvertisedTipValid()) {
			assertTransportException(c,
					"want " + tipAdvertisedCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnadvertisedTipWithBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(tipUnadvertisedCommit));
		if (!isUnadvertisedTipCommitValid()) {
			assertTransportException(c,
					"want " + tipUnadvertisedCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnadvertisedTipWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(tipUnadvertisedCommit));
		if (!isUnadvertisedTipCommitValid()) {
			assertTransportException(c,
					"want " + tipUnadvertisedCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnreachableCommitWithBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(unreachableCommit));
		if (!isUnreachableCommitValid()) {
			assertTransportException(c,
					"want " + unreachableCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnreachableCommitWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(unreachableCommit));
		if (!isUnreachableCommitValid()) {
			assertTransportException(c,
					"want " + unreachableCommit.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateReachableBlobWithBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(reachableBlob));
		if (!isReachableBlobValid_withBitmaps()) {
			assertTransportException(c,
					"want " + reachableBlob.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateReachableBlobWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(reachableBlob));
		if (!isReachableBlobValid_withoutBitmaps()) {
			assertTransportException(c,
					"want " + reachableBlob.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnreachableBlobWithBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithBitmaps()),
				Arrays.asList(unreachableBlob));
		if (!isUnreachableBlobValid()) {
			assertTransportException(c,
					"want " + unreachableBlob.name() + " not valid");
			return;
		}
		c.call();
	}

	@Test
	public void validateUnreachableBlobWithoutBitmaps() throws Throwable {
		ThrowingCallable c = () -> createValidator().checkWants(
				getUploadPack(getRepoWithoutBitmaps()),
				Arrays.asList(unreachableBlob));
		if (!isUnreachableBlobValid()) {
			assertTransportException(c,
					"want " + unreachableBlob.name() + " not valid");
			return;
		}
		c.call();
	}

	private void assertTransportException(ThrowingCallable c,
			String messageContent) throws AssertionError {
		assertThat(catchThrowableOfType(c, TransportException.class))
				.hasMessageContaining(messageContent);
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
