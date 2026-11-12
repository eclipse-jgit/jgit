/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.InMemoryPack;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/** Tests placement added by mirroring; normal import is covered separately. */
public class ReceivedObjectMirroringTest extends RepositoryTestCase {
	private static final ObjectId BASE = ObjectId
			.fromString("ad471007bd7f5983d273b9584e5629230150fd54");

	private static final ObjectId CHILD = ObjectId
			.fromString("0ce70fe2ac6bd0a66e49c594921a89677c1d2f09");

	private static final ObjectId OTHER = ObjectId
			.fromString("1c6d48da91c5be0b0c9f90ca23cf1033d33eba3e");

	@Test
	public void mirrorsSelectedWholeAndDeltaWithoutRewritingMainPack()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, destination);
			InMemoryPack input = mixedPack();
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> id.equals(BASE) || id.equals(CHILD))) {
				PackParser parser = inserter.newPackParser(input.toInputStream());
				parser.setNeedNewObjectIds(false);
				parser.parse(NullProgressMonitor.INSTANCE);
				inserter.flush();

				assertTrue("selected whole object must be physically loose",
						destination.fileFor(BASE).isFile());
				assertTrue("selected delta must be physically loose",
						destination.fileFor(CHILD).isFile());
				assertFalse(destination.fileFor(OTHER).exists());
				assertArrayEquals(Constants.encode("0123456789"),
						main.open(BASE).getBytes());
				assertArrayEquals(Constants.encode("0123456789b"),
						main.open(CHILD).getBytes());
				Pack pack = main.getPacks().iterator().next();
				assertArrayEquals(input.toByteArray(),
						Files.readAllBytes(pack.getPackFile().toPath()));
				assertEquals(pack.getPackFile().length()
						+ new File(main.getPackDirectory(), pack.getPackFile()
								.getName().replace(".pack", ".idx")).length(),
						parser.getPackSize());
				assertEquals(2,
						parser.getReceivedPackStatistics().getNumWholeBlob());
				assertEquals(1,
						parser.getReceivedPackStatistics().getNumRefDelta());
				assertEquals(1,
						parser.getReceivedPackStatistics().getNumDeltaBlob());
			}
		}
	}

	@Test
	public void filterRunsAfterNormalWholeObjectCrcBookkeeping()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, destination);
			byte[] baseEntry = singleBlob(Constants.encode("0123456789"))
					.toByteArray();
			CRC32 crc = new CRC32();
			crc.update(baseEntry, 12, baseEntry.length - 12 - 20);
			PackedObjectInfo[] completion = new PackedObjectInfo[1];
			MirroringPackParser parser = new MirroringPackParser(main, destination,
					(id, type, size) -> {
						if (id.equals(BASE)) {
							assertEquals((int) crc.getValue(), completion[0].getCRC());
						}
						return false;
					}, mixedPack().toInputStream()) {
				@Override
				protected void onEndWholeObject(PackedObjectInfo info)
						throws IOException {
					completion[0] = info;
					super.onEndWholeObject(info);
				}
			};
			parser.parse(null);
		}
	}

	@Test
	public void mirrorsOnlyReceivedChildToExplicitNonFirstAlternate()
			throws Exception {
		try (FileRepository bases = createBareRepository();
				FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory baseDatabase = bases.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, baseDatabase, destination);
			try (ObjectInserter inserter = baseDatabase.newInserter()) {
				assertEquals(BASE, inserter.insert(Constants.OBJ_BLOB,
						Constants.encode("0123456789")));
				inserter.flush();
			}
			List<ObjectId> selected = new ArrayList<>();
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> {
						selected.add(id);
						return true;
					})) {
				PackParser parser = inserter.newPackParser(thinPack().toInputStream());
				parser.setAllowThin(true);
				parser.parse(null, null);
			}
			assertEquals(List.of(CHILD), selected);
			assertTrue(destination.fileFor(CHILD).isFile());
			assertFalse(destination.fileFor(BASE).exists());
			assertFalse(baseDatabase.fileFor(CHILD).exists());
			Pack imported = main.getPacks().iterator().next();
			assertTrue(imported.hasObject(BASE));
			assertTrue(imported.hasObject(CHILD));
		}
	}

	@Test
	public void createsLooseCopyDespiteExistingDestinationPack()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, destination);
			try (PackInserter inserter = destination.newPackInserter()) {
				inserter.insert(Constants.OBJ_BLOB, Constants.encode("0123456789"));
				inserter.flush();
			}
			assertTrue(destination.has(BASE));
			assertFalse(destination.fileFor(BASE).exists());
			destination.getConfig().setInt("core", null, "compression", 0);
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> true)) {
				inserter.newPackParser(singleBlob(Constants.encode("0123456789"))
						.toInputStream()).parse(null);
			}
			assertTrue(destination.fileFor(BASE).isFile());
			// core.compression=0: 18 header/content bytes plus 11 zlib bytes.
			assertEquals(29, destination.fileFor(BASE).length());
		}
	}

	@Test
	public void filtersEachIdOnceAcrossWholeAndDeltaRepresentations()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, destination);
			InMemoryPack pack = new InMemoryPack();
			pack.header(4);
			whole(pack, Constants.OBJ_BLOB, Constants.encode("0123456789"));
			refDelta(pack);
			whole(pack, Constants.OBJ_BLOB, Constants.encode("0123456789b"));
			whole(pack, Constants.OBJ_TREE, new byte[0]);
			pack.digest();
			Map<ObjectId, Integer> calls = new HashMap<>();
			List<ObjectId> order = new ArrayList<>();
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> {
						calls.merge(id, Integer.valueOf(1), Integer::sum);
						order.add(id);
						if (id.equals(CHILD)) {
							assertEquals(Constants.OBJ_BLOB, type);
							assertEquals(11, size);
						}
						return id.equals(CHILD);
					})) {
				inserter.newPackParser(pack.toInputStream()).parse(null);
			}
			ObjectId tree = ObjectId
					.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");
			assertEquals(List.of(BASE, CHILD, tree), order);
			assertEquals(Map.of(BASE, Integer.valueOf(1), CHILD, Integer.valueOf(1),
					tree, Integer.valueOf(1)), calls);
			assertTrue(destination.fileFor(CHILD).isFile());
			assertFalse(destination.fileFor(BASE).exists());
			assertFalse(destination.fileFor(tree).exists());
		}
	}

	@Test
	public void rejectsInconsistentResolvedMetadataWithoutReselecting()
			throws Exception {
		// A valid pack cannot cheaply supply a same-ID type/size collision.
		// Exercise the new completion-bookkeeping contract directly instead.
		for (boolean changedType : new boolean[] { false, true }) {
			try (FileRepository alternate = createBareRepository()) {
				ObjectDirectory destination = alternate.getObjectDatabase();
				List<ObjectId> calls = new ArrayList<>();
				MirroringPackParser parser = new MirroringPackParser(
						db.getObjectDatabase(), destination, (id, type, size) -> {
							calls.add(id);
							return false;
						}, new InMemoryPack().header(0).digest().toInputStream());
				PackedObjectInfo first = new PackedObjectInfo(BASE);
				first.setType(Constants.OBJ_BLOB);
				first.setFullSize(10);
				parser.onEndWholeObject(first);
				PackedObjectInfo later = new PackedObjectInfo(BASE);
				later.setType(changedType ? Constants.OBJ_TREE : Constants.OBJ_BLOB);
				later.setFullSize(changedType ? 10 : 11);
				assertThrows(IOException.class, () -> parser
						.onInflatedObjectData(later, later.getType(), new byte[0]));
				assertEquals(List.of(BASE), calls);
				parser.parse(null);
			}
		}
	}

	@Test
	public void destinationMustBeExplicitDistinctAndDirect() throws Exception {
		try (FileRepository direct = createBareRepository();
				FileRepository indirect = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = direct.getObjectDatabase();
			ObjectDirectory other = indirect.getObjectDatabase();
			configureAlternates(destination, other);
			configureAlternates(main, destination);
			assertThrows(IllegalArgumentException.class,
					() -> main.newMirroringInserter(main, (id, type, size) -> true));
			assertThrows(IllegalArgumentException.class,
					() -> main.newMirroringInserter(other, (id, type, size) -> true));
			assertThrows(NullPointerException.class,
					() -> main.newMirroringInserter(null, (id, type, size) -> true));
			assertThrows(NullPointerException.class,
					() -> main.newMirroringInserter(destination, null));
			assertTrue(destination.getPacks().isEmpty());
			assertTrue(other.getPacks().isEmpty());
		}
	}

	@Test
	public void matchesDirectDestinationByCanonicalDirectoryIdentity()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				ObjectDirectory alias = new ObjectDirectory(alternate.getConfig(),
						new File(alternate.getObjectDatabase().getDirectory(), "."),
						null, FS.DETECTED, null)) {
			ObjectDirectory main = db.getObjectDatabase();
			configureAlternates(main, alternate.getObjectDatabase());
			try (ObjectInserter inserter = main.newMirroringInserter(alias,
					(id, type, size) -> true)) {
				inserter.newPackParser(singleBlob(Constants.encode("0123456789"))
						.toInputStream()).parse(null);
			}
			assertTrue(alternate.getObjectDatabase().fileFor(BASE).isFile());
		}
	}

	@Test
	public void streamsLargeSelectedObjectWithBoundedBuffers() throws Exception {
		WindowCacheConfig original = new WindowCacheConfig();
		WindowCacheConfig streaming = new WindowCacheConfig();
		streaming.setStreamFileThreshold(1024);
		streaming.install();
		try (FileRepository alternate = createBareRepository();
				TrackingObjectDirectory main = new TrackingObjectDirectory(db);
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			configureAlternates(main, destination);
			main.requireStreaming = true;
			byte[] contents = new byte[512 * 1024];
			new Random(1).nextBytes(contents);
			ObjectId id;
			try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
				id = formatter.idFor(Constants.OBJ_BLOB, contents);
			}
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(objectId, type, size) -> true)) {
				inserter.newPackParser(singleBlob(contents).toInputStream())
						.parse(null);
			}
			assertEquals(contents.length, main.bytesRead);
			assertTrue(main.maxRead > 0);
			assertTrue("mirroring must not request full-object buffers",
					main.maxRead <= 8192);
			assertEquals(1, main.closedReaders);
			assertEquals(1, main.closedStreams);
			assertEquals(1, destination.closedInserters);
			try (ObjectStream stream = alternate.open(id).openStream()) {
				assertArrayEquals(contents, stream.readAllBytes());
			}
		} finally {
			original.install();
		}
	}

	@Test
	public void copyFailureKeepsMainAndPublishedLooseObjectsButReleasesOwnedKeep()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				FileRepository unusedAlternate = createBareRepository();
				TrackingObjectDirectory main = new TrackingObjectDirectory(db);
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			configureAlternates(main, unusedAlternate.getObjectDatabase(), destination);
			destination.failOnInsert = 2;
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> id.equals(BASE) || id.equals(CHILD))) {
				PackParser parser = inserter.newPackParser(mixedPack().toInputStream());
				parser.setLockMessage("owned receive keep");
				assertThrows(IOException.class, () -> parser.parse(null));
			}
			assertTrue(destination.fileFor(BASE).isFile());
			assertFalse(destination.fileFor(CHILD).exists());
			assertFalse(unusedAlternate.getObjectDatabase().fileFor(BASE).exists());
			assertFalse(unusedAlternate.getObjectDatabase().fileFor(CHILD).exists());
			assertTrue(unusedAlternate.getObjectDatabase().getPacks().isEmpty());
			assertEquals(1, main.getPacks().size());
			assertNoFilesEndingWith(main.getPackDirectory(), ".keep");
			assertNoFilesStartingWith(main.getDirectory(), "incoming_");
			assertNoFilesStartingWith(destination.getDirectory(), "noz");
			assertEquals(1, main.closedReaders);
			assertEquals(2, main.closedStreams);
			assertEquals(1, destination.closedInserters);
			assertArrayEquals(Constants.encode("0123456789b"),
					db.open(CHILD).getBytes());
		}
	}

	@Test
	public void copiedIdMismatchRejectsReceiveWithoutDeletingPublishedObjects()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				TrackingObjectDirectory main = new TrackingObjectDirectory(db);
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			configureAlternates(main, destination);
			main.corruptFirstRead = true;
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> true)) {
				PackParser parser = inserter.newPackParser(
						singleBlob(Constants.encode("0123456789")).toInputStream());
				parser.setLockMessage("owned keep for mismatched copy");
				assertThrows(IOException.class, () -> parser.parse(null));
			}
			// The read fault changed '0' to '1'; its canonical loose ID is valid.
			ObjectId changed = ObjectId
					.fromString("b8b54712d7d146956a8bfbe520cd217b8d9aaefc");
			assertFalse(destination.fileFor(BASE).exists());
			assertTrue(destination.fileFor(changed).isFile());
			assertArrayEquals(Constants.encode("1123456789"),
					alternate.open(changed).getBytes());
			assertArrayEquals(Constants.encode("0123456789"),
					db.open(BASE).getBytes());
			assertNoFilesEndingWith(main.getPackDirectory(), ".keep");
			assertEquals(1, main.closedReaders);
			assertEquals(1, main.closedStreams);
			assertEquals(1, destination.closedInserters);
		}
	}

	@Test
	public void copyFailureWithNullLockDoesNotUnlockForeignKeep() throws Exception {
		try (FileRepository alternate = createBareRepository();
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			ObjectDirectory main = db.getObjectDatabase();
			configureAlternates(main, destination);
			PackLock foreign;
			try (ObjectInserter inserter = main.newInserter()) {
				PackParser parser = inserter.newPackParser(mixedPack().toInputStream());
				parser.setLockMessage("foreign keep");
				foreign = parser.parse(null);
			}
			assertNotNull(foreign);
			try {
				destination.failOnInsert = 1;
				try (ObjectInserter inserter = main.newMirroringInserter(destination,
						(id, type, size) -> true)) {
					PackParser parser = inserter
							.newPackParser(mixedPack().toInputStream());
					parser.setLockMessage("not acquired by mirroring");
					assertThrows(IOException.class, () -> parser.parse(null));
				}
				assertEquals(1, main.getPacks().size());
				assertEquals(1, filesEndingWith(main.getPackDirectory(), ".keep").length);
			} finally {
				foreign.unlock();
			}
		}
	}

	@Test
	public void emptyImportReturnsNullLockWithoutMirroring() throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, destination);
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> {
						throw new AssertionError("empty input has no received IDs");
					})) {
				PackParser parser = inserter.newPackParser(
						new InMemoryPack().header(0).digest().toInputStream());
				parser.setLockMessage("empty input owns no keep");
				assertNull(parser.parse(null));
			}
			assertTrue(main.getPacks().isEmpty());
			assertTrue(destination.getPacks().isEmpty());
			assertNoFilesEndingWith(main.getPackDirectory(), ".keep");
		}
	}

	@Test
	public void cancellationDuringCopyLeavesCompletedCopiesAndMainPack()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				TrackingObjectDirectory main = new TrackingObjectDirectory(db);
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			configureAlternates(main, destination);
			CancellationMonitor monitor = new CancellationMonitor();
			main.cancelDuringLargeRead = monitor;
			byte[] large = new byte[32 * 1024];
			new Random(2).nextBytes(large);
			ObjectId largeId;
			try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
				largeId = formatter.idFor(Constants.OBJ_BLOB, large);
			}
			InMemoryPack pack = new InMemoryPack(large.length * 2).header(2);
			whole(pack, Constants.OBJ_BLOB, Constants.encode("0123456789"));
			whole(pack, Constants.OBJ_BLOB, large);
			pack.digest();
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> true)) {
				PackParser parser = inserter.newPackParser(pack.toInputStream());
				parser.setLockMessage("cancelled copy owns this keep");
				assertThrows(IOException.class,
						() -> parser.parse(NullProgressMonitor.INSTANCE, monitor));
			}
			assertTrue(destination.fileFor(BASE).isFile());
			assertFalse(destination.fileFor(largeId).exists());
			assertEquals(1, main.getPacks().size());
			assertNoFilesEndingWith(main.getPackDirectory(), ".keep");
			assertNoFilesStartingWith(destination.getDirectory(), "noz");
			assertEquals(1, main.closedReaders);
			assertEquals(2, main.closedStreams);
			assertEquals(1, destination.closedInserters);
		}
	}

	@Test
	public void receiveOnlyInserterDoesNotOwnDatabaseHandles() throws Exception {
		try (FileRepository alternate = createBareRepository();
				TrackingObjectDirectory main = new TrackingObjectDirectory(db);
				TrackingObjectDirectory destination = new TrackingObjectDirectory(
						alternate)) {
			configureAlternates(main, destination);
			try (ObjectInserter inserter = main.newMirroringInserter(destination,
					(id, type, size) -> true)) {
				assertThrows(UnsupportedOperationException.class,
						() -> inserter.insert(Constants.OBJ_BLOB, new byte[0]));
				try (ObjectReader reader = inserter.newReader()) {
					assertSame(inserter, reader.getCreatedFromInserter());
				}
				inserter.newPackParser(singleBlob(Constants.encode("0123456789"))
						.toInputStream()).parse(null);
				inserter.flush();
			}
			assertFalse(main.databaseClosed);
			assertFalse(destination.databaseClosed);
			assertArrayEquals(Constants.encode("0123456789"),
					db.open(BASE).getBytes());
		}
	}

	private static InMemoryPack singleBlob(byte[] contents) throws IOException {
		InMemoryPack pack = new InMemoryPack(contents.length * 2 + 1024).header(1);
		whole(pack, Constants.OBJ_BLOB, contents);
		return pack.digest();
	}

	private static void whole(InMemoryPack pack, int type, byte[] contents)
			throws IOException {
		int size = contents.length;
		int c = (type << 4) | (size & 15);
		size >>>= 4;
		while (size != 0) {
			pack.write(c | 0x80);
			c = size & 0x7f;
			size >>>= 7;
		}
		pack.write(c);
		pack.deflate(contents);
	}

	private static void refDelta(InMemoryPack pack) throws IOException {
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6);
		pack.copyRaw(BASE);
		pack.deflate(new byte[] { 10, 11, (byte) 0x90, 10, 1, 'b' });
	}

	private static InMemoryPack thinPack() throws IOException {
		InMemoryPack pack = new InMemoryPack().header(1);
		refDelta(pack);
		return pack.digest();
	}

	private static File[] filesEndingWith(File directory, String suffix) {
		File[] files = directory.listFiles((dir, name) -> name.endsWith(suffix));
		return files != null ? files : new File[0];
	}

	private static void assertNoFilesEndingWith(File directory, String suffix) {
		assertEquals(0, filesEndingWith(directory, suffix).length);
	}

	private static void assertNoFilesStartingWith(File directory, String prefix) {
		File[] files = directory.listFiles((dir, name) -> name.startsWith(prefix));
		assertTrue(files == null || files.length == 0);
	}

	private static class TrackingObjectDirectory extends ObjectDirectory {
		boolean requireStreaming;

		boolean databaseClosed;

		boolean corruptFirstRead;

		CancellationMonitor cancelDuringLargeRead;

		int maxRead;

		long bytesRead;

		int closedReaders;

		int closedStreams;

		int closedInserters;

		int failOnInsert;

		TrackingObjectDirectory(FileRepository repository) throws IOException {
			super(repository.getConfig(), repository.getObjectDatabase().getDirectory(),
					null, FS.DETECTED, null);
		}

		@Override
		public ObjectDirectoryInserter newInserter() {
			return new ObjectDirectoryInserter(this, getConfig()) {
				private int inserts;

				@Override
				ObjectId insert(int type, long size, InputStream in, boolean duplicate)
						throws IOException {
					if (++inserts == failOnInsert) {
						throw new IOException("injected loose copy failure");
					}
					return super.insert(type, size, in, duplicate);
				}

				@Override
				public void close() {
					closedInserters++;
					super.close();
				}
			};
		}

		@Override
		public ObjectReader newReader() {
			ObjectReader reader = super.newReader();
			return new ObjectReader.Filter() {
				@Override
				protected ObjectReader delegate() {
					return reader;
				}

				@Override
				public ObjectLoader open(AnyObjectId id, int type) throws IOException {
					ObjectLoader loader = reader.open(id, type);
					if (requireStreaming) {
						assertTrue(loader.isLarge());
					}
					return new ObjectLoader() {
						@Override
						public int getType() {
							return loader.getType();
						}

						@Override
						public long getSize() {
							return loader.getSize();
						}

						@Override
						public byte[] getCachedBytes() {
							throw new AssertionError("copy must use openStream()");
						}

						@Override
						public ObjectStream openStream() throws IOException {
							return new ObjectStream.Filter(loader.getType(),
									loader.getSize(), loader.openStream()) {
								@Override
								public int read(byte[] b, int off, int len)
										throws IOException {
									maxRead = Math.max(maxRead, len);
									int n = super.read(b, off, len);
									if (n > 0) {
										if (corruptFirstRead) {
											b[off] ^= 1;
											corruptFirstRead = false;
										}
										bytesRead += n;
										if (cancelDuringLargeRead != null
												&& loader.getSize() > 8192) {
											cancelDuringLargeRead.cancelled = true;
										}
									}
									return n;
								}

								@Override
								public void close() throws IOException {
									closedStreams++;
									super.close();
								}
							};
						}
					};
				}

				@Override
				public void close() {
					closedReaders++;
					super.close();
				}
			};
		}

		@Override
		public void close() {
			databaseClosed = true;
			super.close();
		}
	}

	private static class CancellationMonitor implements ProgressMonitor {
		boolean cancelled;

		@Override
		public void start(int totalTasks) {
			// No reporting.
		}

		@Override
		public void beginTask(String title, int totalWork) {
			// No reporting.
		}

		@Override
		public void update(int completed) {
			// No reporting.
		}

		@Override
		public void endTask() {
			// No reporting.
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public void showDuration(boolean enabled) {
			// No reporting.
		}
	}

	private static InMemoryPack mixedPack() throws IOException {
		InMemoryPack pack = new InMemoryPack();
		pack.header(3);
		pack.write(Constants.OBJ_BLOB << 4 | 10);
		pack.deflate(Constants.encode("0123456789"));
		pack.write(Constants.OBJ_BLOB << 4 | 12);
		pack.deflate(Constants.encode("not selected"));
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6);
		pack.copyRaw(BASE);
		// Copy the ten-byte base and append 'b'.
		pack.deflate(new byte[] { 10, 11, (byte) 0x90, 10, 1, 'b' });
		return pack.digest();
	}

	private static void configureAlternates(ObjectDirectory main,
			ObjectDirectory... alternates) throws IOException {
		StringBuilder paths = new StringBuilder();
		for (ObjectDirectory alternate : alternates) {
			paths.append(alternate.getDirectory().getCanonicalPath())
					.append('\n');
		}
		Files.writeString(new File(main.getDirectory(),
				Constants.INFO_ALTERNATES).toPath(), paths);
	}
}
