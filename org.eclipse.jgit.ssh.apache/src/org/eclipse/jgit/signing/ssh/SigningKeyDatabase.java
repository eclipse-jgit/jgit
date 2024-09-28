/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing.ssh;

import java.io.IOException;
import java.security.PublicKey;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.signing.ssh.SigningDatabase;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * A database storing meta-information about signing keys and certificates.
 *
 * @since 7.1
 */
public interface SigningKeyDatabase {

	/**
	 * Obtains the current global instance.
	 *
	 * @return the global {@link SigningKeyDatabase}
	 */
	static SigningKeyDatabase getInstance() {
		return SigningDatabase.getInstance();
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
	static SigningKeyDatabase setInstance(SigningKeyDatabase database) {
		return SigningDatabase.setInstance(database);
	}

	/**
	 * Determines whether the gives key has been revoked.
	 *
	 * @param repository
	 *            {@link Repository} the key is being used in
	 * @param config
	 *            {@link GpgConfig} to use
	 * @param key
	 *            {@link PublicKey} to check
	 * @return {@code true} if the key has been revoked, {@code false} otherwise
	 * @throws IOException
	 *             if an I/O problem occurred
	 */
	boolean isRevoked(@NonNull Repository repository, @NonNull GpgConfig config,
			@NonNull PublicKey key) throws IOException;

	/**
	 * Checks whether the given key is allowed to be used for signing, and if
	 * allowed returns the principal.
	 *
	 * @param repository
	 *            {@link Repository} the key is being used in
	 * @param config
	 *            {@link GpgConfig} to use
	 * @param key
	 *            {@link PublicKey} to check
	 * @param namespace
	 *            of the signature
	 * @param ident
	 *            optional {@link PersonIdent} giving a signer's e-mail address
	 *            and a signature time
	 * @return {@code null} if the database does not contain any information
	 *         about the given key; the principal if it does and all checks
	 *         passed
	 * @throws IOException
	 *             if an I/O problem occurred
	 * @throws VerificationException
	 *             if the database contains information about the key and the
	 *             checks determined that the key is not allowed to be used for
	 *             signing
	 */
	String isAllowed(@NonNull Repository repository, @NonNull GpgConfig config,
			@NonNull PublicKey key, @NonNull String namespace,
			PersonIdent ident) throws IOException, VerificationException;
}
