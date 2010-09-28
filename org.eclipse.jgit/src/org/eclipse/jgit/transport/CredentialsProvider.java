/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
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
package org.eclipse.jgit.transport;

import java.util.Arrays;
import java.util.Set;

import org.eclipse.jgit.errors.UnsupportedCredentialType;

/**
 * Implementors can provide credentials for use in connecting to Git
 * repositories.
 */
public abstract class CredentialsProvider {
	/**
	 * This enum defines the types of information which can provided by the
	 * concrete {@link CredentialsProvider}
	 */
	public enum CredentialType {
		/**
		 * The username which is a String
		 */
		USERNAME,

		/**
		 * The password which is a String
		 */
		PASSWORD,
	}

	/**
	 * @return the set of {@link CredentialType}s supported by this provider
	 */
	public abstract Set<CredentialType> getSupportedCredentialTypes();

	/**
	 * @param types
	 * @return <code>true</code> if this {@link CredentialsProvider} supports
	 *         credentials of the given type
	 */
	public boolean supports(CredentialType... types) {
		return getSupportedCredentialTypes().containsAll(Arrays.asList(types));
	}

	/**
	 * Get a specific credential for a given URI. The method must only be called
	 * for supported {@link CredentialType}s. Otherwise a
	 * {@link UnsupportedCredentialType} exception is thrown
	 * 
	 * @param uri
	 *            the URI for which credential is requested
	 * @param type
	 *            the type of the requested credential
	 * 
	 * @return an object containing the credential data or null if no data is
	 *         available for this {@link URIish}
	 * @throws UnsupportedCredentialType
	 *             if the credential type is not supported by this provider
	 */
	public abstract Object getCredentials(URIish uri, CredentialType type)
			throws UnsupportedCredentialType;
}
