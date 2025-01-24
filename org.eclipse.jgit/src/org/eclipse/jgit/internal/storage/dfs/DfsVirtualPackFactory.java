package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * Convert DfsPackFile into DfsVirtualPacks
 */
class DfsVirtualPackFactory {
	static DfsVirtualPack create(DfsPackFile packFile) {
		return new Single(packFile);
	}

	static DfsVirtualPack create(DfsMultiPackIndex midx,
			Map<String, DfsPackFile> packsByName)
			throws MultipackIndexMissingPackException {
		return new Multi(midx, packsByName);
	}

	public static final class MultipackIndexMissingPackException
			extends Exception {
		private MultipackIndexMissingPackException() {
			super();
		}
	}

	private DfsVirtualPackFactory() {
	}

	private static class Single implements DfsVirtualPack {
		private final DfsPackOffset po = new DfsPackOffset();

		DfsPackFile realPack;

		Single(DfsPackFile realPack) {
			this.realPack = realPack;
		}

		@Override
		public DfsPackDescription getPackDescription() {
			return realPack.getPackDescription();
		}

		@Override
		public long getLastModified() {
			return this.realPack.getPackDescription().getLastModified();
		}

		@Override
		public boolean hasObject(DfsReader ctx, AnyObjectId objectId)
				throws IOException {
			return realPack.hasObject(ctx, objectId);
		}

		@Override
		@Nullable
		public DfsPackOffset find(DfsReader ctx, AnyObjectId objectId)
				throws IOException {
			long offset = realPack.findOffset(ctx, objectId);
			if (offset <= 0) {
				return null;
			}
			return po.setValues(realPack, offset);
		}

		@Override
		@Nullable
		public ObjectLoader get(DfsReader ctx, AnyObjectId objectId)
				throws IOException {
			return realPack.get(ctx, objectId);
		}

		@Override
		public void resolve(DfsReader ctx, Set<ObjectId> matches,
				AbbreviatedObjectId id, int matchLimit) throws IOException {
			realPack.resolve(ctx, matches, id, matchLimit);
		}

		@Override
		@Nullable
		public PackBitmapIndex getBitmapIndex(DfsReader ctx)
				throws IOException {
			return realPack.getBitmapIndex(ctx);
		}

		@Override
		@Nullable
		public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
			return realPack.getCommitGraph(ctx);
		}

		@Override
		public long getObjectSize(DfsReader ctx, AnyObjectId objectId)
				throws IOException {
			return realPack.getObjectSize(ctx, objectId);
		}

		@Override
		public boolean isUnreachableGarbage() {
			return realPack.getPackDescription()
					.getPackSource() == DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
		}
	}

	private static class Multi implements DfsVirtualPack {

		private final DfsPackOffset po = new DfsPackOffset();

		private final DfsMultiPackIndex midx;

		private final DfsPackFile[] packsById;

		private final long virtualLastModified;

		private Multi(DfsMultiPackIndex midx,
				Map<String, DfsPackFile> packsByName)
				throws MultipackIndexMissingPackException {
			this.midx = midx;

			List<String> requiredPackNames = midx.getPackDescription()
					.getRequiredPacks();
			boolean requiredPacksAvailable = requiredPackNames.stream()
					.allMatch(reqPack -> packsByName.containsKey(reqPack));
			if (!requiredPacksAvailable) {
				throw new MultipackIndexMissingPackException();
			}

			packsById = new DfsPackFile[requiredPackNames.size()];
			for (int i = 0; i < requiredPackNames.size(); i++) {
				DfsPackFile pack = packsByName.remove(requiredPackNames.get(i));
				if (pack == null) {
					throw new IllegalStateException(
							"Required pack not in the stack"); //$NON-NLS-1$
				}
				packsById[i] = pack;
			}

			// Take the top "last modified" of the packs in the midx
			// for this virtual pack.
			Optional<DfsPackDescription> topPackInMidx = Arrays
					.stream(packsById).map(DfsPackFile::getPackDescription)
					.sorted(DfsPackDescription.objectLookupComparator())
					.findFirst();
			this.virtualLastModified = topPackInMidx.orElseThrow()
					.getLastModified();
		}

		@Override
		public DfsPackDescription getPackDescription() {
			return midx.getPackDescription();
		}

		@Override
		public long getLastModified() {
			return virtualLastModified;
		}

		@Override
		public boolean hasObject(DfsReader ctx, AnyObjectId objectId)
				throws IOException {
			return midx.getMultiPackIndex(ctx).hasObject(objectId);
		}

		@Override
		@Nullable
		public DfsPackOffset find(DfsReader ctx, AnyObjectId id)
				throws IOException {
			PackOffset location = midx.getMultiPackIndex(ctx).find(id);
			if (location == null) {
				return null;
			}

			return po.setValues(packsById[location.getPackId()],
					location.getOffset());
		}

		@Override
		@Nullable
		public ObjectLoader get(DfsReader ctx, AnyObjectId id)
				throws IOException {
			PackOffset location = midx.getMultiPackIndex(ctx).find(id);
			if (location == null) {
				return null;
			}
			DfsPackFile pack = packsById[location.getPackId()];
			return pack.load(ctx, location.getOffset());
		}

		@Override
		public void resolve(DfsReader ctx, Set<ObjectId> matches,
				AbbreviatedObjectId id, int matchLimit) throws IOException {
			// TODO(ifrade): Implement this
		}

		@Override
		@Nullable
		public PackBitmapIndex getBitmapIndex(DfsReader ctx)
				throws IOException {
			// TODO(ifrade): Implement bitmaps over midx. At the moment
			// we can reuse GC bitmaps if it is the only pack in the midx.
			if (packsById.length == 1) {
				return packsById[0].getBitmapIndex(ctx);
			}
			return null;
		}

		@Override
		@Nullable
		public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
			// Cache this?
			for (int i = 0; i < packsById.length; i++) {
				CommitGraph cg = packsById[i].getCommitGraph(ctx);
				if (cg != null) {
					return cg;
				}
			}
			return null;
		}

		@Override
		public long getObjectSize(DfsReader ctx, AnyObjectId id)
				throws IOException {
			PackOffset location = midx.getMultiPackIndex(ctx).find(id);
			if (location == null) {
				return -1;
			}
			DfsPackFile pack = packsById[location.getPackId()];
			return pack.getObjectSize(ctx, location.getOffset());
		}

		@Override
		public boolean isUnreachableGarbage() {
			// midx should never cover unreachable garbage packs
			return false;
		}
	}
}
