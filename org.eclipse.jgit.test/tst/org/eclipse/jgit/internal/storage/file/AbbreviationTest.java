/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbbreviationTest extends LocalDiskRepositoryTestCase {
	private FileRepository db;

	private ObjectReader reader;

	private TestRepository<Repository> test;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createBareRepository();
		reader = db.newObjectReader();
		test = new TestRepository<>(db);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (reader != null) {
			reader.close();
		}
	}

	@Test
	public void testAbbreviateOnEmptyRepository() throws IOException {
		ObjectId id = id("9d5b926ed164e8ee88d3b8b1e525d699adda01ba");

		assertEquals(id.abbreviate(2), reader.abbreviate(id, 2));
		assertEquals(id.abbreviate(7), reader.abbreviate(id, 7));
		assertEquals(id.abbreviate(8), reader.abbreviate(id, 8));
		assertEquals(id.abbreviate(10), reader.abbreviate(id, 10));
		assertEquals(id.abbreviate(16), reader.abbreviate(id, 16));

		assertEquals(AbbreviatedObjectId.fromObjectId(id), //
				reader.abbreviate(id, OBJECT_ID_STRING_LENGTH));

		Collection<ObjectId> matches;

		matches = reader.resolve(reader.abbreviate(id, 8));
		assertNotNull(matches);
		assertEquals(0, matches.size());

		matches = reader.resolve(AbbreviatedObjectId.fromObjectId(id));
		assertNotNull(matches);
		assertEquals(1, matches.size());
		assertEquals(id, matches.iterator().next());
	}

	@Test
	public void testAbbreviateLooseBlob() throws Exception {
		ObjectId id = test.blob("test");

		assertEquals(id.abbreviate(2), reader.abbreviate(id, 2));
		assertEquals(id.abbreviate(7), reader.abbreviate(id, 7));
		assertEquals(id.abbreviate(8), reader.abbreviate(id, 8));
		assertEquals(id.abbreviate(10), reader.abbreviate(id, 10));
		assertEquals(id.abbreviate(16), reader.abbreviate(id, 16));

		Collection<ObjectId> matches = reader.resolve(reader.abbreviate(id, 8));
		assertNotNull(matches);
		assertEquals(1, matches.size());
		assertEquals(id, matches.iterator().next());

		assertEquals(id, db.resolve(reader.abbreviate(id, 8).name()));
	}

	@Test
	public void testAbbreviatePackedBlob() throws Exception {
		RevBlob id = test.blob("test");
		test.branch("master").commit().add("test", id).child();
		test.packAndPrune();
		assertTrue(reader.has(id));

		assertEquals(id.abbreviate(7), reader.abbreviate(id, 7));
		assertEquals(id.abbreviate(8), reader.abbreviate(id, 8));
		assertEquals(id.abbreviate(10), reader.abbreviate(id, 10));
		assertEquals(id.abbreviate(16), reader.abbreviate(id, 16));

		Collection<ObjectId> matches = reader.resolve(reader.abbreviate(id, 8));
		assertNotNull(matches);
		assertEquals(1, matches.size());
		assertEquals(id, matches.iterator().next());

		assertEquals(id, db.resolve(reader.abbreviate(id, 8).name()));
	}

	@Test
	public void testAbbreviateIsActuallyUnique() throws Exception {
		// This test is far more difficult. We have to manually craft
		// an input that contains collisions at a particular prefix,
		// but this is computationally difficult. Instead we force an
		// index file to have what we want.
		//

		ObjectId id = id("9d5b926ed164e8ee88d3b8b1e525d699adda01ba");
		byte[] idBuf = toByteArray(id);
		List<PackedObjectInfo> objects = new ArrayList<>();
		for (int i = 0; i < 256; i++) {
			idBuf[9] = (byte) i;
			objects.add(new PackedObjectInfo(ObjectId.fromRaw(idBuf)));
		}

		String packName = "pack-" + id.name();
		File packDir = db.getObjectDatabase().getPackDirectory();
		File idxFile = new File(packDir, packName + ".idx");
		File packFile = new File(packDir, packName + ".pack");
		FileUtils.mkdir(packDir, true);
		try (OutputStream dst = new BufferedOutputStream(
				new FileOutputStream(idxFile))) {
			PackIndexWriter writer = new PackIndexWriterV2(dst);
			writer.write(objects, new byte[OBJECT_ID_LENGTH]);
		}

		try (FileOutputStream unused = new FileOutputStream(packFile)) {
			// unused
		}

		assertEquals(id.abbreviate(20), reader.abbreviate(id, 2));

		AbbreviatedObjectId abbrev8 = id.abbreviate(8);
		Collection<ObjectId> matches = reader.resolve(abbrev8);
		assertNotNull(matches);
		assertEquals(objects.size(), matches.size());
		for (PackedObjectInfo info : objects)
			assertTrue("contains " + info.name(), matches.contains(info));

		try {
			db.resolve(abbrev8.name());
			fail("did not throw AmbiguousObjectException");
		} catch (AmbiguousObjectException err) {
			assertEquals(abbrev8, err.getAbbreviatedObjectId());
			matches = err.getCandidates();
			assertNotNull(matches);
			assertEquals(objects.size(), matches.size());
			for (PackedObjectInfo info : objects)
				assertTrue("contains " + info.name(), matches.contains(info));
		}

		assertEquals(id, db.resolve(id.abbreviate(20).name()));
	}

	private static ObjectId id(String name) {
		return ObjectId.fromString(name);
	}

	private static byte[] toByteArray(ObjectId id) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream(OBJECT_ID_LENGTH);
		id.copyRawTo(buf);
		return buf.toByteArray();
	}
}
