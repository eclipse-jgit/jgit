/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Iterator over an empty tree (a directory with no files).
 */
public class EmptyTreeIterator extends AbstractTreeIterator {
	/**
	 * Create a new iterator with no parent.
	 */
	public EmptyTreeIterator() {
		// Create a root empty tree.
	}

	EmptyTreeIterator(AbstractTreeIterator p) {
		super(p);
		pathLen = pathOffset;
	}

	/**
	 * Create an iterator for a subtree of an existing iterator.
	 * <p>
	 * The caller is responsible for setting up the path of the child iterator.
	 *
	 * @param p
	 *            parent tree iterator.
	 * @param childPath
	 *            path array to be used by the child iterator. This path must
	 *            contain the path from the top of the walk to the first child
	 *            and must end with a '/'.
	 * @param childPathOffset
	 *            position within <code>childPath</code> where the child can
	 *            insert its data. The value at
	 *            <code>childPath[childPathOffset-1]</code> must be '/'.
	 */
	public EmptyTreeIterator(final AbstractTreeIterator p,
			final byte[] childPath, final int childPathOffset) {
		super(p, childPath, childPathOffset);
		pathLen = childPathOffset - 1;
	}

	/** {@inheritDoc} */
	@Override
	public AbstractTreeIterator createSubtreeIterator(ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return new EmptyTreeIterator(this);
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasId() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getEntryObjectId() {
		return ObjectId.zeroId();
	}

	/** {@inheritDoc} */
	@Override
	public byte[] idBuffer() {
		return zeroid;
	}

	/** {@inheritDoc} */
	@Override
	public int idOffset() {
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public void reset() {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public boolean first() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean eof() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void next(int delta) throws CorruptObjectException {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void back(int delta) throws CorruptObjectException {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void stopWalk() {
		if (parent != null)
			parent.stopWalk();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean needsStopWalk() {
		return parent != null && parent.needsStopWalk();
	}
}
