/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.dircache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.FileMode.TREE;
import static org.eclipse.jgit.lib.TreeFormatter.entrySize;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Single tree record from the 'TREE' {@link org.eclipse.jgit.dircache.DirCache}
 * extension.
 * <p>
 * A valid cache tree record contains the object id of a tree object and the
 * total number of {@link org.eclipse.jgit.dircache.DirCacheEntry} instances
 * (counted recursively) from the DirCache contained within the tree. This
 * information facilitates faster traversal of the index and quicker generation
 * of tree objects prior to creating a new commit.
 * <p>
 * An invalid cache tree record indicates a known subtree whose file entries
 * have changed in ways that cause the tree to no longer have a known object id.
 * Invalid cache tree records must be revalidated prior to use.
 */
public class DirCacheTree {
	private static final byte[] NO_NAME = {};

	private static final DirCacheTree[] NO_CHILDREN = {};

	private static final Comparator<DirCacheTree> TREE_CMP = (DirCacheTree o1,
			DirCacheTree o2) -> {
		final byte[] a = o1.encodedName;
		final byte[] b = o2.encodedName;
		final int aLen = a.length;
		final int bLen = b.length;
		int cPos;
		for (cPos = 0; cPos < aLen && cPos < bLen; cPos++) {
			final int cmp = (a[cPos] & 0xff) - (b[cPos] & 0xff);
			if (cmp != 0)
				return cmp;
		}
		if (aLen == bLen)
			return 0;
		if (aLen < bLen)
			return '/' - (b[cPos] & 0xff);
		return (a[cPos] & 0xff) - '/';
	};

	/** Tree this tree resides in; null if we are the root. */
	private DirCacheTree parent;

	/** Name of this tree within its parent. */
	byte[] encodedName;

	/** Number of {@link DirCacheEntry} records that belong to this tree. */
	private int entrySpan;

	/** Unique SHA-1 of this tree; null if invalid. */
	private ObjectId id;

	/** Child trees, if any, sorted by {@link #encodedName}. */
	private DirCacheTree[] children;

	/** Number of valid children in {@link #children}. */
	private int childCnt;

	DirCacheTree() {
		encodedName = NO_NAME;
		children = NO_CHILDREN;
		childCnt = 0;
		entrySpan = -1;
	}

	private DirCacheTree(final DirCacheTree myParent, final byte[] path,
			final int pathOff, final int pathLen) {
		parent = myParent;
		encodedName = new byte[pathLen];
		System.arraycopy(path, pathOff, encodedName, 0, pathLen);
		children = NO_CHILDREN;
		childCnt = 0;
		entrySpan = -1;
	}

	DirCacheTree(final byte[] in, final MutableInteger off,
			final DirCacheTree myParent) {
		parent = myParent;

		int ptr = RawParseUtils.next(in, off.value, '\0');
		final int nameLen = ptr - off.value - 1;
		if (nameLen > 0) {
			encodedName = new byte[nameLen];
			System.arraycopy(in, off.value, encodedName, 0, nameLen);
		} else
			encodedName = NO_NAME;

		entrySpan = RawParseUtils.parseBase10(in, ptr, off);
		final int subcnt = RawParseUtils.parseBase10(in, off.value, off);
		off.value = RawParseUtils.next(in, off.value, '\n');

		if (entrySpan >= 0) {
			// Valid trees have a positive entry count and an id of a
			// tree object that should exist in the object database.
			//
			id = ObjectId.fromRaw(in, off.value);
			off.value += Constants.OBJECT_ID_LENGTH;
		}

		if (subcnt > 0) {
			boolean alreadySorted = true;
			children = new DirCacheTree[subcnt];
			for (int i = 0; i < subcnt; i++) {
				children[i] = new DirCacheTree(in, off, this);

				// C Git's ordering differs from our own; it prefers to
				// sort by length first. This sometimes produces a sort
				// we do not desire. On the other hand it may have been
				// created by us, and be sorted the way we want.
				//
				if (alreadySorted && i > 0
						&& TREE_CMP.compare(children[i - 1], children[i]) > 0)
					alreadySorted = false;
			}
			if (!alreadySorted)
				Arrays.sort(children, 0, subcnt, TREE_CMP);
		} else {
			// Leaf level trees have no children, only (file) entries.
			//
			children = NO_CHILDREN;
		}
		childCnt = subcnt;
	}

	void write(byte[] tmp, OutputStream os) throws IOException {
		int ptr = tmp.length;
		tmp[--ptr] = '\n';
		ptr = RawParseUtils.formatBase10(tmp, ptr, childCnt);
		tmp[--ptr] = ' ';
		ptr = RawParseUtils.formatBase10(tmp, ptr, isValid() ? entrySpan : -1);
		tmp[--ptr] = 0;

		os.write(encodedName);
		os.write(tmp, ptr, tmp.length - ptr);
		if (isValid()) {
			id.copyRawTo(tmp, 0);
			os.write(tmp, 0, Constants.OBJECT_ID_LENGTH);
		}
		for (int i = 0; i < childCnt; i++)
			children[i].write(tmp, os);
	}

	/**
	 * Determine if this cache is currently valid.
	 * <p>
	 * A valid cache tree knows how many
	 * {@link org.eclipse.jgit.dircache.DirCacheEntry} instances from the parent
	 * {@link org.eclipse.jgit.dircache.DirCache} reside within this tree
	 * (recursively enumerated). It also knows the object id of the tree, as the
	 * tree should be readily available from the repository's object database.
	 *
	 * @return true if this tree is knows key details about itself; false if the
	 *         tree needs to be regenerated.
	 */
	public boolean isValid() {
		return id != null;
	}

	/**
	 * Get the number of entries this tree spans within the DirCache.
	 * <p>
	 * If this tree is not valid (see {@link #isValid()}) this method's return
	 * value is always strictly negative (less than 0) but is otherwise an
	 * undefined result.
	 *
	 * @return total number of entries (recursively) contained within this tree.
	 */
	public int getEntrySpan() {
		return entrySpan;
	}

	/**
	 * Get the number of cached subtrees contained within this tree.
	 *
	 * @return number of child trees available through this tree.
	 */
	public int getChildCount() {
		return childCnt;
	}

	/**
	 * Get the i-th child cache tree.
	 *
	 * @param i
	 *            index of the child to obtain.
	 * @return the child tree.
	 */
	public DirCacheTree getChild(int i) {
		return children[i];
	}

	/**
	 * Get the tree's ObjectId.
	 * <p>
	 * If {@link #isValid()} returns false this method will return null.
	 *
	 * @return ObjectId of this tree or null.
	 * @since 4.3
	 */
	public ObjectId getObjectId() {
		return id;
	}

	/**
	 * Get the tree's name within its parent.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return name of the tree. This does not contain any '/' characters.
	 */
	public String getNameString() {
		final ByteBuffer bb = ByteBuffer.wrap(encodedName);
		return UTF_8.decode(bb).toString();
	}

	/**
	 * Get the tree's path within the repository.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return path of the tree, relative to the repository root. If this is not
	 *         the root tree the path ends with '/'. The root tree's path string
	 *         is the empty string ("").
	 */
	public String getPathString() {
		final StringBuilder r = new StringBuilder();
		appendName(r);
		return r.toString();
	}

	/**
	 * Write (if necessary) this tree to the object store.
	 *
	 * @param cache
	 *            the complete cache from DirCache.
	 * @param cIdx
	 *            first position of <code>cache</code> that is a member of this
	 *            tree. The path of <code>cache[cacheIdx].path</code> for the
	 *            range <code>[0,pathOff-1)</code> matches the complete path of
	 *            this tree, from the root of the repository.
	 * @param pathOffset
	 *            number of bytes of <code>cache[cacheIdx].path</code> that
	 *            matches this tree's path. The value at array position
	 *            <code>cache[cacheIdx].path[pathOff-1]</code> is always '/' if
	 *            <code>pathOff</code> is > 0.
	 * @param ow
	 *            the writer to use when serializing to the store.
	 * @return identity of this tree.
	 * @throws UnmergedPathException
	 *             one or more paths contain higher-order stages (stage > 0),
	 *             which cannot be stored in a tree object.
	 * @throws IOException
	 *             an unexpected error occurred writing to the object store.
	 */
	ObjectId writeTree(final DirCacheEntry[] cache, int cIdx,
			final int pathOffset, final ObjectInserter ow)
			throws UnmergedPathException, IOException {
		if (id == null) {
			final int endIdx = cIdx + entrySpan;
			final TreeFormatter fmt = new TreeFormatter(computeSize(cache,
					cIdx, pathOffset, ow));
			int childIdx = 0;
			int entryIdx = cIdx;

			while (entryIdx < endIdx) {
				final DirCacheEntry e = cache[entryIdx];
				final byte[] ep = e.path;
				if (childIdx < childCnt) {
					final DirCacheTree st = children[childIdx];
					if (st.contains(ep, pathOffset, ep.length)) {
						fmt.append(st.encodedName, TREE, st.id);
						entryIdx += st.entrySpan;
						childIdx++;
						continue;
					}
				}

				fmt.append(ep, pathOffset, ep.length - pathOffset, e
						.getFileMode(), e.idBuffer(), e.idOffset());
				entryIdx++;
			}

			id = ow.insert(fmt);
		}
		return id;
	}

	private int computeSize(final DirCacheEntry[] cache, int cIdx,
			final int pathOffset, final ObjectInserter ow)
			throws UnmergedPathException, IOException {
		final int endIdx = cIdx + entrySpan;
		int childIdx = 0;
		int entryIdx = cIdx;
		int size = 0;

		while (entryIdx < endIdx) {
			final DirCacheEntry e = cache[entryIdx];
			if (e.getStage() != 0)
				throw new UnmergedPathException(e);

			final byte[] ep = e.path;
			if (childIdx < childCnt) {
				final DirCacheTree st = children[childIdx];
				if (st.contains(ep, pathOffset, ep.length)) {
					final int stOffset = pathOffset + st.nameLength() + 1;
					st.writeTree(cache, entryIdx, stOffset, ow);

					size += entrySize(TREE, st.nameLength());

					entryIdx += st.entrySpan;
					childIdx++;
					continue;
				}
			}

			size += entrySize(e.getFileMode(), ep.length - pathOffset);
			entryIdx++;
		}

		return size;
	}

	private void appendName(StringBuilder r) {
		if (parent != null) {
			parent.appendName(r);
			r.append(getNameString());
			r.append('/');
		} else if (nameLength() > 0) {
			r.append(getNameString());
			r.append('/');
		}
	}

	final int nameLength() {
		return encodedName.length;
	}

	final boolean contains(byte[] a, int aOff, int aLen) {
		final byte[] e = encodedName;
		final int eLen = e.length;
		for (int eOff = 0; eOff < eLen && aOff < aLen; eOff++, aOff++)
			if (e[eOff] != a[aOff])
				return false;
		if (aOff >= aLen)
			return false;
		return a[aOff] == '/';
	}

	/**
	 * Update (if necessary) this tree's entrySpan.
	 *
	 * @param cache
	 *            the complete cache from DirCache.
	 * @param cCnt
	 *            number of entries in <code>cache</code> that are valid for
	 *            iteration.
	 * @param cIdx
	 *            first position of <code>cache</code> that is a member of this
	 *            tree. The path of <code>cache[cacheIdx].path</code> for the
	 *            range <code>[0,pathOff-1)</code> matches the complete path of
	 *            this tree, from the root of the repository.
	 * @param pathOff
	 *            number of bytes of <code>cache[cacheIdx].path</code> that
	 *            matches this tree's path. The value at array position
	 *            <code>cache[cacheIdx].path[pathOff-1]</code> is always '/' if
	 *            <code>pathOff</code> is > 0.
	 */
	void validate(final DirCacheEntry[] cache, final int cCnt, int cIdx,
			final int pathOff) {
		if (entrySpan >= 0 && cIdx + entrySpan <= cCnt) {
			// If we are valid, our children are also valid.
			// We have no need to validate them.
			//
			return;
		}

		entrySpan = 0;
		if (cCnt == 0) {
			// Special case of an empty index, and we are the root tree.
			//
			return;
		}

		final byte[] firstPath = cache[cIdx].path;
		int stIdx = 0;
		while (cIdx < cCnt) {
			final byte[] currPath = cache[cIdx].path;
			if (pathOff > 0 && !peq(firstPath, currPath, pathOff)) {
				// The current entry is no longer in this tree. Our
				// span is updated and the remainder goes elsewhere.
				//
				break;
			}

			DirCacheTree st = stIdx < childCnt ? children[stIdx] : null;
			final int cc = namecmp(currPath, pathOff, st);
			if (cc > 0) {
				// This subtree is now empty.
				//
				removeChild(stIdx);
				continue;
			}

			if (cc < 0) {
				final int p = slash(currPath, pathOff);
				if (p < 0) {
					// The entry has no '/' and thus is directly in this
					// tree. Count it as one of our own.
					//
					cIdx++;
					entrySpan++;
					continue;
				}

				// Build a new subtree for this entry.
				//
				st = new DirCacheTree(this, currPath, pathOff, p - pathOff);
				insertChild(stIdx, st);
			}

			// The entry is contained in this subtree.
			//
			assert(st != null);
			st.validate(cache, cCnt, cIdx, pathOff + st.nameLength() + 1);
			cIdx += st.entrySpan;
			entrySpan += st.entrySpan;
			stIdx++;
		}

		// None of our remaining children can be in this tree
		// as the current cache entry is after our own name.
		//
		while (stIdx < childCnt)
			removeChild(childCnt - 1);
	}

	private void insertChild(int stIdx, DirCacheTree st) {
		final DirCacheTree[] c = children;
		if (childCnt + 1 <= c.length) {
			if (stIdx < childCnt)
				System.arraycopy(c, stIdx, c, stIdx + 1, childCnt - stIdx);
			c[stIdx] = st;
			childCnt++;
			return;
		}

		final int n = c.length;
		final DirCacheTree[] a = new DirCacheTree[n + 1];
		if (stIdx > 0)
			System.arraycopy(c, 0, a, 0, stIdx);
		a[stIdx] = st;
		if (stIdx < n)
			System.arraycopy(c, stIdx, a, stIdx + 1, n - stIdx);
		children = a;
		childCnt++;
	}

	private void removeChild(int stIdx) {
		final int n = --childCnt;
		if (stIdx < n)
			System.arraycopy(children, stIdx + 1, children, stIdx, n - stIdx);
		children[n] = null;
	}

	static boolean peq(byte[] a, byte[] b, int aLen) {
		if (b.length < aLen)
			return false;
		for (aLen--; aLen >= 0; aLen--)
			if (a[aLen] != b[aLen])
				return false;
		return true;
	}

	private static int namecmp(byte[] a, int aPos, DirCacheTree ct) {
		if (ct == null)
			return -1;
		final byte[] b = ct.encodedName;
		final int aLen = a.length;
		final int bLen = b.length;
		int bPos = 0;
		for (; aPos < aLen && bPos < bLen; aPos++, bPos++) {
			final int cmp = (a[aPos] & 0xff) - (b[bPos] & 0xff);
			if (cmp != 0)
				return cmp;
		}
		if (bPos == bLen)
			return a[aPos] == '/' ? 0 : -1;
		return aLen - bLen;
	}

	private static int slash(byte[] a, int aPos) {
		final int aLen = a.length;
		for (; aPos < aLen; aPos++)
			if (a[aPos] == '/')
				return aPos;
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getNameString();
	}
}
