/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.util.Optionally;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

public class PackMidx extends Pack {
	private final List<Pack> packsInIdOrder;

	private final OffsetCalculator offsetCalculator;

	private Optionally<MultiPackIndex> midx = Optionally.empty();

	// TODO(ifrade): Encapsulate invalid/invalidatingCause in Pack to reuse here
	private IOException invalidatingCause;

	/**
	 * Construct a reader for an existing, pre-indexed packfile.
	 *
	 * @param cfg
	 *            configuration this directory consults for write settings.
	 * @param midxFile
	 *            path of the <code>.midx</code> file holding the data.
	 */
	public PackMidx(Config cfg, File midxFile,
			List<Pack> knownPacks) throws IOException {
		super(cfg, midxFile, null);
		// Maybe we could load only the packnames chunk at this point
		String[] packNames = getMidx().getPackNames();

		Map<String, Pack> knownPacksByName = knownPacks.stream()
				.collect(toMap(
						p -> p.getPackFile().create(PackExt.INDEX).getName(),
						Function.identity()));
		packsInIdOrder = Arrays.stream(packNames).map(knownPacksByName::get)
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableList());
		if (packsInIdOrder.size() != packNames.length) {
			throw new IOException("Midx refers to packs not in the pack list"); //$NON-NLS-1$
		}
		offsetCalculator = new OffsetCalculator(packsInIdOrder.stream()
				.mapToLong(p -> p.getPackFile().length()).toArray());

		ObjectId checksumHex = ObjectId.fromRaw(getMidx().getChecksum());
		File midxBitmaps = new File(midxFile.getParentFile(), String
				.format("multi-pack-index-%s.bitmap", checksumHex.name()));
		if (midxBitmaps.exists()) {
			setBitmapIndexFile(new PackFile(midxBitmaps));
		}
	}

	@Override
	public PackFile getPackFile() {
		return super.getPackFile();
	}

	@Override
	public PackIndex getIndex() {
		return new MidxPackIndex(midx.getOptional().get(), offsetCalculator);
	}

	@Override
	public boolean hasObjectSizeIndex() throws IOException {
		return false;
	}

	@Override
	public long getObjectSizeIndexCount() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getIndexedObjectSize(AnyObjectId id) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasObject(AnyObjectId id) throws IOException {
		return getMidx().hasObject(id);
	}

	@Override
	public boolean shouldBeKept() {
		return false;
	}

	@Override
	ObjectLoader get(WindowCursor curs, AnyObjectId id) throws IOException {
		MultiPackIndex.PackOffset packOffset = getMidx().find(id);
		if (packOffset == null) {
			return null;
		}
		return packsInIdOrder.get(packOffset.getPackId()).get(curs, id);
	}

	@Override
	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id, int matchLimit)
			throws IOException {
		getMidx().resolve(matches, id, matchLimit);
	}

	@Override
	public void close() {
		// TODO(ifrade): make closeIndice protected to plug this?
		midx = Optionally.empty();
		super.close();
	}

	@Override
	public Iterator<PackIndex.MutableEntry> iterator() {
		return getIndex().iterator();
	}

	@Override
	long getObjectCount() throws IOException {
		return getMidx().getObjectCount();
	}

	@Override
	ObjectId findObjectForOffset(long offset) throws IOException {
		MultiPackIndex.PackOffset decode = offsetCalculator.decode(offset);
		if (decode == null) {
			return null;
		}
		MultiPackIndex theMidx = getMidx();
		int bitmapPosition = theMidx.findBitmapPosition(decode);
		if (bitmapPosition < 0) {
			throw new IllegalStateException(
					"Object in midx without ridx position");
		}

		return theMidx.getObjectAtBitmapPosition(bitmapPosition);
	}

	@Override
	AnyObjectId getPackChecksum() {
		try {
			return ObjectId.fromRaw(getMidx().getChecksum());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	void copyPackAsIs(PackOutputStream out, WindowCursor curs)
			throws IOException {
		for (Pack p : packsInIdOrder) {
			p.copyPackAsIs(out, curs);
		}
	}

	@Override
	boolean beginWindowCache() throws IOException {
		boolean startedWindow = false;
		for (Pack p : packsInIdOrder) {
			startedWindow |= p.beginWindowCache();
		}
		return startedWindow;
	}

	@Override
	boolean endWindowCache() {
		boolean lastWindow = false;
		for (Pack p : packsInIdOrder) {
			lastWindow |= p.endWindowCache();
		}
		return lastWindow;
	}

	@Override
	ByteArrayWindow read(long pos, int size) throws IOException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).read(po.getOffset(), size);
	}

	@Override
	ByteWindow mmap(long pos, int size) throws IOException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).mmap(po.getOffset(), size);
	}

	@Override
	ObjectLoader load(WindowCursor curs, long pos)
			throws IOException, LargeObjectException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).load(curs, po.getOffset());
	}

	@Override
	byte[] getDeltaHeader(WindowCursor wc, long pos)
			throws IOException, DataFormatException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).getDeltaHeader(wc,
				po.getOffset());
	}

	@Override
	int getObjectType(WindowCursor curs, long pos) throws IOException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).getObjectType(curs,
				po.getOffset());
	}

	@Override
	long getObjectSize(WindowCursor curs, AnyObjectId id) throws IOException {
		MultiPackIndex.PackOffset po = getMidx().find(id);
		return packsInIdOrder.get(po.getPackId()).getObjectSize(curs,
				po.getOffset());
	}

	@Override
	long getObjectSize(WindowCursor curs, long pos) throws IOException {
		MultiPackIndex.PackOffset po = offsetCalculator.decode(pos);
		return packsInIdOrder.get(po.getPackId()).getObjectSize(curs,
				po.getOffset());
	}

	@Override
	LocalObjectRepresentation representation(WindowCursor curs,
			AnyObjectId objectId) throws IOException {
		MultiPackIndex.PackOffset po = getMidx().find(objectId);
		return packsInIdOrder.get(po.getPackId()).representation(curs,
				objectId);
	}

	private MultiPackIndex getMidx() throws IOException {
		Optional<MultiPackIndex> optional = midx.getOptional();
		if (optional.isPresent()) {
			return optional.get();
		}
		return memoizeMidxIfNeeded();
	}

	private synchronized MultiPackIndex memoizeMidxIfNeeded()
			throws IOException {
		if (invalid()) {
			throw new PackInvalidException(getPackFile(), invalidatingCause);
		}
		Optional<MultiPackIndex> optional = midx.getOptional();
		if (optional.isPresent()) {
			return optional.get();
		}

		try {
			MultiPackIndex loadedMidx = MultiPackIndexLoader
					.open(getPackFile());
			midx = optionally(loadedMidx);
			return loadedMidx;
		} catch (IOException e) {
			setInvalid();
			invalidatingCause = e;
			throw e;
		}
	}

	private static class OffsetCalculator {
		private final MultiPackIndex.PackOffset mutablePo = new MultiPackIndex.PackOffset();

		private final long[] accSizes;

		OffsetCalculator(long[] packSizes) {
			accSizes = new long[packSizes.length];
			accSizes[0] = 0;
			for (int i = 1; i < packSizes.length; i++) {
				accSizes[i] = accSizes[i - 1] + packSizes[i - 1];
			}
		}

		long encode(MultiPackIndex.PackOffset po) {
			if (po == null) {
				return -1;
			}
			return accSizes[po.getPackId()] + po.getOffset();
		}

		long encode(int packId, long offset) {
			return accSizes[packId] + offset;
		}

		MultiPackIndex.PackOffset decode(long totalOffset) {
			if (totalOffset < 0) {
				return null;
			}

			for (int i = accSizes.length - 1; i >= 0; i--) {
				if (totalOffset >= accSizes[i]) {
					return mutablePo.setValues(i, totalOffset - accSizes[i]);
				}
			}
			return null;
		}
	}

	private static class MidxPackIndex implements PackIndex {

		private final MultiPackIndex midx;

		private final OffsetCalculator offsetCalculator;

		MidxPackIndex(MultiPackIndex midx, OffsetCalculator offsetCalculator) {
			this.midx = midx;
			this.offsetCalculator = offsetCalculator;
		}

		@Override
		public Iterator<MutableEntry> iterator() {
			MultiPackIndex.MidxIterator it = midx.iterator();
			return new Iterator<MutableEntry>() {

				private final MutableEntry me = new MutableEntry();

				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public MutableEntry next() {
					MultiPackIndex.MutableEntry entry = it.next();
					me.idBuffer.fromObjectId(entry.getObjectId());
					me.offset = offsetCalculator.encode(entry.getPackId(),
							entry.getOffset());
					return me;
				}
			};
		}

		@Override
		public long getObjectCount() {
			return midx.getObjectCount();
		}

		@Override
		public long getOffset64Count() {
			return 0;
		}

		@Override
		public ObjectId getObjectId(long nthPosition) {
			return midx.getObjectAt((int) nthPosition);
		}

		@Override
		public long getOffset(long nthPosition) {
			ObjectId objectAt = midx.getObjectAt((int) nthPosition);
			MultiPackIndex.PackOffset packOffset = midx.find(objectAt);
			return offsetCalculator.encode(packOffset);
		}

		@Override
		public long findOffset(AnyObjectId objId) {
			MultiPackIndex.PackOffset packOffset = midx.find(objId);
			return offsetCalculator.encode(packOffset);
		}

		@Override
		public int findPosition(AnyObjectId objId) {
			return midx.findPosition(objId);
		}

		@Override
		public long findCRC32(AnyObjectId objId)
				throws MissingObjectException, UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasCRC32Support() {
			return false;
		}

		@Override
		public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
				int matchLimit) throws IOException {
			midx.resolve(matches, id, matchLimit);
		}

		@Override
		public byte[] getChecksum() {
			// ?? index or midx checksum?
			return midx.getChecksum();
		}
	}
}
