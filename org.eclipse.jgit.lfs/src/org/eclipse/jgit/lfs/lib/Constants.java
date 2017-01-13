/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.lib;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Misc. constants used throughout JGit LFS extension.
 *
 * @since 4.3
 **/
@SuppressWarnings("nls")
public final class Constants {
	/**
	 * lfs folder
	 *
	 * @since 4.6
	 */
	public static final String LFS = "lfs";

	/**
	 * Hash function used natively by Git LFS extension for large objects.
	 *
	 * @since 4.6
	 */
	public static final String LONG_HASH_FUNCTION = "SHA-256";

	/**
	 * A Git LFS large object hash is 256 bits, i.e. 32 bytes.
	 * <p>
	 * Changing this assumption is not going to be as easy as changing this
	 * declaration.
	 */
	public static final int LONG_OBJECT_ID_LENGTH = 32;

	/**
	 * A Git LFS large object can be expressed as a 64 character string of
	 * hexadecimal digits.
	 *
	 * @see #LONG_OBJECT_ID_LENGTH
	 */
	public static final int LONG_OBJECT_ID_STRING_LENGTH = LONG_OBJECT_ID_LENGTH
			* 2;

	/**
	 * LFS upload operation.
	 *
	 * @since 4.7
	 */
	public static final String UPLOAD = "upload";

	/**
	 * LFS download operation.
	 *
	 * @since 4.7
	 */
	public static final String DOWNLOAD = "download";

	/**
	 * LFS verify operation.
	 *
	 * @since 4.7
	 */
	public static final String VERIFY = "verify";

	/**
	 * Create a new digest function for objects.
	 *
	 * @return a new digest object.
	 * @throws RuntimeException
	 *             this Java virtual machine does not support the required hash
	 *             function. Very unlikely given that JGit uses a hash function
	 *             that is in the Java reference specification.
	 */
	public static MessageDigest newMessageDigest() {
		try {
			return MessageDigest.getInstance(LONG_HASH_FUNCTION);
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException(MessageFormat.format(
					LfsText.get().requiredHashFunctionNotAvailable,
					LONG_HASH_FUNCTION), nsae);
		}
	}

	static {
		if (LONG_OBJECT_ID_LENGTH != newMessageDigest().getDigestLength())
			throw new LinkageError(
					LfsText.get().incorrectLONG_OBJECT_ID_LENGTH);
	}

	/**
	 * Content type used by LFS REST API as defined in
	 * {@link "https://github.com/github/git-lfs/blob/master/docs/api/v1/http-v1-batch.md"}
	 */
	public static final String CONTENT_TYPE_GIT_LFS_JSON = "application/vnd.git-lfs+json";

	/**
	 * "arbitrary binary data" as defined in RFC 2046
	 * {@link "https://www.ietf.org/rfc/rfc2046.txt"}
	 */
	public static final String HDR_APPLICATION_OCTET_STREAM = "application/octet-stream";
}
