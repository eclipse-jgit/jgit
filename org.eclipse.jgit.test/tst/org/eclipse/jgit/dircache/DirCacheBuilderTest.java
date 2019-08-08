/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.time.Instant;

import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.events.IndexChangedListener;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class DirCacheBuilderTest extends RepositoryTestCase {
	@Test
	public void testBuildEmpty() throws Exception {
		{
			final DirCache dc = db.lockDirCache();
			final DirCacheBuilder b = dc.builder();
			assertNotNull(b);
			b.finish();
			dc.write();
			assertTrue(dc.commit());
		}
		{
			final DirCache dc = db.readDirCache();
			assertEquals(0, dc.getEntryCount());
		}
	}

	@Test
	public void testBuildRejectsUnsetFileMode() throws Exception {
		final DirCache dc = DirCache.newInCore();
		final DirCacheBuilder b = dc.builder();
		assertNotNull(b);

		final DirCacheEntry e = new DirCacheEntry("a");
		assertEquals(0, e.getRawMode());
		try {
			b.add(e);
			fail("did not reject unset file mode");
		} catch (IllegalArgumentException err) {
			assertEquals("FileMode not set for path a", err.getMessage());
		}
	}

	@Test
	public void testBuildOneFile_FinishWriteCommit() throws Exception {
		final String path = "a-file-path";
		final FileMode mode = FileMode.REGULAR_FILE;
		final Instant lastModified = Instant.ofEpochMilli(1218123387057L);
		final int length = 1342;
		final DirCacheEntry entOrig;
		{
			final DirCache dc = db.lockDirCache();
			final DirCacheBuilder b = dc.builder();
			assertNotNull(b);

			entOrig = new DirCacheEntry(path);
			entOrig.setFileMode(mode);
			entOrig.setLastModified(lastModified);
			entOrig.setLength(length);

			assertNotSame(path, entOrig.getPathString());
			assertEquals(path, entOrig.getPathString());
			assertEquals(ObjectId.zeroId(), entOrig.getObjectId());
			assertEquals(mode.getBits(), entOrig.getRawMode());
			assertEquals(0, entOrig.getStage());
			assertEquals(lastModified, entOrig.getLastModifiedInstant());
			assertEquals(length, entOrig.getLength());
			assertFalse(entOrig.isAssumeValid());
			b.add(entOrig);

			b.finish();
			assertEquals(1, dc.getEntryCount());
			assertSame(entOrig, dc.getEntry(0));

			dc.write();
			assertTrue(dc.commit());
		}
		{
			final DirCache dc = db.readDirCache();
			assertEquals(1, dc.getEntryCount());

			final DirCacheEntry entRead = dc.getEntry(0);
			assertNotSame(entOrig, entRead);
			assertEquals(path, entRead.getPathString());
			assertEquals(ObjectId.zeroId(), entOrig.getObjectId());
			assertEquals(mode.getBits(), entOrig.getRawMode());
			assertEquals(0, entOrig.getStage());
			assertEquals(lastModified, entOrig.getLastModifiedInstant());
			assertEquals(length, entOrig.getLength());
			assertFalse(entOrig.isAssumeValid());
		}
	}

	@Test
	public void testBuildOneFile_Commit() throws Exception {
		final String path = "a-file-path";
		final FileMode mode = FileMode.REGULAR_FILE;
		final Instant lastModified = Instant.ofEpochMilli(1218123387057L);
		final int length = 1342;
		final DirCacheEntry entOrig;
		{
			final DirCache dc = db.lockDirCache();
			final DirCacheBuilder b = dc.builder();
			assertNotNull(b);

			entOrig = new DirCacheEntry(path);
			entOrig.setFileMode(mode);
			entOrig.setLastModified(lastModified);
			entOrig.setLength(length);

			assertNotSame(path, entOrig.getPathString());
			assertEquals(path, entOrig.getPathString());
			assertEquals(ObjectId.zeroId(), entOrig.getObjectId());
			assertEquals(mode.getBits(), entOrig.getRawMode());
			assertEquals(0, entOrig.getStage());
			assertEquals(lastModified, entOrig.getLastModifiedInstant());
			assertEquals(length, entOrig.getLength());
			assertFalse(entOrig.isAssumeValid());
			b.add(entOrig);

			assertTrue(b.commit());
			assertEquals(1, dc.getEntryCount());
			assertSame(entOrig, dc.getEntry(0));
			assertFalse(new File(db.getDirectory(), "index.lock").exists());
		}
		{
			final DirCache dc = db.readDirCache();
			assertEquals(1, dc.getEntryCount());

			final DirCacheEntry entRead = dc.getEntry(0);
			assertNotSame(entOrig, entRead);
			assertEquals(path, entRead.getPathString());
			assertEquals(ObjectId.zeroId(), entOrig.getObjectId());
			assertEquals(mode.getBits(), entOrig.getRawMode());
			assertEquals(0, entOrig.getStage());
			assertEquals(lastModified, entOrig.getLastModifiedInstant());
			assertEquals(length, entOrig.getLength());
			assertFalse(entOrig.isAssumeValid());
		}
	}

	@Test
	public void testBuildOneFile_Commit_IndexChangedEvent()
			throws Exception {
		final class ReceivedEventMarkerException extends RuntimeException {
			private static final long serialVersionUID = 1L;
			// empty
		}

		final String path = "a-file-path";
		final FileMode mode = FileMode.REGULAR_FILE;
		// "old" date in 2008
		final Instant lastModified = Instant.ofEpochMilli(1218123387057L);
		final int length = 1342;
		DirCacheEntry entOrig;
		boolean receivedEvent = false;

		DirCache dc = db.lockDirCache();
		IndexChangedListener listener = new IndexChangedListener() {

			@Override
			public void onIndexChanged(IndexChangedEvent event) {
				throw new ReceivedEventMarkerException();
			}
		};

		ListenerList l = db.getListenerList();
		l.addIndexChangedListener(listener);
		DirCacheBuilder b = dc.builder();

		entOrig = new DirCacheEntry(path);
		entOrig.setFileMode(mode);
		entOrig.setLastModified(lastModified);
		entOrig.setLength(length);
		b.add(entOrig);
		try {
			b.commit();
		} catch (ReceivedEventMarkerException e) {
			receivedEvent = true;
		}
		if (!receivedEvent)
			fail("did not receive IndexChangedEvent");

		// do the same again, as this doesn't change index compared to first
		// round we should get no event this time
		dc = db.lockDirCache();
		listener = new IndexChangedListener() {

			@Override
			public void onIndexChanged(IndexChangedEvent event) {
				throw new ReceivedEventMarkerException();
			}
		};

		l = db.getListenerList();
		l.addIndexChangedListener(listener);
		b = dc.builder();

		entOrig = new DirCacheEntry(path);
		entOrig.setFileMode(mode);
		entOrig.setLastModified(lastModified);
		entOrig.setLength(length);
		b.add(entOrig);
		try {
			b.commit();
		} catch (ReceivedEventMarkerException e) {
			fail("unexpected IndexChangedEvent");
		}
	}

	@Test
	public void testFindSingleFile() throws Exception {
		final String path = "a-file-path";
		final DirCache dc = db.readDirCache();
		final DirCacheBuilder b = dc.builder();
		assertNotNull(b);

		final DirCacheEntry entOrig = new DirCacheEntry(path);
		entOrig.setFileMode(FileMode.REGULAR_FILE);
		assertNotSame(path, entOrig.getPathString());
		assertEquals(path, entOrig.getPathString());
		b.add(entOrig);
		b.finish();

		assertEquals(1, dc.getEntryCount());
		assertSame(entOrig, dc.getEntry(0));
		assertEquals(0, dc.findEntry(path));

		assertEquals(-1, dc.findEntry("@@-before"));
		assertEquals(0, real(dc.findEntry("@@-before")));

		assertEquals(-2, dc.findEntry("a-zoo"));
		assertEquals(1, real(dc.findEntry("a-zoo")));

		assertSame(entOrig, dc.getEntry(path));
	}

	@Test
	public void testAdd_InGitSortOrder() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a-", "a.b", "a/b", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		assertEquals(paths.length, dc.getEntryCount());
		for (int i = 0; i < paths.length; i++) {
			assertSame(ents[i], dc.getEntry(i));
			assertEquals(paths[i], dc.getEntry(i).getPathString());
			assertEquals(i, dc.findEntry(paths[i]));
			assertSame(ents[i], dc.getEntry(paths[i]));
		}
	}

	@Test
	public void testAdd_ReverseGitSortOrder() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a-", "a.b", "a/b", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = ents.length - 1; i >= 0; i--)
			b.add(ents[i]);
		b.finish();

		assertEquals(paths.length, dc.getEntryCount());
		for (int i = 0; i < paths.length; i++) {
			assertSame(ents[i], dc.getEntry(i));
			assertEquals(paths[i], dc.getEntry(i).getPathString());
			assertEquals(i, dc.findEntry(paths[i]));
			assertSame(ents[i], dc.getEntry(paths[i]));
		}
	}

	@Test
	public void testBuilderClear() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a-", "a.b", "a/b", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}
		{
			final DirCacheBuilder b = dc.builder();
			for (int i = 0; i < ents.length; i++)
				b.add(ents[i]);
			b.finish();
		}
		assertEquals(paths.length, dc.getEntryCount());
		{
			final DirCacheBuilder b = dc.builder();
			b.finish();
		}
		assertEquals(0, dc.getEntryCount());
	}

	private static int real(int eIdx) {
		if (eIdx < 0)
			eIdx = -(eIdx + 1);
		return eIdx;
	}
}
