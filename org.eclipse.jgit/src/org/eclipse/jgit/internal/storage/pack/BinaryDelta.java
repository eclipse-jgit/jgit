/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
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

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Recreate a stream from a base stream and a GIT pack delta.
 * <p>
 * This entire class is heavily cribbed from <code>patch-delta.c</code> in the
 * GIT project. The original delta patching code was written by Nicolas Pitre
 * (&lt;nico@cam.org&gt;).
 * </p>
 */
public class BinaryDelta {
	/**
	 * Length of the base object in the delta stream.
	 *
	 * @param delta
	 *            the delta stream, or at least the header of it.
	 * @return the base object's size.
	 */
	public static long getBaseSize(final byte[] delta) {
		int p = 0;
		long baseLen = 0;
		int c, shift = 0;
		do {
			c = delta[p++] & 0xff;
			baseLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);
		return baseLen;
	}

	/**
	 * Length of the resulting object in the delta stream.
	 *
	 * @param delta
	 *            the delta stream, or at least the header of it.
	 * @return the resulting object's size.
	 */
	public static long getResultSize(final byte[] delta) {
		int p = 0;

		// Skip length of the base object.
		//
		int c;
		do {
			c = delta[p++] & 0xff;
		} while ((c & 0x80) != 0);

		long resLen = 0;
		int shift = 0;
		do {
			c = delta[p++] & 0xff;
			resLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);
		return resLen;
	}

	/**
	 * Apply the changes defined by delta to the data in base, yielding a new
	 * array of bytes.
	 *
	 * @param base
	 *            some byte representing an object of some kind.
	 * @param delta
	 *            a git pack delta defining the transform from one version to
	 *            another.
	 * @return patched base
	 */
	public static final byte[] apply(final byte[] base, final byte[] delta) {
		return apply(base, delta, null);
	}

	/**
	 * Apply the changes defined by delta to the data in base, yielding a new
	 * array of bytes.
	 *
	 * @param base
	 *            some byte representing an object of some kind.
	 * @param delta
	 *            a git pack delta defining the transform from one version to
	 *            another.
	 * @param result
	 *            array to store the result into. If null the result will be
	 *            allocated and returned.
	 * @return either {@code result}, or the result array allocated.
	 */
	public static final byte[] apply(final byte[] base, final byte[] delta,
			byte[] result) {
		int deltaPtr = 0;

		// Length of the base object (a variable length int).
		//
		int baseLen = 0;
		int c, shift = 0;
		do {
			c = delta[deltaPtr++] & 0xff;
			baseLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);
		if (base.length != baseLen)
			throw new IllegalArgumentException(
					JGitText.get().baseLengthIncorrect);

		// Length of the resulting object (a variable length int).
		//
		int resLen = 0;
		shift = 0;
		do {
			c = delta[deltaPtr++] & 0xff;
			resLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		if (result == null)
			result = new byte[resLen];
		else if (result.length != resLen)
			throw new IllegalArgumentException(
					JGitText.get().resultLengthIncorrect);

		int resultPtr = 0;
		while (deltaPtr < delta.length) {
			final int cmd = delta[deltaPtr++] & 0xff;
			if ((cmd & 0x80) != 0) {
				// Determine the segment of the base which should
				// be copied into the output. The segment is given
				// as an offset and a length.
				//
				int copyOffset = 0;
				if ((cmd & 0x01) != 0)
					copyOffset = delta[deltaPtr++] & 0xff;
				if ((cmd & 0x02) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 8;
				if ((cmd & 0x04) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 16;
				if ((cmd & 0x08) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 24;

				int copySize = 0;
				if ((cmd & 0x10) != 0)
					copySize = delta[deltaPtr++] & 0xff;
				if ((cmd & 0x20) != 0)
					copySize |= (delta[deltaPtr++] & 0xff) << 8;
				if ((cmd & 0x40) != 0)
					copySize |= (delta[deltaPtr++] & 0xff) << 16;
				if (copySize == 0)
					copySize = 0x10000;

				System.arraycopy(base, copyOffset, result, resultPtr, copySize);
				resultPtr += copySize;
			} else if (cmd != 0) {
				// Anything else the data is literal within the delta
				// itself.
				//
				System.arraycopy(delta, deltaPtr, result, resultPtr, cmd);
				deltaPtr += cmd;
				resultPtr += cmd;
			} else {
				// cmd == 0 has been reserved for future encoding but
				// for now its not acceptable.
				//
				throw new IllegalArgumentException(
						JGitText.get().unsupportedCommand0);
			}
		}

		return result;
	}

	/**
	 * Format this delta as a human readable string.
	 *
	 * @param delta
	 *            the delta instruction sequence to format.
	 * @return the formatted delta.
	 */
	public static String format(byte[] delta) {
		return format(delta, true);
	}

	/**
	 * Format this delta as a human readable string.
	 *
	 * @param delta
	 *            the delta instruction sequence to format.
	 * @param includeHeader
	 *            true if the header (base size and result size) should be
	 *            included in the formatting.
	 * @return the formatted delta.
	 */
	public static String format(byte[] delta, boolean includeHeader) {
		StringBuilder r = new StringBuilder();
		int deltaPtr = 0;

		long baseLen = 0;
		int c, shift = 0;
		do {
			c = delta[deltaPtr++] & 0xff;
			baseLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		long resLen = 0;
		shift = 0;
		do {
			c = delta[deltaPtr++] & 0xff;
			resLen |= ((long) (c & 0x7f)) << shift;
			shift += 7;
		} while ((c & 0x80) != 0);

		if (includeHeader)
			r.append("DELTA( BASE=" + baseLen + " RESULT=" + resLen + " )\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		while (deltaPtr < delta.length) {
			final int cmd = delta[deltaPtr++] & 0xff;
			if ((cmd & 0x80) != 0) {
				// Determine the segment of the base which should
				// be copied into the output. The segment is given
				// as an offset and a length.
				//
				int copyOffset = 0;
				if ((cmd & 0x01) != 0)
					copyOffset = delta[deltaPtr++] & 0xff;
				if ((cmd & 0x02) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 8;
				if ((cmd & 0x04) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 16;
				if ((cmd & 0x08) != 0)
					copyOffset |= (delta[deltaPtr++] & 0xff) << 24;

				int copySize = 0;
				if ((cmd & 0x10) != 0)
					copySize = delta[deltaPtr++] & 0xff;
				if ((cmd & 0x20) != 0)
					copySize |= (delta[deltaPtr++] & 0xff) << 8;
				if ((cmd & 0x40) != 0)
					copySize |= (delta[deltaPtr++] & 0xff) << 16;
				if (copySize == 0)
					copySize = 0x10000;

				r.append("  COPY  (" + copyOffset + ", " + copySize + ")\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			} else if (cmd != 0) {
				// Anything else the data is literal within the delta
				// itself.
				//
				r.append("  INSERT("); //$NON-NLS-1$
				r.append(QuotedString.GIT_PATH.quote(RawParseUtils.decode(
						delta, deltaPtr, deltaPtr + cmd)));
				r.append(")\n"); //$NON-NLS-1$
				deltaPtr += cmd;
			} else {
				// cmd == 0 has been reserved for future encoding but
				// for now its not acceptable.
				//
				throw new IllegalArgumentException(
						JGitText.get().unsupportedCommand0);
			}
		}

		return r.toString();
	}
}
