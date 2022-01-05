/*
 * Copyright (C) 2021, Google LLC. and others
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.file.ObjectDirectoryPackParser;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pack parsing is covered in {@link PackParserTest}.
 *
 * Here we test ObjectDirectoryPackParser specific parts. e.g. that is creates
 * the object-size index.
 */
public class ObjectDirectoryPackParserTest extends RepositoryTestCase {

	@Before
	public void setup() throws IOException {
		FileBasedConfig jGitConfig = mockSystemReader.getJGitConfig();
		jGitConfig.setInt(ConfigConstants.CONFIG_PACK_SECTION, null,
				ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, 7);
		jGitConfig.save();
	}

	/**
	 * Test indexing one of the test packs in the egit repo. It has deltas.
	 *
	 * @throws IOException
	 */
	@Test
	public void testGitPack() throws IOException {
		File packFile = JGitTestUtil.getTestResourceFile("pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.pack");
		try (InputStream is = new FileInputStream(packFile)) {
			ObjectDirectoryPackParser p = index(is);
			p.parse(NullProgressMonitor.INSTANCE);

			Pack pack = p.getPack();
			assertTrue(pack.hasObjSizeIndex());

			// Only blobs in the pack
			ObjectId blob1 = ObjectId
					.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3");
			ObjectId blob2 = ObjectId
					.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259");
			assertEquals(18787, pack.getIndexedObjectSize(blob1));
			assertEquals(18009, pack.getIndexedObjectSize(blob2));

			// Indexed sizes match object db sizes
			assertEquals(db.getObjectDatabase().open(blob1).getSize(),
					pack.getIndexedObjectSize(blob1));
			assertEquals(db.getObjectDatabase().open(blob2).getSize(),
					pack.getIndexedObjectSize(blob2));

		}
	}

	/**
	 * This is just another pack. It so happens that we have two convenient pack to
	 * test with in the repository.
	 *
	 * @throws IOException
	 */
	@Test
	public void testAnotherGitPack() throws IOException {
		File packFile = JGitTestUtil.getTestResourceFile("pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371.pack");
		try (InputStream is = new FileInputStream(packFile)) {
			ObjectDirectoryPackParser p = index(is);
			p.parse(NullProgressMonitor.INSTANCE);
			Pack pack = p.getPack();

			// Blob smaller than threshold:
			assertEquals(-1, pack.getIndexedObjectSize(ObjectId
					.fromString("15fae9e651043de0fd1deef588aa3fbf5a7a41c6")));

			// Blob bigger than threshold
			assertEquals(10, pack.getIndexedObjectSize(ObjectId
					.fromString("8230f48330e0055d9e0bc5a2a77718f6dd9324b8")));

			// A commit (not indexed)
			assertEquals(-1, pack.getIndexedObjectSize(ObjectId
					.fromString("d0114ab8ac326bab30e3a657a0397578c5a1af88")));

			// Object not in pack
			assertEquals(-2, pack.getIndexedObjectSize(ObjectId
					.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
		}
	}

	@Test
	public void testTinyThinPack() throws Exception {
		// less than 16 bytes, so its length fits in a single byte later
		String base = "abcdefghijklmn";
		RevBlob a;
		try (TestRepository d = new TestRepository<Repository>(db)) {
			a = d.blob(base);
		}

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 1);

		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { (byte) base.length(), // size of the base
				(byte) (base.length() + 1), // size after reconstruction
				0x1, 'b' }); // append one byte

		digest(pack);

		ObjectDirectoryPackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.parse(NullProgressMonitor.INSTANCE);

		Pack writtenPack = p.getPack();
		// base
		assertEquals(base.length(), writtenPack.getIndexedObjectSize(a));
		// undeltified blob
		assertEquals(base.length() + 1,
				writtenPack.getIndexedObjectSize(ObjectId.fromString(
						"f177875498138143c9657cc52b049ad4d20d5223")));
	}

	@Test
	public void testPackWithDuplicateBlob() throws Exception {
		final byte[] data = Constants.encode("0123456789abcdefg");
		RevBlob blob;
		try (TestRepository<Repository> d = new TestRepository<>(db)) {
			blob = d.blob(data);
			assertTrue(db.getObjectDatabase().has(blob));
		}

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 1);
		pack.write((Constants.OBJ_BLOB) << 4 | 0x80 | 1);
		pack.write(1);
		deflate(pack, data);
		digest(pack);

		ObjectDirectoryPackParser p = index(
				new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(false);
		p.parse(NullProgressMonitor.INSTANCE);

		assertEquals(data.length, p.getPack().getIndexedObjectSize(blob));
	}

	private static void packHeader(TemporaryBuffer.Heap tinyPack, int cnt)
			throws IOException {
		final byte[] hdr = new byte[8];
		NB.encodeInt32(hdr, 0, 2);
		NB.encodeInt32(hdr, 4, cnt);

		tinyPack.write(Constants.PACK_SIGNATURE);
		tinyPack.write(hdr, 0, 8);
	}

	private static void deflate(TemporaryBuffer.Heap tinyPack,
			final byte[] content)
			throws IOException {
		final Deflater deflater = new Deflater();
		final byte[] buf = new byte[128];
		deflater.setInput(content, 0, content.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				tinyPack.write(buf, 0, n);
		} while (!deflater.finished());
	}

	private static void digest(TemporaryBuffer.Heap buf) throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		md.update(buf.toByteArray());
		buf.write(md.digest());
	}

	private ObjectInserter inserter;

	@After
	public void release() {
		if (inserter != null) {
			inserter.close();
		}
	}

	private ObjectDirectoryPackParser index(InputStream in) throws IOException {
		if (inserter == null)
			inserter = db.newObjectInserter();
		return (ObjectDirectoryPackParser) inserter.newPackParser(in);
	}
}
