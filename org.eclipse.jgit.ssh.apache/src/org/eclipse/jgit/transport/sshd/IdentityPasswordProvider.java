/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * A {@link FilePasswordProvider} based on a {@link CredentialsProvider}.
 *
 * @since 5.2
 */
public class IdentityPasswordProvider implements FilePasswordProvider {

	private CredentialsProvider provider;

	/**
	 * Creates a new {@link IdentityPasswordProvider} to get the passphrase for
	 * an encrypted identity.
	 *
	 * @param provider
	 *            to use
	 */
	public IdentityPasswordProvider(CredentialsProvider provider) {
		this.provider = provider;
	}

	/**
	 * Creates a {@link URIish} from a given string. The
	 * {@link CredentialsProvider} uses uris as resource identifications.
	 *
	 * @param resourceKey
	 *            to convert
	 * @return the uri
	 */
	protected URIish toUri(String resourceKey) {
		try {
			return new URIish(resourceKey);
		} catch (URISyntaxException e) {
			return new URIish().setPath(resourceKey); // Doesn't check!!
		}
	}

	@Override
	public String getPassword(String resourceKey) throws IOException {
		if (provider == null) {
			return null;
		}
		URIish file = toUri(resourceKey);
		List<CredentialItem> items = new ArrayList<>(2);
		items.add(new CredentialItem.InformationalMessage(
				format(SshdText.get().keyEncryptedMsg, resourceKey)));
		CredentialItem.Password password = new CredentialItem.Password(
				SshdText.get().keyEncryptedPrompt);
		items.add(password);
		try {
			provider.get(file, items);
			char[] pass = password.getValue();
			if (pass == null) {
				throw new CancellationException(
						SshdText.get().authenticationCanceled);
			}
			return new String(pass);
		} finally {
			password.clear();
		}
	}

}
