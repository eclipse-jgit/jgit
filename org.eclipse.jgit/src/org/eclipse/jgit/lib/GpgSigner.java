/*
 * Copyright (C) 2018, 2022 Salesforce and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates GPG signatures for Git objects.
 *
 * @since 5.3
 */
public abstract class GpgSigner {

	private static final Logger LOG = LoggerFactory.getLogger(GpgSigner.class);

	private static class DefaultSigner {

		private static volatile GpgSigner defaultSigner = loadGpgSigner();

		private static GpgSigner loadGpgSigner() {
			try {
				ServiceLoader<GpgSigner> loader = ServiceLoader
						.load(GpgSigner.class);
				Iterator<GpgSigner> iter = loader.iterator();
				if (iter.hasNext()) {
					return iter.next();
				}
			} catch (ServiceConfigurationError e) {
				LOG.error(e.getMessage(), e);
			}
			return null;
		}

		private DefaultSigner() {
			// No instantiation
		}

		public static GpgSigner getDefault() {
			return defaultSigner;
		}

		public static void setDefault(GpgSigner signer) {
			defaultSigner = signer;
		}
	}

	/**
	 * Get the default signer, or <code>null</code>.
	 *
	 * @return the default signer, or <code>null</code>.
	 */
	public static GpgSigner getDefault() {
		return DefaultSigner.getDefault();
	}

	/**
	 * Set the default signer.
	 *
	 * @param signer
	 *            the new default signer, may be <code>null</code> to select no
	 *            default.
	 */
	public static void setDefault(GpgSigner signer) {
		DefaultSigner.setDefault(signer);
	}

	/**
	 * Signs the specified commit.
	 *
	 * <p>
	 * Implementors should obtain the payload for signing from the specified
	 * commit via {@link CommitBuilder#build()} and create a proper
	 * {@link GpgSignature}. The generated signature must be set on the
	 * specified {@code commit} (see
	 * {@link CommitBuilder#setGpgSignature(GpgSignature)}).
	 * </p>
	 * <p>
	 * Any existing signature on the commit must be discarded prior obtaining
	 * the payload via {@link CommitBuilder#build()}.
	 * </p>
	 *
	 * @param commit
	 *            the commit to sign (must not be <code>null</code> and must be
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
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 */
	public abstract void sign(@NonNull CommitBuilder commit,
			@Nullable String gpgSigningKey, @NonNull PersonIdent committer,
			CredentialsProvider credentialsProvider) throws CanceledException;

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
	 * @return <code>true</code> if a signing key is available,
	 *         <code>false</code> otherwise
	 * @throws CanceledException
	 *             when signing was canceled (eg., user aborted when entering
	 *             passphrase)
	 */
	public abstract boolean canLocateSigningKey(@Nullable String gpgSigningKey,
			@NonNull PersonIdent committer,
			CredentialsProvider credentialsProvider) throws CanceledException;

}
