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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * DfsPackFile with the extra methods to support midx.
 * <p>
 * This is an abstract class to keep the inheritance from DfsPackFile and allow
 * a dummy implementation for single packs.
 * <p>
 * We implement at this level methods that just translate midx to pack offsets
 * and forward to the pack.
 */
public abstract sealed class DfsPackFileMidx extends DfsPackFile
		permits DfsPackFileMidxNPacks, DfsPackFileMidxSingle {

	/**
	 * Create a midx pack
	 *
	 * @param cache
	 *            dfs block cache
	 * @param desc
	 *            description of the pack, covering at least one other pack
	 * @param requiredPacks
	 *            DfsPackFile instances of the covered packs
	 * @param base
	 *            midx acting a base of this
	 * @return a midx pack
	 */
	public static DfsPackFileMidx create(DfsBlockCache cache,
			DfsPackDescription desc, List<DfsPackFile> requiredPacks,
			@Nullable DfsPackFileMidx base) {
		if (desc.getCoveredPacks().size() == 1) {
			return new DfsPackFileMidxSingle(cache, desc, requiredPacks.get(0),
					base);
		}
		return new DfsPackFileMidxNPacks(cache, desc, requiredPacks, base);
	}

	/**
	 * Default constructor
	 *
	 * @param cache
	 *            dfs block cache
	 * @param desc
	 *            midx pack description
	 */
	protected DfsPackFileMidx(DfsBlockCache cache, DfsPackDescription desc) {
		super(cache, desc);
	}

	/**
	 * Base of this multipack index
	 * <p>
	 * If this midx is part of a chain, this is its parent
	 *
	 * @return the base of this multipack index
	 */
	public abstract DfsPackFileMidx getMultipackIndexBase();

	/**
	 * Packs indexed by this multipack index (base NOT included)
	 *
	 * @return packs indexed by this multipack index
	 */
	public abstract List<DfsPackFile> getCoveredPacks();

	/**
	 * All packs indexed by this multipack index and its chain
	 * <p>
	 * This does not include the inner multipack indexes themselves, only their
	 * covered packs.
	 *
	 * @return packs indexed by this multipack index and its parents.
	 */
	public List<DfsPackFile> getAllCoveredPacks() {
		List<DfsPackFile> coveredPacks = new ArrayList<>(getCoveredPacks());
		DfsPackFileMidx base = getMultipackIndexBase();
		while (base != null) {
			coveredPacks.addAll(base.getCoveredPacks());
			base = base.getMultipackIndexBase();
		}

		return coveredPacks;
	}

	/**
	 * Get the objectId at the corresponding position in the midx chain up to
	 * this point
	 * <p>
	 * In a chain with midx-tip (100 objects) and midx-base (50 objects),
	 * positions 0-49 belong to the base midx and 50-149 to the tip midx.
	 *
	 * @param ctx
	 *            a reader for the midx data
	 * @param nthPosition
	 *            position in midx chain
	 * @return the objectId
	 * @throws IOException
	 *             a problem reading midx bytes
	 */
	abstract ObjectId getObjectAt(DfsReader ctx, long nthPosition)
			throws IOException;

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
	protected int getObjectCount(DfsReader ctx) throws IOException {
		return (int) getPackDescription().getObjectCount();
	}

	/**
	 * Return checksum of the midx
	 *
	 * @param ctx
	 *            a reader
	 * @return checksum of the midx
	 * @throws IOException
	 *             an error reading the file
	 */
	protected abstract byte[] getChecksum(DfsReader ctx) throws IOException;

	/**
	 * Get a midx iterator over the contents of *this* midx, without the base.
	 *
	 * @param ctx
	 *            a ready
	 * @return an iterator over the objects in this midx in sha1 order
	 * @throws IOException
	 *             an error loading the underlying data
	 */
	protected abstract MultiPackIndex.MidxIterator localIterator(DfsReader ctx)
			throws IOException;

	@Override
	public final PackIndex getPackIndex(DfsReader ctx) {
		return new MidxPackIndex(this, ctx);
	}

	/**
	 * Return all objects in this midx (not recursively) as ObjectToPack
	 * instances (oid, offset, type). Ordered by sha1.
	 * <p>
	 * ObjectToPack is the preferred format for the bitmap builder. This can
	 * probably be optimized.
	 *
	 * @param ctx
	 *            a reader
	 * @return list of objects in this midx (NOT in its chain) with offset and
	 *         type
	 * @throws IOException
	 *             an error reading the midx
	 */
	abstract List<ObjectToPack> getLocalObjects(DfsReader ctx)
			throws IOException;

	@Override
	public abstract PackReverseIndex getReverseIdx(DfsReader ctx)
			throws IOException;

	@Override
	ObjectLoader load(DfsReader ctx, long midxOffset) throws IOException {
		DfsPackOffset location = getOffsetCalculator().decode(midxOffset);
		if (location == null) {
			return null;
		}
		return location.getPack().load(ctx, location.getPackOffset());
	}

	@Override
	void copyAsIs(PackOutputStream out, DfsObjectToPack src, boolean validate,
			DfsReader ctx) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		if (src.pack != this) {
			throw new IllegalArgumentException(
					"pack mismatch in object description"); //$NON-NLS-1$
		}

		DfsPackOffset location = getOffsetCalculator().decode(src.offset);
		// The real pack requires the real offset
		src.offset = location.getPackOffset();
		location.getPack().copyAsIs(out, src, validate, ctx);
		// Restore, just in case
		src.offset = location.getPackStart() + location.getPackOffset();
	}

	@Override
	final byte[] getDeltaHeader(DfsReader ctx, long pos)
			throws IOException, DataFormatException {
		DfsPackOffset location = getOffsetCalculator().decode(pos);
		return location.getPack().getDeltaHeader(ctx, location.getPackOffset());
	}

	@Override
	final int getObjectType(DfsReader ctx, long pos) throws IOException {
		DfsPackOffset location = getOffsetCalculator().decode(pos);
		return location.getPack().getObjectType(ctx, location.getPackOffset());
	}

	@Override
	final long getObjectSize(DfsReader ctx, long pos) throws IOException {
		if (pos < 0) {
			return -1;
		}
		DfsPackOffset location = getOffsetCalculator().decode(pos);
		return location.getPack().getObjectSize(ctx, location.getPackOffset());
	}

	@Override
	final void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx) throws IOException {
		DfsPackOffset location = getOffsetCalculator().decode(offset);
		if (location == null) {
			throw new IllegalArgumentException("Invalid offset in midx"); //$NON-NLS-1$
		}
		// This will load the reverse index. The multipack index removes
		// duplicated objects, so next offset in midx is not necessarily the
		// following object in pack.
		location.getPack().fillRepresentation(r, location.getPackOffset(), ctx);
		// We return *our* midx offset, not the one in the pack
		r.offset = offset;
	}

	@Override
	final void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx, PackReverseIndex rev) {
		// This method shouldn't be called on the midx pack
		throw new UnsupportedOperationException();
	}

	@Override
	final boolean isCorrupt(long offset) {
		// The index must have been loaded before to have this offset
		DfsPackOffset location = getOffsetCalculator().decode(offset);
		if (location == null) {
			throw new IllegalArgumentException("Invalid offset in midx"); //$NON-NLS-1$
		}
		return location.getPack().isCorrupt(location.getPackOffset());
	}

	@Override
	final DfsBlock readOneBlock(long pos, DfsReader ctx, ReadableChannel rc)
			throws IOException {
		// The index must have been loaded before to have this offset
		DfsPackOffset location = getOffsetCalculator().decode(pos);
		return new DfsBlockMidx(location.getPack().readOneBlock(
				location.getPackOffset(), ctx, rc), location.getPackStart());
	}

	@Override
	final DfsBlock getOrLoadBlock(long pos, DfsReader ctx) throws IOException {
		// The index must have been loaded before to have this offset
		DfsPackOffset location = getOffsetCalculator().decode(pos);
		return new DfsBlockMidx(location.getPack().getOrLoadBlock(
				location.getPackOffset(), ctx), location.getPackStart());
	}

	/**
	 * Get the object calculator of this midx
	 *
	 * @return an offset calculator for this midx (including its chain)
	 */
	protected abstract VOffsetCalculator getOffsetCalculator();

	/**
	 * Translates from midx-offset (considering all packs concatenated in midx
	 * order) to (pack, offset) pair. This covers the whole midx chain.
	 *
	 * @implNote implementations take care of the encoding and chaining offset
	 *           calculators.
	 */
	protected interface VOffsetCalculator {
		/**
		 * Return the pair of pack and offset from a midx offset
		 *
		 * @param voffset
		 *            an offset in the midx chain
		 * @return the corresponding pack and offset pair
		 */
		DfsPackOffset decode(long voffset);

		/**
		 * Max offset for this DfsPackFileMidx
		 *
		 * @return max offset for this pack (including its parents)
		 */
		long getMaxOffset();
	}

	/**
	 * Data object that keeps a location readable as midx-offset or as
	 * (pack/offset).
	 * <p>
	 * midx-offset is the offset considering the concatenation of all covered
	 * packs in midx order. Only in the first pack of the base of the midx
	 * chain, the pack offsets match the midx offsets.
	 */
	protected static final class DfsPackOffset {
		private DfsPackFile pack;

		private long packStart;

		private long midxOffset;

		/**
		 * Set a location in this instance
		 *
		 * @param pack
		 *            the pack that contains the object
		 * @param packStart
		 *            midx-offset where the pack starts
		 * @param midxOffset
		 *            midx-offset
		 * @return an instance with this data
		 */
		DfsPackOffset setValues(DfsPackFile pack, long packStart,
				long midxOffset) {
			this.pack = pack;
			this.packStart = packStart;
			this.midxOffset = midxOffset;
			return this;
		}

		/**
		 * Set only the midx-offset
		 *
		 * @param midxOffset
		 *            offset in the midx
		 * @return and updated DfsPackOffset instance
		 */
		DfsPackOffset setMidxOffset(long midxOffset) {
			this.midxOffset = midxOffset;
			return this;
		}

		/**
		 * The pack containing the object
		 *
		 * @return the pack
		 */
		DfsPackFile getPack() {
			return pack;
		}

		/**
		 * Where the pack starts in the total midx concat of packs
		 * <p>
		 * After loading a block, callers will use "midx-offsets" to refer to
		 * positions. We need this to bring the value to the block.
		 *
		 * @return offset where the pack starts, when all packs are concatenated
		 *         in midx order.
		 */
		long getPackStart() {
			return packStart;
		}

		/**
		 * Offset inside the pack (regular offset)
		 *
		 * @return offset inside the pack
		 */
		long getPackOffset() {
			return midxOffset - packStart;
		}
	}

	private static class MidxPackIndex implements PackIndex {

		private final DfsPackFileMidx pack;

		private final DfsReader ctx;

		MidxPackIndex(DfsPackFileMidx pack, DfsReader ctx) {
			this.pack = pack;
			this.ctx = ctx;
		}

		@Override
		public Iterator<MutableEntry> iterator() {
			throw new UnsupportedOperationException("Not implemented yet");
		}

		@Override
		public long getObjectCount() {
			try {
				return pack.getObjectCount(ctx);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public long getOffset64Count() {
			// TODO(ifrade): This method seems to be used only for stats.
			// Maybe we can just remove it.
			return 0;
		}

		@Override
		public ObjectId getObjectId(long nthPosition) {
			try {
				return pack.getObjectAt(ctx, nthPosition);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public long getOffset(long nthPosition) {
			ObjectId objectAt;
			try {
				objectAt = pack.getObjectAt(ctx, nthPosition);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (objectAt == null) {
				return -1;
			}

			return findOffset(objectAt);
		}

		@Override
		public long findOffset(AnyObjectId objId) {
			try {
				return pack.findOffset(ctx, objId);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public int findPosition(AnyObjectId objId) {
			try {
				return pack.findIdxPosition(ctx, objId);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public long findCRC32(AnyObjectId objId)
				throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasCRC32Support() {
			return false;
		}

		@Override
		public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
				int matchLimit) throws IOException {
			pack.resolve(ctx, matches, id, matchLimit);
		}

		@Override
		public byte[] getChecksum() {
			throw new UnsupportedOperationException();
		}
	}
}
