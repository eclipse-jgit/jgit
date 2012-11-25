/*
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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
import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.EntryExistsException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A representation of a Git tree entry. A Tree is a directory in Git.
 *
 * @deprecated To look up information about a single path, use
 * {@link org.eclipse.jgit.treewalk.TreeWalk#forPath(Repository, String, org.eclipse.jgit.revwalk.RevTree)}.
 * To lookup information about multiple paths at once, use a
 * {@link org.eclipse.jgit.treewalk.TreeWalk} and obtain the current entry's
 * information from its getter methods.
 */
@Deprecated
public class Tree extends TreeEntry {
	private static final TreeEntry[] EMPTY_TREE = {};

	/**
	 * Compare two names represented as bytes. Since git treats names of trees and
	 * blobs differently we have one parameter that represents a '/' for trees. For
	 * other objects the value should be NUL. The names are compare by their positive
	 * byte value (0..255).
	 *
	 * A blob and a tree with the same name will not compare equal.
	 *
	 * @param a name
	 * @param b name
	 * @param lasta '/' if a is a tree, else NUL
	 * @param lastb '/' if b is a tree, else NUL
	 *
	 * @return < 0 if a is sorted before b, 0 if they are the same, else b
	 */
	public static final int compareNames(final byte[] a, final byte[] b, final int lasta,final int lastb) {
		return compareNames(a, b, 0, b.length, lasta, lastb);
	}

	private static final int compareNames(final byte[] a, final byte[] nameUTF8,
			final int nameStart, final int nameEnd, final int lasta, int lastb) {
		int j,k;
		for (j = 0, k = nameStart; j < a.length && k < nameEnd; j++, k++) {
			final int aj = a[j] & 0xff;
			final int bk = nameUTF8[k] & 0xff;
			if (aj < bk)
				return -1;
			else if (aj > bk)
				return 1;
		}
		if (j < a.length) {
			int aj = a[j]&0xff;
			if (aj < lastb)
				return -1;
			else if (aj > lastb)
				return 1;
			else
				if (j == a.length - 1)
					return 0;
				else
					return -1;
		}
		if (k < nameEnd) {
			int bk = nameUTF8[k] & 0xff;
			if (lasta < bk)
				return -1;
			else if (lasta > bk)
				return 1;
			else
				if (k == nameEnd - 1)
					return 0;
				else
					return 1;
		}
		if (lasta < lastb)
			return -1;
		else if (lasta > lastb)
			return 1;

		final int namelength = nameEnd - nameStart;
		if (a.length == namelength)
			return 0;
		else if (a.length < namelength)
			return -1;
		else
			return 1;
	}

	private static final byte[] substring(final byte[] s, final int nameStart,
			final int nameEnd) {
		if (nameStart == 0 && nameStart == s.length)
			return s;
		final byte[] n = new byte[nameEnd - nameStart];
		System.arraycopy(s, nameStart, n, 0, n.length);
		return n;
	}

	private static final int binarySearch(final TreeEntry[] entries,
			final byte[] nameUTF8, final int nameUTF8last, final int nameStart, final int nameEnd) {
		if (entries.length == 0)
			return -1;
		int high = entries.length;
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final int cmp = compareNames(entries[mid].getNameUTF8(), nameUTF8,
					nameStart, nameEnd, TreeEntry.lastChar(entries[mid]), nameUTF8last);
			if (cmp < 0)
				low = mid + 1;
			else if (cmp == 0)
				return mid;
			else
				high = mid;
		} while (low < high);
		return -(low + 1);
	}

	private final Repository db;

	private TreeEntry[] contents;

	/**
	 * Constructor for a new Tree
	 *
	 * @param repo The repository that owns the Tree.
	 */
	public Tree(final Repository repo) {
		super(null, null, null);
		db = repo;
		contents = EMPTY_TREE;
	}

	/**
	 * Construct a Tree object with known content and hash value
	 *
	 * @param repo
	 * @param myId
	 * @param raw
	 * @throws IOException
	 */
	public Tree(final Repository repo, final ObjectId myId, final byte[] raw)
			throws IOException {
		super(null, myId, null);
		db = repo;
		readTree(raw);
	}

	/**
	 * Construct a new Tree under another Tree
	 *
	 * @param parent
	 * @param nameUTF8
	 */
	public Tree(final Tree parent, final byte[] nameUTF8) {
		super(parent, null, nameUTF8);
		db = parent.getRepository();
		contents = EMPTY_TREE;
	}

	/**
	 * Construct a Tree with a known SHA-1 under another tree. Data is not yet
	 * specified and will have to be loaded on demand.
	 *
	 * @param parent
	 * @param id
	 * @param nameUTF8
	 */
	public Tree(final Tree parent, final ObjectId id, final byte[] nameUTF8) {
		super(parent, id, nameUTF8);
		db = parent.getRepository();
	}

	public FileMode getMode() {
		return FileMode.TREE;
	}

	/**
	 * @return true if this Tree is the top level Tree.
	 */
	public boolean isRoot() {
		return getParent() == null;
	}

	public Repository getRepository() {
		return db;
	}

	/**
	 * @return true of the data of this Tree is loaded
	 */
	public boolean isLoaded() {
		return contents != null;
	}

	/**
	 * Forget the in-memory data for this tree.
	 */
	public void unload() {
		if (isModified())
			throw new IllegalStateException(JGitText.get().cannotUnloadAModifiedTree);
		contents = null;
	}

	/**
	 * Adds a new or existing file with the specified name to this tree.
	 * Trees are added if necessary as the name may contain '/':s.
	 *
	 * @param name Name
	 * @return a {@link FileTreeEntry} for the added file.
	 * @throws IOException
	 */
	public FileTreeEntry addFile(final String name) throws IOException {
		return addFile(Repository.gitInternalSlash(Constants.encode(name)), 0);
	}

	/**
	 * Adds a new or existing file with the specified name to this tree.
	 * Trees are added if necessary as the name may contain '/':s.
	 *
	 * @param s an array containing the name
	 * @param offset when the name starts in the tree.
	 *
	 * @return a {@link FileTreeEntry} for the added file.
	 * @throws IOException
	 */
	public FileTreeEntry addFile(final byte[] s, final int offset)
			throws IOException {
		int slash;
		int p;

		for (slash = offset; slash < s.length && s[slash] != '/'; slash++) {
			// search for path component terminator
		}

		ensureLoaded();
		byte xlast = slash<s.length ? (byte)'/' : 0;
		p = binarySearch(contents, s, xlast, offset, slash);
		if (p >= 0 && slash < s.length && contents[p] instanceof Tree)
			return ((Tree) contents[p]).addFile(s, slash + 1);

		final byte[] newName = substring(s, offset, slash);
		if (p >= 0)
			throw new EntryExistsException(RawParseUtils.decode(newName));
		else if (slash < s.length) {
			final Tree t = new Tree(this, newName);
			insertEntry(p, t);
			return t.addFile(s, slash + 1);
		} else {
			final FileTreeEntry f = new FileTreeEntry(this, null, newName,
					false);
			insertEntry(p, f);
			return f;
		}
	}

	/**
	 * Adds a new or existing Tree with the specified name to this tree.
	 * Trees are added if necessary as the name may contain '/':s.
	 *
	 * @param name Name
	 * @return a {@link FileTreeEntry} for the added tree.
	 * @throws IOException
	 */
	public Tree addTree(final String name) throws IOException {
		return addTree(Repository.gitInternalSlash(Constants.encode(name)), 0);
	}

	/**
	 * Adds a new or existing Tree with the specified name to this tree.
	 * Trees are added if necessary as the name may contain '/':s.
	 *
	 * @param s an array containing the name
	 * @param offset when the name starts in the tree.
	 *
	 * @return a {@link FileTreeEntry} for the added tree.
	 * @throws IOException
	 */
	public Tree addTree(final byte[] s, final int offset) throws IOException {
		int slash;
		int p;

		for (slash = offset; slash < s.length && s[slash] != '/'; slash++) {
			// search for path component terminator
		}

		ensureLoaded();
		p = binarySearch(contents, s, (byte)'/', offset, slash);
		if (p >= 0 && slash < s.length && contents[p] instanceof Tree)
			return ((Tree) contents[p]).addTree(s, slash + 1);

		final byte[] newName = substring(s, offset, slash);
		if (p >= 0)
			throw new EntryExistsException(RawParseUtils.decode(newName));

		final Tree t = new Tree(this, newName);
		insertEntry(p, t);
		return slash == s.length ? t : t.addTree(s, slash + 1);
	}

	/**
	 * Add the specified tree entry to this tree.
	 *
	 * @param e
	 * @throws IOException
	 */
	public void addEntry(final TreeEntry e) throws IOException {
		final int p;

		ensureLoaded();
		p = binarySearch(contents, e.getNameUTF8(), TreeEntry.lastChar(e), 0, e.getNameUTF8().length);
		if (p < 0) {
			e.attachParent(this);
			insertEntry(p, e);
		} else {
			throw new EntryExistsException(e.getName());
		}
	}

	private void insertEntry(int p, final TreeEntry e) {
		final TreeEntry[] c = contents;
		final TreeEntry[] n = new TreeEntry[c.length + 1];
		p = -(p + 1);
		for (int k = c.length - 1; k >= p; k--)
			n[k + 1] = c[k];
		n[p] = e;
		for (int k = p - 1; k >= 0; k--)
			n[k] = c[k];
		contents = n;
		setModified();
	}

	void removeEntry(final TreeEntry e) {
		final TreeEntry[] c = contents;
		final int p = binarySearch(c, e.getNameUTF8(), TreeEntry.lastChar(e), 0,
				e.getNameUTF8().length);
		if (p >= 0) {
			final TreeEntry[] n = new TreeEntry[c.length - 1];
			for (int k = c.length - 1; k > p; k--)
				n[k - 1] = c[k];
			for (int k = p - 1; k >= 0; k--)
				n[k] = c[k];
			contents = n;
			setModified();
		}
	}

	/**
	 * @return number of members in this tree
	 * @throws IOException
	 */
	public int memberCount() throws IOException {
		ensureLoaded();
		return contents.length;
	}

	/**
	 * Return all members of the tree sorted in Git order.
	 *
	 * Entries are sorted by the numerical unsigned byte
	 * values with (sub)trees having an implicit '/'. An
	 * example of a tree with three entries. a:b is an
	 * actual file name here.
	 *
	 * <p>
	 * 100644 blob e69de29bb2d1d6434b8b29ae775ad8c2e48c5391    a.b
	 * 040000 tree 4277b6e69d25e5efa77c455340557b384a4c018a    a
	 * 100644 blob e69de29bb2d1d6434b8b29ae775ad8c2e48c5391    a:b
	 *
	 * @return all entries in this Tree, sorted.
	 * @throws IOException
	 */
	public TreeEntry[] members() throws IOException {
		ensureLoaded();
		final TreeEntry[] c = contents;
		if (c.length != 0) {
			final TreeEntry[] r = new TreeEntry[c.length];
			for (int k = c.length - 1; k >= 0; k--)
				r[k] = c[k];
			return r;
		} else
			return c;
	}

	private boolean exists(final String s, byte slast) throws IOException {
		return findMember(s, slast) != null;
	}

	/**
	 * @param path to the tree.
	 * @return true if a tree with the specified path can be found under this
	 *         tree.
	 * @throws IOException
	 */
	public boolean existsTree(String path) throws IOException {
		return exists(path,(byte)'/');
	}

	/**
	 * @param path of the non-tree entry.
	 * @return true if a blob, symlink, or gitlink with the specified name
	 *         can be found under this tree.
	 * @throws IOException
	 */
	public boolean existsBlob(String path) throws IOException {
		return exists(path,(byte)0);
	}

	private TreeEntry findMember(final String s, byte slast) throws IOException {
		return findMember(Repository.gitInternalSlash(Constants.encode(s)), slast, 0);
	}

	private TreeEntry findMember(final byte[] s, final byte slast, final int offset)
			throws IOException {
		int slash;
		int p;

		for (slash = offset; slash < s.length && s[slash] != '/'; slash++) {
			// search for path component terminator
		}

		ensureLoaded();
		byte xlast = slash<s.length ? (byte)'/' : slast;
		p = binarySearch(contents, s, xlast, offset, slash);
		if (p >= 0) {
			final TreeEntry r = contents[p];
			if (slash < s.length-1)
				return r instanceof Tree ? ((Tree) r).findMember(s, slast, slash + 1)
						: null;
			return r;
		}
		return null;
	}

	/**
	 * @param s
	 *            blob name
	 * @return a {@link TreeEntry} representing an object with the specified
	 *         relative path.
	 * @throws IOException
	 */
	public TreeEntry findBlobMember(String s) throws IOException {
		return findMember(s,(byte)0);
	}

	/**
	 * @param s Tree Name
	 * @return a Tree with the name s or null
	 * @throws IOException
	 */
	public TreeEntry findTreeMember(String s) throws IOException {
		return findMember(s,(byte)'/');
	}

	private void ensureLoaded() throws IOException, MissingObjectException {
		if (!isLoaded()) {
			ObjectLoader ldr = db.open(getId(), Constants.OBJ_TREE);
			readTree(ldr.getCachedBytes());
		}
	}

	private void readTree(final byte[] raw) throws IOException {
		final int rawSize = raw.length;
		int rawPtr = 0;
		TreeEntry[] temp;
		int nextIndex = 0;

		while (rawPtr < rawSize) {
			while (rawPtr < rawSize && raw[rawPtr] != 0)
				rawPtr++;
			rawPtr++;
			rawPtr += Constants.OBJECT_ID_LENGTH;
			nextIndex++;
		}

		temp = new TreeEntry[nextIndex];
		rawPtr = 0;
		nextIndex = 0;
		while (rawPtr < rawSize) {
			int c = raw[rawPtr++];
			if (c < '0' || c > '7')
				throw new CorruptObjectException(getId(), JGitText.get().corruptObjectInvalidEntryMode);
			int mode = c - '0';
			for (;;) {
				c = raw[rawPtr++];
				if (' ' == c)
					break;
				else if (c < '0' || c > '7')
					throw new CorruptObjectException(getId(), JGitText.get().corruptObjectInvalidMode);
				mode <<= 3;
				mode += c - '0';
			}

			int nameLen = 0;
			while (raw[rawPtr + nameLen] != 0)
				nameLen++;
			final byte[] name = new byte[nameLen];
			System.arraycopy(raw, rawPtr, name, 0, nameLen);
			rawPtr += nameLen + 1;

			final ObjectId id = ObjectId.fromRaw(raw, rawPtr);
			rawPtr += Constants.OBJECT_ID_LENGTH;

			final TreeEntry ent;
			if (FileMode.REGULAR_FILE.equals(mode))
				ent = new FileTreeEntry(this, id, name, false);
			else if (FileMode.EXECUTABLE_FILE.equals(mode))
				ent = new FileTreeEntry(this, id, name, true);
			else if (FileMode.TREE.equals(mode))
				ent = new Tree(this, id, name);
			else if (FileMode.SYMLINK.equals(mode))
				ent = new SymlinkTreeEntry(this, id, name);
			else if (FileMode.GITLINK.equals(mode))
				ent = new GitlinkTreeEntry(this, id, name);
			else
				throw new CorruptObjectException(getId(), MessageFormat.format(
						JGitText.get().corruptObjectInvalidMode2, Integer.toOctalString(mode)));
			temp[nextIndex++] = ent;
		}

		contents = temp;
	}

	/**
	 * Format this Tree in canonical format.
	 *
	 * @return canonical encoding of the tree object.
	 * @throws IOException
	 *             the tree cannot be loaded, or its not in a writable state.
	 */
	public byte[] format() throws IOException {
		TreeFormatter fmt = new TreeFormatter();
		for (TreeEntry e : members()) {
			ObjectId id = e.getId();
			if (id == null)
				throw new ObjectWritingException(MessageFormat.format(JGitText
						.get().objectAtPathDoesNotHaveId, e.getFullName()));

			fmt.append(e.getNameUTF8(), e.getMode(), id);
		}
		return fmt.toByteArray();
	}

	public String toString() {
		final StringBuilder r = new StringBuilder();
		r.append(ObjectId.toString(getId()));
		r.append(" T "); //$NON-NLS-1$
		r.append(getFullName());
		return r.toString();
	}

}
