/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.List;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * Provide credentials for use in connecting to Git repositories.
 *
 * Implementors are strongly encouraged to support at least the minimal
 * {@link org.eclipse.jgit.transport.CredentialItem.Username} and
 * {@link org.eclipse.jgit.transport.CredentialItem.Password} items. More
 * sophisticated implementors may implement additional types, such as
 * {@link org.eclipse.jgit.transport.CredentialItem.StringType}.
 *
 * CredentialItems are usually presented in bulk, allowing implementors to
 * combine them into a single UI widget and streamline the authentication
 * process for an end-user.
 *
 * @see UsernamePasswordCredentialsProvider
 */
public abstract class CredentialsProvider {
	private static volatile CredentialsProvider defaultProvider;

	/**
	 * Get the default credentials provider, or null.
	 *
	 * @return the default credentials provider, or null.
	 */
	public static CredentialsProvider getDefault() {
		return defaultProvider;
	}

	/**
	 * Set the default credentials provider.
	 *
	 * @param p
	 *            the new default provider, may be null to select no default.
	 */
	public static void setDefault(CredentialsProvider p) {
		defaultProvider = p;
	}

	/**
	 * Whether any of the passed items is null
	 *
	 * @param items
	 *            credential items to check
	 * @return {@code true} if any of the passed items is null, {@code false}
	 *         otherwise
	 * @since 4.2
	 */
	protected static boolean isAnyNull(CredentialItem... items) {
		for (CredentialItem i : items)
			if (i == null)
				return true;
		return false;
	}

	/**
	 * Check if the provider is interactive with the end-user.
	 *
	 * An interactive provider may try to open a dialog box, or prompt for input
	 * on the terminal, and will wait for a user response. A non-interactive
	 * provider will either populate CredentialItems, or fail.
	 *
	 * @return {@code true} if the provider is interactive with the end-user.
	 */
	public abstract boolean isInteractive();

	/**
	 * Check if the provider can supply the necessary
	 * {@link org.eclipse.jgit.transport.CredentialItem}s.
	 *
	 * @param items
	 *            the items the application requires to complete authentication.
	 * @return {@code true} if this
	 *         {@link org.eclipse.jgit.transport.CredentialsProvider} supports
	 *         all of the items supplied.
	 */
	public abstract boolean supports(CredentialItem... items);

	/**
	 * Ask for the credential items to be populated.
	 *
	 * @param uri
	 *            the URI of the remote resource that needs authentication.
	 * @param items
	 *            the items the application requires to complete authentication.
	 * @return {@code true} if the request was successful and values were
	 *         supplied; {@code false} if the user canceled the request and did
	 *         not supply all requested values.
	 * @throws org.eclipse.jgit.errors.UnsupportedCredentialItem
	 *             if one of the items supplied is not supported.
	 */
	public abstract boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem;

	/**
	 * Ask for the credential items to be populated.
	 *
	 * @param uri
	 *            the URI of the remote resource that needs authentication.
	 * @param items
	 *            the items the application requires to complete authentication.
	 * @return {@code true} if the request was successful and values were
	 *         supplied; {@code false} if the user canceled the request and did
	 *         not supply all requested values.
	 * @throws org.eclipse.jgit.errors.UnsupportedCredentialItem
	 *             if one of the items supplied is not supported.
	 */
	public boolean get(URIish uri, List<CredentialItem> items)
			throws UnsupportedCredentialItem {
		return get(uri, items.toArray(new CredentialItem[0]));
	}

	/**
	 * Reset the credentials provider for the given URI
	 *
	 * @param uri
	 *            a {@link org.eclipse.jgit.transport.URIish} object.
	 */
	public void reset(URIish uri) {
		// default does nothing
	}
}
