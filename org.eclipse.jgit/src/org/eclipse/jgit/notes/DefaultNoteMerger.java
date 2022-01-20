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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.stream.UnionInputStream;

/**
 * Default implementation of the {@link org.eclipse.jgit.notes.NoteMerger}.
 * <p>
 * If ours and theirs are both non-null, which means they are either both edits
 * or both adds, then this merger will simply join the content of ours and
 * theirs (in that order) and return that as the merge result.
 * <p>
 * If one or ours/theirs is non-null and the other one is null then the non-null
 * value is returned as the merge result. This means that an edit/delete
 * conflict is resolved by keeping the edit version.
 * <p>
 * If both ours and theirs are null then the result of the merge is also null.
 */
public class DefaultNoteMerger implements NoteMerger {

	/** {@inheritDoc} */
	@Override
	public Note merge(Note base, Note ours, Note theirs, ObjectReader reader,
			ObjectInserter inserter) throws IOException {
		if (ours == null)
			return theirs;

		if (theirs == null)
			return ours;

		if (ours.getData().equals(theirs.getData()))
			return ours;

		ObjectLoader lo = reader.open(ours.getData());
		ObjectLoader lt = reader.open(theirs.getData());
		try (UnionInputStream union = new UnionInputStream(lo.openStream(),
				lt.openStream())) {
			ObjectId noteData = inserter.insert(Constants.OBJ_BLOB,
					lo.getSize() + lt.getSize(), union);
			return new Note(ours, noteData);
		}
	}
}
