/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExternalObjectDatabaseTest {

	private static final byte[] CONTENT = "a large blob".getBytes(UTF_8);

	private static final ObjectId ID = new ObjectInserter.Formatter()
			.idFor(OBJ_BLOB, CONTENT);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private CountingStore store;

	private ExternalObjectDatabase db;

	@Before
	public void setUp() {
		store = new CountingStore(new LocalDirectoryObjectStore(
				new File(tmp.getRoot(), "store")));
		db = new ExternalObjectDatabase("large", 100, store);
	}

	@After
	public void tearDown() {
		db.close();
	}

	@Test
	public void insertedBlobIsVisibleWithoutFlush() throws IOException {
		try (ExternalObjectInserter ins = db.newInserter()) {
			assertEquals(ID, insert(ins, ID, CONTENT));
		}

		assertTrue(db.has(ID));
		assertEquals(CONTENT.length, db.getObjectSize(ID));
	}

	@Test
	public void insertSkipsUploadWhenBlobIsAlreadyStored()
			throws IOException {
		try (ExternalObjectInserter ins = db.newInserter()) {
			insert(ins, ID, CONTENT);
			insert(ins, ID, CONTENT);
		}

		assertEquals(1, store.puts);
	}

	@Test
	public void insertWithWrongIdFailsAndStoresNothing() throws IOException {
		ObjectId wrong = new ObjectInserter.Formatter().idFor(OBJ_BLOB,
				"other".getBytes(UTF_8));
		try (ExternalObjectInserter ins = db.newInserter()) {
			assertThrows(IOException.class, () -> insert(ins, wrong, CONTENT));
		}

		assertFalse(db.has(wrong));
	}

	@Test
	public void insertWithWrongSizeFails() {
		try (ExternalObjectInserter ins = db.newInserter()) {
			assertThrows(IOException.class, () -> ins.insert(ID, OBJ_BLOB,
					CONTENT.length + 1, new ByteArrayInputStream(CONTENT)));
		}
	}

	@Test
	public void insertRejectsObjectsOtherThanBlobs() {
		try (ExternalObjectInserter ins = db.newInserter()) {
			assertThrows(IllegalArgumentException.class,
					() -> ins.insert(ID, OBJ_TREE, CONTENT.length,
							new ByteArrayInputStream(CONTENT)));
		}
		assertEquals(0, store.puts);
	}

	@Test
	public void insertWithoutKnownIdIsNotSupported() {
		try (ExternalObjectInserter ins = db.newInserter()) {
			assertThrows(UnsupportedOperationException.class,
					() -> ins.insert(OBJ_BLOB, CONTENT));
			assertThrows(UnsupportedOperationException.class,
					() -> ins.newPackParser(
							new ByteArrayInputStream(new byte[0])));
		}
	}

	@Test
	public void readerStreamsContent() throws IOException {
		insert(ID, CONTENT);

		try (ObjectReader reader = db.newReader()) {
			ObjectLoader ldr = reader.open(ID, OBJ_BLOB);
			assertEquals(OBJ_BLOB, ldr.getType());
			assertEquals(CONTENT.length, ldr.getSize());
			assertTrue(ldr.isLarge());
			try (ObjectStream in = ldr.openStream()) {
				assertEquals(OBJ_BLOB, in.getType());
				assertEquals(CONTENT.length, in.getSize());
				assertArrayEquals(CONTENT, in.readAllBytes());
			}
			assertEquals(CONTENT.length, reader.getObjectSize(ID, OBJ_BLOB));
		}
	}

	@Test
	public void loaderNeverCachesWholeContent() throws IOException {
		insert(ID, CONTENT);

		ObjectLoader ldr = db.openObject(ID);
		assertThrows(LargeObjectException.class, ldr::getCachedBytes);
	}

	@Test
	public void openingLoaderDoesNotReadContent() throws IOException {
		insert(ID, CONTENT);
		int opensBefore = store.opens;

		try (ObjectReader reader = db.newReader()) {
			reader.open(ID);
			reader.has(ID);
			reader.getObjectSize(ID, OBJ_BLOB);
		}

		assertEquals(opensBefore, store.opens);
	}

	@Test
	public void missingObjectIsReportedAsMissing() throws IOException {
		assertFalse(db.has(ID));
		assertNull(db.openObject(ID));
		assertEquals(-1, db.getObjectSize(ID));
		try (ObjectReader reader = db.newReader()) {
			assertFalse(reader.has(ID));
			assertThrows(MissingObjectException.class, () -> reader.open(ID));
			assertThrows(MissingObjectException.class,
					() -> reader.open(ID, OBJ_BLOB));
		}
	}

	@Test
	public void storeFailureIsNeverReportedAsMissing() {
		store.failFind = true;

		assertThrows(IOException.class, () -> db.has(ID));
		assertThrows(IOException.class, () -> db.openObject(ID));
		try (ObjectReader reader = db.newReader()) {
			IOException e = assertThrows(IOException.class,
					() -> reader.has(ID));
			assertFalse(e instanceof MissingObjectException);
			e = assertThrows(IOException.class, () -> reader.open(ID));
			assertFalse(e instanceof MissingObjectException);
		}
	}

	@Test
	public void openWithWrongTypeHintThrows() throws IOException {
		insert(ID, CONTENT);

		try (ObjectReader reader = db.newReader()) {
			assertThrows(IncorrectObjectTypeException.class,
					() -> reader.open(ID, OBJ_COMMIT));
		}
	}

	@Test
	public void readerResolvesOnlyCompleteIds() throws IOException {
		insert(ID, CONTENT);

		try (ObjectReader reader = db.newReader()) {
			assertEquals(Collections.singletonList(ID),
					new ArrayList<>(reader.resolve(
							AbbreviatedObjectId.fromObjectId(ID))));
			assertTrue(reader
					.resolve(AbbreviatedObjectId.fromString(
							ID.name().substring(0, 8)))
					.isEmpty());
		}
	}

	@Test
	public void approximateObjectCountIsUnknown() {
		assertEquals(-1, db.getApproximateObjectCount());
	}

	@Test
	public void acceptsOnlyBlobsAtOrAboveMinimumSize() {
		assertTrue(db.accepts(OBJ_BLOB, 100));
		assertTrue(db.accepts(OBJ_BLOB, 101));
		assertFalse(db.accepts(OBJ_BLOB, 99));
		assertFalse(db.accepts(OBJ_TREE, 1000));
	}

	@Test
	public void fromConfigWithoutStoresIsEmpty() throws IOException {
		assertTrue(ExternalObjectDatabase.fromConfig(new Config(),
				tmp.getRoot()).isEmpty());
	}

	@Test
	public void fromConfigCreatesStoresInOrder() throws Exception {
		File objects = tmp.newFolder("objects");
		File absolute = tmp.newFolder("absolute");
		Config cfg = config("[objectStore \"large\"]\n"
				+ "  path = " + absolute.getAbsolutePath() + "\n"
				+ "  minBlobSize = 1k\n"
				+ "[objectStore \"huge\"]\n"
				+ "  type = local\n"
				+ "  path = ../huge\n");

		List<ExternalObjectDatabase> dbs = ExternalObjectDatabase
				.fromConfig(cfg, objects);
		try {
			assertEquals(2, dbs.size());

			ExternalObjectDatabase large = dbs.get(0);
			assertEquals("large", large.getName());
			assertEquals(1024, large.getMinBlobSize());
			assertEquals(absolute, storeDirectory(large));

			ExternalObjectDatabase huge = dbs.get(1);
			assertEquals("huge", huge.getName());
			assertEquals(ExternalObjectDatabase.DEFAULT_MIN_BLOB_SIZE,
					huge.getMinBlobSize());
			assertEquals(new File(objects, "../huge"),
					storeDirectory(huge));
		} finally {
			dbs.forEach(ExternalObjectDatabase::close);
		}
	}

	@Test
	public void fromConfigRejectsMissingPath() throws Exception {
		Config cfg = config("[objectStore \"large\"]\n  minBlobSize = 1k\n");

		assertThrows(IOException.class,
				() -> ExternalObjectDatabase.fromConfig(cfg, tmp.getRoot()));
	}

	@Test
	public void fromConfigRejectsUnsupportedType() throws Exception {
		Config cfg = config(
				"[objectStore \"large\"]\n  type = s3\n  path = x\n");

		assertThrows(IOException.class,
				() -> ExternalObjectDatabase.fromConfig(cfg, tmp.getRoot()));
	}

	private void insert(ObjectId id, byte[] content) throws IOException {
		try (ExternalObjectInserter ins = db.newInserter()) {
			insert(ins, id, content);
		}
	}

	private static ObjectId insert(ExternalObjectInserter ins, ObjectId id,
			byte[] content) throws IOException {
		return ins.insert(id, OBJ_BLOB, content.length,
				new ByteArrayInputStream(content));
	}

	private static File storeDirectory(ExternalObjectDatabase edb) {
		return ((LocalDirectoryObjectStore) edb.getStore()).getDirectory();
	}

	private static Config config(String text) throws ConfigInvalidException {
		Config cfg = new Config();
		cfg.fromText(text);
		return cfg;
	}

	/** Store wrapper that counts calls and can fail lookups. */
	private static class CountingStore implements ExternalObjectStore {
		private final ExternalObjectStore delegate;

		int puts;

		int opens;

		boolean failFind;

		CountingStore(ExternalObjectStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public long find(AnyObjectId id) throws IOException {
			if (failFind) {
				throw new IOException("store unreachable");
			}
			return delegate.find(id);
		}

		@Override
		public InputStream open(AnyObjectId id) throws IOException {
			opens++;
			return delegate.open(id);
		}

		@Override
		public void put(AnyObjectId id, long size, InputStream content)
				throws IOException {
			puts++;
			delegate.put(id, size, content);
		}

		@Override
		public void delete(AnyObjectId id) throws IOException {
			delegate.delete(id);
		}

		@Override
		public void close() {
			delegate.close();
		}
	}
}
