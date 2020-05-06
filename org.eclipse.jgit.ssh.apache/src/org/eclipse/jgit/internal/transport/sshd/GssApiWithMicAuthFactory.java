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

import java.io.IOException;

import org.apache.sshd.client.auth.AbstractUserAuthFactory;
import org.apache.sshd.client.auth.UserAuth;
import org.apache.sshd.client.session.ClientSession;

/**
 * Factory to create {@link GssApiWithMicAuthentication} handlers.
 */
public class GssApiWithMicAuthFactory extends AbstractUserAuthFactory {

	/** The authentication identifier for GSSApi-with-MIC. */
	public static final String NAME = "gssapi-with-mic"; //$NON-NLS-1$

	/** The singleton {@link GssApiWithMicAuthFactory}. */
	public static final GssApiWithMicAuthFactory INSTANCE = new GssApiWithMicAuthFactory();

	private GssApiWithMicAuthFactory() {
		super(NAME);
	}

	@Override
	public UserAuth createUserAuth(ClientSession session)
			throws IOException {
		return new GssApiWithMicAuthentication();
	}

}
