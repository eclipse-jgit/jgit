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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.Test;

/** Tests the receive inserter hook's placement and failure contracts. */
public class ReceivePackObjectRoutingTest extends RepositoryTestCase {
	private static final ObjectId BASE = ObjectId
			.fromString("ad471007bd7f5983d273b9584e5629230150fd54");

	private static final ObjectId CHILD = ObjectId
			.fromString("0ce70fe2ac6bd0a66e49c594921a89677c1d2f09");

	private static final ObjectId WHOLE = ObjectId
			.fromString("1c6d48da91c5be0b0c9f90ca23cf1033d33eba3e");

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
			assertThrows(UnpackException.class,
					() -> receive(receiver, mixedPack(), response));
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
		receiver.setBiDirectionalPipe(false);
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		PacketLineOut commands = new PacketLineOut(input);
		commands.writeString(ObjectId.zeroId().name() + ' ' + CHILD.name() + ' '
				+ REF + '\0' + "report-status");
		commands.end();
		input.write(pack.toByteArray());
		receiver.receive(new ByteArrayInputStream(input.toByteArray()), response, null);
	}

	private static InMemoryPack mixedPack() throws IOException {
		InMemoryPack pack = new InMemoryPack().header(3);
		pack.write(Constants.OBJ_BLOB << 4 | 10);
		pack.deflate(Constants.encode("0123456789"));
		pack.write(Constants.OBJ_BLOB << 4 | 12);
		pack.deflate(Constants.encode("not selected"));
		pack.write(Constants.OBJ_REF_DELTA << 4 | 6);
		pack.copyRaw(BASE);
		pack.deflate(new byte[] { 10, 11, (byte) 0x90, 10, 1, 'b' });
		return pack.digest();
	}

	private static void configureAlternate(ObjectDirectory main,
			ObjectDirectory destination) throws IOException {
		Files.writeString(new File(main.getDirectory(), Constants.INFO_ALTERNATES)
				.toPath(), destination.getDirectory().getCanonicalPath() + '\n');
	}

	private static void assertNoKeeps(ObjectDirectory main) {
		File[] keeps = main.getPackDirectory()
				.listFiles((dir, name) -> name.endsWith(".keep"));
		assertTrue(keeps == null || keeps.length == 0);
	}
}
