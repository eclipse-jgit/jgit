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

import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.BlockList;

/**
 * Implementation of a DfsPackfile that tries to solve the queries in a
 * multipack index before resorting to the real packs.
 * <p>
 * It uses the position in the multipack index of the objects as their "offset".
 */
public final class DfsPackFileMidxNPacks extends DfsPackFileMidx {

	private static final int REF_POSITION = 0;

	private final List<DfsPackFile> packs;

	// The required packs, in the order specified in the multipack index
	// Initialized lazily, when the midx is loaded
	private final DfsPackFile[] packsInIdOrder;

	private MultiPackIndex midx;

	private final DfsPackFileMidx base;

	private final VOffsetCalculatorNPacks offsetCalculator;

	DfsPackFileMidxNPacks(DfsBlockCache cache, DfsPackDescription desc,
			List<DfsPackFile> requiredPacks, @Nullable DfsPackFileMidx base) {
		super(cache, desc);
		this.base = base;
		this.packs = requiredPacks;
		String[] coveredPackNames = desc.getCoveredPacks().stream()
				.map(DfsPackDescription::getPackName).toArray(String[]::new);
		packsInIdOrder = getPacksInMidxIdOrder(coveredPackNames);
		offsetCalculator = VOffsetCalculatorNPacks.fromPacks(packsInIdOrder,
				base != null ? base.getOffsetCalculator() : null);
		this.length = offsetCalculator.getMaxOffset();
	}

	private MultiPackIndex midx(DfsReader ctx) throws IOException {
		if (midx != null) {
			return midx;
		}

		DfsStreamKey revKey = desc.getStreamKey(MULTI_PACK_INDEX);
		// Keep the value parsed in the loader, in case the Ref<> is
		// nullified in ClockBlockCacheTable#reserveSpace
		// before we read its value.
		AtomicReference<MultiPackIndex> loadedRef = new AtomicReference<>(null);
		DfsBlockCache.Ref<MultiPackIndex> cachedRef = cache.getOrLoadRef(revKey,
				REF_POSITION, () -> {
					RefWithSize midx1 = loadMultiPackIndex(ctx, desc);
					loadedRef.set(midx1.idx);
					return new DfsBlockCache.Ref<>(revKey, REF_POSITION,
							midx1.size, midx1.idx);
				});
		// if (loadedRef.get() == null) {
		// ctx.stats.ridxCacheHit;
		// }
		midx = cachedRef.get() != null ? cachedRef.get() : loadedRef.get();
		return midx;
	}

	private static RefWithSize loadMultiPackIndex(DfsReader ctx,
			DfsPackDescription desc) throws IOException {
		try (ReadableChannel rc = ctx.db.openFile(desc, MULTI_PACK_INDEX)) {
			MultiPackIndex midx = MultiPackIndexLoader
					.read(Channels.newInputStream(rc));
			// ctx.stats.readIdxBytes += rc.position();
			return new RefWithSize(midx, midx.getMemorySize());
		}
	}

	private record RefWithSize(MultiPackIndex idx, long size) {
	}

	private DfsPackFile[] getPacksInMidxIdOrder(String[] packNames) {
		Map<String, DfsPackFile> byName = packs.stream()
				.collect(Collectors.toUnmodifiableMap(
						p -> p.getPackDescription().getPackName(),
						Function.identity()));
		DfsPackFile[] result = new DfsPackFile[desc.getCoveredPacks().size()];
		for (int i = 0; i < packNames.length; i++) {
			DfsPackFile pack = byName.get(packNames[i]);
			if (pack == null) {
				// This should have been checked in the object db
				// when the pack description was loaded
				throw new IllegalStateException("Required pack missing"); //$NON-NLS-1$
			}
			result[i] = pack;
		}
		return result;
	}

	// Visible for testing
	@Override
	protected VOffsetCalculator getOffsetCalculator() {
		return offsetCalculator;
	}

	@Override
	public ObjectIdSet asObjectIdSet(DfsReader ctx) throws IOException {
		MultiPackIndex multiPackIndex = midx(ctx);
		return multiPackIndex::hasObject;
	}


	@Override
	public PackBitmapIndex getBitmapIndex(DfsReader ctx) throws IOException {
		// TODO(ifrade): at some point we will have bitmaps over the multipack
		// index
		// At the moment bitmap is in GC, at the end of the chain
		if (base != null) {
			return base.getBitmapIndex(ctx);
		}

		for (DfsPackFile pack : packsInIdOrder) {
			PackBitmapIndex bitmapIndex = pack.getBitmapIndex(ctx);
			if (bitmapIndex != null) {
				return bitmapIndex;
			}
		}
		return null;
	}

	@Override
	List<DfsPackFile> fullyIncludedIn(DfsReader ctx,
			BitmapIndex.BitmapBuilder need) throws IOException {
		List<DfsPackFile> fullyIncluded = new ArrayList<>();
		for (DfsPackFile pack : packs) {
			List<DfsPackFile> includedPacks = pack.fullyIncludedIn(ctx, need);
			if (!includedPacks.isEmpty()) {
				fullyIncluded.addAll(includedPacks);
			}
		}

		if (base != null) {
			fullyIncluded.addAll(base.fullyIncludedIn(ctx, need));
		}

		return fullyIncluded;
	}

	@Override
	public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
		for (DfsPackFile pack : packs) {
			CommitGraph cg = pack.getCommitGraph(ctx);
			if (cg != null) {
				return cg;
			}
		}
		return null;
	}

	/**
	 * Count of objects in this <b>pack</> (i.e. including, recursively, its
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
		return midx(ctx).getObjectCount() + baseObjectCount;
	}

	/**
	 * Packs indexed by this multipack index (base NOT included)
	 *
	 * @return packs indexed by this multipack index
	 */
	@Override
	public List<DfsPackFile> getCoveredPacks() {
		return packs;
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
		int p = midx(ctx).findPosition(id);
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
		if (midx(ctx).hasObject(id)) {
			return true;
		}

		if (base == null) {
			return false;
		}

		return base.hasObject(ctx, id);
	}

	@Override
	ObjectLoader get(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset location = midx(ctx).find(id);
		if (location != null) {
			return packsInIdOrder[location.getPackId()].get(ctx, id);
		}

		if (base == null) {
			return null;
		}

		return base.get(ctx, id);
	}

	@Override
	long findOffset(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset location = midx(ctx).find(id);
		if (location != null) {
			return offsetCalculator.encode(location);
		}

		if (base == null) {
			return -1;
		}

		return base.findOffset(ctx, id);
	}

	@Override
	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		midx(ctx).resolve(matches, id, matchLimit);
		if (matches.size() < matchLimit && base != null) {
			base.resolve(ctx, matches, id, matchLimit);
		}
	}

	@Override
	void copyPackAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		// Assumming the order of the packs does not really matter
		for (DfsPackFile pack : packs) {
			pack.copyPackAsIs(out, ctx);
		}

		if (base != null) {
			base.copyPackAsIs(out, ctx);
		}
	}

	@Override
	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset local = midx(ctx).find(id);
		if (local != null) {
			return packsInIdOrder[local.getPackId()].getObjectSize(ctx, id);
		}

		if (base == null) {
			return -1;
		}

		return base.getObjectSize(ctx, id);
	}

	@Override
	boolean hasObjectSizeIndex(DfsReader ctx) {
		return false;
	}

	@Override
	int getObjectSizeIndexThreshold(DfsReader ctx) {
		return Integer.MAX_VALUE;
	}

	@Override
	long getIndexedObjectSize(DfsReader ctx, int idxPosition) {
		// TODO(ifrade): if we forward to the pack, it reads its primary index
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
			long p = offsetCalculator.encode(midx(ctx).find(otp));
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


	// Visible for testing
	static class VOffsetCalculatorNPacks implements VOffsetCalculator {
		private final DfsPackFile[] packs;

		private final long[] accSizes;

		private final long baseMaxOffset;

		private final VOffsetCalculator baseOffsetCalculator;

		private final DfsPackOffset poBuffer = new DfsPackOffset();

		static VOffsetCalculatorNPacks fromPacks(DfsPackFile[] packsInIdOrder,
				VOffsetCalculator baseOffsetCalculator) {
			long[] accSizes = new long[packsInIdOrder.length + 1];
			accSizes[0] = 0;
			for (int i = 0; i < packsInIdOrder.length; i++) {
				accSizes[i + 1] = accSizes[i] + packsInIdOrder[i]
						.getPackDescription().getFileSize(PACK);
			}
			return new VOffsetCalculatorNPacks(packsInIdOrder, accSizes,
					baseOffsetCalculator);
		}

		VOffsetCalculatorNPacks(DfsPackFile[] packs, long[] packSizes,
				VOffsetCalculator baseOffsetCalculator) {
			this.packs = packs;
			this.baseOffsetCalculator = baseOffsetCalculator;
			this.baseMaxOffset = baseOffsetCalculator != null
					? baseOffsetCalculator.getMaxOffset()
					: 0;
			accSizes = packSizes;
		}

		long encode(MultiPackIndex.PackOffset location) {
			if (location == null) {
				return -1;
			}
			return location.getOffset() + accSizes[location.getPackId()]
					+ baseMaxOffset;
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
			for (int i = 0; i < accSizes.length; i++) {
				if (localOffset <= accSizes[i]) {
					return poBuffer.setValues(packs[i - 1],
							accSizes[i - 1] + baseMaxOffset, voffset);
				}
			}
			throw new IllegalArgumentException("Asking offset beyond limits"); //$NON-NLS-1$
		}

		@Override
		public long getMaxOffset() {
			return accSizes[accSizes.length - 1] + baseMaxOffset;
		}
	}
}
