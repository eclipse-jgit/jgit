/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht.spi.util;

import java.util.Arrays;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.dht.RowKey;
import org.eclipse.jgit.util.RawParseUtils;

/** Utility to deal with columns named as byte arrays. */
public class ColumnMatcher {
	private final byte[] name;

	/**
	 * Create a new column matcher for the given named string.
	 *
	 * @param nameStr
	 *            the column name, as a string.
	 */
	public ColumnMatcher(String nameStr) {
		name = Constants.encode(nameStr);
	}

	/** @return the column name, encoded in UTF-8. */
	public byte[] name() {
		return name;
	}

	/**
	 * Check if the column is an exact match.
	 *
	 * @param col
	 *            the column as read from the database.
	 * @return true only if {@code col} is exactly the same as this column.
	 */
	public boolean sameName(byte[] col) {
		return Arrays.equals(name, col);
	}

	/**
	 * Check if the column is a member of this family.
	 * <p>
	 * This method checks that {@link #name()} (the string supplied to the
	 * constructor) is a prefix of {@code col}.
	 *
	 * @param col
	 *            the column as read from the database.
	 * @return true if {@code col} is a member of this column family.
	 */
	public boolean sameFamily(byte[] col) {
		if (name.length < col.length) {
			for (int i = 0; i < name.length; i++) {
				if (name[i] != col[i]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Extract the portion of the column name that comes after the family.
	 *
	 * @param col
	 *            the column as read from the database.
	 * @return everything after the family name.
	 */
	public byte[] suffix(byte[] col) {
		byte[] r = new byte[col.length - name.length];
		System.arraycopy(col, name.length, r, 0, r.length);
		return r;
	}

	/**
	 * Append a suffix onto this column name.
	 *
	 * @param suffix
	 *            name component to appear after the family name.
	 * @return the joined name, ready for storage in the database.
	 */
	public byte[] append(RowKey suffix) {
		return append(suffix.asBytes());
	}

	/**
	 * Append a suffix onto this column name.
	 *
	 * @param suffix
	 *            name component to appear after the family name.
	 * @return the joined name, ready for storage in the database.
	 */
	public byte[] append(byte[] suffix) {
		byte[] r = new byte[name.length + suffix.length];
		System.arraycopy(name, 0, r, 0, name.length);
		System.arraycopy(suffix, 0, r, name.length, suffix.length);
		return r;
	}

	@Override
	public String toString() {
		return RawParseUtils.decode(name);
	}
}
