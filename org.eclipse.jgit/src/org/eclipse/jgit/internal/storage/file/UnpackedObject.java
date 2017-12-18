/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Loose object loader. This class loads an object not stored in a pack.
 */
public class UnpackedObject {
	private static final int BUFFER_SIZE = 8192;

	/**
	 * Parse an object from the unpacked object format.
	 *
	 * @param raw
	 *            complete contents of the compressed object.
	 * @param id
	 *            expected ObjectId of the object, used only for error reporting
	 *            in exceptions.
	 * @return loader to read the inflated contents.
	 * @throws java.io.IOException
	 *             the object cannot be parsed.
	 */
	public static ObjectLoader parse(byte[] raw, AnyObjectId id)
			throws IOException {
		try (WindowCursor wc = new WindowCursor(null)) {
			return open(new ByteArrayInputStream(raw), null, id, wc);
		}
	}

	static ObjectLoader open(InputStream in, File path, AnyObjectId id,
			WindowCursor wc) throws IOException {
		try {
			in = buffer(in);
			in.mark(20);
			final byte[] hdr = new byte[64];
			IO.readFully(in, hdr, 0, 2);

			if (isStandardFormat(hdr)) {
				in.reset();
				Inflater inf = wc.inflater();
				InputStream zIn = inflate(in, inf);
				int avail = readSome(zIn, hdr, 0, 64);
				if (avail < 5)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectNoHeader);

				final MutableInteger p = new MutableInteger();
				int type = Constants.decodeTypeString(id, hdr, (byte) ' ', p);
				long size = RawParseUtils.parseLongBase10(hdr, p.value, p);
				if (size < 0)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectNegativeSize);
				if (hdr[p.value++] != 0)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectGarbageAfterSize);
				if (path == null && Integer.MAX_VALUE < size) {
					LargeObjectException.ExceedsByteArrayLimit e;
					e = new LargeObjectException.ExceedsByteArrayLimit();
					e.setObjectId(id);
					throw e;
				}
				if (size < wc.getStreamFileThreshold() || path == null) {
					byte[] data = new byte[(int) size];
					int n = avail - p.value;
					if (n > 0)
						System.arraycopy(hdr, p.value, data, 0, n);
					IO.readFully(zIn, data, n, data.length - n);
					checkValidEndOfStream(in, inf, id, hdr);
					return new ObjectLoader.SmallObject(type, data);
				}
				return new LargeObject(type, size, path, id, wc.db);

			} else {
				readSome(in, hdr, 2, 18);
				int c = hdr[0] & 0xff;
				int type = (c >> 4) & 7;
				long size = c & 15;
				int shift = 4;
				int p = 1;
				while ((c & 0x80) != 0) {
					c = hdr[p++] & 0xff;
					size += ((long) (c & 0x7f)) << shift;
					shift += 7;
				}

				switch (type) {
				case Constants.OBJ_COMMIT:
				case Constants.OBJ_TREE:
				case Constants.OBJ_BLOB:
				case Constants.OBJ_TAG:
					// Acceptable types for a loose object.
					break;
				default:
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectInvalidType);
				}

				if (path == null && Integer.MAX_VALUE < size) {
					LargeObjectException.ExceedsByteArrayLimit e;
					e = new LargeObjectException.ExceedsByteArrayLimit();
					e.setObjectId(id);
					throw e;
				}
				if (size < wc.getStreamFileThreshold() || path == null) {
					in.reset();
					IO.skipFully(in, p);
					Inflater inf = wc.inflater();
					InputStream zIn = inflate(in, inf);
					byte[] data = new byte[(int) size];
					IO.readFully(zIn, data, 0, data.length);
					checkValidEndOfStream(in, inf, id, hdr);
					return new ObjectLoader.SmallObject(type, data);
				}
				return new LargeObject(type, size, path, id, wc.db);
			}
		} catch (ZipException badStream) {
			throw new CorruptObjectException(id,
					JGitText.get().corruptObjectBadStream);
		}
	}

	static long getSize(InputStream in, AnyObjectId id, WindowCursor wc)
			throws IOException {
		try {
			in = buffer(in);
			in.mark(20);
			final byte[] hdr = new byte[64];
			IO.readFully(in, hdr, 0, 2);

			if (isStandardFormat(hdr)) {
				in.reset();
				Inflater inf = wc.inflater();
				InputStream zIn = inflate(in, inf);
				int avail = readSome(zIn, hdr, 0, 64);
				if (avail < 5)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectNoHeader);

				final MutableInteger p = new MutableInteger();
				Constants.decodeTypeString(id, hdr, (byte) ' ', p);
				long size = RawParseUtils.parseLongBase10(hdr, p.value, p);
				if (size < 0)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectNegativeSize);
				return size;

			} else {
				readSome(in, hdr, 2, 18);
				int c = hdr[0] & 0xff;
				long size = c & 15;
				int shift = 4;
				int p = 1;
				while ((c & 0x80) != 0) {
					c = hdr[p++] & 0xff;
					size += ((long) (c & 0x7f)) << shift;
					shift += 7;
				}
				return size;
			}
		} catch (ZipException badStream) {
			throw new CorruptObjectException(id,
					JGitText.get().corruptObjectBadStream);
		}
	}

	static void checkValidEndOfStream(InputStream in, Inflater inf,
			AnyObjectId id, final byte[] buf) throws IOException,
			CorruptObjectException {
		for (;;) {
			int r;
			try {
				r = inf.inflate(buf);
			} catch (DataFormatException e) {
				throw new CorruptObjectException(id,
						JGitText.get().corruptObjectBadStream);
			}
			if (r != 0)
				throw new CorruptObjectException(id,
						JGitText.get().corruptObjectIncorrectLength);

			if (inf.finished()) {
				if (inf.getRemaining() != 0 || in.read() != -1)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectBadStream);
				break;
			}

			if (!inf.needsInput())
				throw new CorruptObjectException(id,
						JGitText.get().corruptObjectBadStream);

			r = in.read(buf);
			if (r <= 0)
				throw new CorruptObjectException(id,
						JGitText.get().corruptObjectBadStream);
			inf.setInput(buf, 0, r);
		}
	}

	static boolean isStandardFormat(final byte[] hdr) {
		/*
		 * We must determine if the buffer contains the standard
		 * zlib-deflated stream or the experimental format based
		 * on the in-pack object format. Compare the header byte
		 * for each format:
		 *
		 * RFC1950 zlib w/ deflate : 0www1000 : 0 <= www <= 7
		 * Experimental pack-based : Stttssss : ttt = 1,2,3,4
		 *
		 * If bit 7 is clear and bits 0-3 equal 8, the buffer MUST be
		 * in standard loose-object format, UNLESS it is a Git-pack
		 * format object *exactly* 8 bytes in size when inflated.
		 *
		 * However, RFC1950 also specifies that the 1st 16-bit word
		 * must be divisible by 31 - this checksum tells us our buffer
		 * is in the standard format, giving a false positive only if
		 * the 1st word of the Git-pack format object happens to be
		 * divisible by 31, ie:
		 *      ((byte0 * 256) + byte1) % 31 = 0
		 *   =>        0ttt10000www1000 % 31 = 0
		 *
		 * As it happens, this case can only arise for www=3 & ttt=1
		 * - ie, a Commit object, which would have to be 8 bytes in
		 * size. As no Commit can be that small, we find that the
		 * combination of these two criteria (bitmask & checksum)
		 * can always correctly determine the buffer format.
		 */
		final int fb = hdr[0] & 0xff;
		return (fb & 0x8f) == 0x08 && (((fb << 8) | hdr[1] & 0xff) % 31) == 0;
	}

	static InputStream inflate(final InputStream in, final long size,
			final ObjectId id) {
		final Inflater inf = InflaterCache.get();
		return new InflaterInputStream(in, inf) {
			private long remaining = size;

			@Override
			public int read(byte[] b, int off, int cnt) throws IOException {
				try {
					int r = super.read(b, off, cnt);
					if (r > 0)
						remaining -= r;
					return r;
				} catch (ZipException badStream) {
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectBadStream);
				}
			}

			@Override
			public void close() throws IOException {
				try {
					if (remaining <= 0)
						checkValidEndOfStream(in, inf, id, new byte[64]);
				} finally {
					InflaterCache.release(inf);
					super.close();
				}
			}
		};
	}

	private static InflaterInputStream inflate(InputStream in, Inflater inf) {
		return new InflaterInputStream(in, inf, BUFFER_SIZE);
	}

	static BufferedInputStream buffer(InputStream in) {
		return new BufferedInputStream(in, BUFFER_SIZE);
	}

	static int readSome(InputStream in, final byte[] hdr, int off,
			int cnt) throws IOException {
		int avail = 0;
		while (0 < cnt) {
			int n = in.read(hdr, off, cnt);
			if (n < 0)
				break;
			avail += n;
			off += n;
			cnt -= n;
		}
		return avail;
	}

	private static final class LargeObject extends ObjectLoader {
		private final int type;

		private final long size;

		private final File path;

		private final ObjectId id;

		private final FileObjectDatabase source;

		LargeObject(int type, long size, File path, AnyObjectId id,
				FileObjectDatabase db) {
			this.type = type;
			this.size = size;
			this.path = path;
			this.id = id.copy();
			this.source = db;
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
		public boolean isLarge() {
			return true;
		}

		@Override
		public byte[] getCachedBytes() throws LargeObjectException {
			throw new LargeObjectException(id);
		}

		@Override
		public ObjectStream openStream() throws MissingObjectException,
				IOException {
			InputStream in;
			try {
				in = buffer(new FileInputStream(path));
			} catch (FileNotFoundException gone) {
				if (path.exists()) {
					throw gone;
				}
				// If the loose file no longer exists, it may have been
				// moved into a pack file in the mean time. Try again
				// to locate the object.
				//
				return source.open(id, type).openStream();
			}

			boolean ok = false;
			try {
				final byte[] hdr = new byte[64];
				in.mark(20);
				IO.readFully(in, hdr, 0, 2);

				if (isStandardFormat(hdr)) {
					in.reset();
					in = buffer(inflate(in, size, id));
					while (0 < in.read())
						continue;
				} else {
					readSome(in, hdr, 2, 18);
					int c = hdr[0] & 0xff;
					int p = 1;
					while ((c & 0x80) != 0)
						c = hdr[p++] & 0xff;

					in.reset();
					IO.skipFully(in, p);
					in = buffer(inflate(in, size, id));
				}

				ok = true;
				return new ObjectStream.Filter(type, size, in);
			} finally {
				if (!ok)
					in.close();
			}
		}
	}
}
