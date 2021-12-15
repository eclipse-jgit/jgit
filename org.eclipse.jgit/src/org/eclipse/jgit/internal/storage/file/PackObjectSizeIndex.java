package org.eclipse.jgit.internal.storage.file;

/**
 * Index of object sizes in a pack
 *
 * It is not guaranteed that the implementation contains the sizes of all
 * objects (e.g. it could store only objects over certain threshold) nor the
 * specific size of an object (e.g. it could store buckets of size ranges).
 *
 * Operators must choose an implementation that suits their precision
 * requirements.
 */
public interface PackObjectSizeIndex {

	/**
	 * Return size of the object in the index.
	 *
	 * @param offset
	 *            position in the pack (as returned from PackIndex)
	 * @return size of the offset in the index, -1 if not found.
	 */
	long getSize(long offset);

	/**
	 * Number of objects in the index
	 *
	 * @return number of objects in the index
	 */
	long getObjectCount();
}
