/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.pack;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.IO;

/**
 * Inflates a delta in an incremental way.
 * <p>
 * Implementations must provide a means to access a stream for the base object.
 * This stream may be accessed multiple times, in order to randomly position it
 * to match the copy instructions. A {@code DeltaStream} performs an efficient
 * skip by only moving through the delta stream, making restarts of stacked
 * deltas reasonably efficient.
 */
public abstract class DeltaStream extends InputStream {
	private static final int CMD_COPY = 0;

	private static final int CMD_INSERT = 1;

	private static final int CMD_EOF = 2;

	private final InputStream deltaStream;

	private long baseSize;

	private long resultSize;

	private final byte[] cmdbuf = new byte[512];

	private int cmdptr;

	private int cmdcnt;

	/** Stream to read from the base object. */
	private InputStream baseStream;

	/** Current position within {@link #baseStream}. */
	private long baseOffset;

	private int curcmd;

	/** If {@code curcmd == CMD_COPY}, position the base has to be at. */
	private long copyOffset;

	/** Total number of bytes in this current command. */
	private int copySize;

	/**
	 * Construct a delta application stream, reading instructions.
	 *
	 * @param deltaStream
	 *            the stream to read delta instructions from.
	 * @throws IOException
	 *             the delta instruction stream cannot be read, or is
	 *             inconsistent with the the base object information.
	 */
	public DeltaStream(final InputStream deltaStream) throws IOException {
		this.deltaStream = deltaStream;
		if (!fill(cmdbuf.length))
			throw new EOFException();

		// Length of the base object.
		//
		int c, shift = 0;
		do {
			c = cmdbuf[cmdptr++] & 0xff;
			baseSize |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		// Length of the resulting object.
		//
		shift = 0;
		do {
			c = cmdbuf[cmdptr++] & 0xff;
			resultSize |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		curcmd = next();
	}

	/**
	 * Open the base stream.
	 * <p>
	 * The {@code DeltaStream} may close and reopen the base stream multiple
	 * times if copy instructions use offsets out of order. This can occur if a
	 * large block in the file was moved from near the top, to near the bottom.
	 * In such cases the reopened stream is skipped to the target offset, so
	 * {@code skip(long)} should be as efficient as possible.
	 *
	 * @return stream to read from the base object. This stream should not be
	 *         buffered (or should be only minimally buffered), and does not
	 *         need to support mark/reset.
	 * @throws IOException
	 *             the base object cannot be opened for reading.
	 */
	protected abstract InputStream openBase() throws IOException;

	/**
	 * @return length of the base object, in bytes.
	 * @throws IOException
	 *             the length of the base cannot be determined.
	 */
	protected abstract long getBaseSize() throws IOException;

	/** @return total size of this stream, in bytes. */
	public long getSize() {
		return resultSize;
	}

	@Override
	public int read() throws IOException {
		byte[] buf = new byte[1];
		int n = read(buf, 0, 1);
		return n == 1 ? buf[0] & 0xff : -1;
	}

	@Override
	public void close() throws IOException {
		deltaStream.close();
		if (baseStream != null)
			baseStream.close();
	}

	@Override
	public long skip(long len) throws IOException {
		long act = 0;
		while (0 < len) {
			long n = Math.min(len, copySize);
			switch (curcmd) {
			case CMD_COPY:
				copyOffset += n;
				break;

			case CMD_INSERT:
				cmdptr += n;
				break;

			case CMD_EOF:
				return act;
			default:
				throw new CorruptObjectException(
						JGitText.get().unsupportedCommand0);
			}

			act += n;
			len -= n;
			copySize -= n;
			if (copySize == 0)
				curcmd = next();
		}
		return act;
	}

	@Override
	public int read(byte[] buf, int off, int len) throws IOException {
		int act = 0;
		while (0 < len) {
			int n = Math.min(len, copySize);
			switch (curcmd) {
			case CMD_COPY:
				seekBase();
				n = baseStream.read(buf, off, n);
				if (n < 0)
					throw new CorruptObjectException(
							JGitText.get().baseLengthIncorrect);
				copyOffset += n;
				baseOffset = copyOffset;
				break;

			case CMD_INSERT:
				System.arraycopy(cmdbuf, cmdptr, buf, off, n);
				cmdptr += n;
				break;

			case CMD_EOF:
				return 0 < act ? act : -1;

			default:
				throw new CorruptObjectException(
						JGitText.get().unsupportedCommand0);
			}

			act += n;
			off += n;
			len -= n;
			copySize -= n;
			if (copySize == 0)
				curcmd = next();
		}
		return act;
	}

	private boolean fill(final int need) throws IOException {
		int n = have();
		if (need < n)
			return true;
		if (n == 0) {
			cmdptr = 0;
			cmdcnt = 0;
		} else if (cmdbuf.length - cmdptr < need) {
			// There isn't room for the entire worst-case copy command,
			// so shift the array down to make sure we can use the entire
			// command without having it span across the end of the array.
			//
			System.arraycopy(cmdbuf, cmdptr, cmdbuf, 0, n);
			cmdptr = 0;
			cmdcnt = n;
		}

		do {
			n = deltaStream.read(cmdbuf, cmdcnt, cmdbuf.length - cmdcnt);
			if (n < 0)
				return 0 < have();
			cmdcnt += n;
		} while (cmdcnt < cmdbuf.length);
		return true;
	}

	private int next() throws IOException {
		if (!fill(8))
			return CMD_EOF;

		final int cmd = cmdbuf[cmdptr++] & 0xff;
		if ((cmd & 0x80) != 0) {
			// Determine the segment of the base which should
			// be copied into the output. The segment is given
			// as an offset and a length.
			//
			copyOffset = 0;
			if ((cmd & 0x01) != 0)
				copyOffset = cmdbuf[cmdptr++] & 0xff;
			if ((cmd & 0x02) != 0)
				copyOffset |= (cmdbuf[cmdptr++] & 0xff) << 8;
			if ((cmd & 0x04) != 0)
				copyOffset |= (cmdbuf[cmdptr++] & 0xff) << 16;
			if ((cmd & 0x08) != 0)
				copyOffset |= ((long) (cmdbuf[cmdptr++] & 0xff)) << 24;

			copySize = 0;
			if ((cmd & 0x10) != 0)
				copySize = cmdbuf[cmdptr++] & 0xff;
			if ((cmd & 0x20) != 0)
				copySize |= (cmdbuf[cmdptr++] & 0xff) << 8;
			if ((cmd & 0x40) != 0)
				copySize |= (cmdbuf[cmdptr++] & 0xff) << 16;
			if (copySize == 0)
				copySize = 0x10000;
			return CMD_COPY;

		} else if (cmd != 0) {
			// Anything else the data is literal within the delta
			// itself. Page the entire thing into the cmdbuf, if
			// its not already there.
			//
			fill(cmd);
			copySize = cmd;
			return CMD_INSERT;

		} else {
			// cmd == 0 has been reserved for future encoding but
			// for now its not acceptable.
			//
			throw new CorruptObjectException(JGitText.get().unsupportedCommand0);
		}
	}

	private int have() {
		return cmdcnt - cmdptr;
	}

	private void seekBase() throws IOException {
		if (baseStream == null) {
			baseStream = openBase();
			if (getBaseSize() != baseSize)
				throw new CorruptObjectException(
						JGitText.get().baseLengthIncorrect);
			IO.skipFully(baseStream, copyOffset);
			baseOffset = copyOffset;

		} else if (baseOffset < copyOffset) {
			IO.skipFully(baseStream, copyOffset - baseOffset);
			baseOffset = copyOffset;

		} else if (baseOffset > copyOffset) {
			baseStream.close();
			baseStream = openBase();
			IO.skipFully(baseStream, copyOffset);
			baseOffset = copyOffset;
		}
	}
}
