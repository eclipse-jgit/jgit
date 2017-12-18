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

