/*
 * Copyright (c) 2020, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Ignore;
import org.junit.Test;

public class GitlinkMergeTest extends SampleDataRepositoryTestCase {
	private static final String LINK_ID1 = "DEADBEEFDEADBEEFBABEDEADBEEFDEADBEEFBABE";
	private static final String LINK_ID2 = "DEADDEADDEADDEADDEADDEADDEADDEADDEADDEAD";
	private static final String LINK_ID3 = "BEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEF";

	private static final String SUBMODULE_PATH = "submodule.link";

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_AddNew() throws Exception {
		assertGitLinkValue(
				testGitLink(null, null, LINK_ID3, newResolveMerger(), true),
				LINK_ID3);
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_Delete() throws Exception {
		assertGitLinkDoesntExist(testGitLink(LINK_ID1, LINK_ID1, null,
				newResolveMerger(), true));
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_UpdateDelete() throws Exception {
		testGitLink(LINK_ID1, LINK_ID2, null, newResolveMerger(), false);
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_DeleteUpdate() throws Exception {
		testGitLink(LINK_ID1, null, LINK_ID3, newResolveMerger(), false);
	}

	@Test
	public void testGitLinkMerging_UpdateUpdate() throws Exception {
		testGitLink(LINK_ID1, LINK_ID2, LINK_ID3, newResolveMerger(), false);
	}

	@Test
	public void testGitLinkMerging_bothAddedSameLink() throws Exception {
		assertGitLinkValue(
				testGitLink(null, LINK_ID2, LINK_ID2, newResolveMerger(), true),
				LINK_ID2);
	}

	@Test
	public void testGitLinkMerging_bothAddedDifferentLink() throws Exception {
		testGitLink(null, LINK_ID2, LINK_ID3, newResolveMerger(), false);
	}

	@Test
	public void testGitLinkMerging_AddNew_ignoreConflicts() throws Exception {
		assertGitLinkValue(
				testGitLink(null, null, LINK_ID3, newIgnoreConflictMerger(),
						true),
				LINK_ID3);
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_Delete_ignoreConflicts() throws Exception {
		assertGitLinkDoesntExist(testGitLink(LINK_ID1, LINK_ID1, null,
				newIgnoreConflictMerger(), true));
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_UpdateDelete_ignoreConflicts()
			throws Exception {
		assertGitLinkValue(testGitLink(LINK_ID1, LINK_ID2, null,
				newIgnoreConflictMerger(), true), LINK_ID2);
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_DeleteUpdate_ignoreConflicts()
			throws Exception {
		assertGitLinkDoesntExist(testGitLink(LINK_ID1, null, LINK_ID3,
				newIgnoreConflictMerger(), true));
	}

	@Test
	public void testGitLinkMerging_UpdateUpdate_ignoreConflicts()
			throws Exception {
		assertGitLinkValue(testGitLink(LINK_ID1, LINK_ID2, LINK_ID3,
				newIgnoreConflictMerger(), true), LINK_ID2);
	}

	@Test
	public void testGitLinkMerging_bothAddedSameLink_ignoreConflicts()
			throws Exception {
		assertGitLinkValue(testGitLink(null, LINK_ID2, LINK_ID2,
				newIgnoreConflictMerger(), true), LINK_ID2);
	}

	@Test
	public void testGitLinkMerging_bothAddedDifferentLink_ignoreConflicts()
			throws Exception {
		assertGitLinkValue(testGitLink(null, LINK_ID2, LINK_ID3,
				newIgnoreConflictMerger(), true), LINK_ID2);
	}

	protected Merger testGitLink(@Nullable String baseLink,
			@Nullable String oursLink, @Nullable String theirsLink,
			Merger merger, boolean shouldMerge)
			throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		maybeAddLink(bTreeBuilder, baseLink);
		maybeAddLink(oTreeBuilder, oursLink);
		maybeAddLink(tTreeBuilder, theirsLink);

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		boolean merge = merger.merge(new ObjectId[] { o, t });
		assertEquals(shouldMerge, merge);

		return merger;
	}

	private Merger newResolveMerger() {
		return MergeStrategy.RESOLVE.newMerger(db, true);
	}

	private Merger newIgnoreConflictMerger() {
		return new ResolveMerger(db, true) {
			@Override
			protected boolean mergeImpl() throws IOException {
				// emulate call with ignore conflicts.
				return mergeTrees(mergeBase(), sourceTrees[0], sourceTrees[1],
						true);
			}
		};
	}

	@Test
	public void testGitLinkMerging_blobWithLink() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob"));
		oTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob 2"));

		maybeAddLink(tTreeBuilder, LINK_ID3);

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger resolveMerger = MergeStrategy.RESOLVE.newMerger(db);
		boolean merge = resolveMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testGitLinkMerging_linkWithBlob() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		maybeAddLink(bTreeBuilder, LINK_ID1);
		maybeAddLink(oTreeBuilder, LINK_ID2);
		tTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob 3"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger resolveMerger = MergeStrategy.RESOLVE.newMerger(db);
		boolean merge = resolveMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testGitLinkMerging_linkWithLink() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob"));
		maybeAddLink(oTreeBuilder, LINK_ID2);
		maybeAddLink(tTreeBuilder, LINK_ID3);

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger resolveMerger = MergeStrategy.RESOLVE.newMerger(db);
		boolean merge = resolveMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testGitLinkMerging_blobWithBlobFromLink() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		maybeAddLink(bTreeBuilder, LINK_ID1);
		oTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob 2"));
		tTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob 3"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger resolveMerger = MergeStrategy.RESOLVE.newMerger(db);
		boolean merge = resolveMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	@Ignore("Broken")
	public void testGitLinkMerging_linkBlobDeleted() throws Exception {
		// We changed a link to a blob, others has deleted this link.
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		maybeAddLink(bTreeBuilder, LINK_ID1);
		oTreeBuilder.add(
				createEntry(SUBMODULE_PATH, FileMode.REGULAR_FILE, "blob 2"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger resolveMerger = MergeStrategy.RESOLVE.newMerger(db);
		boolean merge = resolveMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	private void maybeAddLink(DirCacheBuilder builder,
			@Nullable String linkId) {
		if (linkId == null) {
			return;
		}
		DirCacheEntry newLink = createGitLink(SUBMODULE_PATH,
				ObjectId.fromString(linkId));
		builder.add(newLink);
	}

	private void assertGitLinkValue(Merger resolveMerger, String expectedValue)
			throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setRecursive(true);
			tw.reset(resolveMerger.getResultTreeId());

			assertTrue(tw.next());
			assertEquals(SUBMODULE_PATH, tw.getPathString());
			assertEquals(ObjectId.fromString(expectedValue), tw.getObjectId(0));

			assertFalse(tw.next());
		}
	}

	private void assertGitLinkDoesntExist(Merger resolveMerger)
			throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setRecursive(true);
			tw.reset(resolveMerger.getResultTreeId());

			assertFalse(tw.next());
		}
	}

	private static ObjectId commit(ObjectInserter odi, DirCache treeB,
			ObjectId[] parentIds) throws Exception {
		CommitBuilder c = new CommitBuilder();
		c.setTreeId(treeB.writeTree(odi));
		c.setAuthor(new PersonIdent("A U Thor", "a.u.thor", 1L, 0));
		c.setCommitter(c.getAuthor());
		c.setParentIds(parentIds);
		c.setMessage("Tree " + c.getTreeId().name());
		ObjectId id = odi.insert(c);
		odi.flush();
		return id;
	}
}
