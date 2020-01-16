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
 * Add object notes.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-notes.html"
 *      >Git documentation about Notes</a>
 */
public class AddNoteCommand extends GitCommand<Note> {

	private RevObject id;

	private String message;

	private String notesRef = Constants.R_NOTES_COMMITS;

	/**
	 * Constructor for AddNoteCommand
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected AddNoteCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public Note call() throws GitAPIException {
		checkCallable();
		NoteMap map = NoteMap.newEmptyMap();
		RevCommit notesCommit = null;
		try (RevWalk walk = new RevWalk(repo);
				ObjectInserter inserter = repo.newObjectInserter()) {
			Ref ref = repo.findRef(notesRef);
			// if we have a notes ref, use it
			if (ref != null) {
				notesCommit = walk.parseCommit(ref.getObjectId());
				map = NoteMap.read(walk.getObjectReader(), notesCommit);
			}
			map.set(id, message, inserter);
			commitNoteMap(repo, notesRef, walk, map, notesCommit, inserter,
					"Notes added by 'git notes add'"); //$NON-NLS-1$
			return map.getNote(id);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Sets the object id of object you want a note on. If the object already
	 * has a note, the existing note will be replaced.
	 *
	 * @param id
	 *            a {@link org.eclipse.jgit.revwalk.RevObject}
	 * @return {@code this}
	 */
	public AddNoteCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	/**
	 * Set the notes message
	 *
	 * @param message
	 *            the notes message used when adding a note
	 * @return {@code this}
	 */
	public AddNoteCommand setMessage(String message) {
		checkCallable();
		this.message = message;
		return this;
	}

	static void commitNoteMap(Repository r, String ref, RevWalk walk,
			NoteMap map,
			RevCommit notesCommit,
			ObjectInserter inserter,
			String msg)
			throws IOException {
		// commit the note
		CommitBuilder builder = new CommitBuilder();
		builder.setTreeId(map.writeTree(inserter));
		builder.setAuthor(new PersonIdent(r));
		builder.setCommitter(builder.getAuthor());
		builder.setMessage(msg);
		if (notesCommit != null)
			builder.setParentIds(notesCommit);
		ObjectId commit = inserter.insert(builder);
		inserter.flush();
		RefUpdate refUpdate = r.updateRef(ref);
		if (notesCommit != null)
			refUpdate.setExpectedOldObjectId(notesCommit);
		else
			refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
		refUpdate.setNewObjectId(commit);
		refUpdate.update(walk);
	}

	/**
	 * Set name of a {@code Ref} to read notes from
	 *
	 * @param notesRef
	 *            the ref to read notes from. Note, the default value of
	 *            {@link org.eclipse.jgit.lib.Constants#R_NOTES_COMMITS} will be
	 *            used if nothing is set
	 * @return {@code this}
	 * @see Constants#R_NOTES_COMMITS
	 */
	public AddNoteCommand setNotesRef(String notesRef) {
		checkCallable();
		this.notesRef = notesRef;
		return this;
	}

}
