/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import org.eclipse.jgit.signing.ssh.CachingSigningKeyDatabase;
import org.eclipse.jgit.signing.ssh.SigningKeyDatabase;

/**
 * A global {@link SigningKeyDatabase} instance.
 */
public final class SigningDatabase {

	private static SigningKeyDatabase INSTANCE = new OpenSshSigningKeyDatabase();

	private SigningDatabase() {
		// No instantiation
	}

	/**
	 * Obtains the current instance.
	 *
	 * @return the global {@link SigningKeyDatabase}
	 */
	public static synchronized SigningKeyDatabase getInstance() {
		return INSTANCE;
	}

	/**
	 * Sets the global {@link SigningKeyDatabase}.
	 *
	 * @param database
	 *            to set; if {@code null} a default database using the OpenSSH
	 *            allowed signers file and the OpenSSH revocation list mechanism
	 *            is used.
	 * @return the previously set {@link SigningKeyDatabase}
	 */
	public static synchronized SigningKeyDatabase setInstance(
			SigningKeyDatabase database) {
		SigningKeyDatabase previous = INSTANCE;
		if (database != INSTANCE) {
			if (INSTANCE instanceof CachingSigningKeyDatabase caching) {
				caching.clearCache();
			}
			if (database == null) {
				INSTANCE = new OpenSshSigningKeyDatabase();
			} else {
				INSTANCE = database;
			}
		}
		return previous;
	}
}
