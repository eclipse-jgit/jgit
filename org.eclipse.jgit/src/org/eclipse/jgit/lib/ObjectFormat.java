/*
 * Copyright (C) 2026, Google LLC and others
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

/**
 * The hash algorithms Git uses to name objects.
 * <p>
 * A repository uses one object format, set in its configuration as
 * {@code extensions.objectFormat}; a repository without it uses SHA-1. There is
 * deliberately no process-wide default: the format is a property of the
 * repository.
 *
 * @since 8.0
 */
public enum ObjectFormat {
	/** SHA-1 hash format. */
	SHA1("sha1", //$NON-NLS-1$
			"SHA-1", //$NON-NLS-1$
			20, 1, "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", //$NON-NLS-1$
			"4b825dc642cb6eb9a060e54bf8d69288fbee4904"), //$NON-NLS-1$

	/** SHA-256 hash format. */
	SHA256("sha256", //$NON-NLS-1$
			"SHA-256", //$NON-NLS-1$
			32, 2,
			"473a0f4c3be8a93681a267e3b1e9a7dcda1185436fe141f7749120a303721813", //$NON-NLS-1$
			"6ef19b41225c5369f1c104d45d8d85efa9b057b53b14b4b9b939dd74decc5321"); //$NON-NLS-1$

	private final String key;

	private final String algorithmName;

	private final int numBytes;

	private final int algorithmIndex;

	private final String emptyBlobId;

	private final String emptyTreeId;

	ObjectFormat(String key, String algorithmName, int numBytes,
			int algorithmIndex, String emptyBlobId, String emptyTreeId) {
		this.key = key;
		this.algorithmName = algorithmName;
		this.numBytes = numBytes;
		this.algorithmIndex = algorithmIndex;
		this.emptyBlobId = emptyBlobId;
		this.emptyTreeId = emptyTreeId;
	}

	/**
	 * Get the id of the empty blob in this format.
	 *
	 * @return the id of the empty blob
	 */
	public ObjectId getEmptyBlobId() {
		return ObjectId.fromString(emptyBlobId);
	}

	/**
	 * Get the id of the empty tree in this format.
	 *
	 * @return the id of the empty tree
	 */
	public ObjectId getEmptyTreeId() {
		return ObjectId.fromString(emptyTreeId);
	}

	/**
	 * Get the all-zeros id of this format, which Git uses where there is no
	 * object, such as the old id of a ref that is being created.
	 *
	 * @return the all-zeros id
	 */
	public ObjectId zeroId() {
		return this == SHA1 ? ObjectId.zeroId() : Sha256ZeroId.ID;
	}

	/**
	 * Holds the SHA-256 all-zeros id, parsed once on first use so that
	 * {@link ObjectFormat#zeroId()} does not allocate.
	 */
	private static final class Sha256ZeroId {
		static final ObjectId ID = ObjectId
				.fromString("0".repeat(SHA256.getStringLength())); //$NON-NLS-1$
	}

	/**
	 * Get the name of this format in Git configuration.
	 *
	 * @return {@code "sha1"} or {@code "sha256"}, as in
	 *         {@code extensions.objectFormat}
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Get the number of hex digits in an object id of this format.
	 *
	 * @return 40 for SHA-1, 64 for SHA-256
	 */
	public int getStringLength() {
		return numBytes * 2;
	}

	/**
	 * Get the number of bytes in an object id of this format.
	 *
	 * @return 20 for SHA-1, 32 for SHA-256
	 */
	public int getNumBytes() {
		return numBytes;
	}

	/**
	 * Get the hash function id Git writes in commit-graph, multi-pack-index and
	 * reverse index headers.
	 *
	 * @return 1 for SHA-1, 2 for SHA-256
	 */
	public int getAlgorithmIndex() {
		return algorithmIndex;
	}

	/**
	 * Create a standard digest for this format's hash algorithm.
	 * <p>
	 * Use it for checksums, such as pack and index trailers. Compute object
	 * names with {@link #newObjectDigest()}, which detects SHA-1 collision
	 * attacks.
	 *
	 * @return a new digest
	 */
	public MessageDigest newMessageDigest() {
		try {
			return MessageDigest.getInstance(algorithmName);
		} catch (NoSuchAlgorithmException e) {
			throw new LinkageError("JVM does not support " + algorithmName, e); //$NON-NLS-1$
		}
	}

	/**
	 * Create a digest for computing object names in this format.
	 * <p>
	 * For SHA-1 this is JGit's collision-detecting
	 * {@link org.eclipse.jgit.util.sha1.SHA1} (SHA-1DC):
	 * {@link MessageDigest#digest()} throws
	 * {@link org.eclipse.jgit.util.sha1.Sha1CollisionException} if the input
	 * contains a known collision attack. SHA-256 uses the standard digest.
	 *
	 * @return a new digest for object names in this format
	 */
	public MessageDigest newObjectDigest() {
		if (this == SHA1) {
			return new CollisionDetectingSha1();
		}
		return newMessageDigest();
	}

	/**
	 * Adapts the collision-detecting {@link org.eclipse.jgit.util.sha1.SHA1} to
	 * {@link MessageDigest}.
	 */
	private static final class CollisionDetectingSha1 extends MessageDigest {
		// Fully qualified: inside this enum, SHA1 names the constant.
		private final org.eclipse.jgit.util.sha1.SHA1 sha1 = org.eclipse.jgit.util.sha1.SHA1
				.newInstance();

		CollisionDetectingSha1() {
			super("SHA-1"); //$NON-NLS-1$
		}

		@Override
		protected int engineGetDigestLength() {
			return SHA1.getNumBytes();
		}

		@Override
		protected void engineUpdate(byte input) {
			sha1.update(input);
		}

		@Override
		protected void engineUpdate(byte[] input, int offset, int len) {
			sha1.update(input, offset, len);
		}

		@Override
		protected byte[] engineDigest() {
			try {
				return sha1.digest();
			} finally {
				sha1.reset();
			}
		}

		@Override
		protected void engineReset() {
			sha1.reset();
		}
	}

	/**
	 * Look up the format whose hex representation has the given length.
	 *
	 * @param len
	 *            number of hex digits in a complete id
	 * @return the format with ids of that length, or {@code null} if none
	 */
	public static ObjectFormat forStringLength(int len) {
		for (ObjectFormat f : values()) {
			if (f.getStringLength() == len) {
				return f;
			}
		}
		return null;
	}

	/**
	 * Look up a format by its name in Git configuration, ignoring case.
	 *
	 * @param name
	 *            the name, such as the value of {@code extensions.objectFormat}
	 * @return the format with that name
	 * @throws IllegalArgumentException
	 *             if {@code name} is not a recognized object format
	 */
	public static ObjectFormat fromKey(String name) {
		for (ObjectFormat f : values()) {
			if (f.key.equalsIgnoreCase(name)) {
				return f;
			}
		}
		throw new IllegalArgumentException(
				String.format("Unknown object format %s", name)); //$NON-NLS-1$
	}
}
