/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Creates signatures for Git objects.
 *
 * @since 7.0
 */
public interface Signer {

	/**
	 * Signs the specified object.
	 *
	 * <p>
	 * Implementors should obtain the payload for signing from the specified
	 * object via {@link ObjectBuilder#build()} and create a proper
	 * {@link GpgSignature}. The generated signature is set on the specified
	 * {@code object} (see {@link ObjectBuilder#setGpgSignature(GpgSignature)}).
	 * </p>
	 * <p>
	 * Any existing signature on the object must be discarded prior obtaining
	 * the payload via {@link ObjectBuilder#build()}.
	 * </p>
	 *
	 * @param repository
	 *            {@link Repository} the object belongs to
	 * @param config
	 *            GPG settings from the git config
	 * @param object
	 *            the object to sign (must not be {@code null} and must be
	 *            complete to allow proper calculation of payload)
	 * @param committer
	 *            the signing identity (to help with key lookup in case signing
	 *            key is not specified)
	 * @param signingKey
	 *            if non-{@code null} overrides the signing key from the config
	 * @param credentialsProvider
	 *            provider to use when querying for signing key credentials (eg.
	 *            passphrase)
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws UnsupportedSigningFormatException
	 *             if a config is given and the wanted key format is not
	 *             supported
	 */
	default void signObject(@NonNull Repository repository,
			@NonNull GpgConfig config, @NonNull ObjectBuilder object,
			@NonNull PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider)
			throws CanceledException, IOException,
			UnsupportedSigningFormatException {
		try {
			object.setGpgSignature(sign(repository, config, object.build(),
					committer, signingKey, credentialsProvider));
		} catch (UnsupportedEncodingException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Signs arbitrary data.
	 *
	 * @param repository
	 *            {@link Repository} the signature is created in
	 * @param config
	 *            GPG settings from the git config
	 * @param data
	 *            the data to sign
	 * @param committer
	 *            the signing identity (to help with key lookup in case signing
	 *            key is not specified)
	 * @param signingKey
	 *            if non-{@code null} overrides the signing key from the config
	 * @param credentialsProvider
	 *            provider to use when querying for signing key credentials (eg.
	 *            passphrase)
	 * @return the signature for {@code data}
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws UnsupportedSigningFormatException
	 *             if a config is given and the wanted key format is not
	 *             supported
	 */
	GpgSignature sign(@NonNull Repository repository, @NonNull GpgConfig config,
			byte[] data, @NonNull PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider) throws CanceledException,
			IOException, UnsupportedSigningFormatException;

	/**
	 * Indicates if a signing key is available for the specified committer
	 * and/or signing key.
	 *
	 * @param repository
	 *            the current {@link Repository}
	 * @param config
	 *            GPG settings from the git config
	 * @param committer
	 *            the signing identity (to help with key lookup in case signing
	 *            key is not specified)
	 * @param signingKey
	 *            if non-{@code null} overrides the signing key from the config
	 * @param credentialsProvider
	 *            provider to use when querying for signing key credentials (eg.
	 *            passphrase)
	 * @return {@code true} if a signing key is available, {@code false}
	 *         otherwise
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 */
	boolean canLocateSigningKey(@NonNull Repository repository,
			@NonNull GpgConfig config, @NonNull PersonIdent committer,
			String signingKey, CredentialsProvider credentialsProvider)
			throws CanceledException;

}
