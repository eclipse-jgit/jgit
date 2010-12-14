package org.eclipse.jgit.notes;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;

/**
 * This exception will be thrown from the {@link NoteMerger} when a conflict on
 * Notes content is found during merge.
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
				base.getName(), ours.getName(), theirs.getName()));
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
		super(MessageFormat.format(JGitText.get().mergeConflictOnNonNoteEntries,
				base, ours, theirs));
	}

}
