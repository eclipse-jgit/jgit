/*
 * Copyright (C) 2018, 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;

import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.session.ClientSession;

/**
 * A customized authentication factory for public key user authentication.
 */
public class JGitPublicKeyAuthFactory extends UserAuthPublicKeyFactory {

	/** The singleton {@link JGitPublicKeyAuthFactory}. */
	public static final JGitPublicKeyAuthFactory FACTORY = new JGitPublicKeyAuthFactory();

	private JGitPublicKeyAuthFactory() {
		super();
	}

	@Override
	public UserAuthPublicKey createUserAuth(ClientSession session)
			throws IOException {
		return new JGitPublicKeyAuthentication(getSignatureFactories());
	}
}
