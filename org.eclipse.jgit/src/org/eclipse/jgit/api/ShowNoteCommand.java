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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Show an object note.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-notes.html"
 *      >Git documentation about Notes</a>
 */
public class ShowNoteCommand extends GitCommand<Note> {

	private RevObject id;

	private String notesRef = Constants.R_NOTES_COMMITS;

	/**
	 * Constructor for ShowNoteCommand.
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected ShowNoteCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public Note call() throws GitAPIException {
		checkCallable();
		NoteMap map = NoteMap.newEmptyMap();
		RevCommit notesCommit = null;
		try (RevWalk walk = new RevWalk(repo)) {
			Ref ref = repo.exactRef(notesRef);
			// if we have a notes ref, use it
			if (ref != null) {
				notesCommit = walk.parseCommit(ref.getObjectId());
				map = NoteMap.read(walk.getObjectReader(), notesCommit);
			}
			return map.getNote(id);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Sets the object id of object you want a note on
	 *
	 * @param id
	 *            the {@link org.eclipse.jgit.revwalk.RevObject} to show notes
	 *            for.
	 * @return {@code this}
	 */
	public ShowNoteCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	/**
	 * Set the {@code Ref} to read notes from.
	 *
	 * @param notesRef
	 *            the ref to read notes from. Note, the default value of
	 *            {@link org.eclipse.jgit.lib.Constants#R_NOTES_COMMITS} will be
	 *            used if nothing is set
	 * @return {@code this}
	 * @see Constants#R_NOTES_COMMITS
	 */
	public ShowNoteCommand setNotesRef(String notesRef) {
		checkCallable();
		this.notesRef = notesRef;
		return this;
	}

}
