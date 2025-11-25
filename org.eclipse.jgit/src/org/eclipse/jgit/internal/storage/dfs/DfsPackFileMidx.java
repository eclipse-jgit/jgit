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
import java.util.zip.DataFormatException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
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
		permits DfsPackFileMidxNPacks {

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
	 * Count of objects in this <b>pack</> (i.e. including, recursively, its
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

	@Override
	public PackIndex getPackIndex(DfsReader ctx) {
		throw new IllegalStateException(
				"Shouldn't use multipack index if the primary index is needed"); //$NON-NLS-1$
	}

	@Override
	public PackReverseIndex getReverseIdx(DfsReader ctx) {
		throw new IllegalStateException(
				"Shouldn't use multipack index if the reverse index is needed"); //$NON-NLS-1$
	}

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
}
