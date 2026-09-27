/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.net.PasswordAuthentication;

import org.eclipse.jgit.util.CachedAuthenticator;

/**
 * Authenticator installed when there is no console: it serves credentials
 * already cached by {@link CachedAuthenticator} (e.g. a proxy user from
 * {@code http_proxy}) but returns {@code null} instead of prompting, so a
 * non-interactive run fails fast rather than blocking on a dialog.
 */
class NoPromptAuthenticator extends CachedAuthenticator {
	static void install() {
		setDefault(new NoPromptAuthenticator());
	}

	@Override
	protected PasswordAuthentication promptPasswordAuthentication() {
		return null;
	}
}
