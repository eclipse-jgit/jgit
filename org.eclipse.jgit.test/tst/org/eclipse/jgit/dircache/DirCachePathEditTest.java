/*
 * Copyright (C) 2011, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.errors.DirCacheNameConflictException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class DirCachePathEditTest {

	static final class AddEdit extends PathEdit {

		public AddEdit(String entryPath) {
			super(entryPath);
		}

		@Override
		public void apply(DirCacheEntry ent) {
			ent.setFileMode(FileMode.REGULAR_FILE);
			ent.setLength(1);
			ent.setObjectId(ObjectId.zeroId());
		}

	}

	private static final class RecordingEdit extends PathEdit {
		final List<DirCacheEntry> entries = new ArrayList<>();

		public RecordingEdit(String entryPath) {
			super(entryPath);
		}

		@Override
		public void apply(DirCacheEntry ent) {
			entries.add(ent);
		}
	}

	@Test
	public void testAddDeletePathAndTreeNormalNames() {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a"));
		editor.add(new AddEdit("b/c"));
		editor.add(new AddEdit("c/d"));
		editor.finish();
		assertEquals(3, dc.getEntryCount());
		assertEquals("a", dc.getEntry(0).getPathString());
		assertEquals("b/c", dc.getEntry(1).getPathString());
		assertEquals("c/d", dc.getEntry(2).getPathString());

		editor = dc.editor();
		editor.add(new DirCacheEditor.DeletePath("b/c"));
		editor.finish();
		assertEquals(2, dc.getEntryCount());
		assertEquals("a", dc.getEntry(0).getPathString());
		assertEquals("c/d", dc.getEntry(1).getPathString());

		editor = dc.editor();
		editor.add(new DirCacheEditor.DeleteTree(""));
		editor.finish();
		assertEquals(0, dc.getEntryCount());
	}

	@Test
	public void testAddDeleteTrickyNames() {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a/b"));
		editor.add(new AddEdit("a-"));
		editor.add(new AddEdit("ab"));
		editor.finish();
		assertEquals(3, dc.getEntryCount());

		// Validate sort order
		assertEquals("a-", dc.getEntry(0).getPathString());
		assertEquals("a/b", dc.getEntry(1).getPathString());
		assertEquals("ab", dc.getEntry(2).getPathString());

		editor = dc.editor();

		// Sort order should not confuse DeleteTree
		editor.add(new DirCacheEditor.DeleteTree("a"));
		editor.finish();
		assertEquals(2, dc.getEntryCount());
		assertEquals("a-", dc.getEntry(0).getPathString());
		assertEquals("ab", dc.getEntry(1).getPathString());
	}

	@Test
	public void testPathEditShouldBeCalledForEachStage() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder builder = new DirCacheBuilder(dc, 3);
		builder.add(createEntry("a", DirCacheEntry.STAGE_1));
		builder.add(createEntry("a", DirCacheEntry.STAGE_2));
		builder.add(createEntry("a", DirCacheEntry.STAGE_3));
		builder.finish();

		DirCacheEditor editor = dc.editor();
		RecordingEdit recorder = new RecordingEdit("a");
		editor.add(recorder);
		editor.finish();

		List<DirCacheEntry> entries = recorder.entries;
		assertEquals(3, entries.size());
		assertEquals(DirCacheEntry.STAGE_1, entries.get(0).getStage());
		assertEquals(DirCacheEntry.STAGE_2, entries.get(1).getStage());
		assertEquals(DirCacheEntry.STAGE_3, entries.get(2).getStage());
	}

	@Test
	public void testFileReplacesTree() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a"));
		editor.add(new AddEdit("b/c"));
		editor.add(new AddEdit("b/d"));
		editor.add(new AddEdit("e"));
		editor.finish();

		editor = dc.editor();
		editor.add(new AddEdit("b"));
		editor.finish();

		assertEquals(3, dc.getEntryCount());
		assertEquals("a", dc.getEntry(0).getPathString());
		assertEquals("b", dc.getEntry(1).getPathString());
		assertEquals("e", dc.getEntry(2).getPathString());

		dc.clear();
		editor = dc.editor();
		editor.add(new AddEdit("A.c"));
		editor.add(new AddEdit("A/c"));
		editor.add(new AddEdit("A0c"));
		editor.finish();

		editor = dc.editor();
		editor.add(new AddEdit("A"));
		editor.finish();
		assertEquals(3, dc.getEntryCount());
		assertEquals("A", dc.getEntry(0).getPathString());
		assertEquals("A.c", dc.getEntry(1).getPathString());
		assertEquals("A0c", dc.getEntry(2).getPathString());
	}

	@Test
	public void testTreeReplacesFile() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a"));
		editor.add(new AddEdit("ab"));
		editor.add(new AddEdit("b"));
		editor.add(new AddEdit("e"));
		editor.finish();

		editor = dc.editor();
		editor.add(new AddEdit("b/c/d/f"));
		editor.add(new AddEdit("b/g/h/i"));
		editor.finish();

		assertEquals(5, dc.getEntryCount());
		assertEquals("a", dc.getEntry(0).getPathString());
		assertEquals("ab", dc.getEntry(1).getPathString());
		assertEquals("b/c/d/f", dc.getEntry(2).getPathString());
		assertEquals("b/g/h/i", dc.getEntry(3).getPathString());
		assertEquals("e", dc.getEntry(4).getPathString());
	}

	@Test
	public void testDuplicateFiles() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a"));
		editor.add(new AddEdit("a"));

		try {
			editor.finish();
			fail("Expected DirCacheNameConflictException to be thrown");
		} catch (DirCacheNameConflictException e) {
			assertEquals("a a", e.getMessage());
			assertEquals("a", e.getPath1());
			assertEquals("a", e.getPath2());
		}
	}

	@Test
	public void testFileOverlapsTree() throws Exception {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor editor = dc.editor();
		editor.add(new AddEdit("a"));
		editor.add(new AddEdit("a/b").setReplace(false));
		try {
			editor.finish();
			fail("Expected DirCacheNameConflictException to be thrown");
		} catch (DirCacheNameConflictException e) {
			assertEquals("a a/b", e.getMessage());
			assertEquals("a", e.getPath1());
			assertEquals("a/b", e.getPath2());
		}

		editor = dc.editor();
		editor.add(new AddEdit("A.c"));
		editor.add(new AddEdit("A/c").setReplace(false));
		editor.add(new AddEdit("A0c"));
		editor.add(new AddEdit("A"));
		try {
			editor.finish();
			fail("Expected DirCacheNameConflictException to be thrown");
		} catch (DirCacheNameConflictException e) {
			assertEquals("A A/c", e.getMessage());
			assertEquals("A", e.getPath1());
			assertEquals("A/c", e.getPath2());
		}

		editor = dc.editor();
		editor.add(new AddEdit("A.c"));
		editor.add(new AddEdit("A/b/c/d").setReplace(false));
		editor.add(new AddEdit("A/b/c"));
		editor.add(new AddEdit("A0c"));
		try {
			editor.finish();
			fail("Expected DirCacheNameConflictException to be thrown");
		} catch (DirCacheNameConflictException e) {
			assertEquals("A/b/c A/b/c/d", e.getMessage());
			assertEquals("A/b/c", e.getPath1());
			assertEquals("A/b/c/d", e.getPath2());
		}
	}

	private static DirCacheEntry createEntry(String path, int stage) {
		DirCacheEntry entry = new DirCacheEntry(path, stage);
		entry.setFileMode(FileMode.REGULAR_FILE);
		return entry;
	}
}
