package org.eclipse.jgit.lib;

import org.eclipse.jgit.errors.CorruptObjectException;

/**
 * Verifies that a blob object is a valid object.
 * <p>
 * Unlike trees, commits and tags, there's no validity of blobs. Implementers
 * can optionally implement this blob checker to reject certain blobs.
 */
public interface BlobObjectChecker {
	/** No-op implementation of {@link BlobObjectChecker}. */
	public static final BlobObjectChecker NULL_CHECKER =
			new BlobObjectChecker() {
				@Override
				public void update(byte[] in, int p, int len) {
					// Empty implementation.
				}

				@Override
				public void endBlob(AnyObjectId id) {
					// Empty implementation.
				}
			};

	/**
	 * Check a new fragment of the blob.
	 *
	 * @param in
	 *            input array of bytes.
	 * @param p
	 *            offset to start at from {@code in}.
	 * @param len
	 *            number of bytes from {@code p}.
	 */
	void update(byte[] in, int p, int len);

	/**
	 * Finalize the blob checking.
	 *
	 * @param id
	 *            identity of the object being checked.
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	void endBlob(AnyObjectId id) throws CorruptObjectException;
}
