/*
 * Copyright (C) 2015, 2017, Dariusz Luksza <dariusz@luksza.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.lib;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Detects Large File pointers, as described in [1] in Git repository.
 *
 * [1] https://github.com/github/git-lfs/blob/master/docs/spec.md
 *
 * @since 4.7
 */
public class LfsPointerFilter extends TreeFilter {

	private LfsPointer pointer;

	/**
	 * Get the field <code>pointer</code>.
	 *
	 * @return {@link org.eclipse.jgit.lfs.LfsPointer} or {@code null}
	 */
	public LfsPointer getPointer() {
		return pointer;
	}

	/** {@inheritDoc} */
	@Override
	public boolean include(TreeWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		pointer = null;
		if (walk.isSubtree()) {
			return walk.isRecursive();
		}
		ObjectId objectId = walk.getObjectId(0);
		ObjectLoader object = walk.getObjectReader().open(objectId);
		if (object.getSize() > 1024) {
			return false;
		}

		try (ObjectStream stream = object.openStream()) {
			pointer = LfsPointer.parseLfsPointer(stream);
			return pointer != null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean shouldBeRecursive() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public TreeFilter clone() {
		return new LfsPointerFilter();
	}
}
