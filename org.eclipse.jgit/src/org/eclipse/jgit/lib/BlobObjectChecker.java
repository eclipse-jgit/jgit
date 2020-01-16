/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.errors.CorruptObjectException;

/**
 * Verifies that a blob object is a valid object.
 * <p>
 * Unlike trees, commits and tags, there's no validity of blobs. Implementers
 * can optionally implement this blob checker to reject certain blobs.
 *
 * @since 4.9
 */
public interface BlobObjectChecker {
	/** No-op implementation of {@link BlobObjectChecker}. */
	BlobObjectChecker NULL_CHECKER =
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
	 * @param offset
	 *            offset to start at from {@code in}.
	 * @param len
	 *            length of the fragment to check.
	 */
	void update(byte[] in, int offset, int len);

	/**
	 * Finalize the blob checking.
	 *
	 * @param id
	 *            identity of the object being checked.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             if any error was detected.
	 */
	void endBlob(AnyObjectId id) throws CorruptObjectException;
}
