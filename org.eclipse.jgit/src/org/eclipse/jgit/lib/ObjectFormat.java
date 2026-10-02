/*
 * Copyright (C) 2026, Eclipse Foundation, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Hash function a Git repository uses to compute object ids.
 * <p>
 * The object format of a repository is recorded in the config option
 * {@code extensions.objectformat}. Repositories created before Git 2.29
 * don't have this option and implicitly use {@link #SHA_1}.
 *
 * @since 7.9
 */
public enum ObjectFormat {

	/** SHA-1 object format ({@code "sha1"}), the historic default. */
	SHA_1("sha1", "SHA-1", 20, //$NON-NLS-1$ //$NON-NLS-2$
			"4b825dc642cb6eb9a060e54bf8d69288fbee4904", //$NON-NLS-1$
			"e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"), //$NON-NLS-1$

	/** SHA-256 object format ({@code "sha256"}). */
	SHA_256("sha256", "SHA-256", 32, //$NON-NLS-1$ //$NON-NLS-2$
			"6ef19b41225c5369f1c104d45d8d85efa9b057b53b14b4b9b939dd74decc5321", //$NON-NLS-1$
			"473a0f4c3be8a93681a267e3b1e9a7dcda1185436fe141f7749120a303721813"); //$NON-NLS-1$

	private final String configName;

	private final String jcaName;

	private final int rawLength;

	private final String emptyTreeIdHex;

	private final String emptyBlobIdHex;

	private volatile ObjectId emptyTreeId;

	private volatile ObjectId emptyBlobId;

	ObjectFormat(String configName, String jcaName, int rawLength,
			String emptyTreeIdHex, String emptyBlobIdHex) {
		this.configName = configName;
		this.jcaName = jcaName;
		this.rawLength = rawLength;
		this.emptyTreeIdHex = emptyTreeIdHex;
		this.emptyBlobIdHex = emptyBlobIdHex;
	}

	/**
	 * Get the name identifying this object format in the Git config option
	 * {@code extensions.objectformat}.
	 *
	 * @return config name of this object format, e.g. {@code "sha1"} or
	 *         {@code "sha256"}.
	 */
	public String getConfigName() {
		return configName;
	}

	/**
	 * Get the name of the hash function in the Java Cryptography Architecture.
	 *
	 * @return JCA algorithm name, e.g. {@code "SHA-1"} or {@code "SHA-256"}.
	 */
	public String getJcaName() {
		return jcaName;
	}

	/**
	 * Get the length of an object id in bytes.
	 *
	 * @return length of a raw object id in bytes.
	 */
	public int getLength() {
		return rawLength;
	}

	/**
	 * Get the length of an object id as a hexadecimal string.
	 *
	 * @return number of hex digits in a string representation of an object id.
	 */
	public int getHexLength() {
		return rawLength * 2;
	}

	/**
	 * Create a new digest function using this object format's hash function.
	 *
	 * @return a new digest object.
	 * @throws java.lang.RuntimeException
	 *             this Java virtual machine does not support the required hash
	 *             function. Very unlikely given that JGit uses hash functions
	 *             that are in the Java reference specification.
	 */
	public MessageDigest newMessageDigest() {
		try {
			return MessageDigest.getInstance(jcaName);
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException(MessageFormat.format(
					JGitText.get().requiredHashFunctionNotAvailable, jcaName),
					nsae);
		}
	}

	/**
	 * Get the object id of the empty tree object in this object format.
	 *
	 * @return object id of the empty tree.
	 * @since 7.9
	 */
	public ObjectId getEmptyTreeId() {
		ObjectId id = emptyTreeId;
		if (id == null) {
			id = ObjectId.fromString(emptyTreeIdHex);
			emptyTreeId = id;
		}
		return id;
	}

	/**
	 * Get the object id of the empty blob object in this object format.
	 *
	 * @return object id of the empty blob.
	 * @since 7.9
	 */
	public ObjectId getEmptyBlobId() {
		ObjectId id = emptyBlobId;
		if (id == null) {
			id = ObjectId.fromString(emptyBlobIdHex);
			emptyBlobId = id;
		}
		return id;
	}

	/**
	 * Look up the object format identified by the given Git config value.
	 *
	 * @param configName
	 *            value of the Git config option
	 *            {@code extensions.objectformat}.
	 * @return the matching object format, or {@code null} if the name doesn't
	 *         identify a known object format.
	 */
	public static ObjectFormat findByConfigName(String configName) {
		for (ObjectFormat format : values()) {
			if (format.configName.equalsIgnoreCase(configName)) {
				return format;
			}
		}
		return null;
	}
}
