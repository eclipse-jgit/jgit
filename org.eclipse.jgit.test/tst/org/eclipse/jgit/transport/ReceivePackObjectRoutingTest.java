/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.Test;

/** Tests the receive inserter hook's placement and failure contracts. */
public class ReceivePackObjectRoutingTest extends RepositoryTestCase {
	private static final ObjectId BASE = ObjectId
			.fromString("ad471007bd7f5983d273b9584e5629230150fd54");

	private static final ObjectId CHILD = ObjectId
			.fromString("0ce70fe2ac6bd0a66e49c594921a89677c1d2f09");

	private static final ObjectId WHOLE = ObjectId
			.fromString("1c6d48da91c5be0b0c9f90ca23cf1033d33eba3e");

	private static final ObjectId EXTERNAL = ObjectId
			.fromString("7074bbccf568292d27cc8fd52958804dd366ad48");

	private static final ObjectId THIN_CHILD = ObjectId
			.fromString("b206f8227cf099726af75b5cbe685a2f93b37cac");

	private static final ObjectId SAFE_CHILD = idFor(Constants.OBJ_BLOB,
			Constants.encode("012345678k"));

	private static final String REF = "refs/tags/mirrored";

	@Test
	public void factoryMirrorsWithResolvedSizeFilterAndNormalReceiveReporting()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternate(main, destination);
			List<ObjectId> calls = new ArrayList<>();
			ReceivePack receiver = new ReceivePack(db) {
				@Override
				protected ObjectInserter newObjectInserter() throws IOException {
					return main.newMirroringInserter(destination, (id, type, size) -> {
						calls.add(id);
						return type == Constants.OBJ_BLOB && size >= 11;
					});
				}
			};
			receiver.setRetainParsedObjectIDs(false);
			receiver.setPreReceiveHook((rp, commands) -> {
				File[] keeps = main.getPackDirectory()
						.listFiles((dir, name) -> name.endsWith(".keep"));
				assertEquals(1, keeps.length);
				assertTrue(destination.fileFor(CHILD).isFile());
			});
			InMemoryPack pack = mixedPack();
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			receive(receiver, pack, response);
			assertTrue("factory must select a physical loose whole copy",
					destination.fileFor(WHOLE).isFile());
			assertTrue(destination.fileFor(CHILD).isFile());
			assertFalse(destination.fileFor(BASE).exists());
			assertEquals(List.of(BASE, WHOLE, CHILD), calls);
			assertEquals(CHILD, db.resolve(REF));
			PacketLineIn status = new PacketLineIn(
					new ByteArrayInputStream(response.toByteArray()));
			assertEquals("unpack ok", status.readString());
			assertEquals("ok " + REF, status.readString());
			assertTrue(PacketLineIn.isEnd(status.readString()));
			Pack imported = main.getPacks().iterator().next();
			assertArrayEquals(pack.toByteArray(),
					Files.readAllBytes(imported.getPackFile().toPath()));
			assertEquals(imported.getPackFile().length()
					+ new File(main.getPackDirectory(), imported.getPackFile()
							.getName().replace(".pack", ".idx")).length(),
					receiver.getPackSize());
			assertEquals(2, receiver.getReceivedPackStatistics().getNumWholeBlob());
			assertEquals(1, receiver.getReceivedPackStatistics().getNumDeltaBlob());
			assertNoKeeps(main);
		}
	}

	@Test
	public void exclusiveRoutingAcceptsCommitWithMixedAndThinObjects()
			throws Exception {
		try (FileRepository bases = createBareRepository();
				FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory baseDatabase = bases.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternates(main, baseDatabase, destination);
			try (ObjectInserter inserter = baseDatabase.newInserter()) {
				assertEquals(EXTERNAL, inserter.insert(Constants.OBJ_BLOB,
						Constants.encode("ABCDEFGHIJ")));
				inserter.flush();
			}
			TreeFormatter tree = new TreeFormatter();
			tree.append("large", FileMode.REGULAR_FILE, CHILD);
			tree.append("small", FileMode.REGULAR_FILE, BASE);
			tree.append("smallDelta", FileMode.REGULAR_FILE, SAFE_CHILD);
			tree.append("thin", FileMode.REGULAR_FILE, THIN_CHILD);
			tree.append("whole", FileMode.REGULAR_FILE, WHOLE);
			byte[] treeBytes = tree.toByteArray();
			ObjectId treeId = idFor(Constants.OBJ_TREE, treeBytes);
			CommitBuilder commit = new CommitBuilder();
			commit.setTreeId(treeId);
			commit.setAuthor(author);
			commit.setCommitter(committer);
			commit.setMessage("exclusive routing integration");
			byte[] commitBytes = commit.build();
			ObjectId commitId = idFor(Constants.OBJ_COMMIT, commitBytes);
			InMemoryPack input = commitPack(treeBytes, commitBytes);
			List<ObjectId> calls = new ArrayList<>();
			ReceivePack receiver = new ReceivePack(db) {
				@Override
				protected ObjectInserter newObjectInserter() throws IOException {
					return main.newRoutingInserter(destination, (id, type, size) -> {
						calls.add(id);
						if (id.equals(CHILD)) {
							assertEquals(Constants.OBJ_BLOB, type);
							assertEquals(11, size);
						}
						return type == Constants.OBJ_BLOB && size >= 11;
					});
				}
			};
			receiver.setCheckReceivedObjects(true);
			receiver.setRetainParsedObjectIDs(false);
			receiver.setPreReceiveHook((rp, commands) -> {
				assertEquals(1, main.getPackDirectory()
						.listFiles((dir, name) -> name.endsWith(".keep")).length);
				assertTrue(destination.fileFor(CHILD).isFile());
				assertTrue(destination.fileFor(WHOLE).isFile());
			});
			String ref = "refs/heads/routed";
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			receive(receiver, input, commitId, ref, response);
			assertEquals(commitId, db.resolve(ref));
			assertEquals(7, calls.size());
			assertEquals(Set.of(BASE, CHILD, WHOLE, THIN_CHILD, SAFE_CHILD, treeId, commitId),
					Set.copyOf(calls));
			PacketLineIn status = new PacketLineIn(
					new ByteArrayInputStream(response.toByteArray()));
			assertEquals("unpack ok", status.readString());
			assertEquals("ok " + ref, status.readString());
			assertTrue(PacketLineIn.isEnd(status.readString()));
			assertEquals(1, main.getPacks().size());
			Pack output = main.getPacks().iterator().next();
			assertEquals(5, output.getIndex().getObjectCount());
			assertFalse(output.hasObject(CHILD));
			assertFalse(output.hasObject(WHOLE));
			assertFalse(output.hasObject(EXTERNAL));
			assertTrue(output.hasObject(SAFE_CHILD));
			assertEquals(Constants.OBJ_REF_DELTA, storedType(output, SAFE_CHILD));
			assertArrayEquals(Constants.encode("012345678k"), db.open(SAFE_CHILD).getBytes());
			assertFalse(destination.fileFor(SAFE_CHILD).exists());
			for (ObjectId id : List.of(BASE, THIN_CHILD, treeId, commitId)) {
				assertTrue(output.hasObject(id));
				assertEquals(id.equals(treeId) ? Constants.OBJ_TREE
						: id.equals(commitId) ? Constants.OBJ_COMMIT : Constants.OBJ_BLOB,
						storedType(output, id));
				assertFalse(destination.fileFor(id).exists());
			}
			assertFalse(baseDatabase.fileFor(CHILD).exists());
			assertFalse(baseDatabase.fileFor(WHOLE).exists());
			assertArrayEquals(Constants.encode("0123456789b"),
					db.open(CHILD).getBytes());
			assertArrayEquals(Constants.encode("not selected"),
					db.open(WHOLE).getBytes());
			assertArrayEquals(Constants.encode("ABCDEFGHIk"),
					db.open(THIN_CHILD).getBytes());
			assertArrayEquals(commitBytes, db.open(commitId).getBytes());
			assertEquals(output.getPackFile().length()
					+ new File(main.getPackDirectory(), output.getPackFile()
							.getName().replace(".pack", ".idx")).length(),
					receiver.getPackSize());
			ReceivedPackStatistics statistics = receiver.getReceivedPackStatistics();
			assertEquals(input.toByteArray().length, statistics.getNumBytesRead());
			assertEquals(2, statistics.getNumWholeBlob());
			// Keep inherited accounting: the resolver reports two root blob chains.
			assertEquals(2, statistics.getNumDeltaBlob());
			assertEquals(3, statistics.getNumRefDelta());
			assertEquals(1, statistics.getNumWholeTree());
			assertEquals(1, statistics.getNumWholeCommit());
			assertNoKeeps(main);
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void copiedArchiveCorruptionRejectsRefsAndPreservesPublishedLooseObject()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternate(main, destination);
			ReceivePack receiver = new ReceivePack(db) {
				@Override
				protected ObjectInserter newObjectInserter() throws IOException {
					return main.newRoutingInserter(destination, (id, type, size) -> {
						if (!id.equals(CHILD)) {
							return false;
						}
						// Delta classification follows successful input checksum validation.
						// Corrupt retained WHOLE, which is not needed by delta reconstruction.
						File[] archives = main.getDirectory()
								.listFiles((dir, name) -> name.endsWith(".spool"));
						assertEquals(1, archives.length);
						try (RandomAccessFile archive = new RandomAccessFile(archives[0], "rw")) {
							archive.seek(33); // WHOLE's valid zlib level hint; contents unchanged.
							archive.write(0x01);
						} catch (IOException failure) {
							throw new AssertionError(failure);
						}
						return true;
					});
				}
			};
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			assertThrows(UnpackException.class, () -> receive(receiver, mixedPack(), response));
			assertNull(db.resolve(REF));
			PacketLineIn status = new PacketLineIn(
					new ByteArrayInputStream(response.toByteArray()));
			assertTrue(status.readString().startsWith("unpack error "));
			assertEquals("ng " + REF + " n/a (unpacker error)", status.readString());
			assertTrue(PacketLineIn.isEnd(status.readString()));
			assertTrue(destination.fileFor(CHILD).isFile());
			assertTrue(main.getPacks().isEmpty());
			assertNoKeeps(main);
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void rejectedRoutedRefReleasesKeepWithoutRollingBackPublishedObjects()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternate(main, destination);
			ReceivePack receiver = new ReceivePack(db) {
				@Override
				protected ObjectInserter newObjectInserter() throws IOException {
					return main.newRoutingInserter(destination,
							(id, type, size) -> id.equals(CHILD));
				}
			};
			receiver.setPreReceiveHook((rp, commands) -> {
				assertEquals(1, main.getPackDirectory()
						.listFiles((dir, name) -> name.endsWith(".keep")).length);
				for (ReceiveCommand command : commands) {
					command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
							"application rejection");
				}
			});
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			receive(receiver, mixedPack(), response);
			assertNull(db.resolve(REF));
			PacketLineIn status = new PacketLineIn(
					new ByteArrayInputStream(response.toByteArray()));
			assertEquals("unpack ok", status.readString());
			assertEquals("ng " + REF + " application rejection", status.readString());
			assertTrue(PacketLineIn.isEnd(status.readString()));
			assertTrue(destination.fileFor(CHILD).isFile());
			assertEquals(1, main.getPacks().size());
			Pack output = main.getPacks().iterator().next();
			assertEquals(2, output.getIndex().getObjectCount());
			assertFalse(output.hasObject(CHILD));
			assertTrue(output.hasObject(BASE));
			assertTrue(output.hasObject(WHOLE));
			assertNoKeeps(main);
			assertNoRoutingTemps(main);
		}
	}

	@Test
	public void defaultReceiveDoesNotMirrorToConfiguredAlternate() throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternate(main, destination);
			receive(new ReceivePack(db), mixedPack(), new ByteArrayOutputStream());
			assertEquals(CHILD, db.resolve(REF));
			assertFalse(destination.fileFor(BASE).exists());
			assertFalse(destination.fileFor(WHOLE).exists());
			assertFalse(destination.fileFor(CHILD).exists());
		}
	}

	@Test
	public void copyFailureRejectsRefsButKeepsInstalledMainAndPublishedLooseCopy()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			ObjectDirectory main = db.getObjectDatabase();
			ObjectDirectory destination = alternate.getObjectDatabase();
			configureAlternate(main, destination);
			// Obstruct only the second selected object's loose fanout directory.
			Files.writeString(new File(destination.getDirectory(),
					CHILD.name().substring(0, 2)).toPath(), "blocked");
			ReceivePack receiver = new ReceivePack(db) {
				@Override
				protected ObjectInserter newObjectInserter() throws IOException {
					return main.newMirroringInserter(destination,
							(id, type, size) -> id.equals(BASE) || id.equals(CHILD));
				}
			};
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			PrintStream errorOutput = System.err;
			ByteArrayOutputStream expectedErrors = new ByteArrayOutputStream();
			try (PrintStream capture = new PrintStream(expectedErrors, true, UTF_8)) {
				System.setErr(capture);
				assertThrows(UnpackException.class,
						() -> receive(receiver, mixedPack(), response));
			} finally {
				System.setErr(errorOutput);
			}
			assertTrue("Capture only the expected loose-write fault",
					expectedErrors.toString(UTF_8).contains(destination.fileFor(CHILD).getPath()));
			assertNull(db.resolve(REF));
			PacketLineIn status = new PacketLineIn(
					new ByteArrayInputStream(response.toByteArray()));
			assertTrue(status.readString().startsWith("unpack error "));
			assertEquals("ng " + REF + " n/a (unpacker error)", status.readString());
			assertTrue(PacketLineIn.isEnd(status.readString()));
			assertTrue(destination.fileFor(BASE).isFile());
			assertFalse(destination.fileFor(CHILD).exists());
			assertEquals(1, main.getPacks().size());
			assertArrayEquals(Constants.encode("0123456789b"),
					db.open(CHILD).getBytes());
			assertNoKeeps(main);
		}
	}

	private static void receive(ReceivePack receiver, InMemoryPack pack,
			ByteArrayOutputStream response) throws IOException {
		receive(receiver, pack, CHILD, REF, response);
	}

	private static void receive(ReceivePack receiver, InMemoryPack pack,
			ObjectId tip, String ref, ByteArrayOutputStream response)
			throws IOException {
		receiver.setBiDirectionalPipe(false);
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		PacketLineOut commands = new PacketLineOut(input);
		commands.writeString(ObjectId.zeroId().name() + ' ' + tip.name() + ' '
				+ ref + '\0' + "report-status");
		commands.end();
		input.write(pack.toByteArray());
		receiver.receive(new ByteArrayInputStream(input.toByteArray()), response, null);
	}

	private static InMemoryPack mixedPack() throws IOException {
		InMemoryPack pack = new InMemoryPack().header(3);
		mixedEntries(pack);
		return pack.digest();
	}

	private static InMemoryPack commitPack(byte[] tree, byte[] commit)
			throws IOException {
		InMemoryPack pack = new InMemoryPack().header(7);
		mixedEntries(pack);
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6);
		pack.copyRaw(EXTERNAL);
		pack.deflate(new byte[] { 10, 10, (byte) 0x90, 9, 1, 'k' });
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6).copyRaw(BASE)
				.deflate(new byte[] { 10, 10, (byte) 0x90, 9, 1, 'k' });
		whole(pack, Constants.OBJ_TREE, tree);
		whole(pack, Constants.OBJ_COMMIT, commit);
		return pack.digest();
	}

	private static void mixedEntries(InMemoryPack pack) throws IOException {
		pack.write(Constants.OBJ_BLOB << 4 | 10);
		pack.deflate(Constants.encode("0123456789"));
		pack.write(Constants.OBJ_BLOB << 4 | 12);
		pack.deflate(Constants.encode("not selected"));
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6);
		pack.copyRaw(BASE);
		pack.deflate(new byte[] { 10, 11, (byte) 0x90, 10, 1, 'b' });
	}

	private static void whole(InMemoryPack pack, int type, byte[] contents)
			throws IOException {
		int size = contents.length;
		int next = (type << 4) | (size & 15);
		size >>>= 4;
		while (size != 0) {
			pack.write(next | 0x80);
			next = size & 0x7f;
			size >>>= 7;
		}
		pack.write(next);
		pack.deflate(contents);
	}

	private static ObjectId idFor(int type, byte[] contents) {
		try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
			return formatter.idFor(type, contents);
		}
	}

	private static int storedType(Pack pack, ObjectId id) throws IOException {
		try (RandomAccessFile bytes = new RandomAccessFile(pack.getPackFile(), "r")) {
			bytes.seek(pack.getIndex().findOffset(id));
			return (bytes.read() >> 4) & 7;
		}
	}

	private static void configureAlternate(ObjectDirectory main,
			ObjectDirectory destination) throws IOException {
		configureAlternates(main, destination);
	}

	private static void configureAlternates(ObjectDirectory main,
			ObjectDirectory... alternates) throws IOException {
		StringBuilder paths = new StringBuilder();
		for (ObjectDirectory alternate : alternates) {
			paths.append(alternate.getDirectory().getCanonicalPath()).append('\n');
		}
		Files.writeString(new File(main.getDirectory(), Constants.INFO_ALTERNATES)
				.toPath(), paths);
	}

	private static void assertNoRoutingTemps(ObjectDirectory main) {
		String[] files = main.getDirectory()
				.list((dir, name) -> name.startsWith("routing_"));
		assertEquals(0, files.length);
	}

	private static void assertNoKeeps(ObjectDirectory main) {
		File[] keeps = main.getPackDirectory()
				.listFiles((dir, name) -> name.endsWith(".keep"));
		assertTrue(keeps == null || keeps.length == 0);
	}
}
