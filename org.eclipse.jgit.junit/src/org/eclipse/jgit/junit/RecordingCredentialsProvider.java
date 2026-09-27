/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * A {@link org.eclipse.jgit.transport.CredentialsProvider} for tests that
 * supplies a fixed username/password and records the {@code store} and
 * {@code erase} callbacks the transport makes, including the credentials handed
 * back on {@code store}.
 */
public class RecordingCredentialsProvider
		extends UsernamePasswordCredentialsProvider {

	/** Whether {@code store} was called. */
	public volatile boolean stored;

	/** Whether {@code erase} was called. */
	public volatile boolean erased;

	/** Username handed to {@code store}, or {@code null}. */
	public volatile String storedUsername;

	/** Password handed to {@code store} (a clone), or {@code null}. */
	public volatile char[] storedPassword;

	/** Bearer token handed to {@code store} (a clone), or {@code null}. */
	public volatile char[] storedBearer;

	/**
	 * Create a provider that supplies the given credentials and records
	 * {@code store}/{@code erase}.
	 *
	 * @param username
	 *            username to supply on {@code get}
	 * @param password
	 *            password to supply on {@code get}
	 */
	public RecordingCredentialsProvider(String username, String password) {
		super(username, password);
	}

	@Override
	public void store(URIish uri, CredentialItem... items) {
		stored = true;
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.Username) {
				storedUsername = ((CredentialItem.Username) item).getValue();
			} else if (item instanceof CredentialItem.Password) {
				char[] v = ((CredentialItem.Password) item).getValue();
				storedPassword = v == null ? null : v.clone();
			} else if (item instanceof CredentialItem.Bearer) {
				char[] v = ((CredentialItem.Bearer) item).getValue();
				storedBearer = v == null ? null : v.clone();
			}
		}
	}

	@Override
	public void erase(URIish uri) {
		erased = true;
		super.erase(uri);
	}
}
