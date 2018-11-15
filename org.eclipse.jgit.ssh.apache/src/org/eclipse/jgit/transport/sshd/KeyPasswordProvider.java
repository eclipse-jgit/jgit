/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.transport.sshd;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.eclipse.jgit.transport.URIish;

/**
 * A {@code KeyPasswordProvider} provides passwords for encrypted private keys.
 *
 * @since 5.2
 */
public interface KeyPasswordProvider {

	/**
	 * Obtains a passphrase to use to decrypt an ecrypted private key. Returning
	 * {@code null} or an empty array will skip this key. To cancel completely,
	 * the operation should raise
	 * {@link java.util.concurrent.CancellationException}.
	 *
	 * @param uri
	 *            identifying the key resource that is being attempted to be
	 *            loaded
	 * @param attempt
	 *            the number of previous attempts to get a passphrase; >= 0
	 * @return the passphrase
	 * @throws IOException
	 *             if no password can be obtained
	 */
	char[] getPassphrase(URIish uri, int attempt) throws IOException;

	/**
	 * Define the maximum number of attempts to get a passphrase that should be
	 * attempted for one identity resource through this provider.
	 *
	 * @param maxNumberOfAttempts
	 *            number of times to ask for a passphrase;
	 *            {@link IllegalArgumentException} may be thrown if <= 0
	 */
	void setAttempts(int maxNumberOfAttempts);

	/**
	 * Gets the maximum number of attempts to get a passphrase that should be
	 * attempted for one identity resource through this provider. The default
	 * return 1.
	 *
	 * @return the number of times to ask for a passphrase; should be >= 1.
	 */
	default int getAttempts() {
		return 1;
	}

	/**
	 * Invoked after a key has been loaded. If this raises an exception, the
	 * original {@code error} is lost unless it is attached to that exception.
	 *
	 * @param uri
	 *            identifying the key resource the key was attempted to be
	 *            loaded from
	 * @param attempt
	 *            the number of times {@link #getPassphrase(URIish, int)} had
	 *            been called; zero indicates that {@code uri} refers to a
	 *            non-encrypted key
	 * @param error
	 *            {@code null} if the key was loaded successfully; otherwise an
	 *            exception indicating why the key could not be loaded
	 * @return {@code true} to re-try again; {@code false} to re-raise the
	 *         {@code error} exception; Ignored if the key was loaded
	 *         successfully, i.e., if {@code error == null}.
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	boolean keyLoaded(URIish uri, int attempt, Exception error)
			throws IOException, GeneralSecurityException;
}
