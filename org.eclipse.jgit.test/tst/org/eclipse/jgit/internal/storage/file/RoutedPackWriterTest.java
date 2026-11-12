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
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

/** Tests streamed whole output and the routed writer's owned publication. */
public class RoutedPackWriterTest extends RepositoryTestCase {
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
