/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;

/**
 * A file in the Git "diff --cc" or "diff --combined" format.
 * <p>
 * A combined diff shows an n-way comparison between two or more ancestors and
 * the final revision. Its primary function is to perform code reviews on a
 * merge which introduces changes not in any ancestor.
 */
public class CombinedFileHeader extends FileHeader {
	private static final byte[] MODE = encodeASCII("mode "); //$NON-NLS-1$

	private AbbreviatedObjectId[] oldIds;

	private FileMode[] oldModes;

	CombinedFileHeader(byte[] b, int offset) {
		super(b, offset);
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("unchecked")
	public List<? extends CombinedHunkHeader> getHunks() {
		return (List<CombinedHunkHeader>) super.getHunks();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * @return number of ancestor revisions mentioned in this diff.
	 */
	@Override
	public int getParentCount() {
		return oldIds.length;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * @return get the file mode of the first parent.
	 */
	@Override
	public FileMode getOldMode() {
		return getOldMode(0);
	}

	/**
	 * Get the file mode of the nth ancestor
	 *
	 * @param nthParent
	 *            the ancestor to get the mode of
	 * @return the mode of the requested ancestor.
	 */
	public FileMode getOldMode(int nthParent) {
		return oldModes[nthParent];
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * @return get the object id of the first parent.
	 */
	@Override
	public AbbreviatedObjectId getOldId() {
		return getOldId(0);
	}

	/**
	 * Get the ObjectId of the nth ancestor
	 *
	 * @param nthParent
	 *            the ancestor to get the object id of
	 * @return the id of the requested ancestor.
	 */
	public AbbreviatedObjectId getOldId(int nthParent) {
		return oldIds[nthParent];
	}

	/** {@inheritDoc} */
	@Override
	public String getScriptText(Charset ocs, Charset ncs) {
		final Charset[] cs = new Charset[getParentCount() + 1];
		Arrays.fill(cs, ocs);
		cs[getParentCount()] = ncs;
		return getScriptText(cs);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Convert the patch script for this file into a string.
	 */
	@Override
	public String getScriptText(Charset[] charsetGuess) {
		return super.getScriptText(charsetGuess);
	}

	@Override
	int parseGitHeaders(int ptr, int end) {
		while (ptr < end) {
			final int eol = nextLF(buf, ptr);
			if (isHunkHdr(buf, ptr, end) >= 1) {
				// First hunk header; break out and parse them later.
				break;

			} else if (match(buf, ptr, OLD_NAME) >= 0) {
				parseOldName(ptr, eol);

			} else if (match(buf, ptr, NEW_NAME) >= 0) {
				parseNewName(ptr, eol);

			} else if (match(buf, ptr, INDEX) >= 0) {
				parseIndexLine(ptr + INDEX.length, eol);

			} else if (match(buf, ptr, MODE) >= 0) {
				parseModeLine(ptr + MODE.length, eol);

			} else if (match(buf, ptr, NEW_FILE_MODE) >= 0) {
				parseNewFileMode(ptr, eol);

			} else if (match(buf, ptr, DELETED_FILE_MODE) >= 0) {
				parseDeletedFileMode(ptr + DELETED_FILE_MODE.length, eol);

			} else {
				// Probably an empty patch (stat dirty).
				break;
			}

			ptr = eol;
		}
		return ptr;
	}

	/** {@inheritDoc} */
	@Override
	protected void parseIndexLine(int ptr, int eol) {
		// "index $asha1,$bsha1..$csha1"
		//
		final List<AbbreviatedObjectId> ids = new ArrayList<>();
		while (ptr < eol) {
			final int comma = nextLF(buf, ptr, ',');
			if (eol <= comma)
				break;
			ids.add(AbbreviatedObjectId.fromString(buf, ptr, comma - 1));
			ptr = comma;
		}

		oldIds = new AbbreviatedObjectId[ids.size() + 1];
		ids.toArray(oldIds);
		final int dot2 = nextLF(buf, ptr, '.');
		oldIds[ids.size()] = AbbreviatedObjectId.fromString(buf, ptr, dot2 - 1);
		newId = AbbreviatedObjectId.fromString(buf, dot2 + 1, eol - 1);
		oldModes = new FileMode[oldIds.length];
	}

	/** {@inheritDoc} */
	@Override
	protected void parseNewFileMode(int ptr, int eol) {
		for (int i = 0; i < oldModes.length; i++)
			oldModes[i] = FileMode.MISSING;
		super.parseNewFileMode(ptr, eol);
	}

	@Override
	HunkHeader newHunkHeader(int offset) {
		return new CombinedHunkHeader(this, offset);
	}

	private void parseModeLine(int ptr, int eol) {
		// "mode $amode,$bmode..$cmode"
		//
		int n = 0;
		while (ptr < eol) {
			final int comma = nextLF(buf, ptr, ',');
			if (eol <= comma)
				break;
			oldModes[n++] = parseFileMode(ptr, comma);
			ptr = comma;
		}
		final int dot2 = nextLF(buf, ptr, '.');
		oldModes[n] = parseFileMode(ptr, dot2);
		newMode = parseFileMode(dot2 + 1, eol);
	}

	private void parseDeletedFileMode(int ptr, int eol) {
		// "deleted file mode $amode,$bmode"
		//
		changeType = ChangeType.DELETE;
		int n = 0;
		while (ptr < eol) {
			final int comma = nextLF(buf, ptr, ',');
			if (eol <= comma)
				break;
			oldModes[n++] = parseFileMode(ptr, comma);
			ptr = comma;
		}
		oldModes[n] = parseFileMode(ptr, eol);
		newMode = FileMode.MISSING;
	}
}
