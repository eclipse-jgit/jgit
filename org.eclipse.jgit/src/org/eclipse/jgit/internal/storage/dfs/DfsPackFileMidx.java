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

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.BlockList;

/**
 * Implementation of a DfsPackfile that tries to solve the queries in a
 * multipack index before resorting to the real packs.
 * <p>
 * It uses the position in the multipack index of the objects as their "offset".
 */
final class DfsPackFileMidx extends DfsPackFile {

	private static final int REF_POSITION = 0;

	private final List<DfsPackFile> packs;

	// The required packs, in the order specified in the multipack index
	private DfsPackFile[] packsInIdOrder;

	private MultiPackIndex midx;

	static DfsPackFileMidx create(DfsBlockCache cache, DfsPackDescription desc,
			List<DfsPackFile> reqPacks) {
		return new DfsPackFileMidx(cache, desc, reqPacks);
	}

	private DfsPackFileMidx(DfsBlockCache cache, DfsPackDescription desc,
			List<DfsPackFile> requiredPacks) {
		super(cache, desc);
		this.packs = requiredPacks;
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
		packsInIdOrder = packsInMidxIdOrder(midx.getPackNames());
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

	private DfsPackFile[] packsInMidxIdOrder(String[] packNames) {
		Map<String, DfsPackFile> byName = packs.stream()
				.collect(Collectors.toUnmodifiableMap(
						p -> p.getPackDescription().getPackName(),
						Function.identity()));
		DfsPackFile[] result = new DfsPackFile[packNames.length];
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

	@Override
	public PackIndex getPackIndex(DfsReader ctx) {
		throw new IllegalStateException(
				"Shouldn't use multipack index if the primary index is needed"); //$NON-NLS-1$
	}

	@Override
	public PackReverseIndex getReverseIdx(DfsReader ctx) {
		throw new IllegalStateException(
				"Shouldn't use multipack index if the reverse index is needed");
	}

	@Override
	public PackBitmapIndex getBitmapIndex(DfsReader ctx) {
		// TODO(ifrade): Implement this
		throw new UnsupportedOperationException();
	}

	@Override
	public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
		for (DfsPackFile pack : packsInIdOrder) {
			CommitGraph cg = pack.getCommitGraph(ctx);
			if (cg != null) {
				return cg;
			}
		}
		return null;
	}

	@Override
	public boolean hasObject(DfsReader ctx, AnyObjectId id) throws IOException {
		return midx(ctx).hasObject(id);
	}

	@Override
	ObjectLoader get(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset location = midx(ctx).find(id);
		if (location == null) {
			return null;
		}
		return getRealPack(location).get(ctx, id);
	}

	@Override
	ObjectLoader load(DfsReader ctx, long pos) throws IOException {
		PackOffset location = midx(ctx).findByMidxPosition((int) pos);
		DfsPackFile realPack = packsInIdOrder[location.getPackId()];
		return realPack.load(ctx, location.getOffset());
	}

	@Override
	long findOffset(DfsReader ctx, AnyObjectId id) throws IOException {
		return midx(ctx).getMidxPosition(id);
	}

	@Override
	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		midx(ctx).resolve(matches, id, matchLimit);
	}

	@Override
	void copyPackAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		if (packsInIdOrder == null) {
			if (midx != null) {
				throw new IllegalStateException(
						"Loaded the midx but didn't set the packlist");
			}
			// Copying this pack without having loaded its index ?!
			midx(ctx);
		}
		for (DfsPackFile pack : packsInIdOrder) {
			pack.copyPackAsIs(out, ctx);
		}
	}

	@Override
	void copyAsIs(PackOutputStream out, DfsObjectToPack src, boolean validate,
			DfsReader ctx) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		if (src.pack != this) {
			throw new IllegalArgumentException(
					"pack mismatch in object description");
		}
		int midxPos = (int) src.offset;
		PackOffset location = midx.findByMidxPosition(midxPos);
		// The real pack requires the real offset
		src.offset = location.getOffset();
		getRealPack(location).copyAsIs(out, src, validate, ctx);
		// Restore, just in case
		src.offset = midxPos;
	}

	@Override
	byte[] getDeltaHeader(DfsReader ctx, long pos)
			throws IOException, DataFormatException {
		PackOffset location = midx(ctx).findByMidxPosition((int) pos);
		DfsPackFile realPack = packsInIdOrder[location.getPackId()];
		return realPack.getDeltaHeader(ctx, location.getOffset());
	}

	@Override
	int getObjectType(DfsReader ctx, long pos) throws IOException {
		PackOffset location = midx(ctx).findByMidxPosition((int) pos);
		return getRealPack(location).getObjectType(ctx, location.getOffset());
	}

	@Override
	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException {
		PackOffset location = midx(ctx).find(id);
		if (location == null) {
			return -1;
		}
		return getRealPack(location).getObjectSize(ctx, id);
	}

	@Override
	long getObjectSize(DfsReader ctx, long pos) throws IOException {
		PackOffset location = midx(ctx).findByMidxPosition((int) pos);
		return getRealPack(location).getObjectSize(ctx, location.getOffset());
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
	long getIndexedObjectSize(DfsReader ctx, AnyObjectId id) {
		// TODO(ifrade): if we forward to the pack, it reads its primary index
		throw new UnsupportedOperationException();
	}

	@Override
	List<DfsObjectToPack> findAllFromPack(DfsReader ctx,
			Iterable<ObjectToPack> objects, boolean skipFound)
			throws IOException {
		List<DfsObjectToPack> tmp = new BlockList<>();
		for (ObjectToPack obj : objects) {
			DfsObjectToPack otp = (DfsObjectToPack) obj;
			if (skipFound && otp.isFound()) {
				continue;
			}
			long p = midx(ctx).getMidxPosition(otp);
			if (p < 0) {
				continue;
			}
			otp.setOffset(p);
			tmp.add(otp);
		}
		tmp.sort(OFFSET_SORT);
		return tmp;
	}

	@Override
	void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx)
			throws IOException {
		PackOffset location = midx(ctx).findByMidxPosition((int) offset);
		if (location == null) {
			throw new IllegalArgumentException("Invalid offset in midx");
		}
		// This will load the reverse index. The multipack index removes
		// duplicated objects, so next offset in midx is not necessarily the
		// following object in pack.
		getRealPack(location).fillRepresentation(r, location.getOffset(), ctx);
	}

	@Override
	void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx, PackReverseIndex rev) {
		// This method shouldn't be called on the multipack index
		throw new UnsupportedOperationException();
	}

	@Override
	boolean isCorrupt(long offset) {
		if (midx == null) {
			throw new IllegalStateException(
					"Asking isCorrupt before loading mdix");
		}
		PackOffset location = midx.findByMidxPosition((int) offset);
		if (location == null) {
			throw new IllegalArgumentException("Invalid offset in midx");
		}
		return getRealPack(location).isCorrupt(location.getOffset());
	}

	private DfsPackFile getRealPack(PackOffset location) {
		return packsInIdOrder[location.getPackId()];
	}

	private record RefWithSize(MultiPackIndex idx, long size) {
	}

	public static class InvalidMultipackIndexException extends Exception {
		private InvalidMultipackIndexException(String message) {
			super(message);
		}
	}
}
