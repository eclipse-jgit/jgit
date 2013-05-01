/*
 * Copyright (C) 2011, Google Inc.
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

import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackIndexWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.CountingOutputStream;

/** Inserts objects into the DFS. */
public class DfsInserter extends ObjectInserter {
	/** Always produce version 2 indexes, to get CRC data. */
	private static final int INDEX_VERSION = 2;

	private final DfsObjDatabase db;

	private List<PackedObjectInfo> objectList;
	private ObjectIdOwnerMap<PackedObjectInfo> objectMap;

	private DfsBlockCache cache;
	private DfsPackKey packKey;
	private DfsPackDescription packDsc;
	private PackStream packOut;
	private boolean rollback;

	/**
	 * Initialize a new inserter.
	 *
	 * @param db
	 *            database the inserter writes to.
	 */
	protected DfsInserter(DfsObjDatabase db) {
		this.db = db;
	}

	@Override
	public DfsPackParser newPackParser(InputStream in) throws IOException {
		return new DfsPackParser(db, this, in);
	}

	@Override
	public ObjectReader newReader() throws IOException {
		if (packOut == null)
			beginPack();
		return new Reader();
	}

	@Override
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		ObjectId id = idFor(type, data, off, len);
		if (objectMap != null && objectMap.contains(id))
			return id;
		if (db.has(id))
			return id;

		long offset = beginObject(type, len);
		packOut.compress.write(data, off, len);
		packOut.compress.finish();
		return endObject(id, offset);
	}

	@Override
	public ObjectId insert(int type, long len, InputStream in)
			throws IOException {
		byte[] buf = buffer();
		if (len <= buf.length) {
			IO.readFully(in, buf, 0, (int) len);
			return insert(type, buf, 0, (int) len);
		}

		long offset = beginObject(type, len);
		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(len));
		md.update((byte) 0);

		while (0 < len) {
			int n = in.read(buf, 0, (int) Math.min(buf.length, len));
			if (n <= 0)
				throw new EOFException();
			md.update(buf, 0, n);
			packOut.compress.write(buf, 0, n);
			len -= n;
		}
		packOut.compress.finish();
		return endObject(ObjectId.fromRaw(md.digest()), offset);
	}

	@Override
	public void flush() throws IOException {
		if (packDsc == null)
			return;

		if (packOut == null)
			throw new IOException();

		byte[] packHash = packOut.writePackFooter();
		packDsc.addFileExt(PACK);
		packDsc.setFileSize(PACK, packOut.getCount());
		packOut.close();
		packOut = null;

		sortObjectsById();

		PackIndex index = writePackIndex(packDsc, packHash, objectList);
		db.commitPack(Collections.singletonList(packDsc), null);
		rollback = false;

		DfsPackFile p = cache.getOrCreate(packDsc, packKey);
		if (index != null)
			p.setPackIndex(index);
		db.addPack(p);
		clear();
	}

	@Override
	public void release() {
		if (packOut != null) {
			try {
				packOut.close();
			} catch (IOException err) {
				// Ignore a close failure, the pack should be removed.
			} finally {
				packOut = null;
			}
		}
		if (rollback && packDsc != null) {
			try {
				db.rollbackPack(Collections.singletonList(packDsc));
			} finally {
				packDsc = null;
				rollback = false;
			}
		}
		clear();
	}

	private void clear() {
		objectList = null;
		objectMap = null;
		packKey = null;
		packDsc = null;
	}

	private long beginObject(int type, long len) throws IOException {
		if (packOut == null)
			beginPack();
		long offset = packOut.getCount();
		packOut.beginObject(type, len);
		return offset;
	}

	private ObjectId endObject(ObjectId id, long offset) throws IOException {
		PackedObjectInfo obj = new PackedObjectInfo(id);
		obj.setOffset(offset);
		obj.setCRC((int) packOut.crc32.getValue());
		objectList.add(obj);
		objectMap.addIfAbsent(obj);
		packOut.flushStream();
		return id;
	}

	private void beginPack() throws IOException {
		objectList = new BlockList<PackedObjectInfo>();
		objectMap = new ObjectIdOwnerMap<PackedObjectInfo>();
		cache = DfsBlockCache.getInstance();

		rollback = true;
		packDsc = db.newPack(DfsObjDatabase.PackSource.INSERT);
		packOut = new PackStream(db.writeFile(packDsc, PACK));
		packKey = new DfsPackKey();

		// Write the header as though it were a single object pack.
		byte[] buf = packOut.hdrBuf;
		System.arraycopy(Constants.PACK_SIGNATURE, 0, buf, 0, 4);
		NB.encodeInt32(buf, 4, 2); // Always use pack version 2.
		NB.encodeInt32(buf, 8, 1); // Always assume 1 object.
		packOut.write(buf, 0, 12);
	}

	private void sortObjectsById() {
		Collections.sort(objectList);
	}

	PackIndex writePackIndex(DfsPackDescription pack, byte[] packHash,
			List<PackedObjectInfo> list) throws IOException {
		pack.setIndexVersion(INDEX_VERSION);
		pack.setObjectCount(list.size());

		// If there are less than 58,000 objects, the entire index fits in under
		// 2 MiB. Callers will probably need the index immediately, so buffer
		// the index in process and load from the buffer.
		TemporaryBuffer.Heap buf = null;
		PackIndex packIndex = null;
		if (list.size() <= 58000) {
			buf = new TemporaryBuffer.Heap(2 << 20);
			index(buf, packHash, list);
			packIndex = PackIndex.read(buf.openInputStream());
		}

		DfsOutputStream os = db.writeFile(pack, INDEX);
		try {
			CountingOutputStream cnt = new CountingOutputStream(os);
			if (buf != null)
				buf.writeTo(cnt, null);
			else
				index(cnt, packHash, list);
			pack.addFileExt(INDEX);
			pack.setFileSize(INDEX, cnt.getCount());
		} finally {
			os.close();
		}
		return packIndex;
	}

	private static void index(OutputStream out, byte[] packHash,
			List<PackedObjectInfo> list) throws IOException {
		PackIndexWriter.createVersion(out, INDEX_VERSION).write(list, packHash);
	}

	private class PackStream extends OutputStream {
		private final DfsOutputStream out;
		private final MessageDigest md;
		private final byte[] hdrBuf;
		private final Deflater deflater;
		private final int blockSize;

		private long currPos; // Position of currBuf[0] in the output stream.
		private int currLen; // Number of bytes in currBuf.
		private int currPtr; // Offset in currPtr written to stream so far.
		private byte[] currBuf;

		final CRC32 crc32;
		final DeflaterOutputStream compress;

		PackStream(DfsOutputStream out) {
			this.out = out;

			hdrBuf = new byte[32];
			md = Constants.newMessageDigest();
			crc32 = new CRC32();
			deflater = new Deflater(Deflater.BEST_COMPRESSION);
			compress = new DeflaterOutputStream(this, deflater, 8192);

			int size = out.blockSize();
			if (size <= 0)
				size = cache.getBlockSize();
			else if (size < cache.getBlockSize())
				size = (cache.getBlockSize() / size) * size;
			blockSize = size;
			currBuf = new byte[blockSize];
		}

		long getCount() {
			return currPos + currLen;
		}

		void beginObject(int objectType, long length) throws IOException {
			crc32.reset();
			deflater.reset();
			write(hdrBuf, 0, encodeTypeSize(objectType, length));
		}

		private int encodeTypeSize(int type, long rawLength) {
			long nextLength = rawLength >>> 4;
			hdrBuf[0] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (rawLength & 0x0F));
			rawLength = nextLength;
			int n = 1;
			while (rawLength > 0) {
				nextLength >>>= 7;
				hdrBuf[n++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (rawLength & 0x7F));
				rawLength = nextLength;
			}
			return n;
		}

		@Override
		public void write(final int b) throws IOException {
			hdrBuf[0] = (byte) b;
			write(hdrBuf, 0, 1);
		}

		@Override
		public void write(byte[] data, int off, int len) throws IOException {
			crc32.update(data, off, len);
			md.update(data, off, len);
			writeNoHash(data, off, len);
		}

		private void writeNoHash(byte[] data, int off, int len)
				throws IOException {
			while (0 < len) {
				int n = Math.min(len, currBuf.length - currLen);
				if (n == 0) {
					flushBlock();
					currBuf = new byte[blockSize];
					continue;
				}

				System.arraycopy(data, off, currBuf, currLen, n);
				off += n;
				len -= n;
				currLen += n;
			}
		}

		private void flushStream() throws IOException {
			out.write(currBuf, currPtr, currLen - currPtr);
			currPtr = currLen;
		}

		private void flushBlock() throws IOException {
			flushStream();

			byte[] buf;
			if (currLen == currBuf.length)
				buf = currBuf;
			else
				buf = copyOf(currBuf, 0, currPtr);
			cache.put(new DfsBlock(packKey, currPos, buf));

			currPos += currLen;
			currLen = 0;
			currPtr = 0;
			currBuf = null;
		}

		private byte[] copyOf(byte[] src, int ptr, int cnt) {
			byte[] dst = new byte[cnt];
			System.arraycopy(src, ptr, dst, 0, cnt);
			return dst;
		}

		byte[] writePackFooter() throws IOException {
			byte[] packHash = md.digest();
			writeNoHash(packHash, 0, packHash.length);
			if (currLen != 0)
				flushBlock();
			return packHash;
		}

		@Override
		public void close() throws IOException {
			deflater.end();
			out.close();
		}
	}

	private class Reader extends ObjectReader {
		private boolean avoidUnreachable;
		private ObjectReader delegate;

		@Override
		public ObjectReader newReader() {
			ObjectReader r = db.newReader();
			r.setAvoidUnreachableObjects(avoidUnreachable);
			return r;
		}

		@Override
		public void setAvoidUnreachableObjects(boolean avoid) {
			avoidUnreachable = avoid;
		}

		@Override
		public Collection<ObjectId> resolve(AbbreviatedObjectId id)
				throws IOException {
			ObjectReader r = newReader();
			try {
				Collection<ObjectId> fromDb = r.resolve(id);
				Set<ObjectId> ids = new HashSet<ObjectId>(fromDb.size() + 2);
				ids.addAll(fromDb);
				for (PackedObjectInfo obj : objectList) {
					if (id.prefixCompare(obj) == 0)
						ids.add(obj.copy());
				}
				return ids;
			} finally {
				r.release();
			}
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			PackedObjectInfo obj = objectMap.get(objectId);
			if (obj != null)
				return new StreamLoader(packOut.out, obj.getOffset());
			if (delegate == null)
				delegate = newReader();
			return delegate.open(objectId, typeHint);
		}

		@Override
		public Set<ObjectId> getShallowCommits() throws IOException {
			return Collections.emptySet();
		}

		@Override
		public void release() {
			if (delegate != null) {
				delegate.release();
				delegate = null;
			}
		}
	}

	private class StreamLoader extends ObjectLoader {
		private final InputStream in;
		private final int type;
		private final long size;

		private StreamLoader(DfsOutputStream out, long position) throws IOException {
			in = out.openInputStream(position);

			int c = in.read();
			if (c < 0)
					throw new IllegalStateException(DfsText.get().unexpectedEofInPack);
			type = (c >> 4) & 7;
			if (type == Constants.OBJ_OFS_DELTA || type == Constants.OBJ_REF_DELTA)
				throw new IllegalStateException(MessageFormat.format(
						DfsText.get().readBackDelta, Integer.toString(type)));

			long sz = c & 15;
			int shift = 4;
			while ((c & 0x80) != 0) {
				c = in.read();
				if (c < 0)
					throw new IllegalStateException(DfsText.get().unexpectedEofInPack);
				sz += ((long) (c & 0x7f)) << shift;
				shift += 7;
			}
			size = sz;
		}

		@Override
		public int getType() {
			return type;
		}

		@Override
		public long getSize() {
			return size;
		}

		@Override
		public byte[] getCachedBytes() throws LargeObjectException {
			throw new LargeObjectException();
		}

		@Override
		public ObjectStream openStream() throws IOException {
			int bufsz = 8192;
			final Inflater inf = InflaterCache.get();
			return new ObjectStream.Filter(type, size,
					new BufferedInputStream(
							new InflaterInputStream(in, inf, bufsz),
							bufsz)) {
				@Override
				public void close() throws IOException {
					InflaterCache.release(inf);
					super.close();
				}
			};
		}
	}
}
