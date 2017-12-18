/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.notes;

import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.io.UnionInputStream;

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
		UnionInputStream union = new UnionInputStream(lo.openStream(),
				lt.openStream());
		try {
			ObjectId noteData = inserter.insert(Constants.OBJ_BLOB,
					lo.getSize() + lt.getSize(), union);
			return new Note(ours, noteData);
		} finally {
			union.close();
		}
	}
}
