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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesRule;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Iterate a {@link org.eclipse.jgit.dircache.DirCache} as part of a
 * <code>TreeWalk</code>.
 * <p>
 * This is an iterator to adapt a loaded <code>DirCache</code> instance (such as
 * read from an existing <code>.git/index</code> file) to the tree structure
 * used by a <code>TreeWalk</code>, making it possible for applications to walk
 * over any combination of tree objects already in the object database, index
 * files, or working directories.
 *
 * @see org.eclipse.jgit.treewalk.TreeWalk
 */
public class DirCacheIterator extends AbstractTreeIterator {
	/** Byte array holding ".gitattributes" string */
	private static final byte[] DOT_GIT_ATTRIBUTES_BYTES = Constants.DOT_GIT_ATTRIBUTES
			.getBytes();

	/** The cache this iterator was created to walk. */
	protected final DirCache cache;

	/** The tree this iterator is walking. */
	private final DirCacheTree tree;

	/** First position in this tree. */
	private final int treeStart;

	/** Last position in this tree. */
	private final int treeEnd;

	/** Special buffer to hold the ObjectId of {@link #currentSubtree}. */
	private final byte[] subtreeId;

	/** Index of entry within {@link #cache}. */
	protected int ptr;

	/** Next subtree to consider within {@link #tree}. */
	private int nextSubtreePos;

	/** The current file entry from {@link #cache}. */
	protected DirCacheEntry currentEntry;

	/** The subtree containing {@link #currentEntry} if this is first entry. */
	protected DirCacheTree currentSubtree;

	/**
	 * Create a new iterator for an already loaded DirCache instance.
	 * <p>
	 * The iterator implementation may copy part of the cache's data during
	 * construction, so the cache must be read in prior to creating the
	 * iterator.
	 *
	 * @param dc
	 *            the cache to walk. It must be already loaded into memory.
	 */
	public DirCacheIterator(DirCache dc) {
		cache = dc;
		tree = dc.getCacheTree(true);
		treeStart = 0;
		treeEnd = tree.getEntrySpan();
		subtreeId = new byte[Constants.OBJECT_ID_LENGTH];
		if (!eof())
			parseEntry();
	}

	DirCacheIterator(DirCacheIterator p, DirCacheTree dct) {
		super(p, p.path, p.pathLen + 1);
		cache = p.cache;
		tree = dct;
		treeStart = p.ptr;
		treeEnd = treeStart + tree.getEntrySpan();
		subtreeId = p.subtreeId;
		ptr = p.ptr;
		parseEntry();
	}

	/** {@inheritDoc} */
	@Override
	public AbstractTreeIterator createSubtreeIterator(ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		if (currentSubtree == null)
			throw new IncorrectObjectTypeException(getEntryObjectId(),
					Constants.TYPE_TREE);
		return new DirCacheIterator(this, currentSubtree);
	}

	/** {@inheritDoc} */
	@Override
	public EmptyTreeIterator createEmptyTreeIterator() {
		final byte[] n = new byte[Math.max(pathLen + 1, DEFAULT_PATH_SIZE)];
		System.arraycopy(path, 0, n, 0, pathLen);
		n[pathLen] = '/';
		return new EmptyTreeIterator(this, n, pathLen + 1);
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasId() {
		if (currentSubtree != null)
			return currentSubtree.isValid();
		return currentEntry != null;
	}

	/** {@inheritDoc} */
	@Override
	public byte[] idBuffer() {
		if (currentSubtree != null)
			return currentSubtree.isValid() ? subtreeId : zeroid;
		if (currentEntry != null)
			return currentEntry.idBuffer();
		return zeroid;
	}

	/** {@inheritDoc} */
	@Override
	public int idOffset() {
		if (currentSubtree != null)
			return 0;
		if (currentEntry != null)
			return currentEntry.idOffset();
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public void reset() {
		if (!first()) {
			ptr = treeStart;
			nextSubtreePos = 0;
			currentEntry = null;
			currentSubtree = null;
			if (!eof())
				parseEntry();
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean first() {
		return ptr == treeStart;
	}

	/** {@inheritDoc} */
	@Override
	public boolean eof() {
		return ptr == treeEnd;
	}

	/** {@inheritDoc} */
	@Override
	public void next(int delta) {
		while (--delta >= 0) {
			if (currentSubtree != null)
				ptr += currentSubtree.getEntrySpan();
			else
				ptr++;
			if (eof())
				break;
			parseEntry();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void back(int delta) {
		while (--delta >= 0) {
			if (currentSubtree != null)
				nextSubtreePos--;
			ptr--;
			parseEntry(false);
			if (currentSubtree != null)
				ptr -= currentSubtree.getEntrySpan() - 1;
		}
	}

	private void parseEntry() {
		parseEntry(true);
	}

	private void parseEntry(boolean forward) {
		currentEntry = cache.getEntry(ptr);
		final byte[] cep = currentEntry.path;

		if (!forward) {
			if (nextSubtreePos > 0) {
				final DirCacheTree p = tree.getChild(nextSubtreePos - 1);
				if (p.contains(cep, pathOffset, cep.length)) {
					nextSubtreePos--;
					currentSubtree = p;
				}
			}
		}
		if (nextSubtreePos != tree.getChildCount()) {
			final DirCacheTree s = tree.getChild(nextSubtreePos);
			if (s.contains(cep, pathOffset, cep.length)) {
				// The current position is the first file of this subtree.
				// Use the subtree instead as the current position.
				//
				currentSubtree = s;
				nextSubtreePos++;

				if (s.isValid())
					s.getObjectId().copyRawTo(subtreeId, 0);
				mode = FileMode.TREE.getBits();
				path = cep;
				pathLen = pathOffset + s.nameLength();
				return;
			}
		}

		// The current position is a file/symlink/gitlink so we
		// do not have a subtree located here.
		//
		mode = currentEntry.getRawMode();
		path = cep;
		pathLen = cep.length;
		currentSubtree = null;
		// Checks if this entry is a .gitattributes file
		if (RawParseUtils.match(path, pathOffset, DOT_GIT_ATTRIBUTES_BYTES) == path.length)
			attributesNode = new LazyLoadingAttributesNode(
					currentEntry.getObjectId());
	}

	/**
	 * Get the DirCacheEntry for the current file.
	 *
	 * @return the current cache entry, if this iterator is positioned on a
	 *         non-tree.
	 */
	public DirCacheEntry getDirCacheEntry() {
		return currentSubtree == null ? currentEntry : null;
	}

	/**
	 * Retrieves the {@link org.eclipse.jgit.attributes.AttributesNode} for the
	 * current entry.
	 *
	 * @param reader
	 *            {@link org.eclipse.jgit.lib.ObjectReader} used to parse the
	 *            .gitattributes entry.
	 * @return {@link org.eclipse.jgit.attributes.AttributesNode} for the
	 *         current entry.
	 * @throws java.io.IOException
	 * @since 3.7
	 */
	public AttributesNode getEntryAttributesNode(ObjectReader reader)
			throws IOException {
		if (attributesNode instanceof LazyLoadingAttributesNode)
			attributesNode = ((LazyLoadingAttributesNode) attributesNode)
					.load(reader);
		return attributesNode;
	}

	/**
	 * {@link AttributesNode} implementation that provides lazy loading
	 * facilities.
	 */
	private static class LazyLoadingAttributesNode extends AttributesNode {
		final ObjectId objectId;

		LazyLoadingAttributesNode(ObjectId objectId) {
			super(Collections.<AttributesRule> emptyList());
			this.objectId = objectId;

		}

		AttributesNode load(ObjectReader reader) throws IOException {
			AttributesNode r = new AttributesNode();
			ObjectLoader loader = reader.open(objectId);
			if (loader != null) {
				try (InputStream in = loader.openStream()) {
					r.parse(in);
				}
			}
			return r.getRules().isEmpty() ? null : r;
		}
	}

}
