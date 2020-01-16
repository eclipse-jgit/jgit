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

import org.apache.sshd.client.auth.AbstractUserAuthFactory;
import org.apache.sshd.client.auth.UserAuth;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;

/**
 * A customized {@link UserAuthPasswordFactory} that creates instance of
 * {@link JGitPasswordAuthentication}.
 */
public class JGitPasswordAuthFactory extends AbstractUserAuthFactory {

	/** The singleton {@link JGitPasswordAuthFactory}. */
	public static final JGitPasswordAuthFactory INSTANCE = new JGitPasswordAuthFactory();

	private JGitPasswordAuthFactory() {
		super(UserAuthPasswordFactory.NAME);
	}

	@Override
	public UserAuth create() {
		return new JGitPasswordAuthentication();
	}
}
