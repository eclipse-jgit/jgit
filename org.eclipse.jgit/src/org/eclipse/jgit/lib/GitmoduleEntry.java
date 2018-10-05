package org.eclipse.jgit.lib.internal;

import org.eclipse.jgit.lib.AnyObjectId;

/**
 * A .gitmodules file found in the pack. Store the blob of the file itself
 * (e.g. to access its contents) and the tree where it was found (e.g. to
 * check if it is in the root)
 */
public final class GitmoduleEntry {
	private final AnyObjectId treeId;

	private final AnyObjectId blobId;

	/**
	 * A record of (tree, blob) for a .gitmodule file in a pack
	 *
	 * @param treeId
	 *            tree id containing a .gitmodules entry
	 * @param blobId
	 *            id of the blob of the .gitmodules file
	 */
	public GitmoduleEntry(AnyObjectId treeId, AnyObjectId blobId) {
		// AnyObjectId's are reused, must keep a copy.
		this.treeId = treeId.copy();
		this.blobId = blobId.copy();
	}

	/**
	 * @return Id of a .gitmodules file found in the pack
	 */
	public AnyObjectId getBlobId() {
		return blobId;
	}

	/**
	 * @return Id of a tree object where the .gitmodules file was found
	 */
	public AnyObjectId getTreeId() {
		return treeId;
	}
}