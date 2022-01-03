/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
 */
@SuppressWarnings("nls")
public final class Constants {
	/**
	 * lfs folder/section/filter name
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
	 * Prefix for all LFS related filters.
	 *
	 * @since 4.11
	 */
	public static final String ATTR_FILTER_DRIVER_PREFIX = "lfs/";

	/**
	 * Config file name for lfs specific configuration
	 */
	public static final String DOT_LFS_CONFIG = ".lfsconfig";

	/**
	 * Create a new digest function for objects.
	 *
	 * @return a new digest object.
	 * @throws java.lang.RuntimeException
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
	 * Content type used by LFS REST API as defined in <a href=
	 * "https://github.com/github/git-lfs/blob/master/docs/api/v1/http-v1-batch.md">
	 * https://github.com/github/git-lfs/blob/master/docs/api/v1/http-v1-batch.md</a>
	 */
	public static final String CONTENT_TYPE_GIT_LFS_JSON = "application/vnd.git-lfs+json";

	/**
	 * "Arbitrary binary data" as defined in
	 * <a href="https://www.ietf.org/rfc/rfc2046.txt">RFC 2046</a>
	 */
	public static final String HDR_APPLICATION_OCTET_STREAM = "application/octet-stream";
}
