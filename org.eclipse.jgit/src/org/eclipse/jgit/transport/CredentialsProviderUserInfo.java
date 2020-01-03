/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

/**
 * A JSch {@link com.jcraft.jsch.UserInfo} adapter for a
 * {@link org.eclipse.jgit.transport.CredentialsProvider}.
 */
public class CredentialsProviderUserInfo implements UserInfo,
		UIKeyboardInteractive {
	private final URIish uri;

	private final CredentialsProvider provider;

	private String password;

	private String passphrase;

	/**
	 * Wrap a CredentialsProvider to make it suitable for use with JSch.
	 *
	 * @param session
	 *            the JSch session this UserInfo will support authentication on.
	 * @param credentialsProvider
	 *            the provider that will perform the authentication.
	 */
	public CredentialsProviderUserInfo(Session session,
			CredentialsProvider credentialsProvider) {
		this.uri = createURI(session);
		this.provider = credentialsProvider;
	}

	private static URIish createURI(Session session) {
		URIish uri = new URIish();
		uri = uri.setScheme("ssh"); //$NON-NLS-1$
		uri = uri.setUser(session.getUserName());
		uri = uri.setHost(session.getHost());
		uri = uri.setPort(session.getPort());
		return uri;
	}

	/** {@inheritDoc} */
	@Override
	public String getPassword() {
		return password;
	}

	/** {@inheritDoc} */
	@Override
	public String getPassphrase() {
		return passphrase;
	}

	/** {@inheritDoc} */
	@Override
	public boolean promptPassphrase(String msg) {
		CredentialItem.StringType v = newPrompt(msg);
		if (provider.get(uri, v)) {
			passphrase = v.getValue();
			return true;
		}
		passphrase = null;
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean promptPassword(String msg) {
		CredentialItem.Password p = new CredentialItem.Password(msg);
		if (provider.get(uri, p)) {
			password = new String(p.getValue());
			return true;
		}
		password = null;
		return false;
	}

	private CredentialItem.StringType newPrompt(String msg) {
		return new CredentialItem.StringType(msg, true);
	}

	/** {@inheritDoc} */
	@Override
	public boolean promptYesNo(String msg) {
		CredentialItem.YesNoType v = new CredentialItem.YesNoType(msg);
		return provider.get(uri, v) && v.getValue();
	}

	/** {@inheritDoc} */
	@Override
	public void showMessage(String msg) {
		provider.get(uri, new CredentialItem.InformationalMessage(msg));
	}

	/** {@inheritDoc} */
	@Override
	public String[] promptKeyboardInteractive(String destination, String name,
			String instruction, String[] prompt, boolean[] echo) {
		CredentialItem.StringType[] v = new CredentialItem.StringType[prompt.length];
		for (int i = 0; i < prompt.length; i++)
			v[i] = new CredentialItem.StringType(prompt[i], !echo[i]);

		List<CredentialItem> items = new ArrayList<>();
		if (instruction != null && instruction.length() > 0)
			items.add(new CredentialItem.InformationalMessage(instruction));
		items.addAll(Arrays.asList(v));

		if (!provider.get(uri, items))
			return null; // cancel

		String[] result = new String[v.length];
		for (int i = 0; i < v.length; i++)
			result[i] = v[i].getValue();
		return result;
	}
}
