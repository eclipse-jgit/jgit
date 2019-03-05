/*
 * Copyright (C) 2018, Tim Neumann <tim.neumann@advantest.com>
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

package org.eclipse.jgit.diff;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.LfsFactory;

/**
 * Util class for Diff Entries
 */
public class DiffEntries {
	private DiffEntries() {
		// hide constrcutor
	}

	/**
	 * Copies the object at one side of a DiffEntry to a given output stream.
	 *
	 * @param repository
	 *            The repository to copy the object from.
	 * @param entry
	 *            The diff entry to get the object from.
	 * @param side
	 *            The side of the entry to get the object from.
	 * @param stream
	 *            The output stream to copy the object into.
	 * @throws IOException
	 *             When any IO error occurs.
	 */
	public static void copyDiffEntrySideToOutputStream(Repository repository,
			DiffEntry entry, DiffEntry.Side side, OutputStream stream)
			throws IOException {
		if (entry.getMode(side) == FileMode.MISSING)
			return;

		try (ObjectReader reader = repository.newObjectReader()) {
			ContentSource cs = ContentSource.create(reader);
			ContentSource.Pair source = new ContentSource.Pair(cs, cs);

			AbbreviatedObjectId id = entry.getId(side);
			if (!id.isComplete()) {
				Collection<ObjectId> ids = reader.resolve(id);
				if (ids.size() == 1) {
					id = AbbreviatedObjectId
							.fromObjectId(ids.iterator().next());
					switch (side) {
					case OLD:
						entry.oldId = id;
						break;
					case NEW:
						entry.newId = id;
						break;
					}
				} else if (ids.size() == 0)
					throw new MissingObjectException(id, Constants.OBJ_BLOB);
				else
					throw new AmbiguousObjectException(id, ids);
			}

			ObjectLoader ldr = LfsFactory.getInstance().applySmudgeFilter(
					repository, source.open(side, entry),
					entry.getDiffAttribute());
			ldr.copyTo(stream);
		}
	}
}
