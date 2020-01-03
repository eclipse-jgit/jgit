/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
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
	 * <p>
	 * Constructor for RemoveNoteCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RemoveNoteCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
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
			AddNoteCommand.commitNoteMap(repo, notesRef, walk, map, notesCommit,
					inserter,
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
	 *            the {@link org.eclipse.jgit.revwalk.RevObject} to remove a
	 *            note from.
	 * @return {@code this}
	 */
	public RemoveNoteCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	/**
	 * Set the name of the <code>Ref</code> to remove a note from.
	 *
	 * @param notesRef
	 *            the {@code Ref} to read notes from. Note, the default value of
	 *            {@link org.eclipse.jgit.lib.Constants#R_NOTES_COMMITS} will be
	 *            used if nothing is set
	 * @return {@code this}
	 * @see Constants#R_NOTES_COMMITS
	 */
	public RemoveNoteCommand setNotesRef(String notesRef) {
		checkCallable();
		this.notesRef = notesRef;
		return this;
	}

}
