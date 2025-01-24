package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;

class DfsMultiPackIndex {
	private static final long REF_POSITION = 0;

	private final DfsPackDescription desc;
	private final DfsBlockCache cache;


	private MultiPackIndex midx;


	DfsMultiPackIndex(DfsBlockCache cache, DfsPackDescription desc) {
		this.desc = desc;
		this.cache = cache;
	}

	DfsPackDescription getPackDescription() {
		return desc;
	}

	MultiPackIndex getMultiPackIndex(DfsReader ctx) throws IOException {
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
//		if (loadedRef.get() == null) {
//			ctx.stats.ridxCacheHit++;
//		}
		midx = cachedRef.get() != null ? cachedRef.get() : loadedRef.get();
		return midx;
	}

	private static RefWithSize loadMultiPackIndex(DfsReader ctx,
			DfsPackDescription desc) throws IOException {
		try (ReadableChannel rc = ctx.db.openFile(desc, INDEX)) {
			MultiPackIndex midx = MultiPackIndexLoader
					.read(Channels.newInputStream(rc));
			// ctx.stats.readIdxBytes += rc.position();
			return new RefWithSize(midx, midx.getMemorySize());
		}
	}

	private record RefWithSize(MultiPackIndex idx, long size) {
	}
}
