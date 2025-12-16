/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.BlockList;

/**
 * A single pack that looks like a midx, to be used in the midx chain
 */
public final class DfsPackFileMidxSingle extends DfsPackFileMidx {
	private final SingleVOffsetCalculator offsetCalculator;

	private final DfsPackFile pack;

	private final DfsPackFileMidx base;

	private final LocalPackOffset poBuffer = new LocalPackOffset();

	DfsPackFileMidxSingle(DfsBlockCache cache, DfsPackDescription midxDesc,
			DfsPackFile pack, @Nullable DfsPackFileMidx base) {
		super(cache, midxDesc);
		this.pack = pack;
		offsetCalculator = new SingleVOffsetCalculator(this.pack,
				base != null ? base.getOffsetCalculator() : null);
		this.length = offsetCalculator.getMaxOffset();
		this.base = base;
	}

	// Visible for testing
	@Override
	protected VOffsetCalculator getOffsetCalculator() {
		return offsetCalculator;
	}

	@Override
	public ObjectIdSet asObjectIdSet(DfsReader ctx) throws IOException {
		return pack.asObjectIdSet(ctx);
	}

	@Override
	public PackBitmapIndex getBitmapIndex(DfsReader ctx) throws IOException {
		// TODO(ifrade): at some point we will have bitmaps over the multipack
		// index
		// At the moment bitmap is in GC, at the end of the chain
		if (base != null) {
			return base.getBitmapIndex(ctx);
		}

		return pack.getBitmapIndex(ctx);
	}

	@Override
	List<DfsPackFile> fullyIncludedIn(DfsReader ctx,
			BitmapIndex.BitmapBuilder need) throws IOException {
		List<DfsPackFile> fullyIncluded = new ArrayList<>();
		List<DfsPackFile> includedPacks = pack.fullyIncludedIn(ctx, need);
		if (!includedPacks.isEmpty()) {
			fullyIncluded.addAll(includedPacks);
		}

		if (base != null) {
			fullyIncluded.addAll(base.fullyIncludedIn(ctx, need));
		}

		return fullyIncluded;
	}

	@Override
	public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
		return pack.getCommitGraph(ctx);
	}

	/**
	 * Count of objects in this <b>pack</b> (i.e. including, recursively, its
	 * base)
	 *
	 * @param ctx
	 *            a reader
	 * @return count of objects in this pack, including its bases
	 * @throws IOException
	 *             an error reading a midx in the chain
	 */
	@Override
	protected int getObjectCount(DfsReader ctx) throws IOException {
		int baseObjectCount = base == null ? 0 : base.getObjectCount(ctx);
		return (int) pack.getPackDescription().getObjectCount()
				+ baseObjectCount;
	}

	/**
	 * Packs indexed by this multipack index (base NOT included)
	 *
	 * @return packs indexed by this multipack index
	 */
	@Override
	public List<DfsPackFile> getCoveredPacks() {
		return List.of(pack);
	}

	/**
	 * Base of this multipack index
	 * <p>
	 * If this midx is part of a chain, this is its parent
	 *
	 * @return the base of this multipack index
	 */
	@Override
	public DfsPackFileMidx getMultipackIndexBase() {
		return base;
	}

	@Override
	public int findIdxPosition(DfsReader ctx, AnyObjectId id)
			throws IOException {
		int p = pack.findIdxPosition(ctx, id);
		if (p >= 0) {
			int baseObjects = base == null ? 0 : base.getObjectCount(ctx);
			return p + baseObjects;
		}

		if (base == null) {
			return -1;
		}

		return base.findIdxPosition(ctx, id);
	}

	@Override
	public boolean hasObject(DfsReader ctx, AnyObjectId id) throws IOException {
		if (pack.hasObject(ctx, id)) {
			return true;
		}

		if (base == null) {
			return false;
		}

		return base.hasObject(ctx, id);
	}

	@Override
	ObjectLoader get(DfsReader ctx, AnyObjectId id) throws IOException {
		ObjectLoader objectLoader = pack.get(ctx, id);
		if (objectLoader != null) {
			return objectLoader;
		}

		if (base == null) {
			return null;
		}

		return base.get(ctx, id);
	}

	private PackOffset find(DfsReader ctx, AnyObjectId id) throws IOException {
		long offset = pack.findOffset(ctx, id);
		if (offset >= 0) {
			poBuffer.setOffset(offset);
			return poBuffer;
		}
		return null;
	}

	@Override
	long findOffset(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset po = find(ctx, id);
		if (po != null) {
			return offsetCalculator.encode(po);
		}

		if (base == null) {
			return -1;
		}

		return base.findOffset(ctx, id);
	}

	@Override
	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		pack.resolve(ctx, matches, id, matchLimit);
		if (matches.size() < matchLimit && base != null) {
			base.resolve(ctx, matches, id, matchLimit);
		}
	}

	@Override
	void copyPackAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		pack.copyPackAsIs(out, ctx);

		if (base != null) {
			base.copyPackAsIs(out, ctx);
		}
	}

	@Override
	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException {
		long objectSize = pack.getObjectSize(ctx, id);
		if (objectSize >= 0) {
			return objectSize;
		}

		if (base == null) {
			return -1;
		}

		return base.getObjectSize(ctx, id);
	}

	@Override
	boolean hasObjectSizeIndex(DfsReader ctx) {
		// TODO(ifrade): If the base has object size index, we can say yes
		return false;
	}

	@Override
	int getObjectSizeIndexThreshold(DfsReader ctx) {
		return Integer.MAX_VALUE;
	}

	@Override
	long getIndexedObjectSize(DfsReader ctx, int idxPosition) {
		return -1;
	}

	@Override
	List<DfsObjectToPack> findAllFromPack(DfsReader ctx,
			Iterable<ObjectToPack> objects, boolean skipFound)
			throws IOException {
		List<DfsObjectToPack> tmp = new BlockList<>();
		List<ObjectToPack> notFoundHere = new BlockList<>();
		for (ObjectToPack obj : objects) {
			DfsObjectToPack otp = (DfsObjectToPack) obj;
			if (skipFound && otp.isFound()) {
				continue;
			}
			long p = offsetCalculator.encode(find(ctx, otp));
			if (p < 0) {
				notFoundHere.add(otp);
				continue;
			}
			otp.setOffset(p);
			tmp.add(otp);
		}

		if (base != null && !notFoundHere.isEmpty()) {
			List<DfsObjectToPack> inChain = base.findAllFromPack(ctx,
					notFoundHere, skipFound);
			tmp.addAll(inChain);
		}
		tmp.sort(OFFSET_SORT);
		return tmp;
	}

	/**
	 * As this pack is always at the bottom, midx-offset is always offset
	 */
	static class SingleVOffsetCalculator implements VOffsetCalculator {

		private final long packSize;

		private final DfsPackOffset poBuffer = new DfsPackOffset();

		private final VOffsetCalculator baseOffsetCalculator;

		private final long baseMaxOffset;

		SingleVOffsetCalculator(DfsPackFile realPack,
				@Nullable VOffsetCalculator base) {
			this.packSize = realPack.getPackDescription()
					.getFileSize(PackExt.PACK);
			this.baseOffsetCalculator = base;
			baseMaxOffset = base != null ? base.getMaxOffset() : 0;
			poBuffer.setValues(realPack, baseMaxOffset, 0);
		}

		long encode(PackOffset location) {
			if (location == null) {
				return -1;
			}

			if (location.getOffset() > packSize || location.getPackId() != 0) {
				throw new IllegalArgumentException(String.format(
						"Invalid midx location (packId: %d, offset: %d)", //$NON-NLS-1$
						location.getPackId(), location.getOffset()));
			}
			return location.getOffset() + baseMaxOffset;
		}

		@Override
		public DfsPackOffset decode(long voffset) {
			if (voffset == -1) {
				return null;
			}

			if (voffset < baseMaxOffset) {
				return baseOffsetCalculator.decode(voffset);
			}

			long localOffset = voffset - baseMaxOffset;
			if (localOffset > packSize) {
				throw new IllegalArgumentException(
						"Asking offset beyond limits"); //$NON-NLS-1$
			}

			return poBuffer.setMidxOffset(voffset);
		}

		@Override
		public long getMaxOffset() {
			return packSize + baseMaxOffset;
		}
	}

	/**
	 * A packOffset with the packId and packStart fixed to 0
	 */
	private static class LocalPackOffset extends PackOffset {

		LocalPackOffset() {
			super();
			setValues(0, 0);
		}

		void setOffset(long offset) {
			super.setValues(0, offset);
		}
	}
}
