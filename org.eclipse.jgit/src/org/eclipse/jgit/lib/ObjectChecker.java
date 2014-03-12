/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;
import static org.eclipse.jgit.util.RawParseUtils.parseBase10;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Verifies that an object is formatted correctly.
 * <p>
 * Verifications made by this class only check that the fields of an object are
 * formatted correctly. The ObjectId checksum of the object is not verified, and
 * connectivity links between objects are also not verified. Its assumed that
 * the caller can provide both of these validations on its own.
 * <p>
 * Instances of this class are not thread safe, but they may be reused to
 * perform multiple object validations.
 */
public class ObjectChecker {
	/** Header "tree " */
	public static final byte[] tree = Constants.encodeASCII("tree "); //$NON-NLS-1$

	/** Header "parent " */
	public static final byte[] parent = Constants.encodeASCII("parent "); //$NON-NLS-1$

	/** Header "author " */
	public static final byte[] author = Constants.encodeASCII("author "); //$NON-NLS-1$

	/** Header "committer " */
	public static final byte[] committer = Constants.encodeASCII("committer "); //$NON-NLS-1$

	/** Header "encoding " */
	public static final byte[] encoding = Constants.encodeASCII("encoding "); //$NON-NLS-1$

	/** Header "object " */
	public static final byte[] object = Constants.encodeASCII("object "); //$NON-NLS-1$

	/** Header "type " */
	public static final byte[] type = Constants.encodeASCII("type "); //$NON-NLS-1$

	/** Header "tag " */
	public static final byte[] tag = Constants.encodeASCII("tag "); //$NON-NLS-1$

	/** Header "tagger " */
	public static final byte[] tagger = Constants.encodeASCII("tagger "); //$NON-NLS-1$

	private final MutableObjectId tempId = new MutableObjectId();

	private final MutableInteger ptrout = new MutableInteger();

	private boolean allowZeroMode;
	private boolean ignoreCase;
	private boolean windows;

	/**
	 * Enable accepting leading zero mode in tree entries.
	 * <p>
	 * Some broken Git libraries generated leading zeros in the mode part of
	 * tree entries. This is technically incorrect but gracefully allowed by
	 * git-core. JGit rejects such trees by default, but may need to accept
	 * them on broken histories.
	 *
	 * @param allow allow leading zero mode.
	 * @return {@code this}.
	 * @since 3.4
	 */
	public ObjectChecker setAllowLeadingZeroFileMode(boolean allow) {
		allowZeroMode = allow;
		return this;
	}

	/**
	 * Assume working directory filesystems are not case sensitive.
	 *
	 * @param ignore true if JGit should reject problems with case.
	 * @return {@code this}.
	 * @since 3.4
	 */
	public ObjectChecker setIgnoreCase(boolean ignore) {
		ignoreCase = ignore;
		return this;
	}

	/**
	 * Restrict trees to only names legal on Windows platforms.
	 * <p>
	 * Also sets {@link #setIgnoreCase(boolean)} to true.
	 *
	 * @param win true if Windows name checking should be performed.
	 * @return {@code this}.
	 * @since 3.4
	 */
	public ObjectChecker setSafeForWindows(boolean win) {
		windows = win;
		return setIgnoreCase(true);
	}

	/**
	 * Check an object for parsing errors.
	 *
	 * @param objType
	 *            type of the object. Must be a valid object type code in
	 *            {@link Constants}.
	 * @param raw
	 *            the raw data which comprises the object. This should be in the
	 *            canonical format (that is the format used to generate the
	 *            ObjectId of the object). The array is never modified.
	 * @throws CorruptObjectException
	 *             if an error is identified.
	 */
	public void check(final int objType, final byte[] raw)
			throws CorruptObjectException {
		switch (objType) {
		case Constants.OBJ_COMMIT:
			checkCommit(raw);
			break;
		case Constants.OBJ_TAG:
			checkTag(raw);
			break;
		case Constants.OBJ_TREE:
			checkTree(raw);
			break;
		case Constants.OBJ_BLOB:
			checkBlob(raw);
			break;
		default:
			throw new CorruptObjectException(MessageFormat.format(
					JGitText.get().corruptObjectInvalidType2,
					Integer.valueOf(objType)));
		}
	}

	private int id(final byte[] raw, final int ptr) {
		try {
			tempId.fromString(raw, ptr);
			return ptr + Constants.OBJECT_ID_STRING_LENGTH;
		} catch (IllegalArgumentException e) {
			return -1;
		}
	}

	private int personIdent(final byte[] raw, int ptr) {
		final int emailB = nextLF(raw, ptr, '<');
		if (emailB == ptr || raw[emailB - 1] != '<')
			return -1;

		final int emailE = nextLF(raw, emailB, '>');
		if (emailE == emailB || raw[emailE - 1] != '>')
			return -1;
		if (emailE == raw.length || raw[emailE] != ' ')
			return -1;

		parseBase10(raw, emailE + 1, ptrout); // when
		ptr = ptrout.value;
		if (emailE + 1 == ptr)
			return -1;
		if (ptr == raw.length || raw[ptr] != ' ')
			return -1;

		parseBase10(raw, ptr + 1, ptrout); // tz offset
		if (ptr + 1 == ptrout.value)
			return -1;
		return ptrout.value;
	}

	/**
	 * Check a commit for errors.
	 *
	 * @param raw
	 *            the commit data. The array is never modified.
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	public void checkCommit(final byte[] raw) throws CorruptObjectException {
		int ptr = 0;

		if ((ptr = match(raw, ptr, tree)) < 0)
			throw new CorruptObjectException("no tree header");
		if ((ptr = id(raw, ptr)) < 0 || raw[ptr++] != '\n')
			throw new CorruptObjectException("invalid tree");

		while (match(raw, ptr, parent) >= 0) {
			ptr += parent.length;
			if ((ptr = id(raw, ptr)) < 0 || raw[ptr++] != '\n')
				throw new CorruptObjectException("invalid parent");
		}

		if ((ptr = match(raw, ptr, author)) < 0)
			throw new CorruptObjectException("no author");
		if ((ptr = personIdent(raw, ptr)) < 0 || raw[ptr++] != '\n')
			throw new CorruptObjectException("invalid author");

		if ((ptr = match(raw, ptr, committer)) < 0)
			throw new CorruptObjectException("no committer");
		if ((ptr = personIdent(raw, ptr)) < 0 || raw[ptr++] != '\n')
			throw new CorruptObjectException("invalid committer");
	}

	/**
	 * Check an annotated tag for errors.
	 *
	 * @param raw
	 *            the tag data. The array is never modified.
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	public void checkTag(final byte[] raw) throws CorruptObjectException {
		int ptr = 0;

		if ((ptr = match(raw, ptr, object)) < 0)
			throw new CorruptObjectException("no object header");
		if ((ptr = id(raw, ptr)) < 0 || raw[ptr++] != '\n')
			throw new CorruptObjectException("invalid object");

		if ((ptr = match(raw, ptr, type)) < 0)
			throw new CorruptObjectException("no type header");
		ptr = nextLF(raw, ptr);

		if ((ptr = match(raw, ptr, tag)) < 0)
			throw new CorruptObjectException("no tag header");
		ptr = nextLF(raw, ptr);

		if ((ptr = match(raw, ptr, tagger)) > 0) {
			if ((ptr = personIdent(raw, ptr)) < 0 || raw[ptr++] != '\n')
				throw new CorruptObjectException("invalid tagger");
		}
	}

	private static int lastPathChar(final int mode) {
		return FileMode.TREE.equals(mode) ? '/' : '\0';
	}

	private static int pathCompare(final byte[] raw, int aPos, final int aEnd,
			final int aMode, int bPos, final int bEnd, final int bMode) {
		while (aPos < aEnd && bPos < bEnd) {
			final int cmp = (raw[aPos++] & 0xff) - (raw[bPos++] & 0xff);
			if (cmp != 0)
				return cmp;
		}

		if (aPos < aEnd)
			return (raw[aPos] & 0xff) - lastPathChar(bMode);
		if (bPos < bEnd)
			return lastPathChar(aMode) - (raw[bPos] & 0xff);
		return 0;
	}

	private static boolean duplicateName(final byte[] raw,
			final int thisNamePos, final int thisNameEnd) {
		final int sz = raw.length;
		int nextPtr = thisNameEnd + 1 + Constants.OBJECT_ID_LENGTH;
		for (;;) {
			int nextMode = 0;
			for (;;) {
				if (nextPtr >= sz)
					return false;
				final byte c = raw[nextPtr++];
				if (' ' == c)
					break;
				nextMode <<= 3;
				nextMode += c - '0';
			}

			final int nextNamePos = nextPtr;
			for (;;) {
				if (nextPtr == sz)
					return false;
				final byte c = raw[nextPtr++];
				if (c == 0)
					break;
			}
			if (nextNamePos + 1 == nextPtr)
				return false;

			final int cmp = pathCompare(raw, thisNamePos, thisNameEnd,
					FileMode.TREE.getBits(), nextNamePos, nextPtr - 1, nextMode);
			if (cmp < 0)
				return false;
			else if (cmp == 0)
				return true;

			nextPtr += Constants.OBJECT_ID_LENGTH;
		}
	}

	/**
	 * Check a canonical formatted tree for errors.
	 *
	 * @param raw
	 *            the raw tree data. The array is never modified.
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	public void checkTree(final byte[] raw) throws CorruptObjectException {
		final int sz = raw.length;
		int ptr = 0;
		int lastNameB = 0, lastNameE = 0, lastMode = 0;

		while (ptr < sz) {
			int thisMode = 0;
			for (;;) {
				if (ptr == sz)
					throw new CorruptObjectException("truncated in mode");
				final byte c = raw[ptr++];
				if (' ' == c)
					break;
				if (c < '0' || c > '7')
					throw new CorruptObjectException("invalid mode character");
				if (thisMode == 0 && c == '0' && !allowZeroMode)
					throw new CorruptObjectException("mode starts with '0'");
				thisMode <<= 3;
				thisMode += c - '0';
			}

			if (FileMode.fromBits(thisMode).getObjectType() == Constants.OBJ_BAD)
				throw new CorruptObjectException("invalid mode " + thisMode);

			final int thisNameB = ptr;
			for (;;) {
				if (ptr == sz)
					throw new CorruptObjectException("truncated in name");
				final byte c = raw[ptr++];
				if (c == 0)
					break;
				if (c == '/')
					throw new CorruptObjectException("name contains '/'");
				if (windows && isInvalidOnWindows(c)) {
					if (c > 31)
						throw new CorruptObjectException(String.format(
								"name contains '%c'", c));
					throw new CorruptObjectException(String.format(
							"name contains byte 0x%x", c & 0xff));
				}
			}
			checkPathSegment(raw, thisNameB, ptr - 1);
			if (duplicateName(raw, thisNameB, ptr - 1))
				throw new CorruptObjectException("duplicate entry names");

			if (lastNameB != 0) {
				final int cmp = pathCompare(raw, lastNameB, lastNameE,
						lastMode, thisNameB, ptr - 1, thisMode);
				if (cmp > 0)
					throw new CorruptObjectException("incorrectly sorted");
			}

			lastNameB = thisNameB;
			lastNameE = ptr - 1;
			lastMode = thisMode;

			ptr += Constants.OBJECT_ID_LENGTH;
			if (ptr > sz)
				throw new CorruptObjectException("truncated in object id");
		}
	}

	private void checkPathSegment(byte[] raw, int ptr, int end)
			throws CorruptObjectException {
		if (ptr == end)
			throw new CorruptObjectException("zero length name");
		if (raw[ptr] == '.') {
			int nameLen = end - ptr;
			if (nameLen == 1)
				throw new CorruptObjectException("invalid name '.'");
			else if (nameLen == 2 && raw[ptr + 1] == '.')
				throw new CorruptObjectException("invalid name '..'");
			else if (nameLen == 4 && isDotGit(raw, ptr + 1)) {
				throw new CorruptObjectException(String.format(
						"invalid name '%s'",
						RawParseUtils.decode(raw, ptr, end)));
			}
		}

		// Windows ignores space and dot at end of file name.
		if (windows && (raw[end - 1] == ' ' || raw[end - 1] == '.'))
			throw new CorruptObjectException("invalid name ends with '"
					+ ((char) raw[end - 1]) + "'");
	}

	private static boolean isInvalidOnWindows(byte c) {
		// Windows disallows "special" characters in a path component.
		switch (c) {
		case '"':
		case '*':
		case ':':
		case '<':
		case '>':
		case '?':
		case '\\':
		case '|':
			return true;
		}
		return 1 <= c && c <= 31;
	}

	private boolean isDotGit(byte[] buf, int p) {
		if (ignoreCase || windows)
			return toLower(buf[p]) == 'g'
					&& toLower(buf[p + 1]) == 'i'
					&& toLower(buf[p + 2]) == 't';
		return buf[p] == 'g' && buf[p + 1] == 'i' && buf[p + 2] == 't';
	}

	private static byte toLower(byte b) {
		if ('A' <= b && b <= 'Z')
			return (byte) (b + ('a' - 'A'));
		return b;
	}

	/**
	 * Check a blob for errors.
	 *
	 * @param raw
	 *            the blob data. The array is never modified.
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	public void checkBlob(final byte[] raw) throws CorruptObjectException {
		// We can always assume the blob is valid.
	}
}
