/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.CRC32;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.SeekableInputStream;

/**
 * Reads a delta base from an object stream, or from a cached file.
 *
 * When this stream seeks forward, it just skips the underlying object stream.
 * On the first reverse seek it instead switches to a cache file stored under
 * {@code $GIT_DIR/objects/delta-base-cache}. If the cache file doesn't yet
 * exist the base is first fully inflated into the cache file. This causes the
 * base to be inflated at most 2x (once during the first scan through, then
 * again if there is a reverse seek).
 *
 * The cache file contains a 20 byte header record describing the contents.
 * Following the header are blocks of data, each data block ends with a CRC 32
 * checksum of that block. The CRC 32 checksum is checked during reads, to
 * ensure the file does not contain and propagate silent data corruption. Doing
 * the checksum at the block level permits reasonable random access without
 * needing to load the entire file in order to validate its contents.
 *
 * When copying the base out to the new cache file, the SHA-1 checksum of the
 * base is validated to ensure it inflated correctly from the source pack, and
 * to prevent pack errors from carrying into a valid cache file.
 */
class LargeDeltaBaseInputStream extends SeekableInputStream {
	private static final int HDR_SZ = 20;

	private static final byte[] MAGIC = { 'c', 'a', 'c', '3' };

	private final WindowCursor wc;

	private final PackFile packFile;

	private final long packOffset;

	private final long size;

	private long ptr;

	private int blockSize;

	private ObjectStream origIn;

	private SeekableInputStream seekIn;

	private CRC32 crc32;

	private byte[] blockBuffer;

	private long blockBufferId;

	private int blockBufferLen;

	private ObjectId baseId;

	private File basePath;

	LargeDeltaBaseInputStream(ObjectStream in, PackFile pack, long ofs,
			WindowCursor wc, int desiredBlockSize) {
		this.origIn = in;
		this.packFile = pack;
		this.packOffset = ofs;
		this.wc = wc;
		this.size = origIn.getSize();
		this.blockSize = desiredBlockSize;
	}

	@Override
	public void seek(long offset) throws IOException {
		if (offset < ptr && seekIn == null) {
			// During our first backwards seek reset and copy the base
			// out to a cache file.
			//

			origIn.close();
			origIn = null;

			crc32 = new CRC32();
			baseId = packFile.findObjectForOffset(packOffset);
			initSeekIn();
		}

		if (origIn != null)
			IO.skipFully(origIn, offset - ptr);
		ptr = offset;
	}

	@Override
	public long size() throws IOException {
		return size;
	}

	@Override
	public long position() {
		return ptr;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0)
			return 0;
		if (ptr == size)
			return -1;

		if (origIn != null) {
			int r = origIn.read(b, off, len);
			if (0 < r)
				ptr += r;
			return r;
		}

		int r = 0;
		while (0 < len) {
			final long blockId = (int) (ptr / blockSize);
			final int p = (int) (ptr - blockId * blockSize);
			if (blockBufferId != blockId) {
				final long bPos = HDR_SZ + blockId * (blockSize + 4);
				final int bLen;

				if (blockId == size / blockSize && (size % blockSize) != 0)
					bLen = (int) (size % blockSize) + 4;
				else
					bLen = blockSize + 4;

				seekIn.seek(bPos);
				IO.readFully(seekIn, blockBuffer, 0, bLen);

				crc32.reset();
				crc32.update(blockBuffer, 0, bLen - 4);
				if (crc32.getValue() == NB.decodeUInt32(blockBuffer, bLen - 4)) {
					blockBufferId = blockId;
					blockBufferLen = bLen;
				} else {
					deleteCorruptBase();
					initSeekIn();
					continue;
				}
			}

			final int n = Math.min(blockBufferLen - 4, len);
			System.arraycopy(blockBuffer, p, b, off, n);

			off += n;
			len -= n;
			r += n;
			ptr += n;
		}
		return r;
	}

	@Override
	public void close() throws IOException {
		if (origIn != null)
			origIn.close();
		if (seekIn != null)
			seekIn.close();
	}

	private void initSeekIn() throws IOException, MissingObjectException,
			FileNotFoundException {
		basePath = wc.db.deltaBaseCacheEntry(baseId);
		byte[] hdr = new byte[HDR_SZ];
		for (;;) {
			try {
				seekIn = SeekableInputStream.open(basePath);
			} catch (FileNotFoundException notFound) {
				extractBase();
				continue;
			}

			IO.readFully(seekIn, hdr, 0, HDR_SZ);
			if (MAGIC[0] != hdr[0] //
					|| MAGIC[1] != hdr[1] //
					|| MAGIC[2] != hdr[2] //
					|| MAGIC[3] != hdr[3] //
					|| 1 != NB.decodeInt32(hdr, 4)
					|| size != NB.decodeUInt64(hdr, 12)) {
				deleteCorruptBase();
				continue;
			}
			break;
		}

		blockSize = NB.decodeInt32(hdr, 8);
		blockBuffer = new byte[blockSize + 4];
		blockBufferId = -1;
	}

	private void deleteCorruptBase() throws IOException {
		seekIn.close();
		seekIn = null;

		if (!basePath.delete() && basePath.exists())
			throw new CorruptObjectException(baseId,
					JGitText.get().cannotReadObject);
	}

	private void extractBase() throws IOException, MissingObjectException,
			FileNotFoundException {
		File dir = basePath.getParentFile();
		if (!dir.exists())
			dir.mkdirs();

		boolean ok = false;
		File tmp = File.createTempFile("base", "dat", dir);
		try {
			ObjectStream in = packFile.load(wc, packOffset).openStream();
			try {
				MessageDigest md = Constants.newMessageDigest();
				md.update(Constants.encodedTypeString(in.getType()));
				md.update((byte) ' ');
				md.update(Constants.encodeASCII(size));
				md.update((byte) 0);

				long cnt = 0;
				FileOutputStream out = new FileOutputStream(tmp);
				try {
					byte[] buf = new byte[blockSize + 4];
					System.arraycopy(MAGIC, 0, buf, 0, 4);
					NB.encodeInt32(buf, 4, 1);
					NB.encodeInt32(buf, 8, blockSize);
					NB.encodeInt64(buf, 12, size);
					out.write(buf, 0, HDR_SZ);

					while (cnt < size) {
						int n = (int) Math.min(blockSize, size - cnt);
						IO.readFully(in, buf, 0, n);

						crc32.reset();
						crc32.update(buf, 0, n);
						NB.encodeInt32(buf, n, (int) crc32.getValue());
						md.update(buf, 0, n);

						out.write(buf, 0, n + 4);
						cnt += n;
					}
				} finally {
					out.close();
				}

				if (!baseId.equals(ObjectId.fromRaw(md.digest())))
					throw new CorruptObjectException(baseId,
							JGitText.get().cannotReadObject);

				if (size == cnt)
					ok = true;
			} finally {
				in.close();
			}

			if (ok) {
				tmp.setReadOnly();
				ok = tmp.renameTo(basePath);
			}
		} finally {
			if (!ok)
				tmp.delete();
		}
	}
}
