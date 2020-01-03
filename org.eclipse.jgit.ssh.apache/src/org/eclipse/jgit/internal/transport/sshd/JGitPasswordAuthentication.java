/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
