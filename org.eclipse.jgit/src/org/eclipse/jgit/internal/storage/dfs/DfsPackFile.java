/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.COMMIT_GRAPH;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.OBJECT_SIZE_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REVERSE_INDEX;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphLoader;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackObjectSizeIndex;
import org.eclipse.jgit.internal.storage.file.PackObjectSizeIndexLoader;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndexFactory;
import org.eclipse.jgit.internal.storage.pack.BinaryDelta;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.LongList;

/**
 * A Git version 2 pack file representation. A pack file contains Git objects in
 * delta packed format yielding high compression of lots of object where some
 * objects are similar.
 */
public sealed class DfsPackFile extends BlockBasedFile permits DfsPackFileMidx {
	private static final int REC_SIZE = Constants.OBJECT_ID_LENGTH + 8;

	private static final long REF_POSITION = 0;

	/**
	 * Order objects by offset
	 */
	protected static final Comparator<DfsObjectToPack> OFFSET_SORT = (
			DfsObjectToPack a,
			DfsObjectToPack b) -> Long.signum(a.getOffset() - b.getOffset());

	/**
	 * Loader for the default file-based {@link PackBitmapIndex} implementation.
	 */
	public static final PackBitmapIndexLoader DEFAULT_BITMAP_LOADER = new StreamPackBitmapIndexLoader();

	/** Index mapping {@link ObjectId} to position within the pack stream. */
	private volatile PackIndex index;

	/** Reverse version of {@link #index} mapping position to {@link ObjectId}. */
	private volatile PackReverseIndex reverseIndex;

	/** Index of compressed bitmap mapping entire object graph. */
	private volatile PackBitmapIndex bitmapIndex;

	/** Index of compressed commit graph mapping entire object graph. */
	private volatile CommitGraph commitGraph;

	private final PackBitmapIndexLoader bitmapLoader;

	/** Index by size */
	private boolean objectSizeIndexLoadAttempted;

	private volatile PackObjectSizeIndex objectSizeIndex;

	/**
	 * Objects we have tried to read, and discovered to be corrupt.
	 * <p>
	 * The list is allocated after the first corruption is found, and filled in
	 * as more entries are discovered. Typically this list is never used, as
	 * pack files do not usually contain corrupt objects.
	 */
	private volatile LongList corruptObjects;

	/** Lock for {@link #corruptObjects}. */
	private final Object corruptObjectsLock = new Object();

	private final IndexFactory indexFactory;

	/**
	 * Returns the indexes for this pack.
	 * <p>
	 * We define indexes in different sub interfaces to allow implementing the
	 * indexes over different combinations of backends.
	 * <p>
	 * Implementations decide if/how to cache the indexes. The calling
	 * DfsPackFile will keep the reference to the index as long as it needs it.
	 */
	public interface IndexFactory {
		/**
		 * Take care of loading the primary and reverse indexes for this pack.
		 */
		interface PackIndexes {
			/**
			 * Load the primary index for the pack.
			 *
			 * @param ctx
			 *            reader to find the raw bytes
			 * @return a primary index
			 * @throws IOException
			 *             a problem finding/parsing the index
			 */
			PackIndex index(DfsReader ctx) throws IOException;

			/**
			 * Load the reverse index of the pack
			 *
			 * @param ctx
			 *            reader to find the raw bytes
			 * @param idx
			 *            primary index for this reverse index (probably loaded
			 *            via #index(DfsReader))
			 * @return the reverse index of the pack
			 * @throws IOException
			 *             a problem finding/parsing the reverse index
			 */
			PackReverseIndex reverseIndex(DfsReader ctx, PackIndex idx)
					throws IOException;
		}

		/**
		 * Returns a provider of the primary and reverse indexes of this pack
		 *
		 * @return an implementation of the {@link PackIndexes} interface
		 */
		PackIndexes getPackIndexes();
	}

	/**
	 * Construct a reader for an existing, packfile.
	 *
	 * @param cache
	 *            cache that owns the pack data.
	 * @param desc
	 *            description of the pack within the DFS.
	 */
	DfsPackFile(DfsBlockCache cache, DfsPackDescription desc) {
		this(cache, desc, DEFAULT_BITMAP_LOADER,
				new CachedStreamIndexFactory(cache, desc));
	}

	/**
	 * Create an instance of DfsPackFile with a custom bitmap loader
	 *
	 * @param cache
	 *            cache that owns the pack data
	 * @param desc
	 *            description of the pack within the DFS
	 * @param bitmapLoader
	 *            loader to get the bitmaps of this pack (if any)
	 * @param indexFactory
	 *            an IndexFactory to get references to the indexes of this pack
	 */
	public DfsPackFile(DfsBlockCache cache, DfsPackDescription desc,
			PackBitmapIndexLoader bitmapLoader, IndexFactory indexFactory) {
		super(cache, desc, PACK);

		int bs = desc.getBlockSize(PACK);
		if (bs > 0) {
			setBlockSize(bs);
		}

		long sz = desc.getFileSize(PACK);
		length = sz > 0 ? sz : -1;

		this.bitmapLoader = bitmapLoader;
		this.indexFactory = indexFactory;
	}

	/**
	 * Get description that was originally used to configure this pack file.
	 *
	 * @return description that was originally used to configure this pack file.
	 */
	public DfsPackDescription getPackDescription() {
		return desc;
	}

	/**
	 * Whether the pack index file is loaded and cached in memory.
	 *
	 * @return whether the pack index file is loaded and cached in memory.
	 */
	public boolean isIndexLoaded() {
		return index != null;
	}

	void setPackIndex(PackIndex idx) {
		long objCnt = idx.getObjectCount();
		int recSize = Constants.OBJECT_ID_LENGTH + 8;
		long sz = objCnt * recSize;
		cache.putRef(desc.getStreamKey(INDEX), sz, idx);
		index = idx;
	}

	/**
	 * Get the PackIndex for this PackFile.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory.
	 * @return the PackIndex.
	 * @throws java.io.IOException
	 *             the pack index is not available, or is corrupt.
	 */
	public PackIndex getPackIndex(DfsReader ctx) throws IOException {
		return idx(ctx);
	}

	private PackIndex idx(DfsReader ctx) throws IOException {
		if (index != null) {
			return index;
		}

		if (invalid) {
			throw new PackInvalidException(getFileName(), invalidatingCause);
		}

		Repository.getGlobalListenerList()
				.dispatch(new BeforeDfsPackIndexLoadedEvent(this));
		try {
			index = indexFactory.getPackIndexes().index(ctx);
			if (index == null) {
				throw new IOException(
						"Couldn't get a reference to the primary index"); //$NON-NLS-1$
			}
			ctx.emitIndexLoad(desc, INDEX, index);
			return index;
		} catch (IOException e) {
			invalid = true;
			invalidatingCause = e;
			throw e;
		}
	}

	final boolean isGarbage() {
		return desc.getPackSource() == UNREACHABLE_GARBAGE;
	}

	/**
	 * Get the BitmapIndex for this PackFile.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory.
	 * @return the BitmapIndex.
	 * @throws java.io.IOException
	 *             the bitmap index is not available, or is corrupt.
	 */
	public PackBitmapIndex getBitmapIndex(DfsReader ctx) throws IOException {
		if (invalid || isGarbage() || !bitmapLoader.hasBitmaps(desc)) {
			return null;
		}

		if (bitmapIndex != null) {
			return bitmapIndex;
		}

		if (!bitmapLoader.keepInDfs(desc)) {
			PackBitmapIndexLoader.LoadResult result = bitmapLoader
					.loadPackBitmapIndex(ctx, this);
			if (bitmapIndex == null && result.bitmapIndex != null) {
				bitmapIndex = result.bitmapIndex;
			}
			return bitmapIndex;
		}

		DfsStreamKey bitmapKey = desc.getStreamKey(BITMAP_INDEX);
		AtomicBoolean cacheHit = new AtomicBoolean(true);
		DfsBlockCache.Ref<PackBitmapIndex> idxref = cache
				.getOrLoadRef(bitmapKey, REF_POSITION, () -> {
					cacheHit.set(false);
					return loadBitmapIndex(ctx, bitmapKey);
				});
		if (cacheHit.get()) {
			ctx.stats.bitmapCacheHit++;
		}
		PackBitmapIndex bmidx = idxref.get();
		if (bitmapIndex == null && bmidx != null) {
			bitmapIndex = bmidx;
		}
		ctx.emitIndexLoad(desc, BITMAP_INDEX, bitmapIndex);
		return bitmapIndex;
	}

	/**
	 * Get the Commit Graph for this PackFile.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory.
	 * @return {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraph},
	 *         null if pack doesn't have it.
	 * @throws java.io.IOException
	 *             the Commit Graph is not available, or is corrupt.
	 */
	public CommitGraph getCommitGraph(DfsReader ctx) throws IOException {
		if (invalid || isGarbage() || !desc.hasFileExt(COMMIT_GRAPH)) {
			return null;
		}

		if (commitGraph != null) {
			return commitGraph;
		}

		DfsStreamKey commitGraphKey = desc.getStreamKey(COMMIT_GRAPH);
		AtomicBoolean cacheHit = new AtomicBoolean(true);
		DfsBlockCache.Ref<CommitGraph> cgref = cache
				.getOrLoadRef(commitGraphKey, REF_POSITION, () -> {
					cacheHit.set(false);
					return loadCommitGraph(ctx, commitGraphKey);
				});
		if (cacheHit.get()) {
			ctx.stats.commitGraphCacheHit++;
		}
		CommitGraph cg = cgref.get();
		if (commitGraph == null && cg != null) {
			commitGraph = cg;
		}
		ctx.emitIndexLoad(desc, COMMIT_GRAPH, commitGraph);
		return commitGraph;
	}

	/**
	 * Get the PackReverseIndex for this PackFile.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory
	 * @return the PackReverseIndex
	 * @throws java.io.IOException
	 *             the pack index is not available, or is corrupt
	 */
	public PackReverseIndex getReverseIdx(DfsReader ctx) throws IOException {
		if (reverseIndex != null) {
			return reverseIndex;
		}

		reverseIndex = indexFactory.getPackIndexes().reverseIndex(ctx, idx(ctx));
		if (reverseIndex == null) {
			throw new IOException(
					"Couldn't get a reference to the reverse index"); //$NON-NLS-1$
		}
		ctx.emitIndexLoad(desc, REVERSE_INDEX, reverseIndex);
		return reverseIndex;
	}

	private PackObjectSizeIndex getObjectSizeIndex(DfsReader ctx)
			throws IOException {
		if (objectSizeIndex != null) {
			return objectSizeIndex;
		}

		if (objectSizeIndexLoadAttempted
				|| !ctx.getOptions().shouldUseObjectSizeIndex()
				|| !desc.hasFileExt(OBJECT_SIZE_INDEX)) {
			// Pack doesn't have object size index
			return null;
		}

		DfsStreamKey objSizeKey = desc.getStreamKey(OBJECT_SIZE_INDEX);
		AtomicBoolean cacheHit = new AtomicBoolean(true);
		try {
			DfsBlockCache.Ref<PackObjectSizeIndex> sizeIdxRef = cache
					.getOrLoadRef(objSizeKey, REF_POSITION, () -> {
						cacheHit.set(false);
						return loadObjectSizeIndex(ctx, objSizeKey);
					});
			if (cacheHit.get()) {
				ctx.stats.objectSizeIndexCacheHit++;
			}
			PackObjectSizeIndex sizeIdx = sizeIdxRef.get();
			if (objectSizeIndex == null && sizeIdx != null) {
				objectSizeIndex = sizeIdx;
			}
		} finally {
			objectSizeIndexLoadAttempted = true;
		}

		// Object size index is optional, it can be null and that's fine
		if (objectSizeIndex != null) {
			ctx.emitIndexLoad(desc, OBJECT_SIZE_INDEX, objectSizeIndex);
		}
		return objectSizeIndex;
	}

	/**
	 * Check if an object is stored within this pack.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory.
	 * @param id
	 *            object to be located.
	 * @return true if the object exists in this pack; false if it does not.
	 * @throws java.io.IOException
	 *             the pack index is not available, or is corrupt.
	 */
	public boolean hasObject(DfsReader ctx, AnyObjectId id) throws IOException {
		final long offset = idx(ctx).findOffset(id);
		return 0 < offset && !isCorrupt(offset);
	}

	int findIdxPosition(DfsReader ctx, AnyObjectId id) throws IOException {
		return idx(ctx).findPosition(id);
	}

	/**
	 * Get an object from this pack.
	 *
	 * @param ctx
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            the object to obtain from the pack. Must not be null.
	 * @return the object loader for the requested object if it is contained in
	 *         this pack; null if the object was not found.
	 * @throws IOException
	 *             the pack file or the index could not be read.
	 */
	ObjectLoader get(DfsReader ctx, AnyObjectId id)
			throws IOException {
		long offset = idx(ctx).findOffset(id);
		return 0 < offset && !isCorrupt(offset) ? load(ctx, offset) : null;
	}

	long findOffset(DfsReader ctx, AnyObjectId id) throws IOException {
		return idx(ctx).findOffset(id);
	}

	/**
	 * Return objects in the list available in this pack, sorted in (pack,
	 * offset) order.
	 *
	 * @param ctx
	 *            a reader
	 * @param objects
	 *            objects we are looking for
	 * @param skipFound
	 *            ignore objects already found.
	 * @return list of objects with pack and offset set.
	 * @throws IOException
	 *             an error occurred
	 */
	List<DfsObjectToPack> findAllFromPack(DfsReader ctx,
			Iterable<ObjectToPack> objects, boolean skipFound)
			throws IOException {
		List<DfsObjectToPack> tmp = new BlockList<>();
		for (ObjectToPack obj : objects) {
			DfsObjectToPack otp = (DfsObjectToPack) obj;
			if (skipFound && otp.isFound()) {
				continue;
			}
			long p = idx(ctx).findOffset(otp);
			if (p <= 0 || isCorrupt(p)) {
				continue;
			}
			otp.setOffset(p);
			tmp.add(otp);
		}
		Collections.sort(tmp, OFFSET_SORT);
		return tmp;
	}

	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		idx(ctx).resolve(matches, id, matchLimit);
	}

	private byte[] decompress(long position, int sz, DfsReader ctx)
			throws IOException, DataFormatException {
		byte[] dstbuf;
		try {
			dstbuf = new byte[sz];
		} catch (OutOfMemoryError noMemory) {
			// The size may be larger than our heap allows, return null to
			// let the caller know allocation isn't possible and it should
			// use the large object streaming approach instead.
			//
			// For example, this can occur when sz is 640 MB, and JRE
			// maximum heap size is only 256 MB. Even if the JRE has
			// 200 MB free, it cannot allocate a 640 MB byte array.
			return null;
		}

		if (ctx.inflate(this, position, dstbuf, false) != sz) {
			throw new EOFException(MessageFormat.format(
					JGitText.get().shortCompressedStreamAt,
					Long.valueOf(position)));
		}
		return dstbuf;
	}

	void copyPackAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		// If the length hasn't been determined yet, pin to set it.
		if (length == -1) {
			ctx.pin(this, 0);
			ctx.unpin();
		}
		try (ReadableChannel rc = ctx.db.openFile(desc, PACK)) {
			int sz = ctx.getOptions().getStreamPackBufferSize();
			if (sz > 0) {
				rc.setReadAheadBytes(sz);
			}
			//TODO(ifrade): report ctx.emitBlockLoaded for this copy
			if (cache.shouldCopyThroughCache(length)) {
				copyPackThroughCache(out, ctx, rc);
			} else {
				copyPackBypassCache(out, rc);
			}
		}
	}

	private void copyPackThroughCache(PackOutputStream out, DfsReader ctx,
			ReadableChannel rc) throws IOException {
		long position = 12;
		long remaining = length - (12 + 20);
		while (0 < remaining) {
			DfsBlock b = cache.getOrLoad(this, position, ctx, () -> rc);
			int ptr = (int) (position - b.start);
			if (b.size() <= ptr) {
				throw packfileIsTruncated();
			}
			int n = (int) Math.min(b.size() - ptr, remaining);
			b.write(out, position, n);
			position += n;
			remaining -= n;
		}
	}

	@SuppressWarnings("ByteBufferBackingArray")
	private long copyPackBypassCache(PackOutputStream out, ReadableChannel rc)
			throws IOException {
		ByteBuffer buf = newCopyBuffer(out, rc);
		long position = 12;
		long remaining = length - (12 + 20);
		boolean packHeadSkipped = false;
		while (0 < remaining) {
			DfsBlock b = cache.get(key, alignToBlock(position));
			if (b != null) {
				int ptr = (int) (position - b.start);
				if (b.size() <= ptr) {
					throw packfileIsTruncated();
				}
				int n = (int) Math.min(b.size() - ptr, remaining);
				b.write(out, position, n);
				position += n;
				remaining -= n;
				rc.position(position);
				packHeadSkipped = true;
				continue;
			}

			// Need to skip the 'PACK' header for the first read
			int ptr = packHeadSkipped ? 0 : 12;
			buf.position(0);
			int bufLen = read(rc, buf);
			if (bufLen <= ptr) {
				throw packfileIsTruncated();
			}
			int n = (int) Math.min(bufLen - ptr, remaining);
			out.write(buf.array(), ptr, n);
			position += n;
			remaining -= n;
			packHeadSkipped = true;
		}
		return position;
	}

	private ByteBuffer newCopyBuffer(PackOutputStream out, ReadableChannel rc) {
		int bs = blockSize(rc);
		byte[] copyBuf = out.getCopyBuffer();
		if (bs > copyBuf.length) {
			copyBuf = new byte[bs];
		}
		return ByteBuffer.wrap(copyBuf, 0, bs);
	}

	void copyAsIs(PackOutputStream out, DfsObjectToPack src,
			boolean validate, DfsReader ctx) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		final CRC32 crc1 = validate ? new CRC32() : null;
		final CRC32 crc2 = validate ? new CRC32() : null;
		final byte[] buf = out.getCopyBuffer();

		// Rip apart the header so we can discover the size.
		//
		try {
			readFully(src.offset, buf, 0, 20, ctx);
		} catch (IOException ioError) {
			throw new StoredObjectRepresentationNotAvailableException(ioError);
		}
		int c = buf[0] & 0xff;
		final int typeCode = (c >> 4) & 7;
		long inflatedLength = c & 15;
		int shift = 4;
		int headerCnt = 1;
		while ((c & 0x80) != 0) {
			c = buf[headerCnt++] & 0xff;
			inflatedLength += ((long) (c & 0x7f)) << shift;
			shift += 7;
		}

		if (typeCode == Constants.OBJ_OFS_DELTA) {
			do {
				c = buf[headerCnt++] & 0xff;
			} while ((c & 128) != 0);
			if (validate) {
				assert(crc1 != null && crc2 != null);
				crc1.update(buf, 0, headerCnt);
				crc2.update(buf, 0, headerCnt);
			}
		} else if (typeCode == Constants.OBJ_REF_DELTA) {
			if (validate) {
				assert(crc1 != null && crc2 != null);
				crc1.update(buf, 0, headerCnt);
				crc2.update(buf, 0, headerCnt);
			}

			readFully(src.offset + headerCnt, buf, 0, 20, ctx);
			if (validate) {
				assert(crc1 != null && crc2 != null);
				crc1.update(buf, 0, 20);
				crc2.update(buf, 0, 20);
			}
			headerCnt += 20;
		} else if (validate) {
			assert(crc1 != null && crc2 != null);
			crc1.update(buf, 0, headerCnt);
			crc2.update(buf, 0, headerCnt);
		}

		final long dataOffset = src.offset + headerCnt;
		final long dataLength = src.length;
		final long expectedCRC;
		final DfsBlock quickCopy;

		// Verify the object isn't corrupt before sending. If it is,
		// we report it missing instead.
		//
		try {
			quickCopy = ctx.quickCopy(this, dataOffset, dataLength);

			if (validate && idx(ctx).hasCRC32Support()) {
				assert(crc1 != null);
				// Index has the CRC32 code cached, validate the object.
				//
				expectedCRC = idx(ctx).findCRC32(src);
				if (quickCopy != null) {
					quickCopy.crc32(crc1, dataOffset, (int) dataLength);
				} else {
					long pos = dataOffset;
					long cnt = dataLength;
					while (cnt > 0) {
						final int n = (int) Math.min(cnt, buf.length);
						readFully(pos, buf, 0, n, ctx);
						crc1.update(buf, 0, n);
						pos += n;
						cnt -= n;
					}
				}
				if (crc1.getValue() != expectedCRC) {
					setCorrupt(src.offset);
					throw new CorruptObjectException(MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream,
							Long.valueOf(src.offset), getFileName()));
				}
			} else if (validate) {
				assert(crc1 != null);
				// We don't have a CRC32 code in the index, so compute it
				// now while inflating the raw data to get zlib to tell us
				// whether or not the data is safe.
				//
				Inflater inf = ctx.inflater();
				byte[] tmp = new byte[1024];
				if (quickCopy != null) {
					quickCopy.check(inf, tmp, dataOffset, (int) dataLength);
				} else {
					long pos = dataOffset;
					long cnt = dataLength;
					while (cnt > 0) {
						final int n = (int) Math.min(cnt, buf.length);
						readFully(pos, buf, 0, n, ctx);
						crc1.update(buf, 0, n);
						inf.setInput(buf, 0, n);
						while (inf.inflate(tmp, 0, tmp.length) > 0) {
							continue;
						}
						pos += n;
						cnt -= n;
					}
				}
				if (!inf.finished() || inf.getBytesRead() != dataLength) {
					setCorrupt(src.offset);
					throw new EOFException(MessageFormat.format(
							JGitText.get().shortCompressedStreamAt,
							Long.valueOf(src.offset)));
				}
				expectedCRC = crc1.getValue();
			} else {
				expectedCRC = -1;
			}
		} catch (DataFormatException dataFormat) {
			setCorrupt(src.offset);

			CorruptObjectException corruptObject = new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream,
							Long.valueOf(src.offset), getFileName()),
					dataFormat);

			throw new StoredObjectRepresentationNotAvailableException(
					corruptObject);

		} catch (IOException ioError) {
			throw new StoredObjectRepresentationNotAvailableException(ioError);
		}

		if (quickCopy != null) {
			// The entire object fits into a single byte array window slice,
			// and we have it pinned.  Write this out without copying.
			//
			out.writeHeader(src, inflatedLength);
			quickCopy.write(out, dataOffset, (int) dataLength);

		} else if (dataLength <= buf.length) {
			// Tiny optimization: Lots of objects are very small deltas or
			// deflated commits that are likely to fit in the copy buffer.
			//
			if (!validate) {
				long pos = dataOffset;
				long cnt = dataLength;
				while (cnt > 0) {
					final int n = (int) Math.min(cnt, buf.length);
					readFully(pos, buf, 0, n, ctx);
					pos += n;
					cnt -= n;
				}
			}
			out.writeHeader(src, inflatedLength);
			out.write(buf, 0, (int) dataLength);
		} else {
			// Now we are committed to sending the object. As we spool it out,
			// check its CRC32 code to make sure there wasn't corruption between
			// the verification we did above, and us actually outputting it.
			//
			out.writeHeader(src, inflatedLength);
			long pos = dataOffset;
			long cnt = dataLength;
			while (cnt > 0) {
				final int n = (int) Math.min(cnt, buf.length);
				readFully(pos, buf, 0, n, ctx);
				if (validate) {
					assert(crc2 != null);
					crc2.update(buf, 0, n);
				}
				out.write(buf, 0, n);
				pos += n;
				cnt -= n;
			}
			if (validate) {
				assert(crc2 != null);
				if (crc2.getValue() != expectedCRC) {
					throw new CorruptObjectException(MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream,
							Long.valueOf(src.offset), getFileName()));
				}
			}
		}
	}

	private IOException packfileIsTruncated() {
		invalid = true;
		IOException exc = new IOException(MessageFormat.format(
				JGitText.get().packfileIsTruncated, getFileName()));
		invalidatingCause = exc;
		return exc;
	}

	private void readFully(long position, byte[] dstbuf, int dstoff, int cnt,
			DfsReader ctx) throws IOException {
		while (cnt > 0) {
			int copied = ctx.copy(this, position, dstbuf, dstoff, cnt);
			if (copied == 0) {
				throw new EOFException();
			}
			position += copied;
			dstoff += copied;
			cnt -= copied;
		}
	}

	ObjectLoader load(DfsReader ctx, long pos)
			throws IOException {
		try {
			final byte[] ib = ctx.tempId;
			Delta delta = null;
			byte[] data = null;
			int type = Constants.OBJ_BAD;
			boolean cached = false;

			SEARCH: for (;;) {
				readFully(pos, ib, 0, 20, ctx);
				int c = ib[0] & 0xff;
				final int typeCode = (c >> 4) & 7;
				long sz = c & 15;
				int shift = 4;
				int p = 1;
				while ((c & 0x80) != 0) {
					c = ib[p++] & 0xff;
					sz += ((long) (c & 0x7f)) << shift;
					shift += 7;
				}

				switch (typeCode) {
				case Constants.OBJ_COMMIT:
				case Constants.OBJ_TREE:
				case Constants.OBJ_BLOB:
				case Constants.OBJ_TAG: {
					if (delta != null) {
						data = decompress(pos + p, (int) sz, ctx);
						type = typeCode;
						break SEARCH;
					}

					if (sz < ctx.getStreamFileThreshold()) {
						data = decompress(pos + p, (int) sz, ctx);
						if (data != null) {
							return new ObjectLoader.SmallObject(typeCode, data);
						}
					}
					return new LargePackedWholeObject(typeCode, sz, pos, p, this, ctx.db);
				}

				case Constants.OBJ_OFS_DELTA: {
					c = ib[p++] & 0xff;
					long base = c & 127;
					while ((c & 128) != 0) {
						base += 1;
						c = ib[p++] & 0xff;
						base <<= 7;
						base += (c & 127);
					}
					base = pos - base;
					delta = new Delta(delta, pos, (int) sz, p, base);
					if (sz != delta.deltaSize) {
						break SEARCH;
					}

					DeltaBaseCache.Entry e = ctx.getDeltaBaseCache().get(key, base);
					if (e != null) {
						type = e.type;
						data = e.data;
						cached = true;
						break SEARCH;
					}
					pos = base;
					continue SEARCH;
				}

				case Constants.OBJ_REF_DELTA: {
					readFully(pos + p, ib, 0, 20, ctx);
					long base = findDeltaBase(ctx, ObjectId.fromRaw(ib));
					delta = new Delta(delta, pos, (int) sz, p + 20, base);
					if (sz != delta.deltaSize) {
						break SEARCH;
					}

					DeltaBaseCache.Entry e = ctx.getDeltaBaseCache().get(key, base);
					if (e != null) {
						type = e.type;
						data = e.data;
						cached = true;
						break SEARCH;
					}
					pos = base;
					continue SEARCH;
				}

				default:
					throw new IOException(MessageFormat.format(
							JGitText.get().unknownObjectType, Integer.valueOf(typeCode)));
				}
			}

			// At this point there is at least one delta to apply to data.
			// (Whole objects with no deltas to apply return early above.)

			if (data == null)
				throw new LargeObjectException();

			assert(delta != null);
			do {
				// Cache only the base immediately before desired object.
				if (cached) {
					cached = false;
				} else if (delta.next == null) {
					ctx.getDeltaBaseCache().put(key, delta.basePos, type, data);
				}

				pos = delta.deltaPos;

				byte[] cmds = decompress(pos + delta.hdrLen, delta.deltaSize, ctx);
				if (cmds == null) {
					data = null; // Discard base in case of OutOfMemoryError
					throw new LargeObjectException();
				}

				final long sz = BinaryDelta.getResultSize(cmds);
				if (Integer.MAX_VALUE <= sz) {
					throw new LargeObjectException.ExceedsByteArrayLimit();
				}

				final byte[] result;
				try {
					result = new byte[(int) sz];
				} catch (OutOfMemoryError tooBig) {
					data = null; // Discard base in case of OutOfMemoryError
					cmds = null;
					throw new LargeObjectException.OutOfMemory(tooBig);
				}

				BinaryDelta.apply(data, cmds, result);
				data = result;
				delta = delta.next;
			} while (delta != null);

			return new ObjectLoader.SmallObject(type, data);

		} catch (DataFormatException dfe) {
			throw new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream, Long.valueOf(pos),
							getFileName()),
					dfe);
		}
	}

	private long findDeltaBase(DfsReader ctx, ObjectId baseId)
			throws IOException, MissingObjectException {
		long ofs = idx(ctx).findOffset(baseId);
		if (ofs < 0) {
			throw new MissingObjectException(baseId,
					JGitText.get().missingDeltaBase);
		}
		return ofs;
	}

	private static class Delta {
		/** Child that applies onto this object. */
		final Delta next;

		/** Offset of the delta object. */
		final long deltaPos;

		/** Size of the inflated delta stream. */
		final int deltaSize;

		/** Total size of the delta's pack entry header (including base). */
		final int hdrLen;

		/** Offset of the base object this delta applies onto. */
		final long basePos;

		Delta(Delta next, long ofs, int sz, int hdrLen, long baseOffset) {
			this.next = next;
			this.deltaPos = ofs;
			this.deltaSize = sz;
			this.hdrLen = hdrLen;
			this.basePos = baseOffset;
		}
	}

	byte[] getDeltaHeader(DfsReader wc, long pos)
			throws IOException, DataFormatException {
		// The delta stream starts as two variable length integers. If we
		// assume they are 64 bits each, we need 16 bytes to encode them,
		// plus 2 extra bytes for the variable length overhead. So 18 is
		// the longest delta instruction header.
		//
		final byte[] hdr = new byte[32];
		wc.inflate(this, pos, hdr, true /* header only */);
		return hdr;
	}

	int getObjectType(DfsReader ctx, long pos) throws IOException {
		final byte[] ib = ctx.tempId;
		for (;;) {
			readFully(pos, ib, 0, 20, ctx);
			int c = ib[0] & 0xff;
			final int type = (c >> 4) & 7;

			switch (type) {
			case Constants.OBJ_COMMIT:
			case Constants.OBJ_TREE:
			case Constants.OBJ_BLOB:
			case Constants.OBJ_TAG:
				return type;

			case Constants.OBJ_OFS_DELTA: {
				int p = 1;
				while ((c & 0x80) != 0) {
					c = ib[p++] & 0xff;
				}
				c = ib[p++] & 0xff;
				long ofs = c & 127;
				while ((c & 128) != 0) {
					ofs += 1;
					c = ib[p++] & 0xff;
					ofs <<= 7;
					ofs += (c & 127);
				}
				pos = pos - ofs;
				continue;
			}

			case Constants.OBJ_REF_DELTA: {
				int p = 1;
				while ((c & 0x80) != 0) {
					c = ib[p++] & 0xff;
				}
				readFully(pos + p, ib, 0, 20, ctx);
				pos = findDeltaBase(ctx, ObjectId.fromRaw(ib));
				continue;
			}

			default:
				throw new IOException(MessageFormat.format(
						JGitText.get().unknownObjectType, Integer.valueOf(type)));
			}
		}
	}

	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException {
		final long offset = idx(ctx).findOffset(id);
		return 0 < offset ? getObjectSize(ctx, offset) : -1;
	}

	long getObjectSize(DfsReader ctx, long pos)
			throws IOException {
		final byte[] ib = ctx.tempId;
		readFully(pos, ib, 0, 20, ctx);
		int c = ib[0] & 0xff;
		final int type = (c >> 4) & 7;
		long sz = c & 15;
		int shift = 4;
		int p = 1;
		while ((c & 0x80) != 0) {
			c = ib[p++] & 0xff;
			sz += ((long) (c & 0x7f)) << shift;
			shift += 7;
		}

		long deltaAt;
		switch (type) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			return sz;

		case Constants.OBJ_OFS_DELTA:
			c = ib[p++] & 0xff;
			while ((c & 128) != 0) {
				c = ib[p++] & 0xff;
			}
			deltaAt = pos + p;
			break;

		case Constants.OBJ_REF_DELTA:
			deltaAt = pos + p + 20;
			break;

		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().unknownObjectType, Integer.valueOf(type)));
		}

		try {
			return BinaryDelta.getResultSize(getDeltaHeader(ctx, deltaAt));
		} catch (DataFormatException dfe) {
			throw new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream, Long.valueOf(pos),
							getFileName()),
					dfe);
		}
	}

	/**
	 * Return if this packfile has an object size index attached.
	 *
	 * This loads the index if it is not already in memory.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the object size index is not already loaded in memory.
	 * @return true if the packfile has an object size index.
	 * @throws IOException
	 *             a problem accessing storage while looking for the index
	 */
	boolean hasObjectSizeIndex(DfsReader ctx) throws IOException {
			return getObjectSizeIndex(ctx) != null;
	}

	/**
	 * Return minimum size for an object to be included in the object size
	 * index.
	 *
	 * Caller must make sure the pack has an object size index with
	 * {@link #hasObjectSizeIndex} before calling this method.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the object size index is not already loaded in memory.
	 * @return minimum size for indexing in bytes
	 * @throws IOException
	 *             no object size index or a problem loading it.
	 */
	int getObjectSizeIndexThreshold(DfsReader ctx) throws IOException {
		PackObjectSizeIndex idx = getObjectSizeIndex(ctx);
		if (idx == null) {
			throw new IOException("Asking threshold of non-existing obj-size"); //$NON-NLS-1$
		}
		return idx.getThreshold();
	}

	/**
	 * Return the size of the object from the object-size index. The object
	 * should be a blob. Any other type is not indexed and returns -1.
	 * <p>
	 * Caller MUST pass a valid index position, as returned by
	 * {@link #findIdxPosition(DfsReader, AnyObjectId)} and verify the pack has
	 * object size index (e.g. with {@link #hasObjectSizeIndex(DfsReader)})
	 * before asking the indexed size.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the object size index is not already loaded in memory.
	 * @param idxPosition
	 *            position in the primary index of the object we are looking
	 *            for, as returned by findIdxPosition
	 * @return size of the object from the index. Negative if object is not in
	 *         the index (below threshold or not a blob)
	 * @throws IOException
	 *             could not read the object size index. IO problem or the pack
	 *             doesn't have it.
	 */
	long getIndexedObjectSize(DfsReader ctx, int idxPosition)
			throws IOException {
		if (idxPosition < 0) {
			throw new IllegalArgumentException("Invalid index position"); //$NON-NLS-1$
		}
		PackObjectSizeIndex sizeIdx = getObjectSizeIndex(ctx);
		if (sizeIdx == null) {
			throw new IllegalStateException(
					"Asking indexed size from a pack without object size index"); //$NON-NLS-1$
		}

		return sizeIdx.getSize(idxPosition);
	}

	/**
	 * Return pack(s) with all its objects included in the bitmap.
	 * <p>
	 * The bitmap is modified removing the objects in the returned packs.
	 * <p>
	 * The list could contain more than one pack in the multipack-index case.
	 *
	 * @param ctx
	 *            a reader
	 * @param need
	 *            bitmap with the required objects. It can be modified in this
	 *            call. The objects from the returned packs are removed from the
	 *            bitmap.
	 * @return list of packs with ALL their objects in the bitmap.
	 * @throws IOException
	 *             error reading the bitmap index.
	 */
	@NonNull
	List<DfsPackFile> fullyIncludedIn(DfsReader ctx,
			BitmapIndex.BitmapBuilder need) throws IOException {
		PackBitmapIndex bi = this.getBitmapIndex(ctx);
		if (bi != null && need.removeAllOrNone(bi)) {
			return Collections.singletonList(this);
		}

		return Collections.emptyList();
	}

	/**
	 * Populates the representation object with the details of how the object at
	 * "pos" is stored in this pack (e.g. whole or deltified, its packed
	 * length).
	 *
	 * @param r
	 *            represention object to carry data
	 * @param offset
	 *            offset in this pack of the object
	 * @param ctx
	 *            a reader
	 * @throws IOException
	 *             an error reading the object from disk
	 */
	void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx) throws IOException {
		fillRepresentation(r, offset, ctx, getReverseIdx(ctx));
	}

	/**
	 * Populates the representation object with the details of how the object at
	 * "pos" is stored in this pack (e.g. whole or deltified, its packed
	 * length).
	 *
	 * @param r
	 *            represention object to carry data
	 * @param offset
	 *            offset in this pack of the object
	 * @param ctx
	 *            a reader
	 * @param rev
	 *            reverse index of this pack
	 * @throws IOException
	 *             an error reading the object from disk
	 */
	void fillRepresentation(DfsObjectRepresentation r, long offset,
			DfsReader ctx, PackReverseIndex rev)
			throws IOException {
		r.offset = offset;
		final byte[] ib = ctx.tempId;
		readFully(offset, ib, 0, 20, ctx);
		int c = ib[0] & 0xff;
		int p = 1;
		final int typeCode = (c >> 4) & 7;
		while ((c & 0x80) != 0) {
			c = ib[p++] & 0xff;
		}

		long len = rev.findNextOffset(offset, length - 20) - offset;
		switch (typeCode) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			r.format = StoredObjectRepresentation.PACK_WHOLE;
			r.baseId = null;
			r.length = len - p;
			return;

		case Constants.OBJ_OFS_DELTA: {
			c = ib[p++] & 0xff;
			long ofs = c & 127;
			while ((c & 128) != 0) {
				ofs += 1;
				c = ib[p++] & 0xff;
				ofs <<= 7;
				ofs += (c & 127);
			}
			r.format = StoredObjectRepresentation.PACK_DELTA;
			r.baseId = rev.findObject(offset - ofs);
			r.length = len - p;
			return;
		}

		case Constants.OBJ_REF_DELTA: {
			readFully(offset + p, ib, 0, 20, ctx);
			r.format = StoredObjectRepresentation.PACK_DELTA;
			r.baseId = ObjectId.fromRaw(ib);
			r.length = len - p - 20;
			return;
		}

		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().unknownObjectType, Integer.valueOf(typeCode)));
		}
	}

	boolean isCorrupt(long offset) {
		LongList list = corruptObjects;
		if (list == null) {
			return false;
		}
		synchronized (list) {
			return list.contains(offset);
		}
	}

	private void setCorrupt(long offset) {
		LongList list = corruptObjects;
		if (list == null) {
			synchronized (corruptObjectsLock) {
				list = corruptObjects;
				if (list == null) {
					list = new LongList();
					corruptObjects = list;
				}
			}
		}
		synchronized (list) {
			list.add(offset);
		}
	}

	private DfsBlockCache.Ref<PackObjectSizeIndex> loadObjectSizeIndex(
			DfsReader ctx, DfsStreamKey objectSizeIndexKey) throws IOException {
		ctx.stats.readObjectSizeIndex++;
		long start = System.nanoTime();
		long size = 0;
		Exception parsingError = null;
		try (ReadableChannel rc = ctx.db.openFile(desc, OBJECT_SIZE_INDEX)) {
			try {
				objectSizeIndex = PackObjectSizeIndexLoader
						.load(Channels.newInputStream(rc));
				size = rc.position();
			} catch (IOException e) {
				parsingError = e;
			}
		} catch (IOException e) {
			// No object size index in this pack
			return new DfsBlockCache.Ref<>(objectSizeIndexKey, REF_POSITION, 0,
					null);
		}

		if (parsingError != null) {
			throw new IOException(
					MessageFormat.format(DfsText.get().shortReadOfIndex,
							desc.getFileName(OBJECT_SIZE_INDEX)),
					parsingError);
		}

		ctx.stats.readObjectSizeIndexBytes += size;
		ctx.stats.readObjectSizeIndexMicros += elapsedMicros(start);
		return new DfsBlockCache.Ref<>(objectSizeIndexKey, REF_POSITION, size,
				objectSizeIndex);
	}

	private DfsBlockCache.Ref<PackBitmapIndex> loadBitmapIndex(DfsReader ctx,
			DfsStreamKey bitmapKey) throws IOException {
		ctx.stats.readBitmap++;
		long start = System.nanoTime();
		PackBitmapIndexLoader.LoadResult result = bitmapLoader
				.loadPackBitmapIndex(ctx, this);
		bitmapIndex = result.bitmapIndex;
		ctx.stats.readBitmapIdxBytes += result.bytesRead;
		ctx.stats.readBitmapIdxMicros += elapsedMicros(start);
		return new DfsBlockCache.Ref<>(bitmapKey, REF_POSITION,
				result.bytesRead, result.bitmapIndex);
	}

	private DfsBlockCache.Ref<CommitGraph> loadCommitGraph(DfsReader ctx,
			DfsStreamKey cgkey) throws IOException {
		ctx.stats.readCommitGraph++;
		long start = System.nanoTime();
		StoredConfig repoConfig = ctx.db.getRepository().getConfig();
		boolean readChangedPathFilters = repoConfig.getBoolean(
				ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS, false);
		try (ReadableChannel rc = ctx.db.openFile(desc, COMMIT_GRAPH)) {
			long size;
			CommitGraph cg;
			try {
				cg = CommitGraphLoader.read(alignTo8kBlocks(rc),
						readChangedPathFilters);
			} finally {
				size = rc.position();
				ctx.stats.readCommitGraphBytes += size;
				ctx.stats.readCommitGraphMicros += elapsedMicros(start);
			}
			commitGraph = cg;
			return new DfsBlockCache.Ref<>(cgkey, REF_POSITION, size, cg);
		} catch (IOException e) {
			throw new IOException(
					MessageFormat.format(DfsText.get().cannotReadCommitGraph,
							desc.getFileName(COMMIT_GRAPH)),
					e);
		}
	}

	private static InputStream alignTo8kBlocks(ReadableChannel rc) {
		// TODO(ifrade): This is not reading from DFS, so the channel should
		// know better the right blocksize. I don't know why this was done in
		// the first place, verify and remove if not needed.
		InputStream in = Channels.newInputStream(rc);
		int wantSize = 8192;
		int bs = rc.blockSize();
		if (0 < bs && bs < wantSize) {
			bs = (wantSize / bs) * bs;
		} else if (bs <= 0) {
			bs = wantSize;
		}
		return new BufferedInputStream(in, bs);
	}

	/**
	 * Loads the PackBitmapIndex associated with this packfile
	 */
	public interface PackBitmapIndexLoader {
		/**
		 * Does this pack has bitmaps associated?
		 *
		 * @param desc
		 *            the pack
		 * @return true if the pack has bitmaps
		 */
		boolean hasBitmaps(DfsPackDescription desc);

		/**
		 * If the returned instance must be kept in DFS cache
		 *
		 * It should be true when the instance is expensive to load and can be
		 * reused.
		 *
		 * @param desc
		 *            the pack
		 * @return true if the returned bitmap index should be kept in DFS
		 */
		boolean keepInDfs(DfsPackDescription desc);

		/**
		 * Returns a PackBitmapIndex for this pack, if the pack has bitmaps
		 * associated.
		 *
		 * @param ctx
		 *            the reader
		 * @param pack
		 *            the pack
		 * @return the pack bitmap index and bytes size (when applicable)
		 * @throws IOException
		 *             error accessing storage
		 */
		LoadResult loadPackBitmapIndex(DfsReader ctx, DfsPackFile pack)
				throws IOException;

		/**
		 * The instance of the pack bitmap index and the amount of bytes loaded.
		 *
		 * The bytes can be 0, if the implementation doesn't do any initial
		 * loading.
		 */
		public class LoadResult {
			/** The loaded {@link PackBitmapIndex}. */
			public final PackBitmapIndex bitmapIndex;

			/** The bytes read upon initial load (may be 0). */
			public final long bytesRead;

			/**
			 * Constructs the LoadResult.
			 *
			 * @param packBitmapIndex
			 *            the loaded index.
			 * @param bytesRead
			 *            the bytes read upon loading.
			 */
			public LoadResult(PackBitmapIndex packBitmapIndex, long bytesRead) {
				this.bitmapIndex = packBitmapIndex;
				this.bytesRead = bytesRead;
			}
		}
	}

	/**
	 * Load the packbitmapindex from the BITMAP_INDEX pack extension
	 */
	private static final class StreamPackBitmapIndexLoader implements PackBitmapIndexLoader {
		@Override
		public boolean hasBitmaps(DfsPackDescription desc) {
			return desc.hasFileExt(BITMAP_INDEX);
		}

		@Override
		public boolean keepInDfs(DfsPackDescription desc) {
			return true;
		}

		@Override
		public LoadResult loadPackBitmapIndex(DfsReader ctx, DfsPackFile pack)
				throws IOException {
			DfsPackDescription desc = pack.getPackDescription();
			try (ReadableChannel rc = ctx.db.openFile(desc, BITMAP_INDEX)) {
				long size;
				PackBitmapIndex bmidx;
				try {
					bmidx = PackBitmapIndex.read(alignTo8kBlocks(rc),
							() -> pack.idx(ctx), () -> pack.getReverseIdx(ctx),
							ctx.getOptions().shouldLoadRevIndexInParallel());
				} finally {
					size = rc.position();
				}
				return new LoadResult(bmidx, size);
			} catch (EOFException e) {
				throw new IOException(
						MessageFormat.format(DfsText.get().shortReadOfIndex,
								desc.getFileName(BITMAP_INDEX)),
						e);
			} catch (IOException e) {
				throw new IOException(
						MessageFormat.format(DfsText.get().cannotReadIndex,
								desc.getFileName(BITMAP_INDEX)),
						e);
			}
		}
	}

	/**
	 * An index factory backed by Dfs streams and references cached in
	 * DfsBlockCache
	 */
	public static final class CachedStreamIndexFactory implements IndexFactory {
		private final CachedStreamPackIndexes indexes;

		/**
		 * An index factory
		 *
		 * @param cache
		 *            DFS block cache to use for the references
		 * @param desc
		 *            This factory loads indexes for this package
		 */
		public CachedStreamIndexFactory(DfsBlockCache cache,
				DfsPackDescription desc) {
			this.indexes = new CachedStreamPackIndexes(cache, desc);
		}

		@Override
		public PackIndexes getPackIndexes() {
			return indexes;
		}
	}

	/**
	 * Load primary and reverse index from Dfs streams and cache the references
	 * in DfsBlockCache.
	 */
	public static final class CachedStreamPackIndexes implements IndexFactory.PackIndexes {
		private final DfsBlockCache cache;

		private final DfsPackDescription desc;

		/**
		 * An index factory
		 *
		 * @param cache
		 *            DFS block cache to use for the references
		 * @param desc This factory loads indexes for this package
		 */
		public CachedStreamPackIndexes(DfsBlockCache cache,
									   DfsPackDescription desc) {
			this.cache = cache;
			this.desc = desc;
		}

		@Override
		public PackIndex index(DfsReader ctx) throws IOException {
			DfsStreamKey idxKey = desc.getStreamKey(INDEX);
			// Keep the value parsed in the loader, in case the Ref<> is
			// nullified in ClockBlockCacheTable#reserveSpace
			// before we read its value.
			AtomicReference<PackIndex> loadedRef = new AtomicReference<>(null);
			DfsBlockCache.Ref<PackIndex> cachedRef = cache.getOrLoadRef(idxKey,
					REF_POSITION, () -> {
						RefWithSize<PackIndex> idx = loadPackIndex(ctx, desc);
						loadedRef.set(idx.ref);
						return new DfsBlockCache.Ref<>(idxKey, REF_POSITION,
								idx.size, idx.ref);
					});
			if (loadedRef.get() == null) {
				ctx.stats.idxCacheHit++;
			}
			return cachedRef.get() != null ? cachedRef.get() : loadedRef.get();
		}

		private static RefWithSize<PackIndex> loadPackIndex(DfsReader ctx,
				DfsPackDescription desc) throws IOException {
			try {
				ctx.stats.readIdx++;
				long start = System.nanoTime();
				try (ReadableChannel rc = ctx.db.openFile(desc, INDEX)) {
					PackIndex idx = PackIndex.read(alignTo8kBlocks(rc));
					ctx.stats.readIdxBytes += rc.position();
					return new RefWithSize<>(idx,
							idx.getObjectCount() * REC_SIZE);
				} finally {
					ctx.stats.readIdxMicros += elapsedMicros(start);
				}
			} catch (EOFException e) {
				throw new IOException(
						MessageFormat.format(DfsText.get().shortReadOfIndex,
								desc.getFileName(INDEX)),
						e);
			} catch (IOException e) {
				throw new IOException(
						MessageFormat.format(DfsText.get().cannotReadIndex,
								desc.getFileName(INDEX)),
						e);
			}
		}

		@Override
		public PackReverseIndex reverseIndex(DfsReader ctx, PackIndex idx)
				throws IOException {
			DfsStreamKey revKey = desc.getStreamKey(REVERSE_INDEX);
			// Keep the value parsed in the loader, in case the Ref<> is
			// nullified in ClockBlockCacheTable#reserveSpace
			// before we read its value.
			AtomicReference<PackReverseIndex> loadedRef = new AtomicReference<>(
					null);
			DfsBlockCache.Ref<PackReverseIndex> cachedRef = cache
					.getOrLoadRef(revKey, REF_POSITION, () -> {
						RefWithSize<PackReverseIndex> ridx = loadReverseIdx(ctx,
								idx);
						loadedRef.set(ridx.ref);
						return new DfsBlockCache.Ref<>(revKey, REF_POSITION,
								ridx.size, ridx.ref);
					});
			if (loadedRef.get() == null) {
				ctx.stats.ridxCacheHit++;
			}
			return cachedRef.get() != null ? cachedRef.get() : loadedRef.get();
		}

		private static RefWithSize<PackReverseIndex> loadReverseIdx(
				DfsReader ctx, PackIndex idx) {
			ctx.stats.readReverseIdx++;
			long start = System.nanoTime();
			PackReverseIndex revidx = PackReverseIndexFactory
					.computeFromIndex(idx);
			ctx.stats.readReverseIdxMicros += elapsedMicros(start);
			return new RefWithSize<>(revidx, idx.getObjectCount() * 8);
		}
	}

	private static final class RefWithSize<V> {
		final V ref;
		final long size;
		RefWithSize(V ref, long size) {
			this.ref = ref;
			this.size = size;
		}
	}
}
