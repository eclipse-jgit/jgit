/*
 * Copyright (C) 2018, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.apache.sshd.core.CoreModuleProperties.PASSWORD_PROMPTS;

import org.apache.sshd.client.auth.password.UserAuthPassword;
import org.apache.sshd.client.session.ClientSession;

/**
 * A password authentication handler that respects the
 * {@code NumberOfPasswordPrompts} ssh config.
 */
public class JGitPasswordAuthentication extends UserAuthPassword {

	private int maxAttempts;

	private int attempts;

	@Override
	public void init(ClientSession session, String service) throws Exception {
		super.init(session, service);
		maxAttempts = Math.max(1,
				PASSWORD_PROMPTS.getRequired(session).intValue());
		attempts = 0;
	}

	@Override
	protected String resolveAttemptedPassword(ClientSession session,
			String service) throws Exception {
		if (++attempts > maxAttempts) {
			return null;
		}
		return super.resolveAttemptedPassword(session, service);
	}
}
