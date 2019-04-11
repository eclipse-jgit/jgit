/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.pack.BinaryDelta;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.LongList;

/**
 * A Git version 2 pack file representation. A pack file contains Git objects in
 * delta packed format yielding high compression of lots of object where some
 * objects are similar.
 */
public final class DfsPackFile {
	/**
	 * File offset used to cache {@link #index} in {@link DfsBlockCache}.
	 * <p>
	 * To better manage memory, the forward index is stored as a single block in
	 * the block cache under this file position. A negative value is used
	 * because it cannot occur in a normal pack file, and it is less likely to
	 * collide with a valid data block from the file as the high bits will all
	 * be set when treated as an unsigned long by the cache code.
	 */
	private static final long POS_INDEX = -1;

	/** Offset used to cache {@link #reverseIndex}. See {@link #POS_INDEX}. */
	private static final long POS_REVERSE_INDEX = -2;

	/** Offset used to cache {@link #bitmapIndex}. See {@link #POS_INDEX}. */
	private static final long POS_BITMAP_INDEX = -3;

	/** Cache that owns this pack file and its data. */
	private final DfsBlockCache cache;

	/** Description of the pack file's storage. */
	private final DfsPackDescription packDesc;

	/** Unique identity of this pack while in-memory. */
	final DfsPackKey key;

	/**
	 * Total number of bytes in this pack file.
	 * <p>
	 * This field initializes to -1 and gets populated when a block is loaded.
	 */
	volatile long length;

	/**
	 * Preferred alignment for loading blocks from the backing file.
	 * <p>
	 * It is initialized to 0 and filled in on the first read made from the
	 * file. Block sizes may be odd, e.g. 4091, caused by the underling DFS
	 * storing 4091 user bytes and 5 bytes block metadata into a lower level
	 * 4096 byte block on disk.
	 */
	private volatile int blockSize;

	/** True once corruption has been detected that cannot be worked around. */
	private volatile boolean invalid;

	/** Exception that caused the packfile to be flagged as invalid */
	private volatile Exception invalidatingCause;

	/**
	 * Lock for initialization of {@link #index} and {@link #corruptObjects}.
	 * <p>
	 * This lock ensures only one thread can perform the initialization work.
	 */
	private final Object initLock = new Object();

	/** Index mapping {@link ObjectId} to position within the pack stream. */
	private volatile DfsBlockCache.Ref<PackIndex> index;

	/** Reverse version of {@link #index} mapping position to {@link ObjectId}. */
	private volatile DfsBlockCache.Ref<PackReverseIndex> reverseIndex;

	/** Index of compressed bitmap mapping entire object graph. */
	private volatile DfsBlockCache.Ref<PackBitmapIndex> bitmapIndex;

	/**
	 * Objects we have tried to read, and discovered to be corrupt.
	 * <p>
	 * The list is allocated after the first corruption is found, and filled in
	 * as more entries are discovered. Typically this list is never used, as
	 * pack files do not usually contain corrupt objects.
	 */
	private volatile LongList corruptObjects;

	/**
	 * Construct a reader for an existing, packfile.
	 *
	 * @param cache
	 *            cache that owns the pack data.
	 * @param desc
	 *            description of the pack within the DFS.
	 * @param key
	 *            interned key used to identify blocks in the block cache.
	 */
	DfsPackFile(DfsBlockCache cache, DfsPackDescription desc, DfsPackKey key) {
		this.cache = cache;
		this.packDesc = desc;
		this.key = key;

		length = desc.getFileSize(PACK);
		if (length <= 0)
			length = -1;
	}

	/** @return description that was originally used to configure this pack file. */
	public DfsPackDescription getPackDescription() {
		return packDesc;
	}

	/**
	 * @return whether the pack index file is loaded and cached in memory.
	 * @since 2.2
	 */
	public boolean isIndexLoaded() {
		DfsBlockCache.Ref<PackIndex> idxref = index;
		return idxref != null && idxref.has();
	}

	/** @return bytes cached in memory for this pack, excluding the index. */
	public long getCachedSize() {
		return key.cachedSize.get();
	}

	String getPackName() {
		return packDesc.getFileName(PACK);
	}

	void setBlockSize(int newSize) {
		blockSize = newSize;
	}

	void setPackIndex(PackIndex idx) {
		long objCnt = idx.getObjectCount();
		int recSize = Constants.OBJECT_ID_LENGTH + 8;
		int sz = (int) Math.min(objCnt * recSize, Integer.MAX_VALUE);
		index = cache.put(key, POS_INDEX, sz, idx);
	}

	/**
	 * Get the PackIndex for this PackFile.
	 *
	 * @param ctx
	 *            reader context to support reading from the backing store if
	 *            the index is not already loaded in memory.
	 * @return the PackIndex.
	 * @throws IOException
	 *             the pack index is not available, or is corrupt.
	 */
	public PackIndex getPackIndex(DfsReader ctx) throws IOException {
		return idx(ctx);
	}

	private PackIndex idx(DfsReader ctx) throws IOException {
		DfsBlockCache.Ref<PackIndex> idxref = index;
		if (idxref != null) {
			PackIndex idx = idxref.get();
			if (idx != null)
				return idx;
		}

		if (invalid) {
			throw new PackInvalidException(getPackName(), invalidatingCause);
		}

		Repository.getGlobalListenerList()
				.dispatch(new BeforeDfsPackIndexLoadedEvent(this));

		synchronized (initLock) {
			idxref = index;
			if (idxref != null) {
				PackIndex idx = idxref.get();
				if (idx != null)
					return idx;
			}

			PackIndex idx;
			try {
				ReadableChannel rc = ctx.db.openFile(packDesc, INDEX);
				try {
					InputStream in = Channels.newInputStream(rc);
					int wantSize = 8192;
					int bs = rc.blockSize();
					if (0 < bs && bs < wantSize)
						bs = (wantSize / bs) * bs;
					else if (bs <= 0)
						bs = wantSize;
					in = new BufferedInputStream(in, bs);
					idx = PackIndex.read(in);
				} finally {
					rc.close();
				}
			} catch (EOFException e) {
				invalid = true;
				invalidatingCause = e;
				IOException e2 = new IOException(MessageFormat.format(
						DfsText.get().shortReadOfIndex,
						packDesc.getFileName(INDEX)));
				e2.initCause(e);
				throw e2;
			} catch (IOException e) {
				invalid = true;
				invalidatingCause = e;
				IOException e2 = new IOException(MessageFormat.format(
						DfsText.get().cannotReadIndex,
						packDesc.getFileName(INDEX)));
				e2.initCause(e);
				throw e2;
			}

			setPackIndex(idx);
			return idx;
		}
	}

	final boolean isGarbage() {
		return packDesc.getPackSource() == UNREACHABLE_GARBAGE;
	}

	PackBitmapIndex getBitmapIndex(DfsReader ctx) throws IOException {
		if (invalid || isGarbage())
			return null;
		DfsBlockCache.Ref<PackBitmapIndex> idxref = bitmapIndex;
		if (idxref != null) {
			PackBitmapIndex idx = idxref.get();
			if (idx != null)
				return idx;
		}

		if (!packDesc.hasFileExt(PackExt.BITMAP_INDEX))
			return null;

		synchronized (initLock) {
			idxref = bitmapIndex;
			if (idxref != null) {
				PackBitmapIndex idx = idxref.get();
				if (idx != null)
					return idx;
			}

			long size;
			PackBitmapIndex idx;
			try {
				ReadableChannel rc = ctx.db.openFile(packDesc, BITMAP_INDEX);
				try {
					InputStream in = Channels.newInputStream(rc);
					int wantSize = 8192;
					int bs = rc.blockSize();
					if (0 < bs && bs < wantSize)
						bs = (wantSize / bs) * bs;
					else if (bs <= 0)
						bs = wantSize;
					in = new BufferedInputStream(in, bs);
					idx = PackBitmapIndex.read(
							in, idx(ctx), getReverseIdx(ctx));
				} finally {
					size = rc.position();
					rc.close();
				}
			} catch (EOFException e) {
				IOException e2 = new IOException(MessageFormat.format(
						DfsText.get().shortReadOfIndex,
						packDesc.getFileName(BITMAP_INDEX)));
				e2.initCause(e);
				throw e2;
			} catch (IOException e) {
				IOException e2 = new IOException(MessageFormat.format(
						DfsText.get().cannotReadIndex,
						packDesc.getFileName(BITMAP_INDEX)));
				e2.initCause(e);
				throw e2;
			}

			bitmapIndex = cache.put(key, POS_BITMAP_INDEX,
					(int) Math.min(size, Integer.MAX_VALUE), idx);
			return idx;
		}
	}

	PackReverseIndex getReverseIdx(DfsReader ctx) throws IOException {
		DfsBlockCache.Ref<PackReverseIndex> revref = reverseIndex;
		if (revref != null) {
			PackReverseIndex revidx = revref.get();
			if (revidx != null)
				return revidx;
		}

		synchronized (initLock) {
			revref = reverseIndex;
			if (revref != null) {
				PackReverseIndex revidx = revref.get();
				if (revidx != null)
					return revidx;
			}

			PackIndex idx = idx(ctx);
			PackReverseIndex revidx = new PackReverseIndex(idx);
			int sz = (int) Math.min(
					idx.getObjectCount() * 8, Integer.MAX_VALUE);
			reverseIndex = cache.put(key, POS_REVERSE_INDEX, sz, revidx);
			return revidx;
		}
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
	 * @throws IOException
	 *             the pack index is not available, or is corrupt.
	 */
	public boolean hasObject(DfsReader ctx, AnyObjectId id) throws IOException {
		final long offset = idx(ctx).findOffset(id);
		return 0 < offset && !isCorrupt(offset);
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

	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		idx(ctx).resolve(matches, id, matchLimit);
	}

	/** Release all memory used by this DfsPackFile instance. */
	public void close() {
		cache.remove(this);
		index = null;
		reverseIndex = null;
	}

	/**
	 * Obtain the total number of objects available in this pack. This method
	 * relies on pack index, giving number of effectively available objects.
	 *
	 * @param ctx
	 *            current reader for the calling thread.
	 * @return number of objects in index of this pack, likewise in this pack
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	long getObjectCount(DfsReader ctx) throws IOException {
		return idx(ctx).getObjectCount();
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

		if (ctx.inflate(this, position, dstbuf, false) != sz)
			throw new EOFException(MessageFormat.format(
					JGitText.get().shortCompressedStreamAt,
					Long.valueOf(position)));
		return dstbuf;
	}

	void copyPackAsIs(PackOutputStream out, DfsReader ctx)
			throws IOException {
		// If the length hasn't been determined yet, pin to set it.
		if (length == -1) {
			ctx.pin(this, 0);
			ctx.unpin();
		}
		if (cache.shouldCopyThroughCache(length))
			copyPackThroughCache(out, ctx);
		else
			copyPackBypassCache(out, ctx);
	}

	private void copyPackThroughCache(PackOutputStream out, DfsReader ctx)
			throws IOException {
		long position = 12;
		long remaining = length - (12 + 20);
		while (0 < remaining) {
			DfsBlock b = cache.getOrLoad(this, position, ctx);
			int ptr = (int) (position - b.start);
			int n = (int) Math.min(b.size() - ptr, remaining);
			b.write(out, position, n);
			position += n;
			remaining -= n;
		}
	}

	private long copyPackBypassCache(PackOutputStream out, DfsReader ctx)
			throws IOException {
		try (ReadableChannel rc = ctx.db.openFile(packDesc, PACK)) {
			ByteBuffer buf = newCopyBuffer(out, rc);
			if (ctx.getOptions().getStreamPackBufferSize() > 0)
				rc.setReadAheadBytes(ctx.getOptions().getStreamPackBufferSize());
			long position = 12;
			long remaining = length - (12 + 20);
			while (0 < remaining) {
				DfsBlock b = cache.get(key, alignToBlock(position));
				if (b != null) {
					int ptr = (int) (position - b.start);
					int n = (int) Math.min(b.size() - ptr, remaining);
					b.write(out, position, n);
					position += n;
					remaining -= n;
					rc.position(position);
					continue;
				}

				buf.position(0);
				int n = read(rc, buf);
				if (n <= 0)
					throw packfileIsTruncated();
				else if (n > remaining)
					n = (int) remaining;
				out.write(buf.array(), 0, n);
				position += n;
				remaining -= n;
			}
			return position;
		}
	}

	private ByteBuffer newCopyBuffer(PackOutputStream out, ReadableChannel rc) {
		int bs = blockSize(rc);
		byte[] copyBuf = out.getCopyBuffer();
		if (bs > copyBuf.length)
			copyBuf = new byte[bs];
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
			StoredObjectRepresentationNotAvailableException gone;
			gone = new StoredObjectRepresentationNotAvailableException(src);
			gone.initCause(ioError);
			throw gone;
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
							Long.valueOf(src.offset), getPackName()));
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
						while (inf.inflate(tmp, 0, tmp.length) > 0)
							continue;
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
							Long.valueOf(src.offset), getPackName()));
			corruptObject.initCause(dataFormat);

			StoredObjectRepresentationNotAvailableException gone;
			gone = new StoredObjectRepresentationNotAvailableException(src);
			gone.initCause(corruptObject);
			throw gone;

		} catch (IOException ioError) {
			StoredObjectRepresentationNotAvailableException gone;
			gone = new StoredObjectRepresentationNotAvailableException(src);
			gone.initCause(ioError);
			throw gone;
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
							Long.valueOf(src.offset), getPackName()));
				}
			}
		}
	}

	boolean invalid() {
		return invalid;
	}

	void setInvalid() {
		invalid = true;
	}

	private IOException packfileIsTruncated() {
		invalid = true;
		IOException exc = new IOException(MessageFormat.format(
				JGitText.get().packfileIsTruncated, getPackName()));
		invalidatingCause = exc;
		return exc;
	}

	private void readFully(long position, byte[] dstbuf, int dstoff, int cnt,
			DfsReader ctx) throws IOException {
		if (ctx.copy(this, position, dstbuf, dstoff, cnt) != cnt)
			throw new EOFException();
	}

	long alignToBlock(long pos) {
		int size = blockSize;
		if (size == 0)
			size = cache.getBlockSize();
		return (pos / size) * size;
	}

	DfsBlock getOrLoadBlock(long pos, DfsReader ctx) throws IOException {
		return cache.getOrLoad(this, pos, ctx);
	}

	DfsBlock readOneBlock(long pos, DfsReader ctx)
			throws IOException {
		if (invalid) {
			throw new PackInvalidException(getPackName(), invalidatingCause);
		}

		ReadableChannel rc = ctx.db.openFile(packDesc, PACK);
		try {
			int size = blockSize(rc);
			pos = (pos / size) * size;

			// If the size of the file is not yet known, try to discover it.
			// Channels may choose to return -1 to indicate they don't
			// know the length yet, in this case read up to the size unit
			// given by the caller, then recheck the length.
			long len = length;
			if (len < 0) {
				len = rc.size();
				if (0 <= len)
					length = len;
			}

			if (0 <= len && len < pos + size)
				size = (int) (len - pos);
			if (size <= 0)
				throw new EOFException(MessageFormat.format(
						DfsText.get().shortReadOfBlock, Long.valueOf(pos),
						getPackName(), Long.valueOf(0), Long.valueOf(0)));

			byte[] buf = new byte[size];
			rc.position(pos);
			int cnt = read(rc, ByteBuffer.wrap(buf, 0, size));
			if (cnt != size) {
				if (0 <= len) {
					throw new EOFException(MessageFormat.format(
						    DfsText.get().shortReadOfBlock,
						    Long.valueOf(pos),
						    getPackName(),
						    Integer.valueOf(size),
						    Integer.valueOf(cnt)));
				}

				// Assume the entire thing was read in a single shot, compact
				// the buffer to only the space required.
				byte[] n = new byte[cnt];
				System.arraycopy(buf, 0, n, 0, n.length);
				buf = n;
			} else if (len < 0) {
				// With no length at the start of the read, the channel should
				// have the length available at the end.
				length = len = rc.size();
			}

			DfsBlock v = new DfsBlock(key, pos, buf);
			return v;
		} finally {
			rc.close();
		}
	}

	private int blockSize(ReadableChannel rc) {
		// If the block alignment is not yet known, discover it. Prefer the
		// larger size from either the cache or the file itself.
		int size = blockSize;
		if (size == 0) {
			size = rc.blockSize();
			if (size <= 0)
				size = cache.getBlockSize();
			else if (size < cache.getBlockSize())
				size = (cache.getBlockSize() / size) * size;
			blockSize = size;
		}
		return size;
	}

	private static int read(ReadableChannel rc, ByteBuffer buf)
			throws IOException {
		int n;
		do {
			n = rc.read(buf);
		} while (0 < n && buf.hasRemaining());
		return buf.position();
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
						if (data != null)
							return new ObjectLoader.SmallObject(typeCode, data);
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
					if (sz != delta.deltaSize)
						break SEARCH;

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
					if (sz != delta.deltaSize)
						break SEARCH;

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
				if (cached)
					cached = false;
				else if (delta.next == null)
					ctx.getDeltaBaseCache().put(key, delta.basePos, type, data);

				pos = delta.deltaPos;

				byte[] cmds = decompress(pos + delta.hdrLen, delta.deltaSize, ctx);
				if (cmds == null) {
					data = null; // Discard base in case of OutOfMemoryError
					throw new LargeObjectException();
				}

				final long sz = BinaryDelta.getResultSize(cmds);
				if (Integer.MAX_VALUE <= sz)
					throw new LargeObjectException.ExceedsByteArrayLimit();

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
			CorruptObjectException coe = new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream, Long.valueOf(pos),
							getPackName()));
			coe.initCause(dfe);
			throw coe;
		}
	}

	private long findDeltaBase(DfsReader ctx, ObjectId baseId)
			throws IOException, MissingObjectException {
		long ofs = idx(ctx).findOffset(baseId);
		if (ofs < 0)
			throw new MissingObjectException(baseId,
					JGitText.get().missingDeltaBase);
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
				while ((c & 0x80) != 0)
					c = ib[p++] & 0xff;
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
				while ((c & 0x80) != 0)
					c = ib[p++] & 0xff;
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
			while ((c & 128) != 0)
				c = ib[p++] & 0xff;
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
			CorruptObjectException coe = new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream, Long.valueOf(pos),
							getPackName()));
			coe.initCause(dfe);
			throw coe;
		}
	}

	void representation(DfsObjectRepresentation r, final long pos,
			DfsReader ctx, PackReverseIndex rev)
			throws IOException {
		r.offset = pos;
		final byte[] ib = ctx.tempId;
		readFully(pos, ib, 0, 20, ctx);
		int c = ib[0] & 0xff;
		int p = 1;
		final int typeCode = (c >> 4) & 7;
		while ((c & 0x80) != 0)
			c = ib[p++] & 0xff;

		long len = rev.findNextOffset(pos, length - 20) - pos;
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
			r.baseId = rev.findObject(pos - ofs);
			r.length = len - p;
			return;
		}

		case Constants.OBJ_REF_DELTA: {
			readFully(pos + p, ib, 0, 20, ctx);
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
		if (list == null)
			return false;
		synchronized (list) {
			return list.contains(offset);
		}
	}

	private void setCorrupt(long offset) {
		LongList list = corruptObjects;
		if (list == null) {
			synchronized (initLock) {
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
}
