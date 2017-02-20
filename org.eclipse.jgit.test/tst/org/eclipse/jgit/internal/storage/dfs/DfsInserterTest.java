/*
 * Copyright (C) 2013, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class DfsInserterTest {
	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void testInserterDiscardsPack() throws IOException {
		try (ObjectInserter ins = db.newObjectInserter()) {
			ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
			ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
			assertEquals(0, db.getObjectDatabase().listPacks().size());
		}
		assertEquals(0, db.getObjectDatabase().listPacks().size());
	}

	@Test
	public void testReadFromInserterSmallObjects() throws IOException {
		ObjectInserter ins = db.newObjectInserter();
		ObjectId id1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ObjectId id2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
		assertEquals(0, db.getObjectDatabase().listPacks().size());

		ObjectReader reader = ins.newReader();
		assertSame(ins, reader.getCreatedFromInserter());
		assertEquals("foo", readString(reader.open(id1)));
		assertEquals("bar", readString(reader.open(id2)));
		assertEquals(0, db.getObjectDatabase().listPacks().size());
		ins.flush();
		assertEquals(1, db.getObjectDatabase().listPacks().size());
	}

	@Test
	public void testReadFromInserterLargerObjects() throws IOException {
		db.getObjectDatabase().getReaderOptions().setStreamFileThreshold(512);
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig()
			.setBlockSize(512)
			.setBlockLimit(2048));

		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(8192);
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		ObjectId id1 = ins.insert(Constants.OBJ_BLOB, data);
		assertEquals(0, db.getObjectDatabase().listPacks().size());

		ObjectReader reader = ins.newReader();
		assertSame(ins, reader.getCreatedFromInserter());
		assertTrue(Arrays.equals(data, readStream(reader.open(id1))));
		assertEquals(0, db.getObjectDatabase().listPacks().size());
		ins.flush();

		List<DfsPackDescription> packs = db.getObjectDatabase().listPacks();
		assertEquals(1, packs.size());
		assertTrue(packs.get(0).getFileSize(PackExt.PACK) > 2048);
	}

	@Test
	public void testReadFromFallback() throws IOException {
		ObjectInserter ins = db.newObjectInserter();
		ObjectId id1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ins.flush();
		ObjectId id2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
		assertEquals(1, db.getObjectDatabase().listPacks().size());

		ObjectReader reader = ins.newReader();
		assertSame(ins, reader.getCreatedFromInserter());
		assertEquals("foo", readString(reader.open(id1)));
		assertEquals("bar", readString(reader.open(id2)));
		assertEquals(1, db.getObjectDatabase().listPacks().size());
		ins.flush();
		assertEquals(2, db.getObjectDatabase().listPacks().size());
	}

	@Test
	public void testReaderResolve() throws IOException {
		ObjectInserter ins = db.newObjectInserter();
		ObjectId id1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ins.flush();
		ObjectId id2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("bar"));
		String abbr1 = ObjectId.toString(id1).substring(0, 4);
		String abbr2 = ObjectId.toString(id2).substring(0, 4);
		assertFalse(abbr1.equals(abbr2));

		ObjectReader reader = ins.newReader();
		assertSame(ins, reader.getCreatedFromInserter());
		Collection<ObjectId> objs;
		objs = reader.resolve(AbbreviatedObjectId.fromString(abbr1));
		assertEquals(1, objs.size());
		assertEquals(id1, objs.iterator().next());

		objs = reader.resolve(AbbreviatedObjectId.fromString(abbr2));
		assertEquals(1, objs.size());
		assertEquals(id2, objs.iterator().next());
	}

	@Test
	public void testGarbageSelectivelyVisible() throws IOException {
		ObjectInserter ins = db.newObjectInserter();
		ObjectId fooId = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ins.flush();
		assertEquals(1, db.getObjectDatabase().listPacks().size());

		// Make pack 0 garbage.
		db.getObjectDatabase().listPacks().get(0).setPackSource(PackSource.UNREACHABLE_GARBAGE);

		// Default behavior should be that the database has foo, because we allow garbage objects.
		assertTrue(db.getObjectDatabase().has(fooId));
		// But we should not be able to see it if we pass the right args.
		assertFalse(db.getObjectDatabase().has(fooId, true));
	}

	@Test
	public void testInserterIgnoresUnreachable() throws IOException {
		ObjectInserter ins = db.newObjectInserter();
		ObjectId fooId = ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ins.flush();
		assertEquals(1, db.getObjectDatabase().listPacks().size());

		// Make pack 0 garbage.
		db.getObjectDatabase().listPacks().get(0).setPackSource(PackSource.UNREACHABLE_GARBAGE);

		// We shouldn't be able to see foo because it's garbage.
		assertFalse(db.getObjectDatabase().has(fooId, true));

		// But if we re-insert foo, it should become visible again.
		ins.insert(Constants.OBJ_BLOB, Constants.encode("foo"));
		ins.flush();
		assertTrue(db.getObjectDatabase().has(fooId, true));

		// Verify that we have a foo in both packs, and 1 of them is garbage.
		DfsReader reader = new DfsReader(db.getObjectDatabase());
		DfsPackFile packs[] = db.getObjectDatabase().getPacks();
		Set<PackSource> pack_sources = new HashSet<>();

		assertEquals(2, packs.length);

		pack_sources.add(packs[0].getPackDescription().getPackSource());
		pack_sources.add(packs[1].getPackDescription().getPackSource());

		assertTrue(packs[0].hasObject(reader, fooId));
		assertTrue(packs[1].hasObject(reader, fooId));
		assertTrue(pack_sources.contains(PackSource.UNREACHABLE_GARBAGE));
		assertTrue(pack_sources.contains(PackSource.INSERT));
	}

	@Test
	public void testNoCheckExisting() throws IOException {
		byte[] contents = Constants.encode("foo");
		ObjectId fooId;
		try (ObjectInserter ins = db.newObjectInserter()) {
			fooId = ins.insert(Constants.OBJ_BLOB, contents);
			ins.flush();
		}
		assertEquals(1, db.getObjectDatabase().listPacks().size());

		try (ObjectInserter ins = db.newObjectInserter()) {
			((DfsInserter) ins).checkExisting(false);
			assertEquals(fooId, ins.insert(Constants.OBJ_BLOB, contents));
			ins.flush();
		}
		assertEquals(2, db.getObjectDatabase().listPacks().size());

		// Verify that we have a foo in both INSERT packs.
		DfsReader reader = new DfsReader(db.getObjectDatabase());
		DfsPackFile packs[] = db.getObjectDatabase().getPacks();

		assertEquals(2, packs.length);
		DfsPackFile p1 = packs[0];
		assertEquals(PackSource.INSERT, p1.getPackDescription().getPackSource());
		assertTrue(p1.hasObject(reader, fooId));

		DfsPackFile p2 = packs[1];
		assertEquals(PackSource.INSERT, p2.getPackDescription().getPackSource());
		assertTrue(p2.hasObject(reader, fooId));
	}

	private static String readString(ObjectLoader loader) throws IOException {
		return RawParseUtils.decode(readStream(loader));
	}

	private static byte[] readStream(ObjectLoader loader) throws IOException {
		ByteBuffer bb = IO.readWholeStream(loader.openStream(), 64);
		byte[] buf = new byte[bb.remaining()];
		bb.get(buf);
		return buf;
	}
}
