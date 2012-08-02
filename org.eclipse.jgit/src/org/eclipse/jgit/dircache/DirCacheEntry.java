/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.dircache;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;

/**
 * A single file (or stage of a file) in a {@link DirCache}.
 * <p>
 * An entry represents exactly one stage of a file. If a file path is unmerged
 * then multiple DirCacheEntry instances may appear for the same path name.
 */
public class DirCacheEntry {
	private static final byte[] nullpad = new byte[8];

	/** The standard (fully merged) stage for an entry. */
	public static final int STAGE_0 = 0;

	/** The base tree revision for an entry. */
	public static final int STAGE_1 = 1;

	/** The first tree revision (usually called "ours"). */
	public static final int STAGE_2 = 2;

	/** The second tree revision (usually called "theirs"). */
	public static final int STAGE_3 = 3;

	private static final int P_CTIME = 0;

	// private static final int P_CTIME_NSEC = 4;

	private static final int P_MTIME = 8;

	// private static final int P_MTIME_NSEC = 12;

	// private static final int P_DEV = 16;

	// private static final int P_INO = 20;

	private static final int P_MODE = 24;

	// private static final int P_UID = 28;

	// private static final int P_GID = 32;

	private static final int P_SIZE = 36;

	private static final int P_OBJECTID = 40;

	private static final int P_FLAGS = 60;
	private static final int P_FLAGS2 = 62;

	/** Mask applied to data in {@link #P_FLAGS} to get the name length. */
	private static final int NAME_MASK = 0xfff;

	private static final int INTENT_TO_ADD = 0x20000000;
	private static final int SKIP_WORKTREE = 0x40000000;
	private static final int EXTENDED_FLAGS = (INTENT_TO_ADD | SKIP_WORKTREE);

	private static final int INFO_LEN = 62;
	private static final int INFO_LEN_EXTENDED = 64;

	private static final int EXTENDED = 0x40;
	private static final int ASSUME_VALID = 0x80;

	/** In-core flag signaling that the entry should be considered as modified. */
	private static final int UPDATE_NEEDED = 0x1;

	/** (Possibly shared) header information storage. */
	private final byte[] info;

	/** First location within {@link #info} where our header starts. */
	private final int infoOffset;

	/** Our encoded path name, from the root of the repository. */
	final byte[] path;

	/** Flags which are never stored to disk. */
	private byte inCoreFlags;

	DirCacheEntry(final byte[] sharedInfo, final MutableInteger infoAt,
			final InputStream in, final MessageDigest md, final int smudge_s,
			final int smudge_ns) throws IOException {
		info = sharedInfo;
		infoOffset = infoAt.value;

		IO.readFully(in, info, infoOffset, INFO_LEN);

		final int len;
		if (isExtended()) {
			len = INFO_LEN_EXTENDED;
			IO.readFully(in, info, infoOffset + INFO_LEN, INFO_LEN_EXTENDED - INFO_LEN);

			if ((getExtendedFlags() & ~EXTENDED_FLAGS) != 0)
				throw new IOException(MessageFormat.format(JGitText.get()
						.DIRCUnrecognizedExtendedFlags, String.valueOf(getExtendedFlags())));
		} else
			len = INFO_LEN;

		infoAt.value += len;
		md.update(info, infoOffset, len);

		int pathLen = NB.decodeUInt16(info, infoOffset + P_FLAGS) & NAME_MASK;
		int skipped = 0;
		if (pathLen < NAME_MASK) {
			path = new byte[pathLen];
			IO.readFully(in, path, 0, pathLen);
			md.update(path, 0, pathLen);
		} else {
			final ByteArrayOutputStream tmp = new ByteArrayOutputStream();
			{
				final byte[] buf = new byte[NAME_MASK];
				IO.readFully(in, buf, 0, NAME_MASK);
				tmp.write(buf);
			}
			for (;;) {
				final int c = in.read();
				if (c < 0)
					throw new EOFException(JGitText.get().shortReadOfBlock);
				if (c == 0)
					break;
				tmp.write(c);
			}
			path = tmp.toByteArray();
			pathLen = path.length;
			skipped = 1; // we already skipped 1 '\0' above to break the loop.
			md.update(path, 0, pathLen);
			md.update((byte) 0);
		}

		// Index records are padded out to the next 8 byte alignment
		// for historical reasons related to how C Git read the files.
		//
		final int actLen = len + pathLen;
		final int expLen = (actLen + 8) & ~7;
		final int padLen = expLen - actLen - skipped;
		if (padLen > 0) {
			IO.skipFully(in, padLen);
			md.update(nullpad, 0, padLen);
		}

		if (mightBeRacilyClean(smudge_s, smudge_ns))
			smudgeRacilyClean();

	}

	/**
	 * Create an empty entry at stage 0.
	 *
	 * @param newPath
	 *            name of the cache entry.
	 * @throws IllegalArgumentException
	 *             If the path starts or ends with "/", or contains "//" either
	 *             "\0". These sequences are not permitted in a git tree object
	 *             or DirCache file.
	 */
	public DirCacheEntry(final String newPath) {
		this(Constants.encode(newPath));
	}

	/**
	 * Create an empty entry at the specified stage.
	 *
	 * @param newPath
	 *            name of the cache entry.
	 * @param stage
	 *            the stage index of the new entry.
	 * @throws IllegalArgumentException
	 *             If the path starts or ends with "/", or contains "//" either
	 *             "\0". These sequences are not permitted in a git tree object
	 *             or DirCache file.  Or if {@code stage} is outside of the
	 *             range 0..3, inclusive.
	 */
	public DirCacheEntry(final String newPath, final int stage) {
		this(Constants.encode(newPath), stage);
	}

	/**
	 * Create an empty entry at stage 0.
	 *
	 * @param newPath
	 *            name of the cache entry, in the standard encoding.
	 * @throws IllegalArgumentException
	 *             If the path starts or ends with "/", or contains "//" either
	 *             "\0". These sequences are not permitted in a git tree object
	 *             or DirCache file.
	 */
	public DirCacheEntry(final byte[] newPath) {
		this(newPath, STAGE_0);
	}

	/**
	 * Create an empty entry at the specified stage.
	 *
	 * @param newPath
	 *            name of the cache entry, in the standard encoding.
	 * @param stage
	 *            the stage index of the new entry.
	 * @throws IllegalArgumentException
	 *             If the path starts or ends with "/", or contains "//" either
	 *             "\0". These sequences are not permitted in a git tree object
	 *             or DirCache file.  Or if {@code stage} is outside of the
	 *             range 0..3, inclusive.
	 */
	@SuppressWarnings("boxing")
	public DirCacheEntry(final byte[] newPath, final int stage) {
		if (!isValidPath(newPath))
			throw new InvalidPathException(toString(newPath));
		if (stage < 0 || 3 < stage)
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().invalidStageForPath
					, stage, toString(newPath)));

		info = new byte[INFO_LEN];
		infoOffset = 0;
		path = newPath;

		int flags = ((stage & 0x3) << 12);
		if (path.length < NAME_MASK)
			flags |= path.length;
		else
			flags |= NAME_MASK;
		NB.encodeInt16(info, infoOffset + P_FLAGS, flags);
	}

	void write(final OutputStream os) throws IOException {
		final int len = isExtended() ? INFO_LEN_EXTENDED : INFO_LEN;
		final int pathLen = path.length;
		os.write(info, infoOffset, len);
		os.write(path, 0, pathLen);

		// Index records are padded out to the next 8 byte alignment
		// for historical reasons related to how C Git read the files.
		//
		final int actLen = len + pathLen;
		final int expLen = (actLen + 8) & ~7;
		if (actLen != expLen)
			os.write(nullpad, 0, expLen - actLen);
	}

	/**
	 * Is it possible for this entry to be accidentally assumed clean?
	 * <p>
	 * The "racy git" problem happens when a work file can be updated faster
	 * than the filesystem records file modification timestamps. It is possible
	 * for an application to edit a work file, update the index, then edit it
	 * again before the filesystem will give the work file a new modification
	 * timestamp. This method tests to see if file was written out at the same
	 * time as the index.
	 *
	 * @param smudge_s
	 *            seconds component of the index's last modified time.
	 * @param smudge_ns
	 *            nanoseconds component of the index's last modified time.
	 * @return true if extra careful checks should be used.
	 */
	public final boolean mightBeRacilyClean(final int smudge_s, final int smudge_ns) {
		// If the index has a modification time then it came from disk
		// and was not generated from scratch in memory. In such cases
		// the entry is 'racily clean' if the entry's cached modification
		// time is equal to or later than the index modification time. In
		// such cases the work file is too close to the index to tell if
		// it is clean or not based on the modification time alone.
		//
		final int base = infoOffset + P_MTIME;
		final int mtime = NB.decodeInt32(info, base);
		if (smudge_s == mtime)
			return smudge_ns <= NB.decodeInt32(info, base + 4);
		return false;
	}

	/**
	 * Force this entry to no longer match its working tree file.
	 * <p>
	 * This avoids the "racy git" problem by making this index entry no longer
	 * match the file in the working directory. Later git will be forced to
	 * compare the file content to ensure the file matches the working tree.
	 */
	public final void smudgeRacilyClean() {
		// To mark an entry racily clean we set its length to 0 (like native git
		// does). Entries which are not racily clean and have zero length can be
		// distinguished from racily clean entries by checking P_OBJECTID
		// against the SHA1 of empty content. When length is 0 and P_OBJECTID is
		// different from SHA1 of empty content we know the entry is marked
		// racily clean
		final int base = infoOffset + P_SIZE;
		Arrays.fill(info, base, base + 4, (byte) 0);
	}

	/**
	 * Check whether this entry has been smudged or not
	 * <p>
	 * If a blob has length 0 we know his id see {@link Constants#EMPTY_BLOB_ID}. If an entry
	 * has length 0 and an ID different from the one for empty blob we know this
	 * entry was smudged.
	 *
	 * @return <code>true</code> if the entry is smudged, <code>false</code>
	 *         otherwise
	 */
	public final boolean isSmudged() {
		final int base = infoOffset + P_OBJECTID;
		return (getLength() == 0) && (Constants.EMPTY_BLOB_ID.compareTo(info, base) != 0);
	}

	final byte[] idBuffer() {
		return info;
	}

	final int idOffset() {
		return infoOffset + P_OBJECTID;
	}

	/**
	 * Is this entry always thought to be unmodified?
	 * <p>
	 * Most entries in the index do not have this flag set. Users may however
	 * set them on if the file system stat() costs are too high on this working
	 * directory, such as on NFS or SMB volumes.
	 *
	 * @return true if we must assume the entry is unmodified.
	 */
	public boolean isAssumeValid() {
		return (info[infoOffset + P_FLAGS] & ASSUME_VALID) != 0;
	}

	/**
	 * Set the assume valid flag for this entry,
	 *
	 * @param assume
	 *            true to ignore apparent modifications; false to look at last
	 *            modified to detect file modifications.
	 */
	public void setAssumeValid(final boolean assume) {
		if (assume)
			info[infoOffset + P_FLAGS] |= ASSUME_VALID;
		else
			info[infoOffset + P_FLAGS] &= ~ASSUME_VALID;
	}

	/**
	 * @return true if this entry should be checked for changes
	 */
	public boolean isUpdateNeeded() {
		return (inCoreFlags & UPDATE_NEEDED) != 0;
	}

	/**
	 * Set whether this entry must be checked for changes
	 *
	 * @param updateNeeded
	 */
	public void setUpdateNeeded(boolean updateNeeded) {
		if (updateNeeded)
			inCoreFlags |= UPDATE_NEEDED;
		else
			inCoreFlags &= ~UPDATE_NEEDED;
	}

	/**
	 * Get the stage of this entry.
	 * <p>
	 * Entries have one of 4 possible stages: 0-3.
	 *
	 * @return the stage of this entry.
	 */
	public int getStage() {
		return (info[infoOffset + P_FLAGS] >>> 4) & 0x3;
	}

	/**
	 * Returns whether this entry should be skipped from the working tree.
	 *
	 * @return true if this entry should be skipepd.
	 */
	public boolean isSkipWorkTree() {
		return (getExtendedFlags() & SKIP_WORKTREE) != 0;
	}

	/**
	 * Returns whether this entry is intent to be added to the Index.
	 *
	 * @return true if this entry is intent to add.
	 */
	public boolean isIntentToAdd() {
		return (getExtendedFlags() & INTENT_TO_ADD) != 0;
	}

	/**
	 * Obtain the raw {@link FileMode} bits for this entry.
	 *
	 * @return mode bits for the entry.
	 * @see FileMode#fromBits(int)
	 */
	public int getRawMode() {
		return NB.decodeInt32(info, infoOffset + P_MODE);
	}

	/**
	 * Obtain the {@link FileMode} for this entry.
	 *
	 * @return the file mode singleton for this entry.
	 */
	public FileMode getFileMode() {
		return FileMode.fromBits(getRawMode());
	}

	/**
	 * Set the file mode for this entry.
	 *
	 * @param mode
	 *            the new mode constant.
	 * @throws IllegalArgumentException
	 *             If {@code mode} is {@link FileMode#MISSING},
	 *             {@link FileMode#TREE}, or any other type code not permitted
	 *             in a tree object.
	 */
	public void setFileMode(final FileMode mode) {
		switch (mode.getBits() & FileMode.TYPE_MASK) {
		case FileMode.TYPE_MISSING:
		case FileMode.TYPE_TREE:
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().invalidModeForPath
					, mode, getPathString()));
		}
		NB.encodeInt32(info, infoOffset + P_MODE, mode.getBits());
	}

	/**
	 * Get the cached creation time of this file, in milliseconds.
	 *
	 * @return cached creation time of this file, in milliseconds since the
	 *         Java epoch (midnight Jan 1, 1970 UTC).
	 */
	public long getCreationTime() {
		return decodeTS(P_CTIME);
	}

	/**
	 * Set the cached creation time of this file, using milliseconds.
	 *
	 * @param when
	 *            new cached creation time of the file, in milliseconds.
	 */
	public void setCreationTime(final long when) {
		encodeTS(P_CTIME, when);
	}

	/**
	 * Get the cached last modification date of this file, in milliseconds.
	 * <p>
	 * One of the indicators that the file has been modified by an application
	 * changing the working tree is if the last modification time for the file
	 * differs from the time stored in this entry.
	 *
	 * @return last modification time of this file, in milliseconds since the
	 *         Java epoch (midnight Jan 1, 1970 UTC).
	 */
	public long getLastModified() {
		return decodeTS(P_MTIME);
	}

	/**
	 * Set the cached last modification date of this file, using milliseconds.
	 *
	 * @param when
	 *            new cached modification date of the file, in milliseconds.
	 */
	public void setLastModified(final long when) {
		encodeTS(P_MTIME, when);
	}

	/**
	 * Get the cached size (mod 4 GB) (in bytes) of this file.
	 * <p>
	 * One of the indicators that the file has been modified by an application
	 * changing the working tree is if the size of the file (in bytes) differs
	 * from the size stored in this entry.
	 * <p>
	 * Note that this is the length of the file in the working directory, which
	 * may differ from the size of the decompressed blob if work tree filters
	 * are being used, such as LF<->CRLF conversion.
	 * <p>
	 * Note also that for very large files, this is the size of the on-disk file
	 * truncated to 32 bits, i.e. modulo 4294967296. If that value is larger
	 * than 2GB, it will appear negative.
	 *
	 * @return cached size of the working directory file, in bytes.
	 */
	public int getLength() {
		return NB.decodeInt32(info, infoOffset + P_SIZE);
	}

	/**
	 * Set the cached size (in bytes) of this file.
	 *
	 * @param sz
	 *            new cached size of the file, as bytes. If the file is larger
	 *            than 2G, cast it to (int) before calling this method.
	 */
	public void setLength(final int sz) {
		NB.encodeInt32(info, infoOffset + P_SIZE, sz);
	}

	/**
	 * Set the cached size (in bytes) of this file.
	 *
	 * @param sz
	 *            new cached size of the file, as bytes.
	 */
	public void setLength(final long sz) {
		setLength((int) sz);
	}

	/**
	 * Obtain the ObjectId for the entry.
	 * <p>
	 * Using this method to compare ObjectId values between entries is
	 * inefficient as it causes memory allocation.
	 *
	 * @return object identifier for the entry.
	 */
	public ObjectId getObjectId() {
		return ObjectId.fromRaw(idBuffer(), idOffset());
	}

	/**
	 * Set the ObjectId for the entry.
	 *
	 * @param id
	 *            new object identifier for the entry. May be
	 *            {@link ObjectId#zeroId()} to remove the current identifier.
	 */
	public void setObjectId(final AnyObjectId id) {
		id.copyRawTo(idBuffer(), idOffset());
	}

	/**
	 * Set the ObjectId for the entry from the raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 */
	public void setObjectIdFromRaw(final byte[] bs, final int p) {
		final int n = Constants.OBJECT_ID_LENGTH;
		System.arraycopy(bs, p, idBuffer(), idOffset(), n);
	}

	/**
	 * Get the entry's complete path.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return complete path of the entry, from the root of the repository. If
	 *         the entry is in a subtree there will be at least one '/' in the
	 *         returned string.
	 */
	public String getPathString() {
		return toString(path);
	}

	/**
	 * Use for debugging only !
	 */
	@Override
	public String toString() {
		return getFileMode() + " " + getLength() + " " + getLastModified()
				+ " " + getObjectId() + " " + getStage() + " "
				+ getPathString() + "\n";
	}

	/**
	 * Copy the ObjectId and other meta fields from an existing entry.
	 * <p>
	 * This method copies everything except the path from one entry to another,
	 * supporting renaming.
	 *
	 * @param src
	 *            the entry to copy ObjectId and meta fields from.
	 */
	public void copyMetaData(final DirCacheEntry src) {
		copyMetaData(src, false);
	}

	/**
	 * Copy the ObjectId and other meta fields from an existing entry.
	 * <p>
	 * This method copies everything except the path and possibly stage from one
	 * entry to another, supporting renaming.
	 *
	 * @param src
	 *            the entry to copy ObjectId and meta fields from.
	 * @param keepStage
	 *            if true, the stage attribute will not be copied
	 */
	void copyMetaData(final DirCacheEntry src, boolean keepStage) {
		int origflags = NB.decodeUInt16(info, infoOffset + P_FLAGS);
		int newflags = NB.decodeUInt16(src.info, src.infoOffset + P_FLAGS);
		System.arraycopy(src.info, src.infoOffset, info, infoOffset, INFO_LEN);
		final int pLen = origflags & NAME_MASK;
		final int SHIFTED_STAGE_MASK = 0x3 << 12;
		final int pStageShifted;
		if (keepStage)
			pStageShifted = origflags & SHIFTED_STAGE_MASK;
		else
			pStageShifted = newflags & SHIFTED_STAGE_MASK;
		NB.encodeInt16(info, infoOffset + P_FLAGS, pStageShifted | pLen
				| (newflags & ~NAME_MASK & ~SHIFTED_STAGE_MASK));
	}

	/**
	 * @return true if the entry contains extended flags.
	 */
	boolean isExtended() {
		return (info[infoOffset + P_FLAGS] & EXTENDED) != 0;
	}

	private long decodeTS(final int pIdx) {
		final int base = infoOffset + pIdx;
		final int sec = NB.decodeInt32(info, base);
		final int ms = NB.decodeInt32(info, base + 4) / 1000000;
		return 1000L * sec + ms;
	}

	private void encodeTS(final int pIdx, final long when) {
		final int base = infoOffset + pIdx;
		NB.encodeInt32(info, base, (int) (when / 1000));
		NB.encodeInt32(info, base + 4, ((int) (when % 1000)) * 1000000);
	}

	private int getExtendedFlags() {
		if (isExtended())
			return NB.decodeUInt16(info, infoOffset + P_FLAGS2) << 16;
		else
			return 0;
	}

	private static String toString(final byte[] path) {
		return Constants.CHARSET.decode(ByteBuffer.wrap(path)).toString();
	}

	static boolean isValidPath(final byte[] path) {
		if (path.length == 0)
			return false; // empty path is not permitted.

		boolean componentHasChars = false;
		for (final byte c : path) {
			switch (c) {
			case 0:
				return false; // NUL is never allowed within the path.

			case '/':
				if (componentHasChars)
					componentHasChars = false;
				else
					return false;
				break;
			case '\\':
			case ':':
				// Tree's never have a backslash in them, not even on Windows
				// but even there we regard it as an invalid path
				if (SystemReader.getInstance().isWindows())
					return false;
				//$FALL-THROUGH$
			default:
				componentHasChars = true;
			}
		}
		return componentHasChars;
	}

	static int getMaximumInfoLength(boolean extended) {
		return extended ? INFO_LEN_EXTENDED : INFO_LEN;
	}
}
