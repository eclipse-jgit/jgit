/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.internal.storage.pack.PackOutputStream;

/**
 * A window of data currently stored within a cache.
 * <p>
 * All bytes in the window can be assumed to be "immediately available", that is
 * they are very likely already in memory, unless the operating system's memory
 * is very low and has paged part of this process out to disk. Therefore copying
 * bytes from a window is very inexpensive.
 * </p>
 */
abstract class ByteWindow {
	protected final PackFile pack;

	protected final long start;

	protected final long end;

	/**
	 * Constructor for ByteWindow.
	 *
	 * @param p
	 *            a {@link org.eclipse.jgit.internal.storage.file.PackFile}.
	 * @param s
	 *            where the byte window starts in the pack file
	 * @param n
	 *            size of the byte window
	 */
	protected ByteWindow(PackFile p, long s, int n) {
		pack = p;
		start = s;
		end = start + n;
	}

	final int size() {
		return (int) (end - start);
	}

	final boolean contains(PackFile neededFile, long neededPos) {
		return pack == neededFile && start <= neededPos && neededPos < end;
	}

	/**
	 * Copy bytes from the window to a caller supplied buffer.
	 *
	 * @param pos
	 *            offset within the file to start copying from.
	 * @param dstbuf
	 *            destination buffer to copy into.
	 * @param dstoff
	 *            offset within <code>dstbuf</code> to start copying into.
	 * @param cnt
	 *            number of bytes to copy. This value may exceed the number of
	 *            bytes remaining in the window starting at offset
	 *            <code>pos</code>.
	 * @return number of bytes actually copied; this may be less than
	 *         <code>cnt</code> if <code>cnt</code> exceeded the number of
	 *         bytes available.
	 */
	final int copy(long pos, byte[] dstbuf, int dstoff, int cnt) {
		return copy((int) (pos - start), dstbuf, dstoff, cnt);
	}

	/**
	 * Copy bytes from the window to a caller supplied buffer.
	 *
	 * @param pos
	 *            offset within the window to start copying from.
	 * @param dstbuf
	 *            destination buffer to copy into.
	 * @param dstoff
	 *            offset within <code>dstbuf</code> to start copying into.
	 * @param cnt
	 *            number of bytes to copy. This value may exceed the number of
	 *            bytes remaining in the window starting at offset
	 *            <code>pos</code>.
	 * @return number of bytes actually copied; this may be less than
	 *         <code>cnt</code> if <code>cnt</code> exceeded the number of
	 *         bytes available.
	 */
	protected abstract int copy(int pos, byte[] dstbuf, int dstoff, int cnt);

	abstract void write(PackOutputStream out, long pos, int cnt)
			throws IOException;

	final int setInput(long pos, Inflater inf) throws DataFormatException {
		return setInput((int) (pos - start), inf);
	}

	/**
	 * Set the input
	 *
	 * @param pos
	 *            position
	 * @param inf
	 *            an {@link java.util.zip.Inflater} object.
	 * @return size of the byte window
	 * @throws java.util.zip.DataFormatException
	 *             if any.
	 */
	protected abstract int setInput(int pos, Inflater inf)
			throws DataFormatException;
}
