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
package org.eclipse.jgit.internal.transport.sshd;

import java.util.concurrent.CancellationException;

import org.apache.sshd.client.ClientAuthenticationManager;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.auth.password.UserAuthPassword;
import org.apache.sshd.client.session.ClientSession;

/**
 * A password authentication handler that uses the {@link JGitUserInteraction}
 * to ask the user for the password. It also respects the
 * {@code NumberOfPasswordPrompts} ssh config.
 */
public class JGitPasswordAuthentication extends UserAuthPassword {

	private int maxAttempts;

	private int attempts;

	@Override
	public void init(ClientSession session, String service) throws Exception {
		super.init(session, service);
		maxAttempts = Math.max(1,
				session.getIntProperty(
						ClientAuthenticationManager.PASSWORD_PROMPTS,
						ClientAuthenticationManager.DEFAULT_PASSWORD_PROMPTS));
		attempts = 0;
	}

	@Override
	protected boolean sendAuthDataRequest(ClientSession session, String service)
			throws Exception {
		if (++attempts > maxAttempts) {
			return false;
		}
		UserInteraction interaction = session.getUserInteraction();
		if (!interaction.isInteractionAllowed(session)) {
			return false;
		}
		String password = getPassword(session, interaction);
		if (password == null) {
			throw new CancellationException();
		}
		// sendPassword takes a buffer as first argument, but actually doesn't
		// use it and creates its own buffer...
		sendPassword(null, session, password, password);
		return true;
	}

	private String getPassword(ClientSession session,
			UserInteraction interaction) {
		String[] results = interaction.interactive(session, null, null, "", //$NON-NLS-1$
				new String[] { SshdText.get().passwordPrompt },
				new boolean[] { false });
		return (results == null || results.length == 0) ? null : results[0];
	}
}
