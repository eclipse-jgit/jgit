/*
 * Copyright (C) 2014, Matthias Sohn <matthias.sohn@sap.com>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * A credentials provider chaining multiple credentials providers
 *
 * @since 3.5
 */
public class ChainingCredentialsProvider extends CredentialsProvider {

	private List<CredentialsProvider> credentialProviders;

	/**
	 * Create a new chaining credential provider. This provider tries to
	 * retrieve credentials from the chained credential providers in the order
	 * they are given here. If multiple providers support the requested items
	 * and have non-null credentials the first of them will be used.
	 *
	 * @param providers
	 *            credential providers asked for credentials in the order given
	 *            here
	 */
	public ChainingCredentialsProvider(CredentialsProvider... providers) {
		this.credentialProviders = new ArrayList<CredentialsProvider>(
				Arrays.asList(providers));
		for (CredentialsProvider p : providers)
			credentialProviders.add(p);
	}

	/**
	 * @return {@code true} if any of the credential providers in the list is
	 *         interactive, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#isInteractive()
	 */
	@Override
	public boolean isInteractive() {
		for (CredentialsProvider p : credentialProviders)
			if (p.isInteractive())
				return true;
		return false;
	}

	/**
	 * @return {@code true} if any of the credential providers in the list
	 *         supports the requested items, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialsProvider p : credentialProviders)
			if (p.supports(items))
				return true;
		return false;
	}

	/**
	 * Populates the credential items with the credentials provided by the first
	 * credential provider in the list which populates them with non-null values
	 *
	 * @return {@code true} if any of the credential providers in the list
	 *         supports the requested items, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialsProvider p : credentialProviders) {
			if (p.supports(items)) {
				p.get(uri, items);
				if (isAnyNull(items))
					continue;
				return true;
			}
		}
		return false;
	}

	private boolean isAnyNull(CredentialItem... items) {
		for (CredentialItem i : items)
			if (i == null)
				return true;
		return false;
	}
}
