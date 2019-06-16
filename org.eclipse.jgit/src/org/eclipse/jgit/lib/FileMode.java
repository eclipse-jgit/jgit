/*
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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Constants describing various file modes recognized by GIT.
 * <p>
 * GIT uses a subset of the available UNIX file permission bits. The
 * <code>FileMode</code> class provides access to constants defining the modes
 * actually used by GIT.
 * </p>
 */
public abstract class FileMode {
	/**
	 * Mask to apply to a file mode to obtain its type bits.
	 *
	 * @see #TYPE_TREE
	 * @see #TYPE_SYMLINK
	 * @see #TYPE_FILE
	 * @see #TYPE_GITLINK
	 * @see #TYPE_MISSING
	 */
	public static final int TYPE_MASK = 0170000;

	/** Bit pattern for {@link #TYPE_MASK} matching {@link #TREE}. */
	public static final int TYPE_TREE = 0040000;

	/** Bit pattern for {@link #TYPE_MASK} matching {@link #SYMLINK}. */
	public static final int TYPE_SYMLINK = 0120000;

	/** Bit pattern for {@link #TYPE_MASK} matching {@link #REGULAR_FILE}. */
	public static final int TYPE_FILE = 0100000;

	/** Bit pattern for {@link #TYPE_MASK} matching {@link #GITLINK}. */
	public static final int TYPE_GITLINK = 0160000;

	/** Bit pattern for {@link #TYPE_MASK} matching {@link #MISSING}. */
	public static final int TYPE_MISSING = 0000000;

	/**
	 * Mode indicating an entry is a tree (aka directory).
	 */
	public static final FileMode TREE = new FileMode(TYPE_TREE,
			Constants.OBJ_TREE) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return (modeBits & TYPE_MASK) == TYPE_TREE;
		}
	};

	/** Mode indicating an entry is a symbolic link. */
	public static final FileMode SYMLINK = new FileMode(TYPE_SYMLINK,
			Constants.OBJ_BLOB) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return (modeBits & TYPE_MASK) == TYPE_SYMLINK;
		}
	};

	/** Mode indicating an entry is a non-executable file. */
	public static final FileMode REGULAR_FILE = new FileMode(0100644,
			Constants.OBJ_BLOB) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return (modeBits & TYPE_MASK) == TYPE_FILE && (modeBits & 0111) == 0;
		}
	};

	/** Mode indicating an entry is an executable file. */
	public static final FileMode EXECUTABLE_FILE = new FileMode(0100755,
			Constants.OBJ_BLOB) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return (modeBits & TYPE_MASK) == TYPE_FILE && (modeBits & 0111) != 0;
		}
	};

	/** Mode indicating an entry is a submodule commit in another repository. */
	public static final FileMode GITLINK = new FileMode(TYPE_GITLINK,
			Constants.OBJ_COMMIT) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return (modeBits & TYPE_MASK) == TYPE_GITLINK;
		}
	};

	/** Mode indicating an entry is missing during parallel walks. */
	public static final FileMode MISSING = new FileMode(TYPE_MISSING,
			Constants.OBJ_BAD) {
		@Override
		@SuppressWarnings("NonOverridingEquals")
		public boolean equals(int modeBits) {
			return modeBits == 0;
		}
	};

	/**
	 * Convert a set of mode bits into a FileMode enumerated value.
	 *
	 * @param bits
	 *            the mode bits the caller has somehow obtained.
	 * @return the FileMode instance that represents the given bits.
	 */
	public static final FileMode fromBits(int bits) {
		switch (bits & TYPE_MASK) {
		case TYPE_MISSING:
			if (bits == 0)
				return MISSING;
			break;
		case TYPE_TREE:
			return TREE;
		case TYPE_FILE:
			if ((bits & 0111) != 0)
				return EXECUTABLE_FILE;
			return REGULAR_FILE;
		case TYPE_SYMLINK:
			return SYMLINK;
		case TYPE_GITLINK:
			return GITLINK;
		}

		return new FileMode(bits, Constants.OBJ_BAD) {
			@Override
			@SuppressWarnings("NonOverridingEquals")
			public boolean equals(int a) {
				return bits == a;
			}
		};
	}

	private final byte[] octalBytes;

	private final int modeBits;

	private final int objectType;

	private FileMode(int mode, int expType) {
		modeBits = mode;
		objectType = expType;
		if (mode != 0) {
			final byte[] tmp = new byte[10];
			int p = tmp.length;

			while (mode != 0) {
				tmp[--p] = (byte) ('0' + (mode & 07));
				mode >>= 3;
			}

			octalBytes = new byte[tmp.length - p];
			for (int k = 0; k < octalBytes.length; k++) {
				octalBytes[k] = tmp[p + k];
			}
		} else {
			octalBytes = new byte[] { '0' };
		}
	}

	/**
	 * Test a file mode for equality with this
	 * {@link org.eclipse.jgit.lib.FileMode} object.
	 *
	 * @param modebits
	 *            a int.
	 * @return true if the mode bits represent the same mode as this object
	 */
	@SuppressWarnings("NonOverridingEquals")
	public abstract boolean equals(int modebits);

	/**
	 * Copy this mode as a sequence of octal US-ASCII bytes.
	 * <p>
	 * The mode is copied as a sequence of octal digits using the US-ASCII
	 * character encoding. The sequence does not use a leading '0' prefix to
	 * indicate octal notation. This method is suitable for generation of a mode
	 * string within a GIT tree object.
	 * </p>
	 *
	 * @param os
	 *            stream to copy the mode to.
	 * @throws java.io.IOException
	 *             the stream encountered an error during the copy.
	 */
	public void copyTo(OutputStream os) throws IOException {
		os.write(octalBytes);
	}

	/**
	 * Copy this mode as a sequence of octal US-ASCII bytes.
	 *
	 * The mode is copied as a sequence of octal digits using the US-ASCII
	 * character encoding. The sequence does not use a leading '0' prefix to
	 * indicate octal notation. This method is suitable for generation of a mode
	 * string within a GIT tree object.
	 *
	 * @param buf
	 *            buffer to copy the mode to.
	 * @param ptr
	 *            position within {@code buf} for first digit.
	 */
	public void copyTo(byte[] buf, int ptr) {
		System.arraycopy(octalBytes, 0, buf, ptr, octalBytes.length);
	}

	/**
	 * Copy the number of bytes written by {@link #copyTo(OutputStream)}.
	 *
	 * @return the number of bytes written by {@link #copyTo(OutputStream)}.
	 */
	public int copyToLength() {
		return octalBytes.length;
	}

	/**
	 * Get the object type that should appear for this type of mode.
	 * <p>
	 * See the object type constants in {@link org.eclipse.jgit.lib.Constants}.
	 *
	 * @return one of the well known object type constants.
	 */
	public int getObjectType() {
		return objectType;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Format this mode as an octal string (for debugging only).
	 */
	@Override
	public String toString() {
		return Integer.toOctalString(modeBits);
	}

	/**
	 * Get the mode bits as an integer.
	 *
	 * @return The mode bits as an integer.
	 */
	public int getBits() {
		return modeBits;
	}
}
