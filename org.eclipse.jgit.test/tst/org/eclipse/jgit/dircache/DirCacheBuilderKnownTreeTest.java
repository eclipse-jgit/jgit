/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that trees added with {@link DirCacheBuilder#addTree} are reused by
 * {@link DirCache#writeTree} and that the result is always the tree a
 * per-entry build would write.
 */
public class DirCacheBuilderKnownTreeTest {
	private InMemoryRepository repo;

	private ObjectInserter ins;

	private ObjectReader reader;

	private CountingInserter counting;

	@Before
	public void setUp() {
		repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
		ins = repo.newObjectInserter();
		reader = ins.newReader();
		counting = new CountingInserter(ins);
	}

	@After
	public void tearDown() {
		reader.close();
		ins.close();
		repo.close();
	}

	@Test
	public void readTreeWritesNoTreeObjects() throws Exception {
		ObjectId root = sampleTree();
		DirCache dc = DirCache.read(reader, root);
		assertEquals(root, dc.writeTree(counting));
		assertEquals(0, counting.trees);
	}

	@Test
	public void addedSubtreeIsReused() throws Exception {
		ObjectId sub = sampleTree();
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		b.add(file("0"));
		b.addTree("a".getBytes(UTF_8), DirCacheEntry.STAGE_0, reader, sub);
		b.add(file("b"));
		b.finish();
		ObjectId root = dc.writeTree(counting);
		assertEquals(reference(dc), root);
		assertEquals(1, counting.trees);
		assertEquals(sub, dc.getCacheTree(false).getChild(0).getObjectId());
	}

	@Test
	public void subtreeWithMoreEntriesIsRewritten() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		b.addTree("a/".getBytes(UTF_8), DirCacheEntry.STAGE_0, reader,
				sampleTree());
		b.add(file("a/z"));
		b.finish();
		assertEquals(reference(dc), dc.writeTree(counting));
		// "a" and the root; "a/d" is still reused.
		assertEquals(2, counting.trees);
	}

	@Test
	public void resortedBuilderIsCorrect() throws Exception {
		ObjectId sub = sampleTree();
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		b.addTree("b".getBytes(UTF_8), DirCacheEntry.STAGE_0, reader, sub);
		b.add(file("a"));
		b.finish();
		assertEquals(reference(dc), dc.writeTree(counting));
		assertEquals(1, counting.trees);
	}

	@Test
	public void overlappingTreesAreRewritten() throws Exception {
		ObjectId sub = sampleTree();
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		b.addTree("a".getBytes(UTF_8), DirCacheEntry.STAGE_0, reader, sub);
		TreeFormatter more = new TreeFormatter();
		more.append("r", FileMode.REGULAR_FILE, blob("r"));
		b.addTree("a/d".getBytes(UTF_8), DirCacheEntry.STAGE_0, reader,
				ins.insert(more));
		b.finish();
		assertEquals(reference(dc), dc.writeTree(counting));
		assertEquals(3, counting.trees);
	}

	@Test
	public void editAfterReadIsWritten() throws Exception {
		DirCache dc = DirCache.read(reader, sampleTree());
		DirCacheEditor e = dc.editor();
		e.add(new DirCacheEditor.PathEdit("d/new") {
			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
				ent.setObjectId(blob("new"));
			}
		});
		e.finish();
		assertEquals(reference(dc), dc.writeTree(counting));
	}

	@Test
	public void nonCanonicalFileModeIsRewritten() throws Exception {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		rawEntry(raw, "100664", "f", blob("f"));
		ObjectId root = ins.insert(OBJ_TREE, raw.toByteArray());
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
	}

	@Test
	public void zeroPaddedTreeModeIsRewritten() throws Exception {
		ObjectId sub = sampleTree();
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		rawEntry(raw, "040000", "d", sub);
		ObjectId root = ins.insert(OBJ_TREE, raw.toByteArray());
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
		assertEquals(1, counting.trees);
	}

	@Test
	public void unsortedTreeIsRewritten() throws Exception {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		rawEntry(raw, "100644", "b", blob("b"));
		rawEntry(raw, "100644", "a", blob("a"));
		ObjectId root = ins.insert(OBJ_TREE, raw.toByteArray());
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
	}

	@Test
	public void emptySubtreeIsDropped() throws Exception {
		TreeFormatter fmt = new TreeFormatter();
		fmt.append("e", FileMode.TREE, ins.insert(new TreeFormatter()));
		fmt.append("f", FileMode.REGULAR_FILE, blob("f"));
		ObjectId root = ins.insert(fmt);
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
	}

	@Test
	public void unmergedStageIsNotReused() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		b.addTree(null, DirCacheEntry.STAGE_1, reader, sampleTree());
		b.finish();
		assertThrows(UnmergedPathException.class, () -> dc.writeTree(ins));
	}

	/** Tree with files x, y and subtree d/{p, q}. */
	@Test
	public void slashInNameIsRewritten() throws Exception {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		rawEntry(raw, "100644", "a/b", blob("b"));
		ObjectId root = ins.insert(OBJ_TREE, raw.toByteArray());
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
	}

	@Test
	public void duplicateSubtreeNameIsRewritten() throws Exception {
		TreeFormatter s1 = new TreeFormatter();
		s1.append("x", FileMode.REGULAR_FILE, blob("x"));
		TreeFormatter s2 = new TreeFormatter();
		s2.append("y", FileMode.REGULAR_FILE, blob("y"));
		TreeFormatter t = new TreeFormatter();
		t.append("a", FileMode.TREE, ins.insert(s1));
		t.append("a", FileMode.TREE, ins.insert(s2));
		ObjectId root = ins.insert(t);
		DirCache dc = DirCache.read(reader, root);
		ObjectId written = dc.writeTree(counting);
		assertNotEquals(root, written);
		assertEquals(reference(dc), written);
	}

	@Test
	public void failedAddTreeLeavesNoPartialRecords() throws Exception {
		ObjectId sub = sampleTree();
		TreeFormatter b1 = new TreeFormatter();
		b1.append("f", FileMode.REGULAR_FILE, blob("f"));
		// Records: root, a, a/d, b, e; looking up b probes e.
		TreeFormatter t = new TreeFormatter();
		t.append("a", FileMode.TREE, sub);
		t.append("b", FileMode.TREE, ins.insert(b1));
		t.append("e", FileMode.TREE, ObjectId
				.fromString("0123456789012345678901234567890123456789"));
		ObjectId root = ins.insert(t);
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		assertThrows(MissingObjectException.class,
				() -> b.addTree(null, DirCacheEntry.STAGE_0, reader, root));
		b.finish();
		assertEquals(reference(dc), dc.writeTree(counting));
	}

	private ObjectId sampleTree() throws IOException {
		TreeFormatter d = new TreeFormatter();
		d.append("p", FileMode.REGULAR_FILE, blob("p"));
		d.append("q", FileMode.EXECUTABLE_FILE, blob("q"));
		TreeFormatter t = new TreeFormatter();
		t.append("d", FileMode.TREE, ins.insert(d));
		t.append("x", FileMode.SYMLINK, blob("x"));
		t.append("y", FileMode.REGULAR_FILE, blob("y"));
		return ins.insert(t);
	}

	private ObjectId blob(String content) {
		try {
			return ins.insert(OBJ_BLOB, content.getBytes(UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private DirCacheEntry file(String path) {
		DirCacheEntry e = new DirCacheEntry(path);
		e.setFileMode(FileMode.REGULAR_FILE);
		e.setObjectId(blob(path));
		return e;
	}

	private static void rawEntry(ByteArrayOutputStream out, String mode,
			String name, ObjectId id) {
		out.writeBytes((mode + ' ' + name + '\0').getBytes(UTF_8));
		byte[] raw = new byte[20];
		id.copyRawTo(raw, 0);
		out.writeBytes(raw);
	}

	/** The tree a per-entry build of the same entries writes. */
	private ObjectId reference(DirCache dc) throws IOException {
		DirCache ref = DirCache.newInCore();
		DirCacheBuilder b = ref.builder();
		for (int i = 0; i < dc.getEntryCount(); i++) {
			DirCacheEntry src = dc.getEntry(i);
			DirCacheEntry e = new DirCacheEntry(src.getRawPath(),
					src.getStage());
			e.setFileMode(src.getRawMode());
			e.setObjectId(src.getObjectId());
			b.add(e);
		}
		b.finish();
		return ref.writeTree(ins);
	}

	private static class CountingInserter extends ObjectInserter.Filter {
		private final ObjectInserter delegate;

		int trees;

		CountingInserter(ObjectInserter delegate) {
			this.delegate = delegate;
		}

		@Override
		protected ObjectInserter delegate() {
			return delegate;
		}

		@Override
		public ObjectId insert(int type, byte[] data, int off, int len)
				throws IOException {
			if (type == OBJ_TREE) {
				trees++;
			}
			return delegate.insert(type, data, off, len);
		}

		@Override
		public ObjectId insert(int type, long len, InputStream in)
				throws IOException {
			if (type == OBJ_TREE) {
				trees++;
			}
			return delegate.insert(type, len, in);
		}
	}
}
