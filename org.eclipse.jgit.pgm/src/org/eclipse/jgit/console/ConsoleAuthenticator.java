/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.console;

import java.io.Console;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.text.MessageFormat;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.util.CachedAuthenticator;

/**
 * Basic network prompt for username/password when using the console.
 *
 * @since 4.0
 */
public class ConsoleAuthenticator extends CachedAuthenticator {
	/**
	 * Install this authenticator implementation into the JVM.
	 */
	public static void install() {
		final ConsoleAuthenticator c = new ConsoleAuthenticator();
		if (c.cons == null)
			throw new NoClassDefFoundError(
					CLIText.get().noSystemConsoleAvailable);
		Authenticator.setDefault(c);
	}

	private final Console cons = System.console();

	/** {@inheritDoc} */
	@Override
	protected PasswordAuthentication promptPasswordAuthentication() {
		final String realm = formatRealm();
		String username = cons.readLine(MessageFormat.format(
				CLIText.get().usernameFor + " ", realm)); //$NON-NLS-1$
		if (username == null || username.isEmpty()) {
			return null;
		}
		char[] password = cons.readPassword(CLIText.get().password + " "); //$NON-NLS-1$
		if (password == null) {
			password = new char[0];
		}
		return new PasswordAuthentication(username, password);
	}

	private String formatRealm() {
		final StringBuilder realm = new StringBuilder();
		if (getRequestorType() == RequestorType.PROXY) {
			realm.append(getRequestorType());
			realm.append(" "); //$NON-NLS-1$
			realm.append(getRequestingHost());
			if (getRequestingPort() > 0) {
				realm.append(":"); //$NON-NLS-1$
				realm.append(getRequestingPort());
			}
		} else {
			realm.append(getRequestingURL());
		}
		return realm.toString();
	}
}
