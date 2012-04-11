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

package org.eclipse.jgit.treewalk;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/** Parses raw Git trees from the canonical semi-text/semi-binary format. */
public class CanonicalTreeParser extends AbstractTreeIterator {
	private static final byte[] EMPTY = {};

	private byte[] raw;

	/** First offset within {@link #raw} of the prior entry. */
	private int prevPtr;

	/** First offset within {@link #raw} of the current entry's data. */
	private int currPtr;

	/** Offset one past the current entry (first byte of next entry). */
	private int nextPtr;

	/** Create a new parser. */
	public CanonicalTreeParser() {
		reset(EMPTY);
	}

	/**
	 * Create a new parser for a tree appearing in a subset of a repository.
	 *
	 * @param prefix
	 *            position of this iterator in the repository tree. The value
	 *            may be null or the empty array to indicate the prefix is the
	 *            root of the repository. A trailing slash ('/') is
	 *            automatically appended if the prefix does not end in '/'.
	 * @param reader
	 *            reader to load the tree data from.
	 * @param treeId
	 *            identity of the tree being parsed; used only in exception
	 *            messages if data corruption is found.
	 * @throws MissingObjectException
	 *             the object supplied is not available from the repository.
	 * @throws IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws IOException
	 *             a loose object or pack file could not be read.
	 */
	public CanonicalTreeParser(final byte[] prefix, final ObjectReader reader,
			final AnyObjectId treeId) throws IncorrectObjectTypeException,
			IOException {
		super(prefix);
		reset(reader, treeId);
	}

	private CanonicalTreeParser(final CanonicalTreeParser p) {
		super(p);
	}

	/**
	 * @return the parent of this tree parser
	 * @internal
	 */
	public CanonicalTreeParser getParent() {
		return (CanonicalTreeParser) parent;
	}

	/**
	 * Reset this parser to walk through the given tree data.
	 *
	 * @param treeData
	 *            the raw tree content.
	 */
	public void reset(final byte[] treeData) {
		raw = treeData;
		prevPtr = -1;
		currPtr = 0;
		if (eof())
			nextPtr = 0;
		else
			parseEntry();
	}

	/**
	 * Reset this parser to walk through the given tree.
	 *
	 * @param reader
	 *            reader to use during repository access.
	 * @param id
	 *            identity of the tree being parsed; used only in exception
	 *            messages if data corruption is found.
	 * @return the root level parser.
	 * @throws MissingObjectException
	 *             the object supplied is not available from the repository.
	 * @throws IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws IOException
	 *             a loose object or pack file could not be read.
	 */
	public CanonicalTreeParser resetRoot(final ObjectReader reader,
			final AnyObjectId id) throws IncorrectObjectTypeException,
			IOException {
		CanonicalTreeParser p = this;
		while (p.parent != null)
			p = (CanonicalTreeParser) p.parent;
		p.reset(reader, id);
		return p;
	}

	/** @return this iterator, or its parent, if the tree is at eof. */
	public CanonicalTreeParser next() {
		CanonicalTreeParser p = this;
		for (;;) {
			if (p.nextPtr == p.raw.length) {
				// This parser has reached EOF, return to the parent.
				if (p.parent == null) {
					p.currPtr = p.nextPtr;
					return p;
				}
				p = (CanonicalTreeParser) p.parent;
				continue;
			}

			p.prevPtr = p.currPtr;
			p.currPtr = p.nextPtr;
			p.parseEntry();
			return p;
		}
	}

	/**
	 * Reset this parser to walk through the given tree.
	 *
	 * @param reader
	 *            reader to use during repository access.
	 * @param id
	 *            identity of the tree being parsed; used only in exception
	 *            messages if data corruption is found.
	 * @throws MissingObjectException
	 *             the object supplied is not available from the repository.
	 * @throws IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws IOException
	 *             a loose object or pack file could not be read.
	 */
	public void reset(final ObjectReader reader, final AnyObjectId id)
			throws IncorrectObjectTypeException, IOException {
		reset(reader.open(id, Constants.OBJ_TREE).getCachedBytes());
	}

	@Override
	public CanonicalTreeParser createSubtreeIterator(final ObjectReader reader,
			final MutableObjectId idBuffer)
			throws IncorrectObjectTypeException, IOException {
		idBuffer.fromRaw(idBuffer(), idOffset());
		if (!FileMode.TREE.equals(mode)) {
			final ObjectId me = idBuffer.toObjectId();
			throw new IncorrectObjectTypeException(me, Constants.TYPE_TREE);
		}
		return createSubtreeIterator0(reader, idBuffer);
	}

	/**
	 * Back door to quickly create a subtree iterator for any subtree.
	 * <p>
	 * Don't use this unless you are ObjectWalk. The method is meant to be
	 * called only once the current entry has been identified as a tree and its
	 * identity has been converted into an ObjectId.
	 *
	 * @param reader
	 *            reader to load the tree data from.
	 * @param id
	 *            ObjectId of the tree to open.
	 * @return a new parser that walks over the current subtree.
	 * @throws IOException
	 *             a loose object or pack file could not be read.
	 */
	public final CanonicalTreeParser createSubtreeIterator0(
			final ObjectReader reader, final AnyObjectId id)
			throws IOException {
		final CanonicalTreeParser p = new CanonicalTreeParser(this);
		p.reset(reader, id);
		return p;
	}

	public CanonicalTreeParser createSubtreeIterator(final ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return createSubtreeIterator(reader, new MutableObjectId());
	}

	@Override
	public boolean hasId() {
		return true;
	}

	@Override
	public byte[] idBuffer() {
		return raw;
	}

	@Override
	public int idOffset() {
		return nextPtr - Constants.OBJECT_ID_LENGTH;
	}

	@Override
	public void reset() {
		if (!first())
			reset(raw);
	}

	@Override
	public boolean first() {
		return currPtr == 0;
	}

	public boolean eof() {
		return currPtr == raw.length;
	}

	@Override
	public void next(int delta) {
		if (delta == 1) {
			// Moving forward one is the most common case.
			//
			prevPtr = currPtr;
			currPtr = nextPtr;
			if (!eof())
				parseEntry();
			return;
		}

		// Fast skip over records, then parse the last one.
		//
		final int end = raw.length;
		int ptr = nextPtr;
		while (--delta > 0 && ptr != end) {
			prevPtr = ptr;
			while (raw[ptr] != 0)
				ptr++;
			ptr += Constants.OBJECT_ID_LENGTH + 1;
		}
		if (delta != 0)
			throw new ArrayIndexOutOfBoundsException(delta);
		currPtr = ptr;
		if (!eof())
			parseEntry();
	}

	@Override
	public void back(int delta) {
		if (delta == 1 && 0 <= prevPtr) {
			// Moving back one is common in NameTreeWalk, as the average tree
			// won't have D/F type conflicts to study.
			//
			currPtr = prevPtr;
			prevPtr = -1;
			if (!eof())
				parseEntry();
			return;
		} else if (delta <= 0)
			throw new ArrayIndexOutOfBoundsException(delta);

		// Fast skip through the records, from the beginning of the tree.
		// There is no reliable way to read the tree backwards, so we must
		// parse all over again from the beginning. We hold the last "delta"
		// positions in a buffer, so we can find the correct position later.
		//
		final int[] trace = new int[delta + 1];
		Arrays.fill(trace, -1);
		int ptr = 0;
		while (ptr != currPtr) {
			System.arraycopy(trace, 1, trace, 0, delta);
			trace[delta] = ptr;
			while (raw[ptr] != 0)
				ptr++;
			ptr += Constants.OBJECT_ID_LENGTH + 1;
		}
		if (trace[1] == -1)
			throw new ArrayIndexOutOfBoundsException(delta);
		prevPtr = trace[0];
		currPtr = trace[1];
		parseEntry();
	}

	private void parseEntry() {
		int ptr = currPtr;
		byte c = raw[ptr++];
		int tmp = c - '0';
		for (;;) {
			c = raw[ptr++];
			if (' ' == c)
				break;
			tmp <<= 3;
			tmp += c - '0';
		}
		mode = tmp;

		tmp = pathOffset;
		for (;; tmp++) {
			c = raw[ptr++];
			if (c == 0)
				break;
			try {
				path[tmp] = c;
			} catch (ArrayIndexOutOfBoundsException e) {
				growPath(tmp);
				path[tmp] = c;
			}
		}
		pathLen = tmp;
		nextPtr = ptr + Constants.OBJECT_ID_LENGTH;
	}
}
