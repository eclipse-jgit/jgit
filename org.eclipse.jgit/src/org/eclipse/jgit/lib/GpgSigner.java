/*
 * Copyright (C) 2018, Salesforce.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.lib;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.internal.BouncyCastleGpgSigner;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Creates GPG signatures for Git objects.
 *
 * @since 5.3
 */
public abstract class GpgSigner {

	private static GpgSigner defaultSigner = new BouncyCastleGpgSigner();

	/**
	 * Get the default signer, or <code>null</code>.
	 *
	 * @return the default signer, or <code>null</code>.
	 */
	public static GpgSigner getDefault() {
		return defaultSigner;
	}

	/**
	 * Set the default signer.
	 *
	 * @param signer
	 *            the new default signer, may be <code>null</code> to select no
	 *            default.
	 */
	public static void setDefault(GpgSigner signer) {
		GpgSigner.defaultSigner = signer;
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
