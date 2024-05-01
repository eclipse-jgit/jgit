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
import java.security.MessageDigest;

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
	/**
	 * SHA1 implementations available in JGit
	 *
	 * @since 5.13.2
	 */
	public enum Sha1Implementation {
		/**
		 * {@link SHA1Java} implemented in Java, supports collision detection.
		 */
		JAVA(SHA1Java.class),
		/**
		 * Native implementation based on JDK's {@link MessageDigest}.
		 */
		JDKNATIVE(SHA1Native.class);

		private final String implClassName;

		private Sha1Implementation(Class implClass) {
			this.implClassName = implClass.getName();
		}

		@Override
		public String toString() {
			return implClassName;
		}
	}

	private static final Sha1Implementation SHA1_IMPLEMENTATION = fromConfig();

	private static Sha1Implementation fromConfig() {
		try {
			return SystemReader.getInstance().getUserConfig().getEnum(
					ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.SHA1_IMPLEMENTATION,
					Sha1Implementation.JAVA);
		} catch (ConfigInvalidException | IOException e) {
			return Sha1Implementation.JAVA;
		}
	}

	private static Sha1Implementation getImplementation() {
		String fromSystemProperty = System
				.getProperty("org.eclipse.jgit.util.sha1.implementation"); //$NON-NLS-1$
		if (fromSystemProperty == null) {
			return SHA1_IMPLEMENTATION;
		}
		if (fromSystemProperty
				.equalsIgnoreCase(Sha1Implementation.JAVA.name())) {
			return Sha1Implementation.JAVA;
		}
		if (fromSystemProperty
				.equalsIgnoreCase(Sha1Implementation.JDKNATIVE.name())) {
			return Sha1Implementation.JDKNATIVE;
		}
		return SHA1_IMPLEMENTATION;
	}

	/**
	 * Create a new context to compute a SHA-1 hash of data.
	 * <p>
	 * If {@code core.sha1Implementation = jdkNative} in the user level global
	 * git configuration or the system property
	 * {@code org.eclipse.jgit.util.sha1.implementation = jdkNative} it will
	 * create an object using the implementation in the JDK. If both are set the
	 * system property takes precedence. Otherwise the pure Java implementation
	 * will be used which supports collision detection but is slower.
	 *
	 * @return a new context to compute a SHA-1 hash of data.
	 */
	public static SHA1 newInstance() {
		if (getImplementation() == Sha1Implementation.JDKNATIVE) {
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
	 *
	 * @return {@code true} if a likely collision was detected.
	 */
	public abstract boolean hasCollision();
}