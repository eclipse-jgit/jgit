/*-
 * Copyright (C) 2019, Salesforce.
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
package org.eclipse.jgit.lib.internal;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.text.MessageFormat;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.CredentialItem.CharArrayType;
import org.eclipse.jgit.transport.CredentialItem.InformationalMessage;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Prompts for a passphrase and caches it until {@link #clear() cleared}.
 * <p>
 * Implements {@link AutoCloseable} so it can be used within a
 * try-with-resources block.
 * </p>
 */
class BouncyCastleGpgKeyPassphrasePrompt implements AutoCloseable {

	private CharArrayType passphrase;

	private CredentialsProvider credentialsProvider;

	public BouncyCastleGpgKeyPassphrasePrompt(
			CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
	}

	/**
	 * Clears any cached passphrase
	 */
	public void clear() {
		if (passphrase != null) {
			passphrase.clear();
			passphrase = null;
		}
	}

	@Override
	public void close() {
		clear();
	}

	private URIish createURI(Path keyLocation) throws URISyntaxException {
		return new URIish(keyLocation.toUri().toString());
	}

	/**
	 * Prompts use for a passphrase unless one was cached from a previous
	 * prompt.
	 *
	 * @param keyFingerprint
	 *            the fingerprint to show to the user during prompting
	 * @param keyLocation
	 *            the location the key was loaded from
	 * @return the passphrase (maybe <code>null</code>)
	 * @throws PGPException
	 * @throws CanceledException
	 *             in case passphrase was not entered by user
	 * @throws URISyntaxException
	 * @throws UnsupportedCredentialItem
	 */
	public char[] getPassphrase(byte[] keyFingerprint, Path keyLocation)
			throws PGPException, CanceledException, UnsupportedCredentialItem,
			URISyntaxException {
		if (passphrase == null) {
			passphrase = new CharArrayType(JGitText.get().credentialPassphrase,
					true);
		}

		if (credentialsProvider == null) {
			throw new PGPException(JGitText.get().gpgNoCredentialsProvider);
		}

		if (passphrase.getValue() == null
				&& !credentialsProvider.get(createURI(keyLocation),
						new InformationalMessage(
								MessageFormat.format(JGitText.get().gpgKeyInfo,
										Hex.toHexString(keyFingerprint))),
						passphrase)) {
			throw new CanceledException(JGitText.get().gpgSigningCancelled);
		}
		return passphrase.getValue();
	}

}
