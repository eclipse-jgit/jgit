/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.notes;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Three-way note merge operation.
 * <p>
 * This operation takes three versions of a note: base, ours and theirs,
 * performs the three-way merge and returns the merge result.
 */
public interface NoteMerger {

	/**
	 * Merges the conflicting note changes.
	 * <p>
	 * base, ours and their are all notes on the same object.
	 *
	 * @param base
	 *            version of the Note
	 * @param ours
	 *            version of the Note
	 * @param their
	 *            version of the Note
	 * @param reader
	 *            the object reader that must be used to read Git objects
	 * @param inserter
	 *            the object inserter that must be used to insert Git objects
	 * @return the merge result
	 * @throws org.eclipse.jgit.notes.NotesMergeConflictException
	 *             in case there was a merge conflict which this note merger
	 *             couldn't resolve
	 * @throws java.io.IOException
	 *             in case the reader or the inserter would throw an
	 *             java.io.IOException the implementor will most likely want to
	 *             propagate it as it can't do much to recover from it
	 */
	Note merge(Note base, Note ours, Note their, ObjectReader reader,
			ObjectInserter inserter) throws NotesMergeConflictException,
			IOException;
}

