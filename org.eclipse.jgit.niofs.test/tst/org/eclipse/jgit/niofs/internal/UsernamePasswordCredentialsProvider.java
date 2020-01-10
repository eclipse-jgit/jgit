/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;

/**
 * Mock CredentialsProvider that handles Yes/No requests
 */
public class UsernamePasswordCredentialsProvider
		extends org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider {

	public UsernamePasswordCredentialsProvider(final String username, final String password) {
		super(username, password);
	}

	@Override
	public boolean get(final URIish uri, final CredentialItem... items) throws UnsupportedCredentialItem {
		try {
			return super.get(uri, items);
		} catch (UnsupportedCredentialItem e) {
			for (CredentialItem i : items) {
				if (i instanceof CredentialItem.YesNoType) {
					((CredentialItem.YesNoType) i).setValue(true);
					return true;
				} else {
					continue;
				}
			}
		}
		return false;
	}
}
