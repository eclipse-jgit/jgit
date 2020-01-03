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
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * This exception will be thrown from the
 * {@link org.eclipse.jgit.notes.NoteMerger} when a conflict on Notes content is
 * found during merge.
 */
public class NotesMergeConflictException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a NotesMergeConflictException for the specified base, ours and
	 * theirs note versions.
	 *
	 * @param base
	 *            note version
	 * @param ours
	 *            note version
	 * @param theirs
	 *            note version
	 */
	public NotesMergeConflictException(Note base, Note ours, Note theirs) {
		super(MessageFormat.format(JGitText.get().mergeConflictOnNotes,
				noteOn(base, ours, theirs), noteData(base), noteData(ours),
				noteData(theirs)));
	}

	/**
	 * Constructs a NotesMergeConflictException for the specified base, ours and
	 * theirs versions of the root note tree.
	 *
	 * @param base
	 *            version of the root note tree
	 * @param ours
	 *            version of the root note tree
	 * @param theirs
	 *            version of the root note tree
	 */
	public NotesMergeConflictException(NonNoteEntry base, NonNoteEntry ours,
			NonNoteEntry theirs) {
		super(MessageFormat.format(
				JGitText.get().mergeConflictOnNonNoteEntries, name(base),
				name(ours), name(theirs)));
	}

	private static String noteOn(Note base, Note ours, Note theirs) {
		if (base != null)
			return base.name();
		if (ours != null)
			return ours.name();
		return theirs.name();
	}

	private static String noteData(Note n) {
		if (n != null)
			return n.getData().name();
		return ""; //$NON-NLS-1$
	}

	private static String name(NonNoteEntry e) {
		return e != null ? e.name() : ""; //$NON-NLS-1$
	}
}
