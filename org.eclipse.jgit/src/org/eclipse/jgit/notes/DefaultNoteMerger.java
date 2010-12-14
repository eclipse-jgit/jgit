package org.eclipse.jgit.notes;

import java.io.IOException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.io.UnionInputStream;

/**
 * Default note merger.
 * <p>
 * If ours and theirs are both edits then this merger will simply join the
 * content of ours and theirs, in that order, and return that as the merge
 * result.
 * <p>
 * If one of ours/theirs is deletion then the other must be an edit and in this
 * case merge result will be the edit note.
 */
class DefaultNoteMerger implements NoteMerger {

	private ObjectReader reader;

	private ObjectInserter inserter;

	/**
	 * Constructs a DefaultNoteMerger
	 *
	 * @param reader
	 * @param inserter
	 */
	DefaultNoteMerger(ObjectReader reader, ObjectInserter inserter) {
		this.reader = reader;
		this.inserter = inserter;
	}

	public Note merge(Note base, Note ours, Note theirs)
			throws MissingObjectException, IOException {
		if (ours == null)
			return theirs;

		if (theirs == null)
			return ours;

		if (ours.getData().equals(theirs.getData())) {
			return ours;
		}

		ObjectLoader lo = reader.open(ours.getData());
		ObjectLoader lt = reader.open(theirs.getData());
		UnionInputStream union = new UnionInputStream(lo.openStream(),
				lt.openStream());
		ObjectId noteData = inserter.insert(Constants.OBJ_BLOB,
				lo.getSize() + lt.getSize(), union);
		inserter.flush();
		return new Note(ours, noteData);
	}
}
