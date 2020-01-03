/*
 * Copyright (C) 2014, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		this.credentialProviders = new ArrayList<>(
				Arrays.asList(providers));
	}

	/** {@inheritDoc} */
	@Override
	public boolean isInteractive() {
		for (CredentialsProvider p : credentialProviders)
			if (p.isInteractive())
				return true;
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialsProvider p : credentialProviders)
			if (p.supports(items))
				return true;
		return false;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Populates the credential items with the credentials provided by the first
	 * credential provider in the list which populates them with non-null values
	 *
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialsProvider p : credentialProviders) {
			if (p.supports(items)) {
				if (!p.get(uri, items)) {
					if (p.isInteractive()) {
						return false; // user cancelled the request
					}
					continue;
				}
				if (isAnyNull(items)) {
					continue;
				}
				return true;
			}
		}
		return false;
	}
}
