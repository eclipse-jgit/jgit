/*
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * This class represents an entry in a tree, like a blob or another tree.
 *
 * @deprecated To look up information about a single path, use
 * {@link org.eclipse.jgit.treewalk.TreeWalk#forPath(Repository, String, org.eclipse.jgit.revwalk.RevTree)}.
 * To lookup information about multiple paths at once, use a
 * {@link org.eclipse.jgit.treewalk.TreeWalk} and obtain the current entry's
 * information from its getter methods.
 */
@Deprecated
public abstract class TreeEntry implements Comparable {
	private byte[] nameUTF8;

	private Tree parent;

	private ObjectId id;

	/**
	 * Construct a named tree entry.
	 *
	 * @param myParent
	 * @param myId
	 * @param myNameUTF8
	 */
	protected TreeEntry(final Tree myParent, final ObjectId myId,
			final byte[] myNameUTF8) {
		nameUTF8 = myNameUTF8;
		parent = myParent;
		id = myId;
	}

	/**
	 * @return parent of this tree.
	 */
	public Tree getParent() {
		return parent;
	}

	/**
	 * Delete this entry.
	 */
	public void delete() {
		getParent().removeEntry(this);
		detachParent();
	}

	/**
	 * Detach this entry from it's parent.
	 */
	public void detachParent() {
		parent = null;
	}

	void attachParent(final Tree p) {
		parent = p;
	}

	/**
	 * @return the repository owning this entry.
	 */
	public Repository getRepository() {
		return getParent().getRepository();
	}

	/**
	 * @return the raw byte name of this entry.
	 */
	public byte[] getNameUTF8() {
		return nameUTF8;
	}

	/**
	 * @return the name of this entry.
	 */
	public String getName() {
		if (nameUTF8 != null)
			return RawParseUtils.decode(nameUTF8);
		return null;
	}

	/**
	 * Rename this entry.
	 *
	 * @param n The new name
	 * @throws IOException
	 */
	public void rename(final String n) throws IOException {
		rename(Constants.encode(n));
	}

	/**
	 * Rename this entry.
	 *
	 * @param n The new name
	 * @throws IOException
	 */
	public void rename(final byte[] n) throws IOException {
		final Tree t = getParent();
		if (t != null) {
			delete();
		}
		nameUTF8 = n;
		if (t != null) {
			t.addEntry(this);
		}
	}

	/**
	 * @return true if this entry is new or modified since being loaded.
	 */
	public boolean isModified() {
		return getId() == null;
	}

	/**
	 * Mark this entry as modified.
	 */
	public void setModified() {
		setId(null);
	}

	/**
	 * @return SHA-1 of this tree entry (null for new unhashed entries)
	 */
	public ObjectId getId() {
		return id;
	}

	/**
	 * Set (update) the SHA-1 of this entry. Invalidates the id's of all
	 * entries above this entry as they will have to be recomputed.
	 *
	 * @param n SHA-1 for this entry.
	 */
	public void setId(final ObjectId n) {
		// If we have a parent and our id is being cleared or changed then force
		// the parent's id to become unset as it depends on our id.
		//
		final Tree p = getParent();
		if (p != null && id != n) {
			if ((id == null && n != null) || (id != null && n == null)
					|| !id.equals(n)) {
				p.setId(null);
			}
		}

		id = n;
	}

	/**
	 * @return repository relative name of this entry
	 */
	public String getFullName() {
		final StringBuilder r = new StringBuilder();
		appendFullName(r);
		return r.toString();
	}

	/**
	 * @return repository relative name of the entry
	 * FIXME better encoding
	 */
	public byte[] getFullNameUTF8() {
		return getFullName().getBytes();
	}

	public int compareTo(final Object o) {
		if (this == o)
			return 0;
		if (o instanceof TreeEntry)
			return Tree.compareNames(nameUTF8, ((TreeEntry) o).nameUTF8, lastChar(this), lastChar((TreeEntry)o));
		return -1;
	}

	/**
	 * Helper for accessing tree/blob methods.
	 *
	 * @param treeEntry
	 * @return '/' for Tree entries and NUL for non-treeish objects.
	 */
	final public static int lastChar(TreeEntry treeEntry) {
		if (!(treeEntry instanceof Tree))
			return '\0';
		else
			return '/';
	}

	/**
	 * @return mode (type of object)
	 */
	public abstract FileMode getMode();

	private void appendFullName(final StringBuilder r) {
		final TreeEntry p = getParent();
		final String n = getName();
		if (p != null) {
			p.appendFullName(r);
			if (r.length() > 0) {
				r.append('/');
			}
		}
		if (n != null) {
			r.append(n);
		}
	}
}
