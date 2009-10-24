/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.lib;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.DataFormatException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A Git version 2 pack file representation. A pack file contains Git objects in
 * delta packed format yielding high compression of lots of object where some
 * objects are similar.
 */
public class PackFile implements Iterable<PackIndex.MutableEntry> {
	/** Sorts PackFiles to be most recently created to least recently created. */
	public static Comparator<PackFile> SORT = new Comparator<PackFile>() {
		public int compare(final PackFile a, final PackFile b) {
			return b.packLastModified - a.packLastModified;
		}
	};

	private final File idxFile;

	private final File packFile;

	final int hash;

	private RandomAccessFile fd;

	long length;

	private int activeWindows;

	private int activeCopyRawData;

	private int packLastModified;

	private volatile boolean invalid;

	private byte[] packChecksum;

	private PackIndex loadedIdx;

	private PackReverseIndex reverseIdx;

	/**
	 * Construct a reader for an existing, pre-indexed packfile.
	 *
	 * @param idxFile
	 *            path of the <code>.idx</code> file listing the contents.
	 * @param packFile
	 *            path of the <code>.pack</code> file holding the data.
	 */
	public PackFile(final File idxFile, final File packFile) {
		this.idxFile = idxFile;
		this.packFile = packFile;
		this.packLastModified = (int) (packFile.lastModified() >> 10);

		// Multiply by 31 here so we can more directly combine with another
		// value in WindowCache.hash(), without doing the multiply there.
		//
		hash = System.identityHashCode(this) * 31;
		length = Long.MAX_VALUE;
	}

	private synchronized PackIndex idx() throws IOException {
		if (loadedIdx == null) {
			if (invalid)
				throw new PackInvalidException(packFile);

			try {
				final PackIndex idx = PackIndex.open(idxFile);

				if (packChecksum == null)
					packChecksum = idx.packChecksum;
				else if (!Arrays.equals(packChecksum, idx.packChecksum))
					throw new PackMismatchException("Pack checksum mismatch");

				loadedIdx = idx;
			} catch (IOException e) {
				invalid = true;
				throw e;
			}
		}
		return loadedIdx;
	}

	final PackedObjectLoader resolveBase(final WindowCursor curs, final long ofs)
			throws IOException {
		return reader(curs, ofs);
	}

	/** @return the File object which locates this pack on disk. */
	public File getPackFile() {
		return packFile;
	}

	/**
	 * Determine if an object is contained within the pack file.
	 * <p>
	 * For performance reasons only the index file is searched; the main pack
	 * content is ignored entirely.
	 * </p>
	 *
	 * @param id
	 *            the object to look for. Must not be null.
	 * @return true if the object is in this pack; false otherwise.
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	public boolean hasObject(final AnyObjectId id) throws IOException {
		return idx().hasObject(id);
	}

	/**
	 * Get an object from this pack.
	 *
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            the object to obtain from the pack. Must not be null.
	 * @return the object loader for the requested object if it is contained in
	 *         this pack; null if the object was not found.
	 * @throws IOException
	 *             the pack file or the index could not be read.
	 */
	public PackedObjectLoader get(final WindowCursor curs, final AnyObjectId id)
			throws IOException {
		final long offset = idx().findOffset(id);
		return 0 < offset ? reader(curs, offset) : null;
	}

	/**
	 * Close the resources utilized by this repository
	 */
	public void close() {
		UnpackedObjectCache.purge(this);
		WindowCache.purge(this);
		synchronized (this) {
			loadedIdx = null;
			reverseIdx = null;
		}
	}

	/**
	 * Provide iterator over entries in associated pack index, that should also
	 * exist in this pack file. Objects returned by such iterator are mutable
	 * during iteration.
	 * <p>
	 * Iterator returns objects in SHA-1 lexicographical order.
	 * </p>
	 *
	 * @return iterator over entries of associated pack index
	 *
	 * @see PackIndex#iterator()
	 */
	public Iterator<PackIndex.MutableEntry> iterator() {
		try {
			return idx().iterator();
		} catch (IOException e) {
			return Collections.<PackIndex.MutableEntry> emptyList().iterator();
		}
	}

	/**
	 * Obtain the total number of objects available in this pack. This method
	 * relies on pack index, giving number of effectively available objects.
	 *
	 * @return number of objects in index of this pack, likewise in this pack
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	long getObjectCount() throws IOException {
		return idx().getObjectCount();
	}

	/**
	 * Search for object id with the specified start offset in associated pack
	 * (reverse) index.
	 *
	 * @param offset
	 *            start offset of object to find
	 * @return object id for this offset, or null if no object was found
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	ObjectId findObjectForOffset(final long offset) throws IOException {
		return getReverseIdx().findObject(offset);
	}

	final UnpackedObjectCache.Entry readCache(final long position) {
		return UnpackedObjectCache.get(this, position);
	}

	final void saveCache(final long position, final byte[] data, final int type) {
		UnpackedObjectCache.store(this, position, data, type);
	}

	final byte[] decompress(final long position, final int totalSize,
			final WindowCursor curs) throws DataFormatException, IOException {
		final byte[] dstbuf = new byte[totalSize];
		if (curs.inflate(this, position, dstbuf, 0) != totalSize)
			throw new EOFException("Short compressed stream at " + position);
		return dstbuf;
	}

	final void copyRawData(final PackedObjectLoader loader,
			final OutputStream out, final byte buf[], final WindowCursor curs)
			throws IOException {
		final long objectOffset = loader.objectOffset;
		final long dataOffset = loader.dataOffset;
		final int cnt = (int) (findEndOffset(objectOffset) - dataOffset);
		final PackIndex idx = idx();

		if (idx.hasCRC32Support()) {
			final CRC32 crc = new CRC32();
			int headerCnt = (int) (dataOffset - objectOffset);
			while (headerCnt > 0) {
				final int toRead = Math.min(headerCnt, buf.length);
				readFully(objectOffset, buf, 0, toRead, curs);
				crc.update(buf, 0, toRead);
				headerCnt -= toRead;
			}
			final CheckedOutputStream crcOut = new CheckedOutputStream(out, crc);
			copyToStream(dataOffset, buf, cnt, crcOut, curs);
			final long computed = crc.getValue();

			final ObjectId id = findObjectForOffset(objectOffset);
			final long expected = idx.findCRC32(id);
			if (computed != expected)
				throw new CorruptObjectException("Object at " + dataOffset
						+ " in " + getPackFile() + " has bad zlib stream");
		} else {
			try {
				curs.inflateVerify(this, dataOffset);
			} catch (DataFormatException dfe) {
				final CorruptObjectException coe;
				coe = new CorruptObjectException("Object at " + dataOffset
						+ " in " + getPackFile() + " has bad zlib stream");
				coe.initCause(dfe);
				throw coe;
			}
			copyToStream(dataOffset, buf, cnt, out, curs);
		}
	}

	boolean supportsFastCopyRawData() throws IOException {
		return idx().hasCRC32Support();
	}

	boolean invalid() {
		return invalid;
	}

	private void readFully(final long position, final byte[] dstbuf,
			int dstoff, final int cnt, final WindowCursor curs)
			throws IOException {
		if (curs.copy(this, position, dstbuf, dstoff, cnt) != cnt)
			throw new EOFException();
	}

	private void copyToStream(long position, final byte[] buf, long cnt,
			final OutputStream out, final WindowCursor curs)
			throws IOException, EOFException {
		while (cnt > 0) {
			final int toRead = (int) Math.min(cnt, buf.length);
			readFully(position, buf, 0, toRead, curs);
			position += toRead;
			cnt -= toRead;
			out.write(buf, 0, toRead);
		}
	}

	synchronized void beginCopyRawData() throws IOException {
		if (++activeCopyRawData == 1 && activeWindows == 0)
			doOpen();
	}

	synchronized void endCopyRawData() {
		if (--activeCopyRawData == 0 && activeWindows == 0)
			doClose();
	}

	synchronized boolean beginWindowCache() throws IOException {
		if (++activeWindows == 1) {
			if (activeCopyRawData == 0)
				doOpen();
			return true;
		}
		return false;
	}

	synchronized boolean endWindowCache() {
		final boolean r = --activeWindows == 0;
		if (r && activeCopyRawData == 0)
			doClose();
		return r;
	}

	private void doOpen() throws IOException {
		try {
			if (invalid)
				throw new PackInvalidException(packFile);
			fd = new RandomAccessFile(packFile, "r");
			length = fd.length();
			onOpenPack();
		} catch (IOException ioe) {
			openFail();
			throw ioe;
		} catch (RuntimeException re) {
			openFail();
			throw re;
		} catch (Error re) {
			openFail();
			throw re;
		}
	}

	private void openFail() {
		activeWindows = 0;
		activeCopyRawData = 0;
		invalid = true;
		doClose();
	}

	private void doClose() {
		if (fd != null) {
			try {
				fd.close();
			} catch (IOException err) {
				// Ignore a close event. We had it open only for reading.
				// There should not be errors related to network buffers
				// not flushed, etc.
			}
			fd = null;
		}
	}

	ByteArrayWindow read(final long pos, int size) throws IOException {
		if (length < pos + size)
			size = (int) (length - pos);
		final byte[] buf = new byte[size];
		IO.readFully(fd.getChannel(), pos, buf, 0, size);
		return new ByteArrayWindow(this, pos, buf);
	}

	ByteWindow mmap(final long pos, int size) throws IOException {
		if (length < pos + size)
			size = (int) (length - pos);

		MappedByteBuffer map;
		try {
			map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
		} catch (IOException ioe1) {
			// The most likely reason this failed is the JVM has run out
			// of virtual memory. We need to discard quickly, and try to
			// force the GC to finalize and release any existing mappings.
			//
			System.gc();
			System.runFinalization();
			map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
		}

		if (map.hasArray())
			return new ByteArrayWindow(this, pos, map.array());
		return new ByteBufferWindow(this, pos, map);
	}

	private void onOpenPack() throws IOException {
		final PackIndex idx = idx();
		final byte[] buf = new byte[20];

		IO.readFully(fd.getChannel(), 0, buf, 0, 12);
		if (RawParseUtils.match(buf, 0, Constants.PACK_SIGNATURE) != 4)
			throw new IOException("Not a PACK file.");
		final long vers = NB.decodeUInt32(buf, 4);
		final long packCnt = NB.decodeUInt32(buf, 8);
		if (vers != 2 && vers != 3)
			throw new IOException("Unsupported pack version " + vers + ".");

		if (packCnt != idx.getObjectCount())
			throw new PackMismatchException("Pack object count mismatch:"
					+ " pack " + packCnt
					+ " index " + idx.getObjectCount()
					+ ": " + getPackFile());

		IO.readFully(fd.getChannel(), length - 20, buf, 0, 20);
		if (!Arrays.equals(buf, packChecksum))
			throw new PackMismatchException("Pack checksum mismatch:"
					+ " pack " + ObjectId.fromRaw(buf).name()
					+ " index " + ObjectId.fromRaw(idx.packChecksum).name()
					+ ": " + getPackFile());
	}

	private PackedObjectLoader reader(final WindowCursor curs,
			final long objOffset) throws IOException {
		long pos = objOffset;
		int p = 0;
		final byte[] ib = curs.tempId;
		readFully(pos, ib, 0, 20, curs);
		int c = ib[p++] & 0xff;
		final int typeCode = (c >> 4) & 7;
		long dataSize = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = ib[p++] & 0xff;
			dataSize += (c & 0x7f) << shift;
			shift += 7;
		}
		pos += p;

		switch (typeCode) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			return new WholePackedObjectLoader(this, pos, objOffset, typeCode,
					(int) dataSize);

		case Constants.OBJ_OFS_DELTA: {
			readFully(pos, ib, 0, 20, curs);
			p = 0;
			c = ib[p++] & 0xff;
			long ofs = c & 127;
			while ((c & 128) != 0) {
				ofs += 1;
				c = ib[p++] & 0xff;
				ofs <<= 7;
				ofs += (c & 127);
			}
			return new DeltaOfsPackedObjectLoader(this, pos + p, objOffset,
					(int) dataSize, objOffset - ofs);
		}
		case Constants.OBJ_REF_DELTA: {
			readFully(pos, ib, 0, 20, curs);
			return new DeltaRefPackedObjectLoader(this, pos + ib.length,
					objOffset, (int) dataSize, ObjectId.fromRaw(ib));
		}
		default:
			throw new IOException("Unknown object type " + typeCode + ".");
		}
	}

	private long findEndOffset(final long startOffset)
			throws IOException, CorruptObjectException {
		final long maxOffset = length - 20;
		return getReverseIdx().findNextOffset(startOffset, maxOffset);
	}

	private synchronized PackReverseIndex getReverseIdx() throws IOException {
		if (reverseIdx == null)
			reverseIdx = new PackReverseIndex(idx());
		return reverseIdx;
	}
}
