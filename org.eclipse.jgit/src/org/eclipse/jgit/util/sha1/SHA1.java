/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.sha1;

import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.SystemReader;

/**
 * SHA-1 interface from FIPS 180-1 / RFC 3174 with optional collision detection.
 * Some implementations may not support collision detection.
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc3174">RFC 3174</a>.
 */
public abstract class SHA1 {
	static final boolean USE_NATIVE;

	private static boolean useNative() {
		try {
			return SystemReader.getInstance().getUserConfig().getBoolean(
					ConfigConstants.CONFIG_CORE_SECTION,
					ConfigConstants.SHA1_JDK, false);
		} catch (ConfigInvalidException | IOException e) {
			return false;
		}
	}

	static {
		USE_NATIVE = useNative();
	}
	/**
	 * Create a new context to compute a SHA-1 hash of data.
	 * <p>
	 * If {@code core.sha1JDK = true} in the user level global git configuration
	 * or the system property {@code org.eclipse.jgit.util.sha1.jdk = true} it
	 * will create an object using the implementation in the JDK. Otherwise it
	 * will use the pure Java implementation {@code SHA1} which supports
	 * collision detection but is slower since it's implemented in pure Java.
	 *
	 * @return a new context to compute a SHA-1 hash of data.
	 */
	public static SHA1 newInstance() {
		if (USE_NATIVE
				|| Boolean.getBoolean("org.eclipse.jgit.util.sha1.jdk")) { //$NON-NLS-1$
			return new SHA1Native();
		}
		return new SHA1Java();
	}

	/**
	 * Update the digest computation by adding a byte.
	 *
	 * @param b a byte.
	 */
	public abstract void update(byte b);

	/**
	 * Update the digest computation by adding bytes to the message.
	 *
	 * @param in
	 *            input array of bytes.
	 */
	public abstract void update(byte[] in);

	/**
	 * Update the digest computation by adding bytes to the message.
	 *
	 * @param in
	 *            input array of bytes.
	 * @param p
	 *            offset to start at from {@code in}.
	 * @param len
	 *            number of bytes to hash.
	 */
	public abstract void update(byte[] in, int p, int len);

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the bytes for the resulting hash.
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public abstract byte[] digest() throws Sha1CollisionException;

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the ObjectId for the resulting hash.
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public abstract ObjectId toObjectId() throws Sha1CollisionException;

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @param id
	 *            destination to copy the digest to.
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public abstract void digest(MutableObjectId id)
			throws Sha1CollisionException;

	/**
	 * Reset this instance to compute another hash.
	 *
	 * @return {@code this}.
	 */
	public abstract SHA1 reset();

	/**
	 * Enable likely collision detection.
	 * <p>
	 * Default for implementations supporting collision detection is
	 * {@code true}.
	 * <p>
	 * Implementations not supporting collision detection ignore calls to this
	 * method.
	 *
	 * @param detect
	 *            a boolean.
	 * @return {@code this}
	 */
	public abstract SHA1 setDetectCollision(boolean detect);

	/**
	 * Check if a collision was detected. This method only returns an accurate
	 * result after the digest was obtained through {@link #digest()},
	 * {@link #digest(MutableObjectId)} or {@link #toObjectId()}, as the hashing
	 * function must finish processing to know the final state.
	 * <p>
	 * Implementations not supporting collision detection always return
	 * {@code false}.
	 * <p>
	 *
	 * @return {@code true} if a likely collision was detected.
	 */
	public abstract boolean hasCollision();
}