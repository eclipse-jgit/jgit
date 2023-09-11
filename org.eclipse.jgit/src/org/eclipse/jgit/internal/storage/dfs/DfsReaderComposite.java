package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * Reader supporting an alternate object db.
 *
 * Override methods fall back to the alternate object db if they fail on the primary.
 */
class DfsReaderComposite extends DfsReader {

	private final DfsReader alternateReader;

	/**
	 * Initialize a new DfsReader
	 *
	 * @param db
	 *            parent DfsObjDatabase.
	 */
	protected DfsReaderComposite(DfsObjDatabase db, DfsObjDatabase alternate) {
		super(db);
		this.alternateReader = alternate.newReader();
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		Collection<ObjectId> candidates = super.resolve(id);
		if (candidates.isEmpty()) {
			return alternateReader.resolve(id);
		}
		return candidates;
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return super.has(objectId) || alternateReader.has(objectId);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		// open() throws if the object doesn't exist, check first
		if (!super.has(objectId)) {
			return alternateReader.open(objectId, typeHint);
		}
		return super.open(objectId, typeHint);
	}
}
