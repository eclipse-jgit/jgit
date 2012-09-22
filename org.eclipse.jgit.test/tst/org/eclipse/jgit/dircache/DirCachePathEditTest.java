/*
 * Copyright (C) 2011, Robin Rosenberg
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
package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
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
		final List<DirCacheEntry> entries = new ArrayList<DirCacheEntry>();

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
		editor.add(new AddEdit("a."));
		editor.add(new AddEdit("ab"));
		editor.finish();
		assertEquals(3, dc.getEntryCount());
		// Validate sort order
		assertEquals("a.", dc.getEntry(0).getPathString());
		assertEquals("a/b", dc.getEntry(1).getPathString());
		assertEquals("ab", dc.getEntry(2).getPathString());

		editor = dc.editor();
		// Sort order should not confuse DeleteTree
		editor.add(new DirCacheEditor.DeleteTree("a"));
		editor.finish();
		assertEquals(2, dc.getEntryCount());
		assertEquals("a.", dc.getEntry(0).getPathString());
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

	private static DirCacheEntry createEntry(String path, int stage) {
		DirCacheEntry entry = new DirCacheEntry(path, stage);
		entry.setFileMode(FileMode.REGULAR_FILE);
		return entry;
	}
}
