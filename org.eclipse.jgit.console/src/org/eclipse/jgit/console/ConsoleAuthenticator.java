/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.console;

import java.io.Console;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.text.MessageFormat;

import org.eclipse.jgit.util.CachedAuthenticator;

/** Basic network prompt for username/password when using the console. */
public class ConsoleAuthenticator extends CachedAuthenticator {
	/** Install this authenticator implementation into the JVM. */
	public static void install() {
		final ConsoleAuthenticator c = new ConsoleAuthenticator();
		if (c.cons == null)
			throw new NoClassDefFoundError(ConsoleText.get().noSystemConsoleAvailable);
		Authenticator.setDefault(c);
	}

	private final Console cons = System.console();

	@Override
	protected PasswordAuthentication promptPasswordAuthentication() {
		final String realm = formatRealm();
		String username = cons.readLine(MessageFormat.format(ConsoleText.get().usernameFor + " ", realm));
		if (username == null || username.isEmpty()) {
			return null;
		}
		char[] password = cons.readPassword(ConsoleText.get().password + " ");
		if (password == null) {
			password = new char[0];
		}
		return new PasswordAuthentication(username, password);
	}

	private String formatRealm() {
		final StringBuilder realm = new StringBuilder();
		if (getRequestorType() == RequestorType.PROXY) {
			realm.append(getRequestorType());
			realm.append(" ");
			realm.append(getRequestingHost());
			if (getRequestingPort() > 0) {
				realm.append(":");
				realm.append(getRequestingPort());
			}
		} else {
			realm.append(getRequestingURL());
		}
		return realm.toString();
	}
}
