/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import static org.eclipse.jgit.lib.Constants.DOT_GIT_ATTRIBUTES;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.lib.Constants.TYPE_TREE;
import static org.eclipse.jgit.lib.Constants.encode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesRule;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Parses raw Git trees from the canonical semi-text/semi-binary format.
 */
public class CanonicalTreeParser extends AbstractTreeIterator {
	private static final byte[] EMPTY = {};
	private static final byte[] ATTRS = encode(DOT_GIT_ATTRIBUTES);

	private byte[] raw;

	/** First offset within {@link #raw} of the prior entry. */
	private int prevPtr;

	/** First offset within {@link #raw} of the current entry's data. */
	private int currPtr;

	/** Offset one past the current entry (first byte of next entry). */
	private int nextPtr;

	/**
	 * Create a new parser.
	 */
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
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public CanonicalTreeParser(final byte[] prefix, final ObjectReader reader,
			final AnyObjectId treeId) throws IncorrectObjectTypeException,
			IOException {
		super(prefix);
		reset(reader, treeId);
	}

	private CanonicalTreeParser(CanonicalTreeParser p) {
		super(p);
	}

	/**
	 * Get the parent of this tree parser.
	 *
	 * @return the parent of this tree parser.
	 * @deprecated internal use only
	 */
	@Deprecated
	public CanonicalTreeParser getParent() {
		return (CanonicalTreeParser) parent;
	}

	/**
	 * Reset this parser to walk through the given tree data.
	 *
	 * @param treeData
	 *            the raw tree content.
	 */
	public void reset(byte[] treeData) {
		attributesNode = null;
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
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws java.io.IOException
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

	/**
	 * Get this iterator, or its parent, if the tree is at eof.
	 *
	 * @return this iterator, or its parent, if the tree is at eof.
	 */
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
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object supplied as an argument is not actually a tree and
	 *             cannot be parsed as though it were a tree.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public void reset(ObjectReader reader, AnyObjectId id)
			throws IncorrectObjectTypeException, IOException {
		reset(reader.open(id, OBJ_TREE).getCachedBytes());
	}

	/** {@inheritDoc} */
	@Override
	public CanonicalTreeParser createSubtreeIterator(final ObjectReader reader,
			final MutableObjectId idBuffer)
			throws IncorrectObjectTypeException, IOException {
		idBuffer.fromRaw(idBuffer(), idOffset());
		if (!FileMode.TREE.equals(mode)) {
			final ObjectId me = idBuffer.toObjectId();
			throw new IncorrectObjectTypeException(me, TYPE_TREE);
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
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public final CanonicalTreeParser createSubtreeIterator0(
			final ObjectReader reader, final AnyObjectId id)
			throws IOException {
		final CanonicalTreeParser p = new CanonicalTreeParser(this);
		p.reset(reader, id);
		return p;
	}

	/** {@inheritDoc} */
	@Override
	public CanonicalTreeParser createSubtreeIterator(ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return createSubtreeIterator(reader, new MutableObjectId());
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasId() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public byte[] idBuffer() {
		return raw;
	}

	/** {@inheritDoc} */
	@Override
	public int idOffset() {
		return nextPtr - OBJECT_ID_LENGTH;
	}

	/** {@inheritDoc} */
	@Override
	public void reset() {
		if (!first())
			reset(raw);
	}

	/** {@inheritDoc} */
	@Override
	public boolean first() {
		return currPtr == 0;
	}

	/** {@inheritDoc} */
	@Override
	public boolean eof() {
		return currPtr == raw.length;
	}

	/** {@inheritDoc} */
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
			ptr += OBJECT_ID_LENGTH + 1;
		}
		if (delta != 0)
			throw new ArrayIndexOutOfBoundsException(delta);
		currPtr = ptr;
		if (!eof())
			parseEntry();
	}

	/** {@inheritDoc} */
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
			ptr += OBJECT_ID_LENGTH + 1;
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
			if (c == 0) {
				break;
			}
			if (tmp >= path.length) {
				growPath(tmp);
			}
			path[tmp] = c;
		}
		pathLen = tmp;
		nextPtr = ptr + OBJECT_ID_LENGTH;
	}

	/**
	 * Retrieve the {@link org.eclipse.jgit.attributes.AttributesNode} for the
	 * current entry.
	 *
	 * @param reader
	 *            {@link org.eclipse.jgit.lib.ObjectReader} used to parse the
	 *            .gitattributes entry.
	 * @return {@link org.eclipse.jgit.attributes.AttributesNode} for the
	 *         current entry.
	 * @throws java.io.IOException
	 * @since 4.2
	 */
	public AttributesNode getEntryAttributesNode(ObjectReader reader)
			throws IOException {
		if (attributesNode == null) {
			attributesNode = findAttributes(reader);
		}
		return attributesNode.getRules().isEmpty() ? null : attributesNode;
	}

	private AttributesNode findAttributes(ObjectReader reader)
			throws IOException {
		CanonicalTreeParser itr = new CanonicalTreeParser();
		itr.reset(raw);
		if (itr.findFile(ATTRS)) {
			return loadAttributes(reader, itr.getEntryObjectId());
		}
		return noAttributes();
	}

	private static AttributesNode loadAttributes(ObjectReader reader,
			AnyObjectId id) throws IOException {
		AttributesNode r = new AttributesNode();
		try (InputStream in = reader.open(id, OBJ_BLOB).openStream()) {
			r.parse(in);
		}
		return r.getRules().isEmpty() ? noAttributes() : r;
	}

	private static AttributesNode noAttributes() {
		return new AttributesNode(Collections.<AttributesRule> emptyList());
	}
}
