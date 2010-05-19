/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

/**
 * Loads known hosts and private keys from <code>$HOME/.ssh</code>.
 * <p>
 * This is the default implementation used by JGit and provides most of the
 * compatibility necessary to match OpenSSH, a popular implementation of SSH
 * used by C Git.
 * <p>
 * If user interactivity is required by SSH (e.g. to obtain a password) the
 * system console is used to display a prompt to the end-user.
 */
public class ConsoleSshSessionFactory extends SshConfigSessionFactory {
	/** Install this session factory implementation into the JVM. */
	public static void install() {
		final ConsoleSshSessionFactory c = new ConsoleSshSessionFactory();
		if (c.cons == null)
			throw new NoClassDefFoundError(ConsoleText.get().noSystemConsoleAvailable);
		SshSessionFactory.setInstance(c);
	}

	private final Console cons = System.console();

	@Override
	protected void configure(final OpenSshConfig.Host hc, final Session session) {
		if (!hc.isBatchMode())
			session.setUserInfo(new ConsoleUserInfo());
	}

	private class ConsoleUserInfo implements UserInfo, UIKeyboardInteractive {
		private String passwd;

		private String passphrase;

		public void showMessage(final String msg) {
			cons.printf("%s\n", msg);
			cons.flush();
		}

		public boolean promptYesNo(final String msg) {
			String r = cons.readLine("%s [%s/%s]? ", msg, ConsoleText.get().answerYes, ConsoleText.get().answerNo);
			return ConsoleText.get().answerYes.equalsIgnoreCase(r);
		}

		public boolean promptPassword(final String msg) {
			passwd = null;
			char[] p = cons.readPassword("%s: ", msg);
			if (p != null) {
				passwd = new String(p);
				return true;
			}
			return false;
		}

		public boolean promptPassphrase(final String msg) {
			passphrase = null;
			char[] p = cons.readPassword("%s: ", msg);
			if (p != null) {
				passphrase = new String(p);
				return true;
			}
			return false;
		}

		public String getPassword() {
			return passwd;
		}

		public String getPassphrase() {
			return passphrase;
		}

		public String[] promptKeyboardInteractive(final String destination,
				final String name, final String instruction,
				final String[] prompt, final boolean[] echo) {
			cons.printf("%s: %s\n", destination, name);
			cons.printf("%s\n", instruction);
			final String[] response = new String[prompt.length];
			for (int i = 0; i < prompt.length; i++) {
				if (echo[i]) {
					response[i] = cons.readLine("%s: ", prompt[i]);
				} else {
					final char[] p = cons.readPassword("%s: ", prompt[i]);
					response[i] = p != null ? new String(p) : "";
				}
			}
			return response;
		}
	}
}
