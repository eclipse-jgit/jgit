/*
 * Copyright (C) 2017, Google Inc.
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
 *	 notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *	 copyright notice, this list of conditions and the following
 *	 disclaimer in the documentation and/or other materials provided
 *	 with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *	 names of its contributors may be used to endorse or promote
 *	 products derived from this software without specific prior
 *	 written permission.
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

package org.eclipse.jgit.internal.storage.file;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("boxing")
public class PackInserterTest extends RepositoryTestCase {
	private WindowCacheConfig origWindowCacheConfig;

	@Before
	public void setWindowCacheConfig() {
		origWindowCacheConfig = new WindowCacheConfig();
		origWindowCacheConfig.install();
	}

	@After
	public void resetWindowCacheConfig() {
		origWindowCacheConfig.install();
	}

	@Before
	public void emptyAtSetUp() throws Exception {
		assertEquals(0, listPacks().size());
		assertNoObjects();
	}

	@Test
	public void noFlush() throws Exception {
		try (PackInserter ins = newInserter()) {
			ins.insert(OBJ_BLOB, Constants.encode("foo contents"));
			// No flush.
		}
		assertNoObjects();
	}

	@Test
	public void flushEmptyPack() throws Exception {
		try (PackInserter ins = newInserter()) {
			ins.flush();
		}
		assertNoObjects();
	}

	@Test
	public void singlePack() throws Exception {
		ObjectId blobId;
		byte[] blob = Constants.encode("foo contents");
		ObjectId treeId;
		ObjectId commitId;
		byte[] commit;
		try (PackInserter ins = newInserter()) {
			blobId = ins.insert(OBJ_BLOB, blob);

			DirCache dc = DirCache.newInCore();
			DirCacheBuilder b = dc.builder();
			DirCacheEntry dce = new DirCacheEntry("foo");
			dce.setFileMode(FileMode.REGULAR_FILE);
			dce.setObjectId(blobId);
			b.add(dce);
			b.finish();
			treeId = dc.writeTree(ins);

			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(treeId);
			cb.setAuthor(author);
			cb.setCommitter(committer);
			cb.setMessage("Commit message");
			commit = cb.toByteArray();
			commitId = ins.insert(cb);
			ins.flush();
		}

		assertPacksOnly();
		List<PackFile> packs = listPacks();
		assertEquals(1, packs.size());
		assertEquals(3, packs.get(0).getObjectCount());

		try (ObjectReader reader = db.newObjectReader()) {
			assertBlob(reader, blobId, blob);

			CanonicalTreeParser treeParser =
					new CanonicalTreeParser(null, reader, treeId);
			assertEquals("foo", treeParser.getEntryPathString());
			assertEquals(blobId, treeParser.getEntryObjectId());

			ObjectLoader commitLoader = reader.open(commitId);
			assertEquals(OBJ_COMMIT, commitLoader.getType());
			assertArrayEquals(commit, commitLoader.getBytes());
		}
	}

	@Test
	public void multiplePacks() throws Exception {
		ObjectId blobId1;
		ObjectId blobId2;
		byte[] blob1 = Constants.encode("blob1");
		byte[] blob2 = Constants.encode("blob2");

		try (PackInserter ins = newInserter()) {
			blobId1 = ins.insert(OBJ_BLOB, blob1);
			ins.flush();
			blobId2 = ins.insert(OBJ_BLOB, blob2);
			ins.flush();
		}

		assertPacksOnly();
		List<PackFile> packs = listPacks();
		assertEquals(2, packs.size());
		assertEquals(1, packs.get(0).getObjectCount());
		assertEquals(1, packs.get(1).getObjectCount());

		try (ObjectReader reader = db.newObjectReader()) {
			assertBlob(reader, blobId1, blob1);
			assertBlob(reader, blobId2, blob2);
		}
	}

	@Test
	public void largeBlob() throws Exception {
		ObjectId blobId;
		byte[] blob = newLargeBlob();
		try (PackInserter ins = newInserter()) {
			assertThat(blob.length, greaterThan(ins.getBufferSize()));
			blobId =
					ins.insert(OBJ_BLOB, blob.length, new ByteArrayInputStream(blob));
			ins.flush();
		}

		assertPacksOnly();
		Collection<PackFile> packs = listPacks();
		assertEquals(1, packs.size());
		PackFile p = packs.iterator().next();
		assertEquals(1, p.getObjectCount());

		try (ObjectReader reader = db.newObjectReader()) {
			assertBlob(reader, blobId, blob);
		}
	}

	@Test
	public void overwriteExistingPack() throws Exception {
		ObjectId blobId;
		byte[] blob = Constants.encode("foo contents");

		try (PackInserter ins = newInserter()) {
			blobId = ins.insert(OBJ_BLOB, blob);
			ins.flush();
		}

		assertPacksOnly();
		List<PackFile> packs = listPacks();
		assertEquals(1, packs.size());
		PackFile pack = packs.get(0);
		assertEquals(1, pack.getObjectCount());

		String inode = getInode(pack.getPackFile());

		try (PackInserter ins = newInserter()) {
			ins.checkExisting(false);
			assertEquals(blobId, ins.insert(OBJ_BLOB, blob));
			ins.flush();
		}

		assertPacksOnly();
		packs = listPacks();
		assertEquals(1, packs.size());
		pack = packs.get(0);
		assertEquals(1, pack.getObjectCount());

		if (inode != null) {
			// Old file was overwritten with new file, although objects were
			// equivalent.
			assertNotEquals(inode, getInode(pack.getPackFile()));
		}
	}

	@Test
	public void checkExisting() throws Exception {
		ObjectId blobId;
		byte[] blob = Constants.encode("foo contents");

		try (PackInserter ins = newInserter()) {
			blobId = ins.insert(OBJ_BLOB, blob);
			ins.insert(OBJ_BLOB, Constants.encode("another blob"));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(1, listPacks().size());

		try (PackInserter ins = newInserter()) {
			assertEquals(blobId, ins.insert(OBJ_BLOB, blob));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(1, listPacks().size());

		try (PackInserter ins = newInserter()) {
			ins.checkExisting(false);
			assertEquals(blobId, ins.insert(OBJ_BLOB, blob));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(2, listPacks().size());

		try (ObjectReader reader = db.newObjectReader()) {
			assertBlob(reader, blobId, blob);
		}
	}

	@Test
	public void insertSmallInputStreamRespectsCheckExisting() throws Exception {
		ObjectId blobId;
		byte[] blob = Constants.encode("foo contents");
		try (PackInserter ins = newInserter()) {
			assertThat(blob.length, lessThan(ins.getBufferSize()));
			blobId = ins.insert(OBJ_BLOB, blob);
			ins.insert(OBJ_BLOB, Constants.encode("another blob"));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(1, listPacks().size());

		try (PackInserter ins = newInserter()) {
			assertEquals(blobId,
					ins.insert(OBJ_BLOB, blob.length, new ByteArrayInputStream(blob)));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(1, listPacks().size());
	}

	@Test
	public void insertLargeInputStreamBypassesCheckExisting() throws Exception {
		ObjectId blobId;
		byte[] blob = newLargeBlob();

		try (PackInserter ins = newInserter()) {
			assertThat(blob.length, greaterThan(ins.getBufferSize()));
			blobId = ins.insert(OBJ_BLOB, blob);
			ins.insert(OBJ_BLOB, Constants.encode("another blob"));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(1, listPacks().size());

		try (PackInserter ins = newInserter()) {
			assertEquals(blobId,
					ins.insert(OBJ_BLOB, blob.length, new ByteArrayInputStream(blob)));
			ins.flush();
		}

		assertPacksOnly();
		assertEquals(2, listPacks().size());
	}

	@Test
	public void readBackSmallFiles() throws Exception {
		ObjectId blobId1;
		ObjectId blobId2;
		ObjectId blobId3;
		byte[] blob1 = Constants.encode("blob1");
		byte[] blob2 = Constants.encode("blob2");
		byte[] blob3 = Constants.encode("blob3");
		try (PackInserter ins = newInserter()) {
			assertThat(blob1.length, lessThan(ins.getBufferSize()));
			blobId1 = ins.insert(OBJ_BLOB, blob1);

			try (ObjectReader reader = ins.newReader()) {
				assertBlob(reader, blobId1, blob1);
			}

			// Read-back should not mess up the file pointer.
			blobId2 = ins.insert(OBJ_BLOB, blob2);
			ins.flush();

			blobId3 = ins.insert(OBJ_BLOB, blob3);
		}

		assertPacksOnly();
		List<PackFile> packs = listPacks();
		assertEquals(1, packs.size());
		assertEquals(2, packs.get(0).getObjectCount());

		try (ObjectReader reader = db.newObjectReader()) {
			assertBlob(reader, blobId1, blob1);
			assertBlob(reader, blobId2, blob2);

			try {
				reader.open(blobId3);
				fail("Expected MissingObjectException");
			} catch (MissingObjectException expected) {
				// Expected.
			}
		}
	}

	@Test
	public void readBackLargeFile() throws Exception {
		ObjectId blobId;
		byte[] blob = newLargeBlob();

		WindowCacheConfig wcc = new WindowCacheConfig();
		wcc.setStreamFileThreshold(1024);
		wcc.install();
		try (ObjectReader reader = db.newObjectReader()) {
			assertThat(blob.length, greaterThan(reader.getStreamFileThreshold()));
		}

		try (PackInserter ins = newInserter()) {
			blobId = ins.insert(OBJ_BLOB, blob);

			try (ObjectReader reader = ins.newReader()) {
				// Double-check threshold is propagated.
				assertThat(blob.length, greaterThan(reader.getStreamFileThreshold()));
				assertBlob(reader, blobId, blob);
			}
		}

		assertPacksOnly();
		// Pack was streamed out to disk and read back from the temp file, but
		// ultimately rolled back and deleted.
		assertEquals(0, listPacks().size());

		try (ObjectReader reader = db.newObjectReader()) {
			try {
				reader.open(blobId);
				fail("Expected MissingObjectException");
			} catch (MissingObjectException expected) {
				// Expected.
			}
		}
	}

	@Test
	public void readBackFallsBackToRepo() throws Exception {
		ObjectId blobId;
		byte[] blob = Constants.encode("foo contents");
		try (PackInserter ins = newInserter()) {
			assertThat(blob.length, lessThan(ins.getBufferSize()));
			blobId = ins.insert(OBJ_BLOB, blob);
			ins.flush();
		}

		try (PackInserter ins = newInserter();
				ObjectReader reader = ins.newReader()) {
			assertBlob(reader, blobId, blob);
		}
	}

	private List<PackFile> listPacks() throws Exception {
		List<PackFile> fromOpenDb = listPacks(db);
		List<PackFile> reopened;
		try (FileRepository db2 = new FileRepository(db.getDirectory())) {
			reopened = listPacks(db2);
		}
		assertEquals(fromOpenDb.size(), reopened.size());
		for (int i = 0 ; i < fromOpenDb.size(); i++) {
			PackFile a = fromOpenDb.get(i);
			PackFile b = reopened.get(i);
			assertEquals(a.getPackName(), b.getPackName());
			assertEquals(
					a.getPackFile().getAbsolutePath(), b.getPackFile().getAbsolutePath());
			assertEquals(a.getObjectCount(), b.getObjectCount());
			a.getObjectCount();
		}
		return fromOpenDb;
	}

	private static List<PackFile> listPacks(FileRepository db) throws Exception {
		return db.getObjectDatabase().getPacks().stream()
				.sorted(comparing(PackFile::getPackName)).collect(toList());
	}

	private PackInserter newInserter() {
		return db.getObjectDatabase().newPackInserter();
	}

	private static byte[] newLargeBlob() {
		byte[] blob = new byte[10240];
		for (int i = 0; i < blob.length; i++) {
			blob[i] = (byte) ('0' + (i % 10));
		}
		return blob;
	}

	private static String getInode(File f) throws Exception {
		BasicFileAttributes attrs = Files.readAttributes(
				f.toPath(), BasicFileAttributes.class);
		Object k = attrs.fileKey();
		if (k == null) {
			return null;
		}
		Pattern p = Pattern.compile("^\\(dev=[^,]*,ino=(\\d+)\\)$");
		Matcher m = p.matcher(k.toString());
		return m.matches() ? m.group(1) : null;
	}

	private static void assertBlob(ObjectReader reader, ObjectId id,
			byte[] expected) throws Exception {
		ObjectLoader loader = reader.open(id);
		assertEquals(OBJ_BLOB, loader.getType());
		assertEquals(expected.length, loader.getSize());
		try (ObjectStream s = loader.openStream()) {
			int n = (int) s.getSize();
			byte[] actual = new byte[n];
			assertEquals(n, IO.readFully(s, actual, 0));
			assertArrayEquals(expected, actual);
		}
	}

	private void assertPacksOnly() throws Exception {
		new BadFileCollector(f -> !f.endsWith(".pack") && !f.endsWith(".idx"))
				.assertNoBadFiles(db.getObjectDatabase().getDirectory());
	}

	private void assertNoObjects() throws Exception {
		new BadFileCollector(f -> true)
				.assertNoBadFiles(db.getObjectDatabase().getDirectory());
	}

	private static class BadFileCollector extends SimpleFileVisitor<Path> {
		private final Predicate<String> badName;
		private List<String> bad;

		BadFileCollector(Predicate<String> badName) {
			this.badName = badName;
		}

		void assertNoBadFiles(File f) throws IOException {
			bad = new ArrayList<>();
			Files.walkFileTree(f.toPath(), this);
			if (!bad.isEmpty()) {
				fail("unexpected files in object directory: " + bad);
			}
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
			String name = file.getFileName().toString();
			if (!attrs.isDirectory() && badName.test(name)) {
				bad.add(name);
			}
			return FileVisitResult.CONTINUE;
		}
	}
}
