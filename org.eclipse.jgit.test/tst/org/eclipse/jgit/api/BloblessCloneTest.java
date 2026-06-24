/*
 * Copyright (C) 2026, Stuart Lang <stuart.b.lang@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

/**
 * End-to-end tests for blobless (partial) clone support:
 * {@code git clone --filter=blob:none}.
 */
public class BloblessCloneTest extends RepositoryTestCase {

	private static final String FILE = "Test.txt";

	private static final String CONTENT = "Hello blobless world";

	private Git git;

	private ObjectId contentBlobId;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		writeTrashFile(FILE, CONTENT);
		git.add().addFilepattern(FILE).call();
		git.commit().setMessage("Initial commit").call();
		contentBlobId = blobId(db, FILE);

		// Allow the served repository to honor object filters and explicit
		// want lines by SHA-1, both required for lazy fetching.
		StoredConfig cfg = db.getConfig();
		cfg.setBoolean("uploadpack", null, "allowfilter", true);
		cfg.setBoolean("uploadpack", null, "allowanysha1inwant", true);
		cfg.save();
	}

	@Test
	public void testPartialCloneConfigPersisted() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			StoredConfig cfg = cloned.getRepository().getConfig();
			assertTrue(cfg.getBoolean(
					ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
					ConfigConstants.CONFIG_KEY_PROMISOR, false));
			assertEquals("blob:none", cfg.getString(
					ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
					ConfigConstants.CONFIG_KEY_PARTIAL_CLONE_FILTER));
			assertEquals("origin", cfg.getString(
					ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_PARTIAL_CLONE));
			assertTrue(cfg.getInt(ConfigConstants.CONFIG_CORE_SECTION,
					ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0) >= 1);
		}
	}

	@Test
	public void testPromisorMarkerWritten() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			FileRepository repo = (FileRepository) cloned.getRepository();
			File packDir = new File(repo.getObjectsDirectory(), "pack");
			FilenameFilter promisor = (d, n) -> n.endsWith(".promisor");
			File[] markers = packDir.listFiles(promisor);
			assertNotNull(markers);
			assertTrue("expected at least one .promisor marker",
					markers.length >= 1);

			// The marker is recognized when the pack is loaded.
			boolean anyPromisor = false;
			for (Pack p : repo.getObjectDatabase().getPacks()) {
				anyPromisor |= p.isPromisor();
			}
			assertTrue("a loaded pack should report isPromisor()",
					anyPromisor);
		}
	}

	@Test
	public void testBlobMissingAfterBloblessClone() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			RevCommit head;
			try (RevWalk rw = new RevWalk(repo)) {
				head = rw.parseCommit(repo.resolve(Constants.HEAD));
			}
			// The commit and its tree are present, the blob is not.
			assertTrue("commit should be present",
					repo.getObjectDatabase().has(head));
			assertTrue("tree should be present",
					repo.getObjectDatabase().has(head.getTree()));
			assertFalse("blob should be absent in a blobless clone",
					repo.getObjectDatabase().has(contentBlobId));
		}
	}

	@Test
	public void testHasDoesNotTriggerFetch() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			FileRepository repo = (FileRepository) cloned.getRepository();
			File packDir = new File(repo.getObjectsDirectory(), "pack");
			FilenameFilter packs = (d, n) -> n.endsWith(".pack");
			int before = packDir.listFiles(packs).length;

			// has() must use QUICK semantics: it reports absence without
			// downloading the object.
			assertFalse(repo.getObjectDatabase().has(contentBlobId));

			assertFalse("has() must not fetch the object",
					repo.getObjectDatabase().has(contentBlobId));
			assertEquals("has() must not create a new pack", before,
					packDir.listFiles(packs).length);
		}
	}

	@Test
	public void testGetObjectSizeFetchesMissingBlob() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			assertFalse(repo.getObjectDatabase().has(contentBlobId));

			// getObjectSize is a distinct read path from open() and must also
			// trigger a lazy fetch.
			try (ObjectReader reader = repo.newObjectReader()) {
				assertEquals(CONTENT.length(),
						reader.getObjectSize(contentBlobId, Constants.OBJ_BLOB));
			}
			assertTrue(repo.getObjectDatabase().has(contentBlobId));
		}
	}

	@Test
	public void testReopenedPartialCloneLazilyFetches() throws Exception {
		File dir = createTempDirectory("blobless");
		try (Git cloned = blobless(dir, true)) {
			// just create the partial clone, then close it
		}
		// Reopen from disk (not via CloneCommand) to prove lazy fetching is a
		// property of the on-disk repository configuration.
		try (Git reopened = Git.open(dir)) {
			Repository repo = reopened.getRepository();
			assertFalse(repo.getObjectDatabase().has(contentBlobId));
			assertEquals(CONTENT, new String(
					repo.open(contentBlobId, Constants.OBJ_BLOB).getBytes(),
					StandardCharsets.UTF_8));
		}
	}

	@Test
	public void testGenuinelyMissingObjectTerminates() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			ObjectId bogus = ObjectId
					.fromString("0123456789012345678901234567890123456789");
			// An object that exists nowhere must terminate with an exception
			// (not loop forever retrying the lazy fetch).
			assertThrows(IOException.class,
					() -> repo.open(bogus, Constants.OBJ_BLOB));
		}
	}

	@Test
	public void testLazyFetchFailureSurfacesError() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			// Point the promisor remote at a non-existent location.
			StoredConfig cfg = repo.getConfig();
			cfg.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
					ConfigConstants.CONFIG_KEY_URL,
					"file:///does/not/exist.git");
			cfg.save();

			assertThrows(IOException.class,
					() -> repo.open(contentBlobId, Constants.OBJ_BLOB));
			// The repository itself is not corrupted by the failed fetch.
			assertNotNull(repo.resolve(Constants.HEAD));
		}
	}

	@Test
	public void testGcDoesNotDefeatPartialClone() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			assertFalse(repo.getObjectDatabase().has(contentBlobId));

			// GC must not silently download the omitted blobs (which a naive
			// reachability walk would do via the lazy-fetch hook).
			cloned.gc().call();

			assertFalse("gc must not fetch omitted blobs",
					repo.getObjectDatabase().has(contentBlobId));
			// The repository is still usable: the blob can still be fetched
			// on demand after gc.
			assertEquals(CONTENT, new String(
					repo.open(contentBlobId, Constants.OBJ_BLOB).getBytes(),
					StandardCharsets.UTF_8));
		}
	}

	@Test
	public void testMissingBlobIsFetchedOnDemand() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();
			assertFalse(repo.getObjectDatabase().has(contentBlobId));

			// Reading the blob must trigger an on-demand fetch from the
			// promisor remote and return the correct content.
			ObjectLoader loader = repo.open(contentBlobId, Constants.OBJ_BLOB);
			assertEquals(CONTENT,
					new String(loader.getBytes(), StandardCharsets.UTF_8));

			// After the lazy fetch the object is present locally.
			assertTrue(repo.getObjectDatabase().has(contentBlobId));
		}
	}

	@Test
	public void testCheckoutLazilyFetchesBlobs() throws Exception {
		// A normal (checking-out) blobless clone must materialize the working
		// tree by lazily fetching the needed blobs.
		File dir = createTempDirectory("blobless");
		try (Git cloned = blobless(dir, false)) {
			File checkedOut = new File(
					cloned.getRepository().getWorkTree(), FILE);
			assertTrue(checkedOut.isFile());
			assertEquals(CONTENT, read(checkedOut));
		}
	}

	@Test
	public void testCheckoutBatchesMissingBlobs() throws Exception {
		// Add several more files so the checkout must fetch multiple blobs.
		for (int i = 0; i < 5; i++) {
			writeTrashFile("file" + i + ".txt", "content " + i);
			git.add().addFilepattern("file" + i + ".txt").call();
		}
		git.commit().setMessage("More files").call();

		File dir = createTempDirectory("blobless");
		try (Git cloned = blobless(dir, false)) {
			Repository repo = cloned.getRepository();
			for (int i = 0; i < 5; i++) {
				assertEquals("content " + i, read(new File(repo.getWorkTree(),
						"file" + i + ".txt")));
			}

			// All the missing blobs were fetched in a single batch: at most
			// one promisor pack for the clone metadata, and one for the blobs.
			// A per-object implementation would produce one pack per blob.
			File packDir = new File(
					((FileRepository) repo).getObjectsDirectory(), "pack");
			File[] markers = packDir
					.listFiles((d, n) -> n.endsWith(".promisor"));
			assertNotNull(markers);
			assertTrue(
					"expected blobs fetched in one batch, found "
							+ markers.length + " promisor packs",
					markers.length <= 2);
		}
	}

	@Test
	public void testSecondFetchStaysPartial() throws Exception {
		try (Git cloned = blobless(createTempDirectory("blobless"), true)) {
			Repository repo = cloned.getRepository();

			// Add a new commit with a new blob on the source.
			writeTrashFile("second.txt", "second content");
			git.add().addFilepattern("second.txt").call();
			git.commit().setMessage("Second commit").call();
			ObjectId secondCommit = db.resolve(Constants.HEAD);
			ObjectId secondBlob = blobId(db, "second.txt");

			// A subsequent fetch must keep the configured filter.
			cloned.fetch().setRemote("origin").call();

			assertTrue("new commit should be fetched",
					repo.getObjectDatabase().has(secondCommit));
			assertFalse("subsequent fetch must stay partial",
					repo.getObjectDatabase().has(secondBlob));
		}
	}

	@Test
	public void testBlobLimitFilter() throws Exception {
		writeTrashFile("small.txt", "hi");
		writeTrashFile("large.txt", "a blob well over the size limit");
		git.add().addFilepattern("small.txt").addFilepattern("large.txt").call();
		git.commit().setMessage("Mixed sizes").call();
		ObjectId smallBlob = blobId(db, "small.txt");
		ObjectId largeBlob = blobId(db, "large.txt");

		File dir = createTempDirectory("bloblimit");
		try (Git cloned = Git.cloneRepository().setDirectory(dir)
				.setURI(fileUri())
				.setFilterSpec(FilterSpec.fromFilterLine("blob:limit=5"))
				.setNoCheckout(true).call()) {
			Repository repo = cloned.getRepository();
			assertEquals("blob:limit=5", repo.getConfig().getString(
					ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
					ConfigConstants.CONFIG_KEY_PARTIAL_CLONE_FILTER));
			assertTrue("blob under the limit should be present",
					repo.getObjectDatabase().has(smallBlob));
			assertFalse("blob over the limit should be absent",
					repo.getObjectDatabase().has(largeBlob));

			// The large blob can still be fetched on demand.
			assertEquals("a blob well over the size limit",
					new String(repo.open(largeBlob, Constants.OBJ_BLOB)
							.getBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	public void testPromisorTransportConfigHookIsApplied() throws Exception {
		File dir = createTempDirectory("blobless");
		try (Git cloned = blobless(dir, true)) {
			// just create the partial clone, then close it
		}
		AtomicBoolean invoked = new AtomicBoolean();
		// Reopen from disk so the hook is the only source of transport
		// configuration for the lazy fetch.
		try (Git reopened = Git.open(dir)) {
			FileRepository repo = (FileRepository) reopened.getRepository();
			repo.setPromisorTransportConfig(
					transport -> invoked.set(true));

			assertFalse(repo.getObjectDatabase().has(contentBlobId));
			assertEquals(CONTENT, new String(
					repo.open(contentBlobId, Constants.OBJ_BLOB).getBytes(),
					StandardCharsets.UTF_8));
			assertTrue("promisor transport config hook should be invoked on a "
					+ "lazy fetch", invoked.get());
		}
	}

	@Test
	public void testCloneForwardsTransportConfigToLazyFetch() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		File dir = createTempDirectory("blobless");
		try (Git cloned = Git.cloneRepository().setDirectory(dir)
				.setURI(fileUri())
				.setFilterSpec(FilterSpec.fromFilterLine("blob:none"))
				.setNoCheckout(true)
				.setTransportConfigCallback(
						transport -> calls.incrementAndGet())
				.call()) {
			Repository repo = cloned.getRepository();
			// The clone's own fetch already invoked the callback; record that
			// baseline so we can detect a further invocation by the lazy fetch.
			int afterClone = calls.get();
			assertTrue("clone fetch should invoke the transport config callback",
					afterClone >= 1);

			assertFalse(repo.getObjectDatabase().has(contentBlobId));
			// CloneCommand must have registered a promisor transport config that
			// re-applies the same callback to on-demand fetches.
			repo.open(contentBlobId, Constants.OBJ_BLOB);
			assertTrue(
					"clone should forward transportConfigCallback to lazy fetch",
					calls.get() > afterClone);
		}
	}

	private Git blobless(File directory, boolean noCheckout) throws Exception {
		// The returned Git owns the cloned repository and closes it when the
		// caller's try-with-resources block exits.
		return Git.cloneRepository().setDirectory(directory).setURI(fileUri())
				.setFilterSpec(FilterSpec.fromFilterLine("blob:none"))
				.setNoCheckout(noCheckout).call();
	}

	private static ObjectId blobId(Repository repo, String path)
			throws Exception {
		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit head = rw.parseCommit(repo.resolve(Constants.HEAD));
			try (TreeWalk tw = TreeWalk.forPath(repo, path, head.getTree())) {
				assertNotNull(tw);
				return tw.getObjectId(0);
			}
		}
	}

	private String fileUri() {
		return "file://"
				+ git.getRepository().getWorkTree().getAbsolutePath();
	}
}
