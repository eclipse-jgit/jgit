/*
 * Copyright (C) 2017, Google Inc.
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
	 * @throws CorruptObjectException
	 *             if any error was detected.
	 */
	void endBlob(AnyObjectId id) throws CorruptObjectException;
}
