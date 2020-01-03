/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.Serializable;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Parsed reflog entry
 */
public class ReflogEntryImpl implements Serializable, ReflogEntry {
	private static final long serialVersionUID = 1L;

	private ObjectId oldId;

	private ObjectId newId;

	private PersonIdent who;

	private String comment;

	ReflogEntryImpl(byte[] raw, int pos) {
		oldId = ObjectId.fromString(raw, pos);
		pos += Constants.OBJECT_ID_STRING_LENGTH;
		if (raw[pos++] != ' ')
			throw new IllegalArgumentException(
					JGitText.get().rawLogMessageDoesNotParseAsLogEntry);
		newId = ObjectId.fromString(raw, pos);
		pos += Constants.OBJECT_ID_STRING_LENGTH;
		if (raw[pos++] != ' ') {
			throw new IllegalArgumentException(
					JGitText.get().rawLogMessageDoesNotParseAsLogEntry);
		}
		who = RawParseUtils.parsePersonIdentOnly(raw, pos);
		int p0 = RawParseUtils.next(raw, pos, '\t');
		if (p0 >= raw.length)
			comment = ""; // personident has no \t, no comment present //$NON-NLS-1$
		else {
			int p1 = RawParseUtils.nextLF(raw, p0);
			comment = p1 > p0 ? RawParseUtils.decode(raw, p0, p1 - 1) : ""; //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogEntry#getOldId()
	 */
	/** {@inheritDoc} */
	@Override
	public ObjectId getOldId() {
		return oldId;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogEntry#getNewId()
	 */
	/** {@inheritDoc} */
	@Override
	public ObjectId getNewId() {
		return newId;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogEntry#getWho()
	 */
	/** {@inheritDoc} */
	@Override
	public PersonIdent getWho() {
		return who;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogEntry#getComment()
	 */
	/** {@inheritDoc} */
	@Override
	public String getComment() {
		return comment;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "Entry[" + oldId.name() + ", " + newId.name() + ", " + getWho()
				+ ", " + getComment() + "]";
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogEntry#parseCheckout()
	 */
	/** {@inheritDoc} */
	@Override
	public CheckoutEntry parseCheckout() {
		if (getComment().startsWith(CheckoutEntryImpl.CHECKOUT_MOVING_FROM)) {
			return new CheckoutEntryImpl(this);
		}
		return null;
	}
}
