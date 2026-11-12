/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import static org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation.PACK_DELTA;
import static org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation.PACK_WHOLE;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.transport.PackParser;
import org.junit.Test;

public class PackWriterWriteObjectsTest {

	@Test
	public void writePack_deepReusedDeltaChain_succeedsWithoutStackOverflow()
			throws Throwable {
		int count = 8000;
		FakeReuseReader reader = new FakeReuseReader();
		ObjectId[] ids = new ObjectId[count];
		byte[][] payloads = new byte[count][];

		for (int i = 0; i < count; i++) {
			payloads[i] = intPayload(i);
			ids[i] = reader.addBlob(payloads[i]);
			if (i == 0) {
				reader.addEpoch(ids[i],
						Collections.singletonList(RepSpec.whole(payloads[i])));
			} else {
				reader.addEpoch(ids[i], Collections.singletonList(
						RepSpec.delta(ids[i - 1], literalDelta4(payloads[i]))));
			}
		}

		// Feed objects from tip of delta chain down to base so writeObjectImpl
		// must walk the entire 8,000-deep base chain in a single call.
		List<RevObject> objects = lookupBlobsReversed(reader, ids);

		PackConfig cfg = defaultPackConfig();
		cfg.setDeltaCompress(false);

		byte[] packBytes = runOnSmallStack(() -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (PackWriter pw = new PackWriter(cfg, reader)) {
				pw.preparePack(objects.iterator());
				pw.writePack(NullProgressMonitor.INSTANCE,
						NullProgressMonitor.INSTANCE, out);
				PackStatistics stats = pw.getStatistics();
				assertEquals(count, stats.getTotalObjects());
				assertEquals(count, stats.getReusedObjects());
				assertEquals(count - 1, stats.getTotalDeltas());
				assertEquals(count - 1, stats.getReusedDeltas());
			}
			return out.toByteArray();
		});

		try (InMemoryRepository dst = parseIntoRepo(packBytes)) {
			assertArrayEquals(payloads[0], dst.open(ids[0]).getBytes());
			assertArrayEquals(payloads[count / 2],
					dst.open(ids[count / 2]).getBytes());
			assertArrayEquals(payloads[count - 1],
					dst.open(ids[count - 1]).getBytes());
		}
	}

	@Test
	public void writePack_deepDeflatedDeltaChain_succeedsWithoutStackOverflow()
			throws Throwable {
		int count = 4000;
		FakeReuseReader reader = new FakeReuseReader();
		ObjectId[] ids = new ObjectId[count];
		byte[][] payloads = new byte[count][];

		for (int i = 0; i < count; i++) {
			payloads[i] = compressiblePayload(i);
			ids[i] = reader.addBlob(payloads[i]);
		}

		List<RevObject> objects = lookupBlobsReversed(reader, ids);

		PackConfig cfg = defaultPackConfig();
		cfg.setReuseDeltas(false);
		cfg.setReuseObjects(false);
		cfg.setDeltaCompress(false);

		byte[] packBytes = runOnSmallStack(() -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (PackWriter pw = new PackWriter(cfg, reader)) {
				pw.preparePack(objects.iterator());
				for (int i = 1; i < count; i++) {
					pw.get(ids[i]).setDeltaBase(pw.get(ids[i - 1]));
				}
				pw.writePack(NullProgressMonitor.INSTANCE,
						NullProgressMonitor.INSTANCE, out);
				PackStatistics stats = pw.getStatistics();
				assertEquals(count, stats.getTotalObjects());
				assertEquals(0, stats.getReusedObjects());
				assertEquals(count - 1, stats.getTotalDeltas());
			}
			return out.toByteArray();
		});

		try (InMemoryRepository dst = parseIntoRepo(packBytes)) {
			assertArrayEquals(payloads[0], dst.open(ids[0]).getBytes());
			assertArrayEquals(payloads[count / 2],
					dst.open(ids[count / 2]).getBytes());
			assertArrayEquals(payloads[count - 1],
					dst.open(ids[count - 1]).getBytes());
		}
	}

	@Test
	public void writePack_deltaCycleFromMissingRepresentation_breaksCycleAndWritesValidPack()
			throws Exception {
		FakeReuseReader reader = new FakeReuseReader();
		byte[] payloadA = intPayload(10);
		byte[] payloadB = intPayload(20);
		ObjectId idA = reader.addBlob(payloadA);
		ObjectId idB = reader.addBlob(payloadB);

		// Epoch 1 (before concurrent repack): A is a delta on B, B is whole.
		// When PackWriter tries to copy B as-is, B's whole representation is
		// gone; reselection sees Epoch 2 (after concurrent repack) where B is a
		// delta on A and A is whole, forming an A -> B -> A cycle.
		reader.addEpoch(idA,
				List.of(RepSpec.delta(idB, literalDelta4(payloadA)),
						RepSpec.whole(payloadA)));
		reader.addEpoch(idB, Collections
				.singletonList(RepSpec.goneBeforeHeader(PACK_WHOLE, null)));
		reader.addEpoch(idB, Collections
				.singletonList(RepSpec.delta(idA, literalDelta4(payloadB))));

		List<RevObject> objects = new ArrayList<>();
		try (RevWalk rw = new RevWalk(reader)) {
			objects.add(rw.lookupBlob(idA));
			objects.add(rw.lookupBlob(idB));
		}

		PackConfig cfg = defaultPackConfig();
		cfg.setDeltaCompress(false);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PackWriter pw = new PackWriter(cfg, reader)) {
			pw.preparePack(objects.iterator());
			pw.writePack(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE, out);
			PackStatistics stats = pw.getStatistics();
			assertEquals(2, stats.getTotalObjects());
			assertEquals(2, stats.getReusedObjects());
			assertEquals(1, stats.getTotalDeltas());
			assertEquals(1, stats.getReusedDeltas());
		}

		try (InMemoryRepository dst = parseIntoRepo(out.toByteArray())) {
			assertArrayEquals(payloadA, dst.open(idA).getBytes());
			assertArrayEquals(payloadB, dst.open(idB).getBytes());
		}
	}

	@Test
	public void writePack_missingRepresentationReselectsUnwrittenDeltaChain_writesBasesFirst()
			throws Exception {
		FakeReuseReader reader = new FakeReuseReader();
		byte[] payloadA = intPayload(1);
		byte[] payloadB = intPayload(2);
		byte[] payloadC = intPayload(3);
		ObjectId idA = reader.addBlob(payloadA);
		ObjectId idB = reader.addBlob(payloadB);
		ObjectId idC = reader.addBlob(payloadC);

		// A is written first as whole, but its whole representation disappears
		// before header output and reselection picks A as a delta on unwritten
		// B (which is itself a delta on unwritten C).
		reader.addEpoch(idA, Collections
				.singletonList(RepSpec.goneBeforeHeader(PACK_WHOLE, null)));
		reader.addEpoch(idA, Collections
				.singletonList(RepSpec.delta(idB, literalDelta4(payloadA))));
		reader.addEpoch(idB, Collections
				.singletonList(RepSpec.delta(idC, literalDelta4(payloadB))));
		reader.addEpoch(idC,
				Collections.singletonList(RepSpec.whole(payloadC)));

		List<RevObject> objects = new ArrayList<>();
		try (RevWalk rw = new RevWalk(reader)) {
			objects.add(rw.lookupBlob(idA));
			objects.add(rw.lookupBlob(idB));
			objects.add(rw.lookupBlob(idC));
		}

		PackConfig cfg = defaultPackConfig();
		cfg.setDeltaCompress(false);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PackWriter pw = new PackWriter(cfg, reader)) {
			pw.preparePack(objects.iterator());
			pw.writePack(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE, out);
			PackStatistics stats = pw.getStatistics();
			assertEquals(3, stats.getTotalObjects());
			assertEquals(2, stats.getReusedDeltas());
		}

		try (InMemoryRepository dst = parseIntoRepo(out.toByteArray())) {
			assertArrayEquals(payloadA, dst.open(idA).getBytes());
			assertArrayEquals(payloadB, dst.open(idB).getBytes());
			assertArrayEquals(payloadC, dst.open(idC).getBytes());
		}
	}

	@Test
	public void writePack_missingRepresentationAfterPartialWrite_throwsCorruptObjectException()
			throws Exception {
		FakeReuseReader reader = new FakeReuseReader();
		byte[] payloadA = intPayload(42);
		ObjectId idA = reader.addBlob(payloadA);
		reader.addEpoch(idA,
				Collections.singletonList(RepSpec.goneAfterPartialWrite()));

		List<RevObject> objects = new ArrayList<>();
		try (RevWalk rw = new RevWalk(reader)) {
			objects.add(rw.lookupBlob(idA));
		}

		PackConfig cfg = defaultPackConfig();
		cfg.setDeltaCompress(false);

		try (PackWriter pw = new PackWriter(cfg, reader)) {
			pw.preparePack(objects.iterator());
			CorruptObjectException thrown = assertThrows(
					CorruptObjectException.class,
					() -> pw.writePack(NullProgressMonitor.INSTANCE,
							NullProgressMonitor.INSTANCE,
							new ByteArrayOutputStream()));
			assertSame(StoredObjectRepresentationNotAvailableException.class,
					thrown.getCause().getClass());
		}
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get() throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T runOnSmallStack(ThrowingSupplier<T> action)
			throws Throwable {
		Object[] result = new Object[1];
		Throwable[] thrown = new Throwable[1];
		Thread worker = new Thread(null, () -> {
			try {
				result[0] = action.get();
			} catch (Throwable t) {
				thrown[0] = t;
			}
		}, "packwriter-small-stack", 256 * 1024);
		worker.start();
		worker.join();
		if (thrown[0] != null) {
			throw thrown[0];
		}
		return (T) result[0];
	}

	private static PackConfig defaultPackConfig() {
		PackConfig cfg = new PackConfig();
		cfg.setDeltaBaseAsOffset(true);
		cfg.setBuildBitmaps(false);
		cfg.setCutDeltaChains(false);
		return cfg;
	}

	private static List<RevObject> lookupBlobsReversed(ObjectReader reader,
			ObjectId[] ids) {
		List<RevObject> objects = new ArrayList<>(ids.length);
		try (RevWalk rw = new RevWalk(reader)) {
			for (int i = ids.length - 1; i >= 0; i--) {
				objects.add(rw.lookupBlob(ids[i]));
			}
		}
		return objects;
	}

	private static InMemoryRepository parseIntoRepo(byte[] packBytes)
			throws IOException {
		InMemoryRepository repo = new InMemoryRepository(
				new DfsRepositoryDescription("verify"));
		try (ObjectInserter ins = repo.newObjectInserter()) {
			PackParser parser = ins
					.newPackParser(new ByteArrayInputStream(packBytes));
			parser.parse(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE);
			ins.flush();
		}
		return repo;
	}

	private static byte[] intPayload(int v) {
		return new byte[] { (byte) (v >>> 24), (byte) (v >>> 16),
				(byte) (v >>> 8), (byte) v };
	}

	private static byte[] compressiblePayload(int v) {
		// Include a shared 28-byte prefix so DeltaIndex produces a delta
		// smaller than the 32-byte unpacked object.
		byte[] b = new byte[32];
		for (int i = 0; i < 28; i++) {
			b[i] = (byte) (0x41 + (i % 26));
		}
		b[28] = (byte) (v >>> 24);
		b[29] = (byte) (v >>> 16);
		b[30] = (byte) (v >>> 8);
		b[31] = (byte) v;
		return b;
	}

	private static byte[] literalDelta4(byte[] target4) {
		return new byte[] { 0x04, 0x04, 0x04, target4[0], target4[1],
				target4[2], target4[3] };
	}

	private static byte[] deflateBytes(byte[] raw) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Deflater def = new Deflater(Deflater.BEST_SPEED);
		try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, def)) {
			dos.write(raw);
		} catch (IOException e) {
			throw new AssertionError(e);
		} finally {
			def.end();
		}
		return baos.toByteArray();
	}

	private enum CopyMode {
		NORMAL, GONE_BEFORE_HEADER, GONE_AFTER_PARTIAL_WRITE
	}

	private static final class RepSpec extends StoredObjectRepresentation {
		final int format;

		final ObjectId baseId;

		final int rawLength;

		final byte[] deflated;

		final CopyMode mode;

		private RepSpec(int format, ObjectId baseId, int rawLength,
				byte[] deflated, CopyMode mode) {
			this.format = format;
			this.baseId = baseId;
			this.rawLength = rawLength;
			this.deflated = deflated;
			this.mode = mode;
		}

		static RepSpec whole(byte[] payload) {
			return new RepSpec(PACK_WHOLE, null, payload.length,
					deflateBytes(payload), CopyMode.NORMAL);
		}

		static RepSpec delta(ObjectId baseId, byte[] rawDelta) {
			return new RepSpec(PACK_DELTA, baseId, rawDelta.length,
					deflateBytes(rawDelta), CopyMode.NORMAL);
		}

		static RepSpec goneBeforeHeader(int format, ObjectId baseId) {
			return new RepSpec(format, baseId, 4, new byte[0],
					CopyMode.GONE_BEFORE_HEADER);
		}

		static RepSpec goneAfterPartialWrite() {
			return new RepSpec(PACK_WHOLE, null, 4, new byte[0],
					CopyMode.GONE_AFTER_PARTIAL_WRITE);
		}

		@Override
		public int getFormat() {
			return format;
		}

		@Override
		public int getWeight() {
			return deflated.length > 0 ? deflated.length : 16;
		}

		@Override
		public ObjectId getDeltaBase() {
			return baseId;
		}
	}

	private static final class FakeObjectToPack extends ObjectToPack {
		RepSpec selected;

		FakeObjectToPack(AnyObjectId src, int type) {
			super(src, type);
		}

		@Override
		public void select(StoredObjectRepresentation ref) {
			this.selected = (RepSpec) ref;
		}

		@Override
		protected void clearReuseAsIs() {
			super.clearReuseAsIs();
			this.selected = null;
		}
	}

	private static final class FakeReuseReader extends ObjectReader
			implements ObjectReuseAsIs {
		private final ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();

		private final Map<ObjectId, byte[]> blobs = new HashMap<>();

		private final Map<ObjectId, Deque<List<RepSpec>>> epochs = new HashMap<>();

		ObjectId addBlob(byte[] data) {
			ObjectId id = fmt.idFor(OBJ_BLOB, data);
			blobs.put(id, data);
			return id;
		}

		void addEpoch(ObjectId id, List<RepSpec> reps) {
			epochs.computeIfAbsent(id, k -> new ArrayDeque<>()).addLast(reps);
		}

		@Override
		public ObjectReader newReader() {
			return this;
		}

		@Override
		public Collection<ObjectId> resolve(AbbreviatedObjectId id) {
			List<ObjectId> matches = new ArrayList<>();
			for (ObjectId candidate : blobs.keySet()) {
				if (id.prefixCompare(candidate) == 0) {
					matches.add(candidate);
				}
			}
			return matches;
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint)
				throws MissingObjectException {
			byte[] data = blobs.get(objectId.toObjectId());
			if (data == null) {
				throw new MissingObjectException(objectId.toObjectId(),
						OBJ_BLOB);
			}
			return new ObjectLoader.SmallObject(OBJ_BLOB, data);
		}

		@Override
		public Set<ObjectId> getShallowCommits() {
			return Collections.emptySet();
		}

		@Override
		public void close() {
			// Nothing to close.
		}

		@Override
		public ObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
			return new FakeObjectToPack(objectId, type);
		}

		@Override
		public void selectObjectRepresentation(PackWriter packer,
				ProgressMonitor monitor, Iterable<ObjectToPack> objects)
				throws MissingObjectException, IOException {
			for (ObjectToPack otp : objects) {
				Deque<List<RepSpec>> queue = epochs.get(otp.toObjectId());
				if (queue != null && !queue.isEmpty()) {
					for (RepSpec rep : queue.peekFirst()) {
						if (!packer.select(otp, rep)) {
							break;
						}
					}
				}
				monitor.update(1);
			}
		}

		@Override
		public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
				throws IOException {
			for (ObjectToPack otp : list) {
				out.writeObject(otp);
			}
		}

		@Override
		public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
				boolean validate) throws IOException,
				StoredObjectRepresentationNotAvailableException {
			FakeObjectToPack fotp = (FakeObjectToPack) otp;
			RepSpec rep = fotp.selected;
			if (rep.mode == CopyMode.GONE_BEFORE_HEADER) {
				Deque<List<RepSpec>> queue = epochs.get(otp.toObjectId());
				if (queue != null && queue.size() > 1) {
					queue.removeFirst();
				}
				throw new StoredObjectRepresentationNotAvailableException(
						new IOException("gone"));
			}
			if (rep.mode == CopyMode.GONE_AFTER_PARTIAL_WRITE) {
				out.write(0);
				throw new StoredObjectRepresentationNotAvailableException(
						new IOException("gone"));
			}
			out.writeHeader(otp, rep.rawLength);
			out.write(rep.deflated);
		}

		@Override
		public void copyPackAsIs(PackOutputStream out, CachedPack pack) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Collection<CachedPack> getCachedPacksAndUpdate(
				BitmapBuilder needBitmap) {
			return Collections.emptyList();
		}
	}
}
