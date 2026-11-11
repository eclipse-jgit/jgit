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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.UploadPack;
import org.junit.Before;
import org.junit.Test;

/** File-backed classification and transfer of an incomplete promisor pack. */
public class PromisorObjectTest extends RepositoryTestCase {
	private ObjectId missing;

	private ObjectId present;

	private ObjectId tree;

	private ObjectId commit;

	private Pack pack;

	@Before
	public void createIncompletePack() throws Exception {
		try (PackInserter ins = new PackInserter(db.getObjectDatabase())) {
			missing = ins.idFor(OBJ_BLOB, Constants.encode("missing contents"));
			present = ins.insert(OBJ_BLOB, Constants.encode("present"));
			TreeFormatter tf = new TreeFormatter();
			tf.append("missing", FileMode.REGULAR_FILE, missing);
			tf.append("present", FileMode.REGULAR_FILE, present);
			tree = ins.insert(tf);
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(tree);
			cb.setAuthor(author);
			cb.setCommitter(committer);
			cb.setMessage("incomplete");
			commit = ins.insert(cb);
			ins.flush();
		}
		pack = db.getObjectDatabase().getPacks().iterator().next();
		Files.createFile(pack.getPackFile().create(PackExt.PROMISOR).toPath());
		var ref = db.updateRef("refs/heads/main");
		ref.setNewObjectId(commit);
		ref.update();
	}

	@Test
	public void markedPackMembershipAndDirectReferences() throws Exception {
		assertTrue(pack.isPromisor());
		try (ObjectReader reader = db.newObjectReader()) {
			assertTrue(reader.isPromisorObject(commit));
			assertTrue(reader.isPromisorObject(tree));
			assertTrue(reader.isPromisorObject(present));
			assertTrue(reader.isPromisorObject(missing));
			assertFalse(reader.isPromisorObject(ObjectId.zeroId()));
		}
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				ObjectReader reader = reopened.newObjectReader()) {
			assertTrue(reader.isPromisorObject(missing));
		}
	}

	@Test
	public void commitParentsAndTagTargetsAreDirectReferences()
			throws Exception {
		ObjectId parent = ObjectId
				.fromString("1111111111111111111111111111111111111111");
		ObjectId target = ObjectId
				.fromString("2222222222222222222222222222222222222222");
		try (PackInserter ins = new PackInserter(db.getObjectDatabase())) {
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(tree);
			cb.setParentId(parent);
			cb.setAuthor(author);
			cb.setCommitter(committer);
			cb.setMessage("parent");
			ObjectId otherCommit = ins.insert(cb);
			TagBuilder tb = new TagBuilder();
			tb.setObjectId(target, OBJ_BLOB);
			tb.setTag("missing-target");
			tb.setTagger(author);
			tb.setMessage("tag");
			ins.insert(tb);
			ins.flush();
			for (Pack p : db.getObjectDatabase().getPacks()) {
				if (p.hasObject(otherCommit)) {
					Files.createFile(
							p.getPackFile().create(PackExt.PROMISOR).toPath());
				}
			}
		}
		try (ObjectReader reader = db.newObjectReader()) {
			assertTrue(reader.isPromisorObject(parent));
			assertTrue(reader.isPromisorObject(target));
		}
	}

	@Test
	public void multiPackIndexDoesNotHidePromisorProvenance() throws Exception {
		try (PackInserter ins = new PackInserter(db.getObjectDatabase())) {
			ins.insert(OBJ_BLOB, Constants.encode("another pack"));
			ins.flush();
		}
		MidxWriter.writeMidx(NullProgressMonitor.INSTANCE, db,
				db.getObjectDatabase().getPacks(),
				db.getObjectDatabase().getPackDirectory().toPath()
						.resolve(Constants.MIDX_FILE).toFile(),
				null);
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				ObjectReader reader = reopened.newObjectReader()) {
			assertTrue(reopened.getObjectDatabase().getPacks().iterator()
					.next() instanceof PackMidx);
			assertTrue(reader.isPromisorObject(missing));
			assertTrue(new GC(reopened).repack().isEmpty());
			assertTrue(pack.getPackFile().create(PackExt.PROMISOR).exists());
		}
	}

	@Test
	public void corruptMarkedIndexCannotBeSilentlyClassified()
			throws Exception {
		Files.delete(pack.getPackFile().create(PackExt.INDEX).toPath());
		Files.write(pack.getPackFile().create(PackExt.INDEX).toPath(),
				new byte[] { 0 });
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				ObjectReader reader = reopened.newObjectReader()) {
			assertThrows(IOException.class,
					() -> reader.isPromisorObject(missing));
		}
	}

	@Test
	public void unmarkedPackAndConfigurationDoNotClassifyObjects()
			throws Exception {
		Files.delete(pack.getPackFile().create(PackExt.PROMISOR).toPath());
		db.getConfig().setBoolean("remote", "objects", "promisor", true);
		try (ObjectReader reader = db.newObjectReader()) {
			assertFalse(reader.isPromisorObject(commit));
			assertFalse(reader.isPromisorObject(missing));
		}
	}

	@Test
	public void classificationSurvivesDelegationAndAlternates()
			throws Exception {
		try (ObjectReader reader = new ObjectReader.Filter() {
			private final ObjectReader delegate = db.newObjectReader();

			@Override
			protected ObjectReader delegate() {
				return delegate;
			}
		};
				PackInserter ins = new PackInserter(db.getObjectDatabase());
				ObjectReader inserted = ins.newReader()) {
			assertTrue(reader.isPromisorObject(missing));
			assertTrue(inserted.isPromisorObject(missing));
		}
		try (FileRepository alternate = createBareRepository()) {
			write(alternate.getObjectDatabase().getDirectory().toPath()
					.resolve(Constants.INFO_ALTERNATES).toFile(),
					db.getObjectDatabase().getDirectory().getAbsolutePath()
							+ "\n");
			try (ObjectReader reader = alternate.newObjectReader()) {
				assertTrue(reader.isPromisorObject(missing));
				assertFalse(reader.isPromisorObject(ObjectId.zeroId()));
			}
			GC gc = new GC(alternate);
			ObjectId loose;
			try (ObjectInserter ins = alternate.newObjectInserter()) {
				loose = ins.insert(OBJ_BLOB,
						Constants.encode("alternate loose"));
				ins.flush();
			}
			gc.setExpireAge(Duration.ZERO);
			fsTick(null);
			gc.prune(Set.of());
			assertTrue(alternate.getObjectDatabase().has(loose));
		}
	}

	@Test
	public void unreadableAlternatesCannotProvePromisedBlobAbsent()
			throws Exception {
		try (FileRepository alternate = createBareRepository()) {
			try (ObjectInserter ins = alternate.newObjectInserter()) {
				assertEquals(missing, ins.insert(OBJ_BLOB,
						Constants.encode("missing contents")));
				ins.flush();
			}
			ByteArrayOutputStream contents = new ByteArrayOutputStream();
			contents.write(Constants.encode(alternate.getObjectDatabase()
					.getDirectory().getAbsolutePath() + "\n"));
			contents.write(0xff);
			Files.write(db.getObjectDatabase().getDirectory().toPath().resolve(
					Constants.INFO_ALTERNATES), contents.toByteArray());
			try (FileRepository reopened = new FileRepository(
					db.getDirectory());
					PackWriter writer = new PackWriter(reopened)) {
				writer.setAllowMissingPromisorObjects(true);
				assertThrows(IOException.class,
						() -> writer.preparePack(NullProgressMonitor.INSTANCE,
								Set.of(commit), Set.of()));
				Files.write(
						reopened.getObjectDatabase().getDirectory().toPath()
								.resolve(Constants.INFO_ALTERNATES),
						new byte[0]);
				reopened.getObjectDatabase().close();
				try (PackWriter retry = new PackWriter(reopened)) {
					retry.setAllowMissingPromisorObjects(true);
					retry.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(commit), Set.of());
					assertFalse(retry.willInclude(missing));
				}
			}
		}
	}

	@Test
	public void packWriterOmitsOnlyAuthorizedMissingBlobsBeforeSizeFiltering()
			throws Exception {
		try (PackWriter writer = new PackWriter(db)) {
			writer.setAllowMissingPromisorObjects(true);
			writer.setFilterSpec(FilterSpec.fromFilterLine("blob:limit=1000"));
			writer.preparePack(NullProgressMonitor.INSTANCE, Set.of(commit),
					Set.of());
			assertFalse(writer.willInclude(missing));
			assertTrue(writer.willInclude(present));
			assertEquals(3, writer.getObjectCount());
			writer.writePack(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE, new ByteArrayOutputStream());
		}
		try (PackWriter writer = new PackWriter(db)) {
			writer.setFilterSpec(FilterSpec.fromFilterLine("blob:limit=1000"));
			assertThrows(MissingObjectException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(commit), Set.of()));
		}
	}

	@Test
	public void explicitMissingWantsAndMissingTraversalObjectsRemainErrors()
			throws Exception {
		try (PackWriter writer = new PackWriter(db)) {
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(MissingObjectException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(missing), Set.of()));
		}
		ObjectId broken;
		try (ObjectInserter ins = db.newObjectInserter()) {
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(ObjectId.zeroId());
			cb.setAuthor(author);
			cb.setCommitter(committer);
			cb.setMessage("missing tree");
			broken = ins.insert(cb);
			ins.flush();
		}
		try (PackWriter writer = new PackWriter(db)) {
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(MissingObjectException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(broken), Set.of()));
		}
	}

	@Test
	public void authorizedPackWriterCannotReenableUnsafeOptimizations()
			throws Exception {
		try (ObjectReader reader = new ObjectReader.Filter() {
			private final ObjectReader delegate = db.newObjectReader();

			@Override
			protected ObjectReader delegate() {
				return delegate;
			}

			@Override
			public BitmapIndex getBitmapIndex() {
				throw new AssertionError("bitmap enumeration");
			}
		}; PackWriter writer = new PackWriter(db, reader)) {
			writer.setUseBitmaps(true);
			writer.setUseCachedPacks(true);
			writer.setThin(true);
			writer.setAllowMissingPromisorObjects(true);
			assertFalse(writer.isThin());
			assertFalse(writer.isUseCachedPacks());
			assertFalse(writer.isUseBitmaps());
			writer.setUseBitmaps(true);
			writer.setUseCachedPacks(true);
			writer.setThin(true);
			assertFalse(writer.isThin());
			assertFalse(writer.isUseCachedPacks());
			assertFalse(writer.isUseBitmaps());
			writer.preparePack(NullProgressMonitor.INSTANCE, Set.of(commit),
					Set.of());
			assertTrue(writer.willInclude(present));
		}
	}

	@Test
	public void explicitCachedPacksCannotBypassOmissionSafety()
			throws Exception {
		LocalCachedPack cached = new LocalCachedPack(List.of(pack));
		try (PackWriter writer = new PackWriter(db)) {
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(IllegalStateException.class,
					() -> writer.preparePack(List.of(cached)));
		}
		try (PackWriter writer = new PackWriter(db)) {
			writer.preparePack(List.of(cached));
			assertThrows(IllegalStateException.class,
					() -> writer.setAllowMissingPromisorObjects(true));
		}
	}

	@Test
	public void corruptPromisedBlobIsNotTreatedAsAbsent() throws Exception {
		var loose = db.getObjectDatabase().fileFor(missing).toPath();
		Files.createDirectories(loose.getParent());
		Files.write(loose, Constants.encode("not a compressed Git object"));
		try (PackWriter writer = new PackWriter(db)) {
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(CorruptObjectException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(commit), Set.of()));
		}
	}

	@Test
	public void corruptPackedPromisedBlobCannotBeOmitted() throws Exception {
		Pack corrupt = packMissingBlob();
		var path = corrupt.getPackFile().toPath();
		byte[] contents = Files.readAllBytes(path);
		int pos = (int) corrupt.getIndex().findOffset(missing);
		while ((contents[pos++] & 0x80) != 0) {
			// Skip the variable-length packed object header.
		}
		contents[pos] = 0;
		corrupt.close();
		Files.delete(path);
		Files.write(path, contents);
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				ObjectReader reader = reopened.newObjectReader();
				PackWriter writer = new PackWriter(reopened, reader)) {
			assertTrue(reader.isPromisorObject(missing));
			assertThrows(MissingObjectException.class,
					() -> reader.open(missing, OBJ_BLOB));
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(CorruptObjectException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(commit), Set.of()));
		}
	}

	@Test
	public void unreadableUnmarkedIndexCannotProvePromisedBlobAbsent()
			throws Exception {
		Pack corrupt = packMissingBlob();
		var index = corrupt.getPackFile().create(PackExt.INDEX).toPath();
		corrupt.close();
		Files.delete(index);
		Files.write(index, new byte[] { 0 });
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				PackWriter writer = new PackWriter(reopened)) {
			writer.setAllowMissingPromisorObjects(true);
			assertThrows(IOException.class,
					() -> writer.preparePack(NullProgressMonitor.INSTANCE,
							Set.of(commit), Set.of()));
		}
	}

	private Pack packMissingBlob() throws IOException {
		try (PackInserter ins = new PackInserter(db.getObjectDatabase())) {
			assertEquals(missing,
					ins.insert(OBJ_BLOB, Constants.encode("missing contents")));
			ins.flush();
		}
		for (Pack candidate : db.getObjectDatabase().getPacks()) {
			if (candidate.hasObject(missing)) {
				assertFalse(candidate.isPromisor());
				return candidate;
			}
		}
		throw new AssertionError("missing blob was not packed");
	}

	@Test
	public void maintenancePreservesPromisorProvenanceAndLooseObjects()
			throws Exception {
		ObjectId loose;
		try (ObjectInserter ins = db.newObjectInserter()) {
			loose = ins.insert(OBJ_BLOB,
					Constants.encode("unreferenced loose"));
			ins.flush();
		}
		GC gc = new GC(db);
		gc.setExpireAge(Duration.ZERO);
		fsTick(null);
		assertTrue(gc.repack().isEmpty());
		gc.prune(Set.of());
		assertTrue(pack.getPackFile().exists());
		assertTrue(pack.getPackFile().create(PackExt.PROMISOR).exists());
		assertTrue(db.getObjectDatabase().has(loose));
		assertEquals(1, db.getObjectDatabase().getPacks().size());
	}

	@Test
	public void maintenanceDetectsMarkedPacksDroppedAfterReadFailure()
			throws Exception {
		var index = pack.getPackFile().create(PackExt.INDEX).toPath();
		Files.delete(index);
		Files.write(index, new byte[] { 0 });
		try (FileRepository reopened = new FileRepository(db.getDirectory());
				FileRepository alternate = createBareRepository()) {
			write(alternate.getObjectDatabase().getDirectory().toPath()
					.resolve(Constants.INFO_ALTERNATES).toFile(),
					db.getObjectDatabase().getDirectory().getAbsolutePath()
							+ "\n");
			for (FileRepository repository : List.of(reopened, alternate)) {
				ObjectId loose;
				try (ObjectInserter ins = repository.newObjectInserter()) {
					loose = ins.insert(OBJ_BLOB,
							Constants.encode("loose after pack failure"));
					ins.flush();
				}
				try (ObjectReader reader = repository.newObjectReader()) {
					assertThrows(MissingObjectException.class,
							() -> reader.open(commit));
					assertThrows(MissingObjectException.class,
							() -> reader.open(commit));
				}
				assertTrue(repository.getObjectDatabase().getPromisorPacks()
						.isEmpty());
				GC gc = new GC(repository);
				gc.setExpireAge(Duration.ZERO);
				fsTick(null);
				assertTrue(gc.repack().isEmpty());
				gc.prune(Set.of());
				assertTrue(repository.getObjectDatabase().has(loose));
				assertTrue(pack.getPackFile().exists());
				assertTrue(
						pack.getPackFile().create(PackExt.PROMISOR).exists());
			}
		}
	}

	@Test
	public void uploadAcceptanceIsRequestLocalAndMustBeAdvertised()
			throws Exception {
		configureAdvertisement();
		try (UploadPack up = new UploadPack(db)) {
			ByteArrayInputStream response = fetch(up, commit, "objects",
					"blob:limit=1000");
			try (FileRepository client = createBareRepository();
					ObjectInserter ins = client.newObjectInserter()) {
				ins.newPackParser(new ByteArrayInputStream(readPack(response)))
						.parse(NullProgressMonitor.INSTANCE);
				ins.flush();
				assertTrue(client.getObjectDatabase().has(present));
				assertFalse(client.getObjectDatabase().has(missing));
			}
			assertMissing(missing, assertThrows(IOException.class,
					() -> fetch(up, commit, null, "blob:limit=1000")));
		}
		try (UploadPack up = new UploadPack(db)) {
			assertThrows(IOException.class,
					() -> fetch(up, commit, "unknown", "blob:limit=1000"));
		}
		db.getConfig().setBoolean("promisor", null, "advertise", false);
		try (UploadPack up = new UploadPack(db)) {
			assertThrows(IOException.class,
					() -> fetch(up, commit, "objects", "blob:limit=1000"));
		}
	}

	@Test
	public void authorizedShallowPackPreservesIncludedTags() throws Exception {
		configureAdvertisement();
		ObjectId tag;
		try (ObjectInserter ins = db.newObjectInserter()) {
			TagBuilder tb = new TagBuilder();
			tb.setObjectId(commit, Constants.OBJ_COMMIT);
			tb.setTag("v1");
			tb.setTagger(author);
			tb.setMessage("release");
			tag = ins.insert(tb);
			ins.flush();
		}
		var ref = db.updateRef("refs/tags/v1");
		ref.setNewObjectId(tag);
		ref.update();
		try (UploadPack up = new UploadPack(db);
				FileRepository client = createBareRepository();
				ObjectInserter ins = client.newObjectInserter()) {
			ByteArrayInputStream response = fetch(up, commit, "objects", null,
					"deepen 1", "include-tag");
			ins.newPackParser(new ByteArrayInputStream(readPack(response)))
					.parse(NullProgressMonitor.INSTANCE);
			ins.flush();
			assertTrue(client.getObjectDatabase().has(tag));
			assertTrue(client.getObjectDatabase().has(present));
			assertFalse(client.getObjectDatabase().has(missing));
		}
	}

	@Test
	public void acceptedRemoteDoesNotAuthorizeUnexpectedMissingBlob()
			throws Exception {
		configureAdvertisement();
		ObjectId unexpected = ObjectId
				.fromString("3333333333333333333333333333333333333333");
		ObjectId broken;
		try (ObjectInserter ins = db.newObjectInserter()) {
			TreeFormatter tf = new TreeFormatter();
			tf.append("unexpected", FileMode.REGULAR_FILE, unexpected);
			ObjectId newTree = ins.insert(tf);
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(newTree);
			cb.setAuthor(author);
			cb.setCommitter(committer);
			cb.setMessage("unexpected");
			broken = ins.insert(cb);
			ins.flush();
		}
		var ref = db.updateRef("refs/heads/broken");
		ref.setNewObjectId(broken);
		ref.update();
		try (UploadPack up = new UploadPack(db)) {
			assertThrows(IOException.class,
					() -> fetch(up, broken, "objects", "blob:limit=1000"));
		}
	}

	private void assertMissing(ObjectId id, Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		assertTrue(cause instanceof MissingObjectException);
		assertEquals(id, ((MissingObjectException) cause).getObjectId());
	}

	private byte[] readPack(ByteArrayInputStream response) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		while (response.available() > 0) {
			int length = Integer.parseInt(new String(response.readNBytes(4),
					java.nio.charset.StandardCharsets.US_ASCII), 16);
			if (length <= 4) {
				continue;
			}
			byte[] packet = response.readNBytes(length - 4);
			if (packet[0] == 1) {
				bytes.write(packet, 1, packet.length - 1);
			} else if (packet[0] == 3) {
				throw new IOException("upload-pack failed");
			}
		}
		return bytes.toByteArray();
	}

	private void configureAdvertisement() {
		db.getConfig().setBoolean("promisor", null, "advertise", true);
		db.getConfig().setBoolean("remote", "objects", "promisor", true);
		db.getConfig().setString("remote", "objects", "url",
				"https://example.com/objects");
		db.getConfig().setBoolean("uploadpack", null, "allowFilter", true);
	}

	private ByteArrayInputStream fetch(UploadPack up, AnyObjectId want,
			String accepted, String filter, String... arguments)
			throws Exception {
		ByteArrayOutputStream request = new ByteArrayOutputStream();
		PacketLineOut lines = new PacketLineOut(request);
		lines.writeString("command=fetch\n");
		if (accepted != null) {
			lines.writeString("promisor-remote=" + accepted + "\n");
		}
		lines.writeDelim();
		lines.writeString("want " + want.name() + "\n");
		if (filter != null) {
			lines.writeString("filter " + filter + "\n");
		}
		for (String argument : arguments) {
			lines.writeString(argument + "\n");
		}
		lines.writeString("thin-pack\n");
		lines.writeString("done\n");
		lines.end();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		up.setExtraParameters(List.of("version=2"));
		up.upload(new ByteArrayInputStream(request.toByteArray()), output,
				null);
		ByteArrayInputStream response = new ByteArrayInputStream(
				output.toByteArray());
		PacketLineIn in = new PacketLineIn(response);
		while (!PacketLineIn.isEnd(in.readString())) {
			// Consume the capability advertisement.
		}
		String section = in.readString();
		if ("shallow-info".equals(section)) {
			while (!PacketLineIn.isDelimiter(in.readString())) {
				// Consume the shallow information section.
			}
			section = in.readString();
		}
		assertEquals("packfile", section);
		return response;
	}
}
