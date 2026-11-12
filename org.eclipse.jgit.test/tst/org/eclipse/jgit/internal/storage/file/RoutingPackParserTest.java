/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.InMemoryPack;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

/**
 * Tests exclusive placement, immediate-base policy and operation ownership.
 */
public class RoutingPackParserTest extends RepositoryTestCase {
	private static final ObjectId A = ObjectId
			.fromString("ad471007bd7f5983d273b9584e5629230150fd54");

	private static final ObjectId B = ObjectId
			.fromString("0ce70fe2ac6bd0a66e49c594921a89677c1d2f09");

	private static final ObjectId C = idFor(Constants.encode("0123456789bc"));

	@Test
	public void exclusivePlacementMatrixPreservesOnlyKnownRetainedImmediateBases()
			throws Exception {
		List<Set<ObjectId>> selections = List.of(Set.of(), Set.of(A), Set.of(B),
				Set.of(C), Set.of(A, C), Set.of(A, B, C));
		// Literal encoded-kind expectations for A, B, C; zero means absent.
		int[][] kinds = { { 3, 7, 7 }, { 0, 3, 7 }, { 3, 0, 3 },
				{ 3, 7, 0 }, { 0, 3, 0 }, { 0, 0, 0 } };
		for (int caseNumber = 0; caseNumber < selections.size(); caseNumber++) {
			Set<ObjectId> selected = selections.get(caseNumber);
			try (FileRepository repository = createBareRepository();
					FileRepository unused = createBareRepository();
					FileRepository alternate = createBareRepository();
					TrackingDestination destination = new TrackingDestination(alternate)) {
				ObjectDirectory main = repository.getObjectDatabase();
				main.getConfig().setInt("pack", null, "minBytesForObjSizeIndex", 0);
				destination.main = main;
				ObjectDirectory other = unused.getObjectDatabase();
				// Reordering lookup alternates must not change the explicit target.
				configureAlternates(main, caseNumber % 2 == 0
						? List.of(other, destination) : List.of(destination, other));
				Map<ObjectId, Integer> calls = new HashMap<>();
				InMemoryPack input = chain();
				PackParser parser;
				PackLock lock;
				try (ObjectInserter inserter = main.newRoutingInserter(destination,
						(id, type, size) -> {
							calls.merge(id, Integer.valueOf(1), Integer::sum);
							assertEquals(Constants.OBJ_BLOB, type);
							assertEquals(id.equals(A) ? 10 : id.equals(B) ? 11 : 12, size);
							return selected.contains(id);
						})) {
					parser = inserter.newPackParser(input.toInputStream());
					parser.setNeedNewObjectIds(true);
					parser.setLockMessage("retained delta routing");
					lock = parser.parse(null);
					inserter.flush();
					inserter.flush();
				}
				try {
					assertEquals(Map.of(A, Integer.valueOf(1), B, Integer.valueOf(1),
							C, Integer.valueOf(1)), calls);
					assertEquals(3, parser.getObjectCount());
					assertEquals(3, parser.getNewObjectIds().size());
					assertEquals(input.toByteArray().length,
							parser.getReceivedPackStatistics().getNumBytesRead());
					assertEquals(1, parser.getReceivedPackStatistics().getNumWholeBlob());
					assertEquals(1, parser.getReceivedPackStatistics().getNumRefDelta());
					assertEquals(1, parser.getReceivedPackStatistics().getNumOfsDelta());
					assertEquals(1, destination.flushes);
					assertEquals(1, destination.closedInserters);
					assertEquals(selected.size(), destination.inserts);
					assertTrue(other.getPacks().isEmpty());
					int retained = 3 - selected.size();
					assertEquals(retained == 0 ? 0 : 1, main.getPacks().size());
					Pack output = retained == 0 ? null : main.getPacks().iterator().next();
					if (output == null) {
						assertNull(lock);
						assertEquals(0, parser.getPackSize());
					} else {
						assertNotNull(lock);
						assertEquals(retained, output.getIndex().getObjectCount());
						assertEquals(output.getPackFile().length()
								+ new PackFile(output.getPackFile()).create(PackExt.INDEX).length(),
								parser.getPackSize());
					}
					for (ObjectId id : List.of(A, B, C)) {
						assertEquals(selected.contains(id), destination.fileFor(id).isFile());
						assertFalse(other.fileFor(id).exists());
						if (output != null) {
							assertEquals(!selected.contains(id), output.hasObject(id));
							if (!selected.contains(id)) {
								assertEquals(kinds[caseNumber][id.equals(A) ? 0 : id.equals(B) ? 1 : 2],
										storedType(output, id));
							}
						}
						assertArrayEquals(Constants.encode(id.equals(A) ? "0123456789"
								: id.equals(B) ? "0123456789b" : "0123456789bc"),
								repository.open(id).getBytes());
					}
					assertNoTemps(main);
				} finally {
					if (lock != null) {
						lock.unlock();
					}
				}
				assertNoKeeps(main);
			}
		}
	}

	@Test
	public void emptyInputProducesNoMainPackOrLock() throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> {
						throw new AssertionError("Empty input has no received objects");
					})) {
				PackParser parser = inserter.newPackParser(new InMemoryPack().header(0)
						.digest().toInputStream());
				parser.setLockMessage("empty routing");
				assertNull(parser.parse(null));
				assertEquals(0, parser.getPackSize());
			}
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void thinBaseIsLookedUpButNeverSelectedOrAppended() throws Exception {
		try (FileRepository bases = createBareRepository();
				FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory baseDatabase = bases.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(baseDatabase, destination));
			try (ObjectInserter inserter = baseDatabase.newInserter()) {
				assertEquals(A, inserter.insert(Constants.OBJ_BLOB,
						Constants.encode("0123456789")));
			}
			List<ObjectId> calls = new ArrayList<>();
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> {
						calls.add(id);
						return false;
					})) {
				InMemoryPack input = new InMemoryPack().header(1);
				refDelta(input, A, 10, 'b');
				PackParser parser = inserter.newPackParser(input.digest().toInputStream());
				parser.setAllowThin(true);
				parser.setNeedNewObjectIds(true);
				parser.setNeedBaseObjectIds(true);
				parser.parse(null);
				assertEquals(1, parser.getObjectCount());
				assertTrue(parser.getNewObjectIds().contains(B));
				assertFalse(parser.getNewObjectIds().contains(A));
				assertTrue(parser.getBaseObjectIds().contains(A));
			}
			assertEquals(List.of(B), calls);
			Pack output = main.getPacks().iterator().next();
			assertTrue(output.hasObject(B));
			assertFalse(output.hasObject(A));
			assertEquals(Constants.OBJ_BLOB, storedType(output, B));
			assertFalse(destination.fileFor(A).exists());
			assertFalse(destination.fileFor(B).exists());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void thinIdentityDeltaCannotUseItselfAsReceivedBase() throws Exception {
		for (boolean selected : new boolean[] { true, false }) {
			try (FileRepository repository = createBareRepository();
					FileRepository bases = createBareRepository();
					FileRepository alternate = createBareRepository()) {
				ObjectDirectory main = repository.getObjectDatabase();
				ObjectDirectory external = bases.getObjectDatabase();
				ObjectDirectory destination = alternate.getObjectDatabase();
				configureAlternates(main, List.of(external, destination));
				byte[] contents = Constants.encode("0123456789");
				try (ObjectInserter inserter = external.newInserter()) {
					assertEquals(A, inserter.insert(Constants.OBJ_BLOB, contents));
					inserter.flush();
				}
				// The received result is identical to its external base, not an
				// already-known received base for preserving this representation.
				InMemoryPack input = new InMemoryPack().header(1);
				input.write(Constants.OBJ_REF_DELTA << 4 | 4).copyRaw(A)
						.deflate(new byte[] { 10, 10, (byte) 0x90, 10 }).digest();
				assertEquals(65, input.toByteArray().length);
				List<ObjectId> calls = new ArrayList<>();
				try (ObjectInserter inserter = main.newRoutingInserter(destination,
						(id, type, size) -> {
							calls.add(id);
							assertEquals(Constants.OBJ_BLOB, type);
							assertEquals(10, size);
							return selected;
						})) {
					PackParser parser = inserter.newPackParser(input.toInputStream());
					parser.setAllowThin(true);
					parser.setNeedNewObjectIds(true);
					parser.setNeedBaseObjectIds(true);
					assertNull(parser.parse(null));
					assertEquals(1, parser.getObjectCount());
					assertTrue(parser.getNewObjectIds().contains(A));
					assertTrue(parser.getBaseObjectIds().contains(A));
					if (selected) {
						assertEquals(0, parser.getPackSize());
					}
				}
				assertEquals(List.of(A), calls);
				assertEquals(selected, destination.fileFor(A).isFile());
				assertArrayEquals(contents, repository.open(A).getBytes());
				assertArrayEquals(contents, bases.open(A).getBytes());
				assertEquals(selected ? 0 : 1, main.getPacks().size());
				if (!selected) {
					Pack output = main.getPacks().iterator().next();
					assertEquals(1, output.getIndex().getObjectCount());
					assertEquals(Constants.OBJ_BLOB, storedType(output, A));
					// Independently import without alternates so external visibility
					// cannot mask a non-self-contained identity delta in main output.
					try (FileRepository independent = createBareRepository();
							ObjectInserter inserter = independent.newObjectInserter();
							InputStream bytes = Files.newInputStream(output.getPackFile().toPath())) {
						PackParser parser = inserter.newPackParser(bytes);
						parser.parse(null);
						assertEquals(1, parser.getObjectCount());
						assertArrayEquals(contents, independent.open(A).getBytes());
					}
				}
				assertNoTemps(main);
				assertNoKeeps(main);
			}
		}
	}

	@Test
	public void duplicateRepresentationsFilterOnceAndEmitOnlyChosenObject()
			throws Exception {
		for (boolean selectDuplicate : new boolean[] { true, false }) {
			try (FileRepository repository = createBareRepository();
					FileRepository alternate = createBareRepository();
					TrackingDestination destination = new TrackingDestination(alternate)) {
				ObjectDirectory main = repository.getObjectDatabase();
				destination.main = main;
				configureAlternates(main, List.of(destination));
				InMemoryPack input = new InMemoryPack().header(4);
				whole(input, Constants.encode("0123456789"));
				refDelta(input, A, 10, 'b');
				whole(input, Constants.encode("0123456789b"));
				refDelta(input, B, 11, 'c');
				input.digest();
				List<ObjectId> calls = new ArrayList<>();
				try (ObjectInserter inserter = main.newRoutingInserter(destination,
						(id, type, size) -> {
							calls.add(id);
							return selectDuplicate && id.equals(B);
						})) {
					PackParser parser = inserter.newPackParser(input.toInputStream());
					parser.setNeedNewObjectIds(true);
					parser.parse(null);
					assertEquals(4, parser.getObjectCount());
					// Preserve inherited incoming ID bookkeeping, including duplicates.
					assertEquals(4, parser.getNewObjectIds().size());
					assertTrue(parser.getNewObjectIds().contains(A));
					assertTrue(parser.getNewObjectIds().contains(B));
					assertTrue(parser.getNewObjectIds().contains(C));
				}
				assertEquals(List.of(A, B, C), calls);
				assertEquals(selectDuplicate ? 1 : 0, destination.inserts);
				Pack output = main.getPacks().iterator().next();
				assertEquals(selectDuplicate ? 2 : 3, output.getIndex().getObjectCount());
				assertEquals(!selectDuplicate, output.hasObject(B));
				if (!selectDuplicate) {
					// Whole B was reconstructed first during INPUT scan, even though
					// a preceding delta B was encoded earlier in the incoming bytes.
					assertEquals(Constants.OBJ_BLOB, storedType(output, B));
				}
				assertEquals(selectDuplicate ? Constants.OBJ_BLOB : Constants.OBJ_REF_DELTA,
						storedType(output, C));
				assertNoTemps(main);
			}
		}
	}

	@Test
	public void firstResolvedDeltaKeepsItsSourceAndBaseAcrossDifferentBaseDuplicates()
			throws Exception {
		ObjectId otherBase = idFor(Constants.encode("012345678x"));
		for (ObjectId selected : List.of(A, otherBase)) {
			try (FileRepository repository = createBareRepository();
					FileRepository alternate = createBareRepository()) {
				ObjectDirectory main = repository.getObjectDatabase();
				ObjectDirectory destination = alternate.getObjectDatabase();
				configureAlternates(main, List.of(destination));
				InMemoryPack input = new InMemoryPack().header(5);
				whole(input, Constants.encode("0123456789"));
				whole(input, Constants.encode("012345678x"));
				refDelta(input, A, 10, 'b');
				// A different base and different instructions reconstruct the same B.
				input.write(Constants.OBJ_REF_DELTA << 4 | 7).copyRaw(otherBase)
						.deflate(new byte[] { 10, 11, (byte) 0x90, 9, 2, '9', 'b' });
				refDelta(input, B, 11, 'c');
				List<ObjectId> calls = new ArrayList<>();
				try (ObjectInserter inserter = main.newRoutingInserter(destination,
						(id, type, size) -> {
							calls.add(id);
							return id.equals(selected);
						})) {
					PackParser parser = inserter.newPackParser(input.digest().toInputStream());
					parser.parse(null);
					assertEquals(5, parser.getObjectCount());
				}
				assertEquals(List.of(A, otherBase, B, C), calls);
				Pack output = main.getPacks().iterator().next();
				assertEquals(3, output.getIndex().getObjectCount());
				assertFalse(output.hasObject(selected));
				assertEquals(selected.equals(A) ? Constants.OBJ_BLOB : Constants.OBJ_REF_DELTA,
						storedType(output, B));
				assertEquals(Constants.OBJ_REF_DELTA, storedType(output, C));
				assertArrayEquals(Constants.encode("0123456789bc"), repository.open(C).getBytes());
				assertNoTemps(main);
			}
		}
	}

	@Test
	public void unknownReceivedBaseExpandsImmediatelyEvenWhenItLaterBecomesRetained()
			throws Exception {
		try (FileRepository bases = createBareRepository();
				FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory external = bases.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(external, destination));
			ObjectId otherBase;
			try (ObjectInserter inserter = external.newInserter()) {
				assertEquals(A, inserter.insert(Constants.OBJ_BLOB, Constants.encode("0123456789")));
				otherBase = inserter.insert(Constants.OBJ_BLOB, Constants.encode("012345678x"));
			}
			// The existing resolver visits external A (ad47...) before c80d...;
			// A's received delta representation remains unresolved during B's callback.
			InMemoryPack input = new InMemoryPack().header(3);
			refDelta(input, A, 10, 'b');
			refDelta(input, B, 11, 'c');
			input.write(Constants.OBJ_REF_DELTA << 4 | 6).copyRaw(otherBase)
					.deflate(new byte[] { 10, 10, (byte) 0x90, 9, 1, '9' });
			List<ObjectId> calls = new ArrayList<>();
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> {
						calls.add(id);
						return false;
					})) {
				PackParser parser = inserter.newPackParser(input.digest().toInputStream());
				parser.setAllowThin(true);
				parser.parse(null);
			}
			assertEquals(List.of(B, C, A), calls);
			Pack output = main.getPacks().iterator().next();
			assertEquals(3, output.getIndex().getObjectCount());
			assertFalse(output.hasObject(otherBase));
			assertEquals(Constants.OBJ_BLOB, storedType(output, A));
			assertEquals(Constants.OBJ_BLOB, storedType(output, B));
			assertEquals(Constants.OBJ_REF_DELTA, storedType(output, C));
			assertArrayEquals(Constants.encode("0123456789bc"), db.open(C).getBytes());
			assertNoTemps(main);
		}
	}

	@Test
	public void inconsistentDuplicateMetadataIsRejectedWithoutReselecting()
			throws Exception {
		// Reuse the mirroring test's direct completion approach: a valid pack
		// cannot cheaply supply a same-ID type/size collision.
		try (FileRepository alternate = createBareRepository()) {
			for (boolean changedType : new boolean[] { false, true }) {
				List<ObjectId> calls = new ArrayList<>();
				try (RoutingPackParser parser = new RoutingPackParser(
						db.getObjectDatabase(), alternate.getObjectDatabase(),
						(id, type, size) -> {
							calls.add(id);
							return false;
						}, new ByteArrayInputStream(new byte[0]))) {
					parser.onBeginWholeObject(12, Constants.OBJ_BLOB, 10);
					parser.onObjectHeader(PackParser.Source.INPUT, new byte[] { 0x3a }, 0, 1);
					PackedObjectInfo first = new PackedObjectInfo(A);
					first.setOffset(12);
					first.setType(Constants.OBJ_BLOB);
					first.setFullSize(10);
					parser.onEndWholeObject(first);
					PackedObjectInfo later = new PackedObjectInfo(A);
					later.setType(changedType ? Constants.OBJ_TREE : Constants.OBJ_BLOB);
					later.setFullSize(changedType ? 10 : 11);
					assertThrows(IOException.class, () -> parser.onInflatedObjectData(
							later, later.getType(), new byte[0]));
					assertEquals(List.of(A), calls);
				}
			}
		}
	}

	@Test
	public void historicalMainAndDestinationPacksDoNotDefeatExclusiveLoosePlacement()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			for (ObjectDirectory database : List.of(main, destination)) {
				try (PackInserter inserter = database.newPackInserter()) {
					inserter.insert(Constants.OBJ_BLOB, Constants.encode("0123456789"));
					inserter.flush();
				}
			}
			Pack historical = main.getPacks().iterator().next();
			byte[] original = Files.readAllBytes(historical.getPackFile().toPath());
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> id.equals(A))) {
				inserter.newPackParser(chain().toInputStream()).parse(null);
			}
			assertTrue(destination.fileFor(A).isFile());
			assertArrayEquals(original, Files.readAllBytes(historical.getPackFile().toPath()));
			assertEquals(2, main.getPacks().size());
			for (Pack pack : main.getPacks()) {
				if (pack.hasObject(B)) {
					assertFalse(pack.hasObject(A));
					assertTrue(pack.hasObject(C));
				}
			}
			assertNoTemps(main);
		}
	}

	@Test
	public void closingUnusedParserReleasesReaderOnceWithoutOwningDatabases()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				CountingMain main = new CountingMain(db)) {
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> false);
			PackParser unused = inserter.newPackParser(new InMemoryPack().header(0)
					.digest().toInputStream());
			assertEquals(0, main.closedReaders);
			inserter.flush();
			inserter.close();
			inserter.close();
			assertEquals(1, main.closedReaders);
			assertFalse(main.databaseClosed);
			assertThrows(IllegalStateException.class, () -> unused.parse(null));
			assertThrows(IllegalStateException.class,
					() -> inserter.newPackParser(new ByteArrayInputStream(new byte[0])));
			assertNoTemps(main);
			try (ObjectInserter ordinary = destination.newInserter()) {
				ordinary.insert(Constants.OBJ_BLOB,
						Constants.encode("caller still owns destination"));
			}
		}
	}

	@Test
	public void postParseCancellationRetainsLooseOrphanButAbortsMainOutput()
			throws Exception {
		for (boolean cancelReceiving : new boolean[] { true, false }) {
			try (FileRepository repository = createBareRepository();
					FileRepository alternate = createBareRepository();
					TrackingDestination destination = new TrackingDestination(alternate)) {
				ObjectDirectory main = repository.getObjectDatabase();
				destination.main = main;
				configureAlternates(main, List.of(destination));
				CancellationMonitor monitor = new CancellationMonitor();
				destination.cancelAfterInsert = monitor;
				InMemoryPack input = new InMemoryPack().header(2);
				whole(input, Constants.encode("0123456789"));
				whole(input, Constants.encode("0123456789b"));
				input.digest();
				try (ObjectInserter inserter = main.newRoutingInserter(destination,
						(id, type, size) -> id.equals(A))) {
					PackParser parser = inserter.newPackParser(input.toInputStream());
					IOException failure = assertThrows(IOException.class,
							() -> parser.parse(cancelReceiving ? monitor : null,
									cancelReceiving ? null : monitor));
					assertEquals(JGitText.get().downloadCancelled, failure.getMessage());
				}
				assertTrue(destination.fileFor(A).isFile());
				assertTrue(main.getPacks().isEmpty());
				assertNoTemps(main);
				assertNoKeeps(main);
			}
		}
	}

	@Test
	public void cancellationDuringCopiedPayloadPassAbortsTemporaryMainOutput()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			CancellationMonitor monitor = new CancellationMonitor() {
				private int ended;

				@Override
				public void endTask() {
					ended++;
				}

				@Override
				public boolean isCancelled() {
					File[] outputs = main.getDirectory().listFiles((dir, name) ->
							name.startsWith("routing_output_") && name.endsWith(".pack"));
					// Cancel after copying A's header, before its payload can be written.
					return ended == 1 && outputs != null && outputs.length == 1
							&& outputs[0].length() == 13;
				}
			};
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> false)) {
				PackParser parser = inserter.newPackParser(chain().toInputStream());
				IOException failure = assertThrows(IOException.class,
						() -> parser.parse(null, monitor));
				assertEquals(JGitText.get().downloadCancelled, failure.getMessage());
			}
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void inheritedDeltaValidationFailurePreservesPrimaryAndPublishedLooseObject()
			throws Exception {
		try (FileRepository alternate = createBareRepository();
				CountingMain main = new CountingMain(db)) {
			main.failReaderClose = true;
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> id.equals(B))) {
				PackParser parser = inserter.newPackParser(chain().toInputStream());
				parser.setObjectChecker(new ObjectChecker() {
					@Override
					public void check(AnyObjectId id, int type, byte[] data)
							throws CorruptObjectException {
						if (C.equals(id)) {
							throw new CorruptObjectException("rejected child");
						}
						super.check(id, type, data);
					}
				});
				CorruptObjectException failure = assertThrows(CorruptObjectException.class,
						() -> parser.parse(null));
				assertTrue(failure.getMessage().contains("rejected child"));
				assertEquals(1, failure.getSuppressed().length);
				assertEquals("reader close failed", failure.getSuppressed()[0].getMessage());
			}
			assertEquals(1, main.closedReaders);
			assertTrue(destination.fileFor(B).isFile());
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void postValidationReplayCorruptionCleansAlreadyStagedMainOutput()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			CancellationMonitor monitor = new CancellationMonitor() {
				private int ended;

				@Override
				public void endTask() {
					if (++ended != 2) {
						return;
					}
					File[] archives = main.getDirectory()
							.listFiles((dir, name) -> name.endsWith(".spool"));
					assertNotNull(archives);
					assertEquals(1, archives.length);
					try (RandomAccessFile archive = new RandomAccessFile(archives[0], "rw")) {
						archive.seek(14); // Whole A's valid zlib level hint, not its contents.
						archive.write(0x01);
					} catch (IOException failure) {
						throw new AssertionError(failure);
					}
				}
			};
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> id.equals(A))) {
				PackParser parser = inserter.newPackParser(chain().toInputStream());
				assertThrows(IOException.class, () -> parser.parse(monitor));
			}
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void alternateFailureHasNoFallbackAndKeepsAlreadyPublishedLooseObjects()
			throws Exception {
		try (FileRepository unused = createBareRepository();
				FileRepository alternate = createBareRepository();
				TrackingDestination destination = new TrackingDestination(alternate)) {
			ObjectDirectory main = db.getObjectDatabase();
			destination.main = main;
			ObjectDirectory other = unused.getObjectDatabase();
			configureAlternates(main, List.of(other, destination));
			destination.failOnInsert = 2;
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> true)) {
				assertThrows(IOException.class,
						() -> inserter.newPackParser(chain().toInputStream()).parse(null));
			}
			assertTrue(destination.fileFor(B).isFile());
			assertFalse(destination.fileFor(C).exists());
			for (ObjectId id : List.of(A, B, C)) {
				assertFalse(other.fileFor(id).exists());
				assertFalse(main.fileFor(id).exists());
			}
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
			assertNoKeeps(main);
		}
	}

	@Test
	public void publicationFailureAfterAlternateSuccessPreservesLooseOrphans()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, List.of(destination));
			Files.delete(main.getPackDirectory().toPath());
			Files.writeString(main.getPackDirectory().toPath(), "blocked pack directory");
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> id.equals(B))) {
				assertThrows(IOException.class,
						() -> inserter.newPackParser(chain().toInputStream()).parse(null));
			}
			assertTrue(destination.fileFor(B).isFile());
			assertEquals("blocked pack directory",
					Files.readString(main.getPackDirectory().toPath()));
			assertTrue(main.getPacks().isEmpty());
			assertNoTemps(main);
		}
	}

	private static InMemoryPack chain() throws IOException {
		InMemoryPack input = new InMemoryPack().header(3);
		whole(input, Constants.encode("0123456789"));
		refDelta(input, A, 10, 'b');
		// B starts at offset 31; its one-byte header, base ID and zlib payload
		// occupy 35 bytes. C's OFS_DELTA therefore refers back by 35.
		input.write(Constants.OBJ_OFS_DELTA << 4 | 6);
		input.write(35);
		input.deflate(new byte[] { 11, 12, (byte) 0x90, 11, 1, 'c' });
		return input.digest();
	}

	private static void whole(InMemoryPack input, byte[] contents) throws IOException {
		int size = contents.length;
		int next = (Constants.OBJ_BLOB << 4) | (size & 15);
		size >>>= 4;
		while (size != 0) {
			input.write(next | 0x80);
			next = size & 0x7f;
			size >>>= 7;
		}
		input.write(next);
		input.deflate(contents);
	}

	private static void refDelta(InMemoryPack input, ObjectId base, int size, char last)
			throws IOException {
		input.write(Constants.OBJ_REF_DELTA << 4 | 6);
		input.copyRaw(base);
		input.deflate(new byte[] { (byte) size, (byte) (size + 1), (byte) 0x90,
				(byte) size, 1, (byte) last });
	}

	private static ObjectId idFor(byte[] contents) {
		try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
			return formatter.idFor(Constants.OBJ_BLOB, contents);
		}
	}

	private static int storedType(Pack pack, ObjectId id) throws IOException {
		try (RandomAccessFile bytes = new RandomAccessFile(pack.getPackFile(), "r")) {
			bytes.seek(pack.getIndex().findOffset(id));
			return (bytes.read() >> 4) & 7;
		}
	}

	private static void configureAlternates(ObjectDirectory main,
			List<ObjectDirectory> alternates) throws IOException {
		StringBuilder paths = new StringBuilder();
		for (ObjectDirectory alternate : alternates) {
			paths.append(alternate.getDirectory().getCanonicalPath()).append('\n');
		}
		Files.writeString(new File(main.getDirectory(), Constants.INFO_ALTERNATES)
				.toPath(), paths);
	}

	private static void assertNoTemps(ObjectDirectory main) {
		String[] files = main.getDirectory()
				.list((dir, name) -> name.startsWith("routing_"));
		assertNotNull(files);
		assertEquals(0, files.length);
	}

	private static void assertNoKeeps(ObjectDirectory main) {
		String[] keeps = main.getPackDirectory()
				.list((dir, name) -> name.endsWith(".keep"));
		assertTrue(keeps == null || keeps.length == 0);
	}

	private static class TrackingDestination extends ObjectDirectory {
		ObjectDirectory main;

		int inserts;

		int flushes;

		int closedInserters;

		int failOnInsert;

		CancellationMonitor cancelAfterInsert;

		TrackingDestination(FileRepository repository) throws IOException {
			super(repository.getConfig(), repository.getObjectsDirectory(), null,
					repository.getFS(), null);
		}

		@Override
		public ObjectDirectoryInserter newInserter() {
			return new ObjectDirectoryInserter(this, getConfig()) {
				@Override
				ObjectId insert(int type, long size, InputStream input, boolean duplicate)
						throws IOException {
					assertTrue("Routing must force physical loose insertion", duplicate);
					if (++inserts == failOnInsert) {
						throw new IOException("alternate failed");
					}
					ObjectId id = super.insert(type, size, new FilterInputStream(input) {
						@Override
						public int read(byte[] data, int offset, int count) throws IOException {
							assertTrue("Routing must stream loose writes", count <= 8192);
							return super.read(data, offset, count);
						}
					}, duplicate);
					if (cancelAfterInsert != null) {
						cancelAfterInsert.cancelled = true;
					}
					return id;
				}

				@Override
				public void flush() throws IOException {
					assertTrue("Alternate must flush before main registration",
							main.getPacks().isEmpty());
					String[] packs = main.getPackDirectory()
							.list((dir, name) -> name.endsWith(".pack"));
					assertTrue("Alternate must flush before main visibility",
							packs == null || packs.length == 0);
					flushes++;
					super.flush();
				}

				@Override
				public void close() {
					closedInserters++;
					super.close();
				}
			};
		}
	}

	private static class CountingMain extends ObjectDirectory {
		int closedReaders;

		boolean failReaderClose;

		boolean databaseClosed;

		CountingMain(FileRepository repository) throws IOException {
			super(repository.getConfig(), repository.getObjectsDirectory(), null,
					repository.getFS(), null);
		}

		@Override
		public ObjectDatabase newCachedDatabase() {
			return this;
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
				public void close() {
					closedReaders++;
					reader.close();
					if (failReaderClose) {
						throw new IllegalStateException("reader close failed");
					}
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
		public void start(int tasks) {
			// No progress output.
		}

		@Override
		public void beginTask(String title, int work) {
			// No progress output.
		}

		@Override
		public void update(int completed) {
			// No progress output.
		}

		@Override
		public void endTask() {
			// No progress output.
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public void showDuration(boolean enabled) {
			// No progress output.
		}
	}
}
