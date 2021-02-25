/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Creates GPG signatures for Git objects.
 *
 * @since 5.11
 */
public interface GpgObjectSigner {

	/**
	 * Signs the specified object.
	 *
	 * <p>
	 * Implementors should obtain the payload for signing from the specified
	 * object via {@link ObjectBuilder#build()} and create a proper
	 * {@link GpgSignature}. The generated signature must be set on the
	 * specified {@code object} (see
	 * {@link ObjectBuilder#setGpgSignature(GpgSignature)}).
	 * </p>
	 * <p>
	 * Any existing signature on the object must be discarded prior obtaining
	 * the payload via {@link ObjectBuilder#build()}.
	 * </p>
	 *
	 * @param object
	 *            the object to sign (must not be {@code null} and must be
	 *            complete to allow proper calculation of payload)
	 * @param gpgSigningKey
	 *            the signing key to locate (passed as is to the GPG signing
	 *            tool as is; eg., value of <code>user.signingkey</code>)
	 * @param committer
	 *            the signing identity (to help with key lookup in case signing
	 *            key is not specified)
	 * @param credentialsProvider
	 *            provider to use when querying for signing key credentials (eg.
	 *            passphrase)
	 * @param config
	 *            GPG settings from the git config
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 * @throws UnsupportedSigningFormatException
	 *             if a config is given and the wanted key format is not
	 *             supported
	 */
	void signObject(@NonNull ObjectBuilder object,
			@Nullable String gpgSigningKey, @NonNull PersonIdent committer,
			CredentialsProvider credentialsProvider, GpgConfig config)
			throws CanceledException, UnsupportedSigningFormatException;

	/**
	 * Indicates if a signing key is available for the specified committer
	 * and/or signing key.
	 *
	 * @param gpgSigningKey
	 *            the signing key to locate (passed as is to the GPG signing
	 *            tool as is; eg., value of <code>user.signingkey</code>)
	 * @param committer
	 *            the signing identity (to help with key lookup in case signing
	 *            key is not specified)
	 * @param credentialsProvider
	 *            provider to use when querying for signing key credentials (eg.
	 *            passphrase)
	 * @param config
	 *            GPG settings from the git config
	 * @return <code>true</code> if a signing key is available,
	 *         <code>false</code> otherwise
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 * @throws UnsupportedSigningFormatException
	 *             if a config is given and the wanted key format is not
	 *             supported
	 */
	public abstract boolean canLocateSigningKey(@Nullable String gpgSigningKey,
			@NonNull PersonIdent committer,
			CredentialsProvider credentialsProvider, GpgConfig config)
			throws CanceledException, UnsupportedSigningFormatException;

}
