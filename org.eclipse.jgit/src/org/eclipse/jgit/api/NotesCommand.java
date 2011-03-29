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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
 * Add or inspect object notes.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-notes.html"
 *      >Git documentation about Notes</a>
 */
public class NotesCommand extends GitCommand<List<Note>> {
	private SubCommand subCommand = SubCommand.LIST; // the default is to list

	private RevObject id;

	private String message;

	/**
	 * The sub commands available for the notes command
	 */
	public enum SubCommand {
		/**
		 * List the notes object for a given object.
		 */
		LIST,
		/**
		 * Show the notes for a given object (defaults to HEAD).
		 */
		SHOW,
		/**
		 * Edit the notes for a given object (defaults to HEAD).
		 */
		EDIT,
		/**
		 * Remove the notes for a given object (defaults to HEAD).
		 */
		REMOVE,
		/**
		 * Add notes for a given object (defaults to HEAD).
		 */
		ADD;
	}

	/**
	 * @param repo
	 */
	protected NotesCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @throws JGitInternalException
	 *             upon internal failure
	 */
	public List<Note> call() throws JGitInternalException {
		checkCallable();
		List<Note> notes = new ArrayList<Note>();
		RevWalk walk = new RevWalk(repo);
		ObjectInserter inserter = repo.newObjectInserter();
		NoteMap map = NoteMap.newEmptyMap();
		try {
			Ref notesRef = repo.getRef(Constants.R_NOTES_COMMITS);
			// if we have a notes ref, use it
			if (notesRef != null) {
				RevCommit notesCommit = walk
						.parseCommit(notesRef.getObjectId());
				map = NoteMap.read(walk.getObjectReader(), notesCommit);
			}

			switch (subCommand) {
			case ADD:
				map.set(id, message, inserter);
				Note note = map.getNote(id);
				notes.add(note);
				commitNoteMap(map, inserter, "Notes added by 'git notes add'");
				break;
			case EDIT:
				break;
			case REMOVE:
				map.set(id, null, inserter);
				commitNoteMap(map, inserter,
						"Notes removed by 'git notes remove'");
				break;
			case SHOW:
				notes.add(map.getNote(id));
				break;
			case LIST:
				Iterator<Note> i = map.iterator();
				while (i.hasNext())
					notes.add(i.next());
				break;
			}
			inserter.flush();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			inserter.release();
			walk.release();
		}

		return notes;
	}

	/**
	 * @param subCommand
	 *            defaults to {@link SubCommand#LIST}
	 * @return this instance
	 */
	public NotesCommand setSubCommand(SubCommand subCommand) {
		checkCallable();
		this.subCommand = subCommand;
		return this;
	}

	/**
	 * Sets the object id of object you want a note on
	 *
	 * @param id
	 * @return {@code this}
	 * @see SubCommand#ADD
	 * @see SubCommand#SHOW
	 * @see SubCommand#REMOVE
	 * @see SubCommand#EDIT
	 */
	public NotesCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	/**
	 * @param message
	 *            the notes message used when adding a note
	 * @return {@code this}
	 * @see SubCommand#ADD
	 */
	public NotesCommand setMessage(String message) {
		checkCallable();
		this.message = message;
		return this;
	}

	private void commitNoteMap(NoteMap map, ObjectInserter inserter,
			String msg)
			throws IOException {
		// commit the note
		CommitBuilder builder = new CommitBuilder();
		builder.setTreeId(map.writeTree(inserter));
		builder.setAuthor(new PersonIdent(repo));
		builder.setCommitter(new PersonIdent(repo));
		builder.setMessage(msg);
		ObjectId oid = repo.resolve(Constants.R_NOTES_COMMITS);
		if (oid != null)
			builder.setParentIds(oid);
		ObjectId commit = inserter.insert(builder);
		RefUpdate refUpdate = repo.updateRef(Constants.R_NOTES_COMMITS);
		refUpdate.setNewObjectId(commit);
		refUpdate.forceUpdate();
	}

}
