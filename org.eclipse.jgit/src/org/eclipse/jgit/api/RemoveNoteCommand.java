/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Remove object notes.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-notes.html"
 *      >Git documentation about Notes</a>
 */
public class RemoveNoteCommand extends GitCommand<Note> {

	private RevObject id;

	private String notesRef = Constants.R_NOTES_COMMITS;

	/**
	 * @param repo
	 */
	protected RemoveNoteCommand(Repository repo) {
		super(repo);
	}

	@Override
	public Note call() throws GitAPIException {
		checkCallable();
		try (RevWalk walk = new RevWalk(repo);
				ObjectInserter inserter = repo.newObjectInserter()) {
			NoteMap map = NoteMap.newEmptyMap();
			RevCommit notesCommit = null;
			Ref ref = repo.exactRef(notesRef);
			// if we have a notes ref, use it
			if (ref != null) {
				notesCommit = walk.parseCommit(ref.getObjectId());
				map = NoteMap.read(walk.getObjectReader(), notesCommit);
			}
			map.set(id, null, inserter);
			commitNoteMap(walk, map, notesCommit, inserter,
					"Notes removed by 'git notes remove'"); //$NON-NLS-1$
			return map.getNote(id);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Sets the object id of object you want to remove a note
	 *
	 * @param id
	 * @return {@code this}
	 */
	public RemoveNoteCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	private void commitNoteMap(RevWalk walk, NoteMap map,
			RevCommit notesCommit,
			ObjectInserter inserter,
			String msg)
			throws IOException {
		// commit the note
		CommitBuilder builder = new CommitBuilder();
		builder.setTreeId(map.writeTree(inserter));
		builder.setAuthor(new PersonIdent(repo));
		builder.setCommitter(builder.getAuthor());
		builder.setMessage(msg);
		if (notesCommit != null)
			builder.setParentIds(notesCommit);
		ObjectId commit = inserter.insert(builder);
		inserter.flush();
		RefUpdate refUpdate = repo.updateRef(notesRef);
		if (notesCommit != null)
			refUpdate.setExpectedOldObjectId(notesCommit);
		else
			refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
		refUpdate.setNewObjectId(commit);
		refUpdate.update(walk);
	}

	/**
	 * @param notesRef
	 *            the ref to read notes from. Note, the default value of
	 *            {@link Constants#R_NOTES_COMMITS} will be used if nothing is
	 *            set
	 * @return {@code this}
	 *
	 * @see Constants#R_NOTES_COMMITS
	 */
	public RemoveNoteCommand setNotesRef(String notesRef) {
		checkCallable();
		this.notesRef = notesRef;
		return this;
	}

}
