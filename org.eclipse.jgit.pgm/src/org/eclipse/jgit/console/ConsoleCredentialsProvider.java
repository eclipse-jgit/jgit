/*
 * Copyright (C) 2010, Google Inc.
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

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Interacts with the user during authentication by using the text console.
 *
 * @since 4.0
 */
public class ConsoleCredentialsProvider extends CredentialsProvider {
	/**
	 * Install this implementation as the default.
	 */
	public static void install() {
		final ConsoleCredentialsProvider c = new ConsoleCredentialsProvider();
		if (c.cons == null)
			throw new NoClassDefFoundError(
					CLIText.get().noSystemConsoleAvailable);
		CredentialsProvider cp = new ChainingCredentialsProvider(
				new NetRCCredentialsProvider(), c);
		CredentialsProvider.setDefault(cp);
	}

	private final Console cons = System.console();

	/** {@inheritDoc} */
	@Override
	public boolean isInteractive() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.StringType)
				continue;

			else if (i instanceof CredentialItem.CharArrayType)
				continue;

			else if (i instanceof CredentialItem.YesNoType)
				continue;

			else if (i instanceof CredentialItem.InformationalMessage)
				continue;

			else
				return false;
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		boolean ok = true;
		for (int i = 0; i < items.length && ok; i++) {
			CredentialItem item = items[i];

			if (item instanceof CredentialItem.StringType)
				ok = get((CredentialItem.StringType) item);

			else if (item instanceof CredentialItem.CharArrayType)
				ok = get((CredentialItem.CharArrayType) item);

			else if (item instanceof CredentialItem.YesNoType)
				ok = get((CredentialItem.YesNoType) item);

			else if (item instanceof CredentialItem.InformationalMessage)
				ok = get((CredentialItem.InformationalMessage) item);

			else
				throw new UnsupportedCredentialItem(uri, item.getPromptText());
		}
		return ok;
	}

	private boolean get(CredentialItem.StringType item) {
		if (item.isValueSecure()) {
			char[] v = cons.readPassword("%s: ", item.getPromptText()); //$NON-NLS-1$
			if (v != null) {
				item.setValue(new String(v));
				return true;
			} else {
				return false;
			}
		} else {
			String v = cons.readLine("%s: ", item.getPromptText()); //$NON-NLS-1$
			if (v != null) {
				item.setValue(v);
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean get(CredentialItem.CharArrayType item) {
		if (item.isValueSecure()) {
			char[] v = cons.readPassword("%s: ", item.getPromptText()); //$NON-NLS-1$
			if (v != null) {
				item.setValueNoCopy(v);
				return true;
			} else {
				return false;
			}
		} else {
			String v = cons.readLine("%s: ", item.getPromptText()); //$NON-NLS-1$
			if (v != null) {
				item.setValueNoCopy(v.toCharArray());
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean get(CredentialItem.InformationalMessage item) {
		cons.printf("%s\n", item.getPromptText()); //$NON-NLS-1$
		cons.flush();
		return true;
	}

	private boolean get(CredentialItem.YesNoType item) {
		String r = cons.readLine("%s [%s/%s]? ", item.getPromptText(), //$NON-NLS-1$
				CLIText.get().answerYes, CLIText.get().answerNo);
		if (r != null) {
			item.setValue(CLIText.get().answerYes.equalsIgnoreCase(r));
			return true;
		} else {
			return false;
		}
	}
}
