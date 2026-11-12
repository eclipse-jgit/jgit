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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.transport.InMemoryPack;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

/** Tests copied/streamed output, source integrity and owned publication. */
public class RoutedPackWriterTest extends RepositoryTestCase {
	@Test
	public void copiedWholeRetainsOriginalHeaderAndCompressedPayload()
			throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		main.getConfig().setInt("pack", null, "compression", 0);
		InMemoryPack input = new InMemoryPack().header(1);
		// Legal nonminimal size encoding makes header preservation observable.
		input.write((Constants.OBJ_BLOB << 4) | 0x80 | 10).write(0);
		input.deflate(Constants.encode("0123456789")).digest();
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory destination = alternate.getObjectDatabase();
			Files.writeString(new File(main.getDirectory(), Constants.INFO_ALTERNATES)
					.toPath(), destination.getDirectory().getCanonicalPath() + '\n');
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> false)) {
				inserter.newPackParser(input.toInputStream()).parse(null);
			}
			Pack output = main.getPacks().iterator().next();
			assertArrayEquals(input.toByteArray(),
					Files.readAllBytes(output.getPackFile().toPath()));
			assertArrayEquals(Constants.encode("0123456789"),
					db.open(idFor(Constants.encode("0123456789"))).getBytes());
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void copiedRefAndMultibyteOfsDeltasKeepPayloadsWithForwardBases()
			throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		main.getConfig().setInt("pack", null, "minBytesForObjSizeIndex", 0);
		CopyFixture fixture = new CopyFixture();
		Pack pack;
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			// The OFS header uses two base-distance bytes. Both REF bases are
			// forward in this output; closure is checked only after all appends.
			writer.appendCopied(fixture.child, spool);
			writer.appendCopied(fixture.delta, spool);
			writer.appendCopied(fixture.base, spool);
			assertNull(writer.publish(null));
			pack = writer.getPack();
		}
		byte[] output = Files.readAllBytes(pack.getPackFile().toPath());
		assertEquals(Constants.OBJ_REF_DELTA << 4 | 6, output[12] & 0xff);
		byte[] baseId = new byte[20];
		fixture.delta.id.copyRawTo(baseId, 0);
		assertArrayEquals(baseId, Arrays.copyOfRange(output, 13, 33));
		assertArrayEquals(Arrays.copyOfRange(fixture.bytes, fixture.childPayload,
				fixture.childEnd), Arrays.copyOfRange(output, 33, 47));
		// Original REF header + payload and whole nonminimal header + payload
		// remain byte-identical, with the unrelated neighboring entry omitted.
		assertArrayEquals(Arrays.copyOfRange(fixture.bytes, fixture.deltaOffset,
				fixture.deltaEnd), Arrays.copyOfRange(output, 47, 82));
		assertArrayEquals(Arrays.copyOfRange(fixture.bytes, 12, fixture.deltaOffset),
				Arrays.copyOfRange(output, 82, 102));
		assertEquals(122, output.length);
		assertEquals(3, pack.getIndex().getObjectCount());
		assertEquals(12, pack.getIndexedObjectSize(fixture.child.id));
		assertArrayEquals(Constants.encode("0123456789bc"),
				db.open(fixture.child.id).getBytes());
		try (FileRepository independent = createBareRepository();
				ObjectInserter inserter = independent.newObjectInserter();
				InputStream input = new ByteArrayInputStream(output)) {
			PackParser parser = inserter.newPackParser(input);
			parser.parse(null);
			assertEquals(3, parser.getObjectCount());
			for (PackedObjectInfo object : parser.getSortedObjectList(null)) {
				assertEquals(object.getCRC() & 0xffffffffL,
						pack.getIndex().findCRC32(object));
			}
		}
		assertNoRoutingTemps(main);
	}

	@Test
	public void capturesOnlyInputRangesForMultibyteOfsHeader() throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory destination = alternate.getObjectDatabase();
			Files.writeString(new File(main.getDirectory(), Constants.INFO_ALTERNATES)
					.toPath(), destination.getDirectory().getCanonicalPath() + '\n');
			try (ObjectInserter inserter = main.newRoutingInserter(destination,
					(id, type, size) -> size == 256)) {
				inserter.newPackParser(new ByteArrayInputStream(fixture.bytes)).parse(null);
			}
			Pack pack = main.getPacks().iterator().next();
			byte[] output = Files.readAllBytes(pack.getPackFile().toPath());
			assertEquals(122, output.length);
			assertArrayEquals(Arrays.copyOfRange(fixture.bytes, 12, fixture.deltaEnd),
					Arrays.copyOfRange(output, 12, 67));
			assertEquals(0x76, output[67] & 0xff);
			byte[] baseId = new byte[20];
			fixture.delta.id.copyRawTo(baseId, 0);
			assertArrayEquals(baseId, Arrays.copyOfRange(output, 68, 88));
			assertArrayEquals(Arrays.copyOfRange(fixture.bytes, fixture.childPayload,
					fixture.childEnd), Arrays.copyOfRange(output, 88, 102));
			assertEquals(3, pack.getIndex().getObjectCount());
			assertArrayEquals(Constants.encode("0123456789bc"), db.open(fixture.child.id).getBytes());
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void changedOriginalHeaderOrPayloadPoisonsCopiedOutput()
			throws Exception {
		for (boolean header : new boolean[] { true, false }) {
			CopyFixture fixture = new CopyFixture();
			ObjectDirectory main = db.getObjectDatabase();
			try (IncomingPackSpool spool = new IncomingPackSpool(main);
					RoutedPackWriter writer = new RoutedPackWriter(main)) {
				spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
				writer.appendCopied(fixture.base, spool);
				try (RandomAccessFile archive = new RandomAccessFile(spool.getFile(), "rw")) {
					// Change only OFS base encoding or its valid zlib level hint.
					archive.seek(header ? fixture.childPayload - 1 : fixture.childPayload + 1);
					archive.write(header ? 0x31 : 0x01);
				}
				assertThrows(IOException.class,
						() -> writer.appendCopied(fixture.child, spool));
				assertThrows(IOException.class, () -> writer.publish("cannot publish"));
			}
			assertTrue(main.getPacks().isEmpty());
			assertEquals(0, main.getPackDirectory().list().length);
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void copiedDeltaRequiresFinalReceivedBaseAndUnselectedMembership()
			throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			writer.appendCopied(fixture.delta, spool);
			assertThrows(IOException.class, () -> writer.publish("missing base"));
		}
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			ReceivedPackEntry selected = new ReceivedPackEntry(fixture.base.replaySource(),
					fixture.base.source, null, true);
			assertThrows(IOException.class, () -> writer.appendCopied(selected, spool));
			assertThrows(IOException.class, () -> writer.publish("selected base"));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void copiedRangeRejectsOverflowNeighborBytesAndTruncatedArchive()
			throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		for (long length : new long[] { Long.MAX_VALUE, 19, fixture.bytes.length }) {
			try (IncomingPackSpool spool = new IncomingPackSpool(main);
					RoutedPackWriter writer = new RoutedPackWriter(main)) {
				spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
				ReceivedPackEntry.Source invalid = new ReceivedPackEntry.Source(12,
						Constants.OBJ_BLOB, 10, new byte[] { (byte) 0xba, 0 }, 14,
						length, fixture.base.source.crc);
				ReceivedPackEntry entry = new ReceivedPackEntry(fixture.base.replaySource(),
						invalid, null, false);
				assertThrows(IOException.class, () -> writer.appendCopied(entry, spool));
				assertThrows(IOException.class, () -> writer.publish("invalid source range"));
			}
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void copiedDeltaCycleCannotPublishEvenWhenEveryBaseIsPresent()
			throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			ReceivedPackEntry cyclic = new ReceivedPackEntry(fixture.delta.replaySource(),
					fixture.delta.source, fixture.child.id, false);
			writer.appendCopied(cyclic, spool);
			writer.appendCopied(fixture.child, spool);
			assertThrows(IOException.class, () -> writer.publish("cyclic output"));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void duplicateCopiedEntryCannotReplaceFirstDependency() throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			writer.appendCopied(fixture.delta, spool);
			ReceivedPackEntry later = new ReceivedPackEntry(fixture.delta.replaySource(),
					fixture.child.source, ObjectId.zeroId(), false);
			writer.appendCopied(later, spool);
			writer.appendCopied(fixture.base, spool);
			assertNull(writer.publish(null));
			assertEquals(2, writer.getPack().getIndex().getObjectCount());
			assertArrayEquals(Constants.encode("0123456789b"),
					db.open(fixture.delta.id).getBytes());
		}
		assertNoRoutingTemps(main);
	}

	@Test
	public void inconsistentDuplicateCopiedMetadataPoisonsOutput() throws Exception {
		CopyFixture fixture = new CopyFixture();
		ObjectDirectory main = db.getObjectDatabase();
		try (IncomingPackSpool spool = new IncomingPackSpool(main);
				RoutedPackWriter writer = new RoutedPackWriter(main)) {
			spool.append(fixture.bytes, 0, fixture.bytes.length - 20);
			writer.appendCopied(fixture.base, spool);
			PackedObjectInfo changed = fixture.base.replaySource();
			changed.setFullSize(11);
			ReceivedPackEntry later = new ReceivedPackEntry(changed,
					fixture.base.source, null, false);
			assertThrows(IOException.class, () -> writer.appendCopied(later, spool));
			assertThrows(IOException.class, () -> writer.publish("inconsistent duplicate"));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void streamsWholeEntriesAndKeepsPublicationProtected() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		main.getConfig().setInt("pack", null, "minBytesForObjSizeIndex", 0);
		byte[] contents = new byte[8193];
		Arrays.fill(contents, (byte) 'a');
		ObjectId blob = idFor(contents);
		ObjectId tree = ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");
		PackLock lock;
		Pack pack;
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			try (InputStream bounded = new FilterInputStream(new ByteArrayInputStream(contents)) {
				@Override
				public int read(byte[] data, int offset, int count) throws IOException {
					assertTrue("Whole writer must use bounded input reads", count <= 8192);
					return super.read(data, offset, count);
				}
			}) {
				writer.appendWhole(blob, Constants.OBJ_BLOB, contents.length, bounded);
			}
			writer.appendWhole(tree, Constants.OBJ_TREE, 0,
					new ByteArrayInputStream(new byte[0]));
			assertTrue(main.getPacks().isEmpty());
			lock = writer.publish("protect receive");
			assertNotNull(lock);
			pack = writer.getPack();
			assertTrue(new PackFile(pack.getPackFile()).create(PackExt.KEEP).isFile());
			assertEquals(pack.getPackFile().length()
					+ new PackFile(pack.getPackFile()).create(PackExt.INDEX).length(),
					writer.getPackSize());
			assertTrue(pack.hasObjectSizeIndex());
			assertEquals(contents.length, pack.getIndexedObjectSize(blob));
		}
		assertTrue(new PackFile(pack.getPackFile()).create(PackExt.KEEP).isFile());
		try {
			assertArrayEquals(contents, db.open(blob).getBytes());
			try (FileRepository independent = createBareRepository();
					ObjectInserter inserter = independent.newObjectInserter();
					InputStream input = Files.newInputStream(pack.getPackFile().toPath())) {
				PackParser parser = inserter.newPackParser(input);
				parser.parse(null);
				assertEquals(2, parser.getObjectCount());
				for (PackedObjectInfo object : parser.getSortedObjectList(null)) {
					assertEquals(object.getCRC() & 0xffffffffL,
							pack.getIndex().findCRC32(object));
					assertEquals(object.equals(blob) ? contents.length : 0,
							object.getFullSize());
				}
			}
		} finally {
			lock.unlock();
		}
		assertFalse(new PackFile(pack.getPackFile()).create(PackExt.KEEP).exists());
		assertNoRoutingTemps(main);
	}

	@Test
	public void invalidWholeIdPreventsPublicationAndCloseAborts() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			assertThrows(IOException.class,
					() -> writer.appendWhole(ObjectId.zeroId(), Constants.OBJ_BLOB,
							7, new ByteArrayInputStream(Constants.encode("content"))));
			assertThrows(IOException.class, () -> writer.publish("must not publish"));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void wholeStreamWithTrailingDataCannotPublish() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		ObjectId id = idFor(Constants.encode("content"));
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			assertThrows(IOException.class, () -> writer.appendWhole(id,
					Constants.OBJ_BLOB, 7,
					new ByteArrayInputStream(Constants.encode("content!"))));
			assertThrows(IOException.class, () -> writer.publish("must not publish"));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
	}

	@Test
	public void closeWithoutPublishNeverInstallsMainOutput() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			byte[] content = Constants.encode("content");
			writer.appendWhole(idFor(content), Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
		}
		assertTrue(main.getPacks().isEmpty());
		assertNoRoutingTemps(main);
		try (RoutedPackWriter empty = new RoutedPackWriter(main)) {
			assertNull(empty.publish("empty"));
			assertEquals(0, empty.getPackSize());
		}
	}

	@Test
	public void foreignKeepCannotBeOverwrittenOrReleasedOnFailure() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		byte[] content = Constants.encode("content");
		ObjectId id = idFor(content);
		PackFile finalPack = packFor(main, id);
		PackFile foreignKeep = finalPack.create(PackExt.KEEP);
		Files.writeString(foreignKeep.toPath(), "foreign owner\n");
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			writer.appendWhole(id, Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
			assertThrows(IOException.class, () -> writer.publish("our owner"));
		}
		assertEquals("foreign owner\n", Files.readString(foreignKeep.toPath()));
		assertFalse(finalPack.exists());
		assertFalse(finalPack.create(PackExt.INDEX).exists());
		assertNoRoutingTemps(main);
	}

	@Test
	public void existingFinalPackIndexAndKeepAreNeverReplaced() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		byte[] content = Constants.encode("content");
		ObjectId id = idFor(content);
		Pack pack;
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			writer.appendWhole(id, Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
			assertNull(writer.publish(null));
			pack = writer.getPack();
		}
		assertNotNull(pack);
		PackFile finalPack = new PackFile(pack.getPackFile());
		PackFile finalIndex = finalPack.create(PackExt.INDEX);
		PackFile foreignKeep = finalPack.create(PackExt.KEEP);
		byte[] priorPack = Files.readAllBytes(finalPack.toPath());
		byte[] priorIndex = Files.readAllBytes(finalIndex.toPath());
		Files.writeString(foreignKeep.toPath(), "foreign owner\n");
		main.getConfig().setInt("pack", null, "compression", 0);
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			writer.appendWhole(id, Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
			assertNull(writer.publish("new receive"));
			assertEquals(priorPack.length + priorIndex.length, writer.getPackSize());
		}
		assertArrayEquals(priorPack, Files.readAllBytes(finalPack.toPath()));
		assertArrayEquals(priorIndex, Files.readAllBytes(finalIndex.toPath()));
		assertEquals("foreign owner\n", Files.readString(foreignKeep.toPath()));
		assertNoRoutingTemps(main);
	}

	@Test
	public void existingOrphanIndexIsNotReplaced() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		byte[] content = Constants.encode("content");
		ObjectId id = idFor(content);
		PackFile finalPack = packFor(main, id);
		PackFile foreignIndex = finalPack.create(PackExt.INDEX);
		Files.writeString(foreignIndex.toPath(), "foreign index");
		try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
			writer.appendWhole(id, Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
			assertThrows(IOException.class, () -> writer.publish("new receive"));
		}
		assertEquals("foreign index", Files.readString(foreignIndex.toPath()));
		assertFalse(finalPack.exists());
		assertFalse(finalPack.create(PackExt.KEEP).exists());
		assertNoRoutingTemps(main);
	}

	@Test
	public void foreignTemporarySidecarFilesAreNotReplaced() throws Exception {
		for (PackExt extension : List.of(PackExt.INDEX, PackExt.OBJECT_SIZE_INDEX)) {
			assertForeignTemporarySidecarPreserved(extension, false);
		}
	}

	@Test
	public void foreignTemporarySidecarDirectoriesAreNotDeleted() throws Exception {
		for (PackExt extension : List.of(PackExt.INDEX, PackExt.OBJECT_SIZE_INDEX)) {
			assertForeignTemporarySidecarPreserved(extension, true);
		}
	}

	@Test
	public void openFailureRemovesOwnedPackIndexesAndKeep() throws Exception {
		ObjectDirectory main = db.getObjectDatabase();
		main.getConfig().setInt("pack", null, "minBytesForObjSizeIndex", 0);
		try (ObjectDirectory failing = new ObjectDirectory(db.getConfig(),
				db.getObjectsDirectory(), null, db.getFS(), null) {
			@Override
			public Pack openPack(File path) throws IOException {
				PackFile finalPack = new PackFile(path);
				assertTrue(finalPack.create(PackExt.INDEX).isFile());
				assertTrue(finalPack.create(PackExt.OBJECT_SIZE_INDEX).isFile());
				assertTrue(finalPack.create(PackExt.KEEP).isFile());
				throw new IOException("open failed");
			}
		}; RoutedPackWriter writer = new RoutedPackWriter(failing)) {
			byte[] content = Constants.encode("content");
			writer.appendWhole(idFor(content), Constants.OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
			IOException failure = assertThrows(IOException.class,
					() -> writer.publish("owned keep"));
			assertEquals("open failed", failure.getMessage());
		}
		assertEquals(0, main.getPackDirectory().list().length);
		assertNoRoutingTemps(main);
	}

	private void assertForeignTemporarySidecarPreserved(PackExt extension,
			boolean directory) throws Exception {
		try (FileRepository repository = createBareRepository()) {
			ObjectDirectory main = repository.getObjectDatabase();
			if (extension == PackExt.OBJECT_SIZE_INDEX) {
				main.getConfig().setInt("pack", null, "minBytesForObjSizeIndex", 0);
			}
			byte[] content = Constants.encode("content");
			ObjectId id = idFor(content);
			File foreignSidecar = null;
			try {
				IOException publicationFailure = null;
				Pack pack;
				try (RoutedPackWriter writer = new RoutedPackWriter(main)) {
					writer.appendWhole(id, Constants.OBJ_BLOB, content.length,
							new ByteArrayInputStream(content));
					File[] temporaryPacks = main.getDirectory()
							.listFiles((dir, name) -> name.startsWith("routing_output_")
									&& name.endsWith(".pack"));
					assertNotNull(temporaryPacks);
					assertEquals(1, temporaryPacks.length);
					String name = temporaryPacks[0].getName();
					foreignSidecar = new File(main.getDirectory(),
							name.substring(0, name.lastIndexOf('.')) + '.'
									+ extension.getExtension());
					if (directory) {
						Files.createDirectory(foreignSidecar.toPath());
					} else {
						Files.writeString(foreignSidecar.toPath(), "foreign owner\n");
					}
					try {
						assertNull(writer.publish(null));
					} catch (IOException failure) {
						publicationFailure = failure;
					}
					pack = writer.getPack();
				}
				assertTrue("Foreign temporary ." + extension.getExtension()
						+ " must survive publication and close",
						directory ? foreignSidecar.isDirectory() : foreignSidecar.isFile());
				if (directory) {
					String[] children = foreignSidecar.list();
					assertNotNull(children);
					assertEquals(0, children.length);
				} else {
					assertEquals("foreign owner\n",
							Files.readString(foreignSidecar.toPath()));
				}
				assertNull("Foreign sidecars must not prevent publication",
						publicationFailure);
				assertNotNull(pack);
				assertTrue(new PackFile(pack.getPackFile()).create(PackExt.INDEX).isFile());
				assertArrayEquals(content, repository.open(id).getBytes());
				if (extension == PackExt.OBJECT_SIZE_INDEX) {
					assertTrue(pack.hasObjectSizeIndex());
					assertEquals(content.length, pack.getIndexedObjectSize(id));
				}
			} finally {
				if (foreignSidecar != null) {
					Files.deleteIfExists(foreignSidecar.toPath());
				}
			}
			assertNoRoutingTemps(main);
		}
	}

	private static class CopyFixture {
		final byte[] bytes;

		final ReceivedPackEntry base;

		final ReceivedPackEntry delta;

		final ReceivedPackEntry child;

		final int deltaOffset = 32;

		final int deltaEnd = 67;

		final int childPayload = 339;

		final int childEnd = 353;

		CopyFixture() throws IOException {
			ObjectId a = idFor(Constants.encode("0123456789"));
			ObjectId b = idFor(Constants.encode("0123456789b"));
			ObjectId c = idFor(Constants.encode("0123456789bc"));
			InMemoryPack input = new InMemoryPack(4096).header(4);
			input.write(0xba).write(0).deflate(Constants.encode("0123456789"));
			assertEquals(deltaOffset, input.toByteArray().length);
			input.write(0x76).copyRaw(a)
					.deflate(new byte[] { 10, 11, (byte) 0x90, 10, 1, 'b' });
			assertEquals(deltaEnd, input.toByteArray().length);
			byte[] neighbor = new byte[256];
			for (int i = 0; i < neighbor.length; i++) {
				neighbor[i] = (byte) i;
			}
			input.write(0xb0).write(0x10).deflate(neighbor);
			assertEquals(336, input.toByteArray().length);
			// 336 - 32 = 304; OFS encoding is 0x81, 0x30.
			input.write(0x66).write(0x81).write(0x30)
					.deflate(new byte[] { 11, 12, (byte) 0x90, 11, 1, 'c' }).digest();
			bytes = input.toByteArray();
			assertEquals(childEnd + 20, bytes.length);
			base = entry(a, null, 10, 12, deltaOffset, 2, Constants.OBJ_BLOB, 10);
			delta = entry(b, a, 11, deltaOffset, deltaEnd, 21, Constants.OBJ_REF_DELTA, 6);
			// Snapshot the mutable ID: PackParser's deltaBase may be a mutable info.
			MutableObjectId mutableBase = new MutableObjectId();
			mutableBase.fromObjectId(b);
			child = entry(c, mutableBase, 12, 336, childEnd, 3, Constants.OBJ_OFS_DELTA, 6);
			mutableBase.clear();
		}

		private ReceivedPackEntry entry(ObjectId id, AnyObjectId baseId, int fullSize,
				int offset, int end, int headerLength, int encodedType, int declaredSize) {
			CRC32 original = new CRC32();
			original.update(bytes, offset, end - offset);
			PackedObjectInfo info = new PackedObjectInfo(id);
			info.setType(Constants.OBJ_BLOB);
			info.setFullSize(fullSize);
			info.setOffset(offset);
			info.setCRC((int) original.getValue());
			byte[] header = Arrays.copyOfRange(bytes, offset, offset + headerLength);
			ReceivedPackEntry.Source source = new ReceivedPackEntry.Source(offset,
					encodedType, declaredSize, header, offset + headerLength,
					end - offset - headerLength, info.getCRC());
			header[0] = 0; // Metadata must not borrow the parser's mutable header buffer.
			return new ReceivedPackEntry(info, source, baseId, false);
		}
	}

	private static ObjectId idFor(byte[] content) {
		try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
			return formatter.idFor(Constants.OBJ_BLOB, content);
		}
	}

	private static PackFile packFor(ObjectDirectory main, ObjectId id) {
		MessageDigest name = Constants.newMessageDigest();
		byte[] raw = new byte[Constants.OBJECT_ID_LENGTH];
		id.copyRawTo(raw, 0);
		name.update(raw);
		return new PackFile(main.getPackDirectory(), ObjectId.fromRaw(name.digest()),
				PackExt.PACK);
	}

	private static void assertNoRoutingTemps(ObjectDirectory main) {
		String[] files = main.getDirectory()
				.list((dir, name) -> name.startsWith("routing_"));
		assertNotNull(files);
		assertEquals(0, files.length);
	}
}
