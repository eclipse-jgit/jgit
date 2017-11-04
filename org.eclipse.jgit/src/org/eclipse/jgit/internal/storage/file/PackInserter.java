/*
 * Copyright (C) 2017, Google Inc.
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
 *	 notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *	 copyright notice, this list of conditions and the following
 *	 disclaimer in the documentation and/or other materials provided
 *	 with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *	 names of its contributors may be used to endorse or promote
 *	 products derived from this software without specific prior
 *	 written permission.
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

package org.eclipse.jgit.internal.storage.file;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_OFS_DELTA;
import static org.eclipse.jgit.lib.Constants.OBJ_REF_DELTA;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
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
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.CountingOutputStream;
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * Object inserter that inserts one pack per call to {@link #flush()}, and never
 * inserts loose objects.
 */
class PackInserter extends ObjectInserter {
	/** Always produce version 2 indexes, to get CRC data. */
	private static final int INDEX_VERSION = 2;

	private final ObjectDirectory db;

	private List<PackedObjectInfo> objectList;
	private ObjectIdOwnerMap<PackedObjectInfo> objectMap;
	private boolean rollback;
	private boolean checkExisting = true;

	private int compression = Deflater.BEST_COMPRESSION;
	private File tmpPack;
	private PackStream packOut;
	private Inflater cachedInflater;

	PackInserter(ObjectDirectory db) {
		this.db = db;
	}

	/**
	 * @param check
	 *            if false, will write out possibly-duplicate objects without
	 *            first checking whether they exist in the repo; default is true.
	 */
	public void checkExisting(boolean check) {
		checkExisting = check;
	}

	/**
	 * @param compression
	 *            compression level for zlib deflater.
	 */
	public void setCompressionLevel(int compression) {
		this.compression = compression;
	}

	int getBufferSize() {
		return buffer().length;
	}

	@Override
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		ObjectId id = idFor(type, data, off, len);
		if (objectMap != null && objectMap.contains(id)) {
			return id;
		}
		// Ignore loose objects, which are potentially unreachable.
		if (checkExisting && db.hasPackedObject(id)) {
			return id;
		}

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
		SHA1 md = digest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(len));
		md.update((byte) 0);

		while (0 < len) {
			int n = in.read(buf, 0, (int) Math.min(buf.length, len));
			if (n <= 0) {
				throw new EOFException();
			}
			md.update(buf, 0, n);
			packOut.compress.write(buf, 0, n);
			len -= n;
		}
		packOut.compress.finish();
		return endObject(md.toObjectId(), offset);
	}

	private long beginObject(int type, long len) throws IOException {
		if (packOut == null) {
			beginPack();
		}
		long offset = packOut.getOffset();
		packOut.beginObject(type, len);
		return offset;
	}

	private ObjectId endObject(ObjectId id, long offset) {
		PackedObjectInfo obj = new PackedObjectInfo(id);
		obj.setOffset(offset);
		obj.setCRC((int) packOut.crc32.getValue());
		objectList.add(obj);
		objectMap.addIfAbsent(obj);
		return id;
	}

	private static File idxFor(File packFile) {
		String p = packFile.getName();
		return new File(
				packFile.getParentFile(),
				p.substring(0, p.lastIndexOf('.')) + ".idx"); //$NON-NLS-1$
	}

	private void beginPack() throws IOException {
		objectList = new BlockList<>();
		objectMap = new ObjectIdOwnerMap<>();

		rollback = true;
		tmpPack = File.createTempFile("insert_", ".pack", db.getDirectory()); //$NON-NLS-1$ //$NON-NLS-2$
		packOut = new PackStream(tmpPack);

		// Write the header as though it were a single object pack.
		packOut.write(packOut.hdrBuf, 0, writePackHeader(packOut.hdrBuf, 1));
	}

	private static int writePackHeader(byte[] buf, int objectCount) {
		System.arraycopy(Constants.PACK_SIGNATURE, 0, buf, 0, 4);
		NB.encodeInt32(buf, 4, 2); // Always use pack version 2.
		NB.encodeInt32(buf, 8, objectCount);
		return 12;
	}

	@Override
	public PackParser newPackParser(InputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ObjectReader newReader() {
		return new Reader();
	}

	@Override
	public void flush() throws IOException {
		if (tmpPack == null) {
			return;
		}

		if (packOut == null) {
			throw new IOException();
		}

		byte[] packHash;
		try {
			packHash = packOut.finishPack();
		} finally {
			packOut.close();
			packOut = null;
		}

		Collections.sort(objectList);
		File tmpIdx = idxFor(tmpPack);
		writePackIndex(tmpIdx, packHash, objectList);

		File realPack = new File(db.getPackDirectory(),
				"pack-" + computeName(objectList).name() + ".pack"); //$NON-NLS-1$ //$NON-NLS-2$
		db.closeAllPackHandles(realPack);
		tmpPack.setReadOnly();
		FileUtils.rename(tmpPack, realPack, ATOMIC_MOVE);

		File realIdx = idxFor(realPack);
		tmpIdx.setReadOnly();
		try {
			FileUtils.rename(tmpIdx, realIdx, ATOMIC_MOVE);
		} catch (IOException e) {
			File newIdx = new File(
					realIdx.getParentFile(), realIdx.getName() + ".new"); //$NON-NLS-1$
			try {
				FileUtils.rename(tmpIdx, newIdx, ATOMIC_MOVE);
			} catch (IOException e2) {
				newIdx = tmpIdx;
				e = e2;
			}
			throw new IOException(MessageFormat.format(
					JGitText.get().panicCantRenameIndexFile, newIdx,
					realIdx), e);
		}

		db.openPack(realPack);
		rollback = false;
		clear();
	}

	private static void writePackIndex(File idx, byte[] packHash,
			List<PackedObjectInfo> list) throws IOException {
		try (OutputStream os = new FileOutputStream(idx)) {
			PackIndexWriter w = PackIndexWriter.createVersion(os, INDEX_VERSION);
			w.write(list, packHash);
		}
	}

	private ObjectId computeName(List<PackedObjectInfo> list) {
		SHA1 md = digest().reset();
		byte[] buf = buffer();
		for (PackedObjectInfo otp : list) {
			otp.copyRawTo(buf, 0);
			md.update(buf, 0, OBJECT_ID_LENGTH);
		}
		return ObjectId.fromRaw(md.digest());
	}

	@Override
	public void close() {
		try {
			if (packOut != null) {
				try {
					packOut.close();
				} catch (IOException err) {
					// Ignore a close failure, the pack should be removed.
				}
			}
			if (rollback && tmpPack != null) {
				try {
					FileUtils.delete(tmpPack);
				} catch (IOException e) {
					// Still delete idx.
				}
				try {
					FileUtils.delete(idxFor(tmpPack));
				} catch (IOException e) {
					// Ignore error deleting temp idx.
				}
				rollback = false;
			}
		} finally {
			clear();
			try {
				InflaterCache.release(cachedInflater);
			} finally {
				cachedInflater = null;
			}
		}
	}

	private void clear() {
		objectList = null;
		objectMap = null;
		tmpPack = null;
		packOut = null;
	}

	private Inflater inflater() {
		if (cachedInflater == null) {
			cachedInflater = InflaterCache.get();
		} else {
			cachedInflater.reset();
		}
		return cachedInflater;
	}

	private class PackStream extends OutputStream {
		final byte[] hdrBuf;
		final CRC32 crc32;
		final DeflaterOutputStream compress;

		private final RandomAccessFile file;
		private final CountingOutputStream out;
		private final Deflater deflater;

		PackStream(File pack) throws IOException {
			file = new RandomAccessFile(pack, "rw"); //$NON-NLS-1$
			out = new CountingOutputStream(new FileOutputStream(file.getFD()));
			deflater = new Deflater(compression);
			compress = new DeflaterOutputStream(this, deflater, 8192);
			hdrBuf = new byte[32];
			crc32 = new CRC32();
		}

		long getOffset() {
			return out.getCount();
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
			out.write(data, off, len);
		}

		byte[] finishPack() throws IOException {
			// Overwrite placeholder header with actual object count, then hash.
			file.seek(0);
			write(hdrBuf, 0, writePackHeader(hdrBuf, objectList.size()));

			byte[] buf = buffer();
			SHA1 md = digest().reset();
			file.seek(0);
			while (true) {
				int r = file.read(buf);
				if (r < 0) {
					break;
				}
				md.update(buf, 0, r);
			}
			byte[] packHash = md.digest();
			out.write(packHash, 0, packHash.length);
			return packHash;
		}

		@Override
		public void close() throws IOException {
			deflater.end();
			out.close();
			file.close();
		}

		byte[] inflate(long filePos, int len) throws IOException, DataFormatException {
			byte[] dstbuf;
			try {
				dstbuf = new byte[len];
			} catch (OutOfMemoryError noMemory) {
				return null; // Caller will switch to large object streaming.
			}

			byte[] srcbuf = buffer();
			Inflater inf = inflater();
			filePos += setInput(filePos, inf, srcbuf);
			for (int dstoff = 0;;) {
				int n = inf.inflate(dstbuf, dstoff, dstbuf.length - dstoff);
				dstoff += n;
				if (inf.finished()) {
					return dstbuf;
				}
				if (inf.needsInput()) {
					filePos += setInput(filePos, inf, srcbuf);
				} else if (n == 0) {
					throw new DataFormatException();
				}
			}
		}

		private int setInput(long filePos, Inflater inf, byte[] buf)
				throws IOException {
			if (file.getFilePointer() != filePos) {
				file.seek(filePos);
			}
			int n = file.read(buf);
			if (n < 0) {
				throw new EOFException(JGitText.get().unexpectedEofInPack);
			}
			inf.setInput(buf, 0, n);
			return n;
		}
	}

	private class Reader extends ObjectReader {
		private final ObjectReader ctx;

		private Reader() {
			ctx = db.newReader();
			setStreamFileThreshold(ctx.getStreamFileThreshold());
		}

		@Override
		public ObjectReader newReader() {
			return db.newReader();
		}

		@Override
		public ObjectInserter getCreatedFromInserter() {
			return PackInserter.this;
		}

		@Override
		public Collection<ObjectId> resolve(AbbreviatedObjectId id)
				throws IOException {
			Collection<ObjectId> stored = ctx.resolve(id);
			if (objectList == null) {
				return stored;
			}

			Set<ObjectId> r = new HashSet<>(stored.size() + 2);
			r.addAll(stored);
			for (PackedObjectInfo obj : objectList) {
				if (id.prefixCompare(obj) == 0) {
					r.add(obj.copy());
				}
			}
			return r;
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			if (objectMap == null) {
				return ctx.open(objectId, typeHint);
			}

			PackedObjectInfo obj = objectMap.get(objectId);
			if (obj == null) {
				return ctx.open(objectId, typeHint);
			}

			byte[] buf = buffer();
			RandomAccessFile f = packOut.file;
			f.seek(obj.getOffset());
			int cnt = f.read(buf, 0, 20);
			if (cnt <= 0) {
				throw new EOFException(JGitText.get().unexpectedEofInPack);
			}

			int c = buf[0] & 0xff;
			int type = (c >> 4) & 7;
			if (type == OBJ_OFS_DELTA || type == OBJ_REF_DELTA) {
				throw new IOException(MessageFormat.format(
						JGitText.get().cannotReadBackDelta, Integer.toString(type)));
			}
			if (typeHint != OBJ_ANY && type != typeHint) {
				throw new IncorrectObjectTypeException(objectId.copy(), typeHint);
			}

			long sz = c & 0x0f;
			int ptr = 1;
			int shift = 4;
			while ((c & 0x80) != 0) {
				if (ptr >= cnt) {
					throw new EOFException(JGitText.get().unexpectedEofInPack);
				}
				c = buf[ptr++] & 0xff;
				sz += ((long) (c & 0x7f)) << shift;
				shift += 7;
			}

			long zpos = obj.getOffset() + ptr;
			if (sz < getStreamFileThreshold()) {
				byte[] data = inflate(obj, zpos, (int) sz);
				if (data != null) {
					return new ObjectLoader.SmallObject(type, data);
				}
			}
			return new StreamLoader(f, type, sz, zpos);
		}

		private byte[] inflate(PackedObjectInfo obj, long zpos, int sz)
				throws IOException, CorruptObjectException {
			try {
				return packOut.inflate(zpos, sz);
			} catch (DataFormatException dfe) {
				CorruptObjectException coe = new CorruptObjectException(
						MessageFormat.format(
								JGitText.get().objectAtHasBadZlibStream,
								Long.valueOf(obj.getOffset()),
								tmpPack.getAbsolutePath()));
				coe.initCause(dfe);
				throw coe;
			}
		}

		@Override
		public Set<ObjectId> getShallowCommits() throws IOException {
			return ctx.getShallowCommits();
		}

		@Override
		public void close() {
			ctx.close();
		}

		private class StreamLoader extends ObjectLoader {
			private final RandomAccessFile file;
			private final int type;
			private final long size;
			private final long pos;

			StreamLoader(RandomAccessFile file, int type, long size, long pos) {
				this.file = file;
				this.type = type;
				this.size = size;
				this.pos = pos;
			}

			@Override
			public ObjectStream openStream()
					throws MissingObjectException, IOException {
				int bufsz = buffer().length;
				file.seek(pos);
				return new ObjectStream.Filter(
						type, size,
						new BufferedInputStream(
								new InflaterInputStream(
										Channels.newInputStream(packOut.file.getChannel()),
										inflater(), bufsz),
								bufsz));
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
				throw new LargeObjectException.ExceedsLimit(
						getStreamFileThreshold(), size);
			}
		}
	}
}
