/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.Serializable;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Parsed reflog entry
 */
public class ReflogEntry implements Serializable {
	private static final long serialVersionUID = 1L;

	private ObjectId oldId;

	private ObjectId newId;

	private PersonIdent who;

	private String comment;

	ReflogEntry(byte[] raw, int pos) {
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

	/**
	 * @return the commit id before the change
	 */
	public ObjectId getOldId() {
		return oldId;
	}

	/**
	 * @return the commit id after the change
	 */
	public ObjectId getNewId() {
		return newId;
	}

	/**
	 * @return user performing the change
	 */
	public PersonIdent getWho() {
		return who;
	}

	/**
	 * @return textual description of the change
	 */
	public String getComment() {
		return comment;
	}

	@Override
	public String toString() {
		return "Entry[" + oldId.name() + ", " + newId.name() + ", " + getWho() + ", " //$NON-NLS-1$
				+ getComment() + "]"; //$NON-NLS-1$
	}

	/**
	 * @return a {@link CheckoutEntry} with parsed information about a branch
	 *         switch, or null if the entry is not a checkout
	 */
	public CheckoutEntry parseCheckout() {
		if (getComment().startsWith(CheckoutEntry.CHECKOUT_MOVING_FROM))
			return new CheckoutEntry(this);
		else
			return null;
	}
}