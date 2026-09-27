/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.util.CachedAuthenticator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NoPromptAuthenticatorTest {

	private Authenticator savedAuth;

	private CredentialsProvider savedCp;

	@Before
	public void saveGlobals() {
		savedAuth = Authenticator.getDefault();
		savedCp = CredentialsProvider.getDefault();
	}

	@After
	public void restoreGlobals() {
		Authenticator.setDefault(savedAuth);
		CredentialsProvider.setDefault(savedCp);
	}

	@Test
	public void neverPrompts() {
		assertNull(new NoPromptAuthenticator().promptPasswordAuthentication());
	}

	@Test
	public void servesCachedProxyButNeverPromptsForUnknownHost() {
		NoPromptAuthenticator.install();
		// CachedAuthenticator's cache is process-global with no remove API, so
		// this entry persists; the reserved .invalid host cannot collide.
		CachedAuthenticator.add(new CachedAuthenticator.CachedAuthentication(
				"proxy.invalid", 8080, "jdoe", "secret"));
		PasswordAuthentication proxy = Authenticator
				.requestPasswordAuthentication("proxy.invalid", null, 8080,
						"http", "", "basic");
		assertNotNull(proxy);
		assertEquals("jdoe", proxy.getUserName());
		// Unknown host: no dialog, returns null so the request fails fast.
		assertNull(Authenticator.requestPasswordAuthentication(
				"unknown.example", null, 443, "https", "", "basic"));
	}

	@Test
	public void headlessInstallsNetRcAndNoPromptAuthenticator() {
		Main.installNoConsoleAuth(true);
		assertTrue(CredentialsProvider
				.getDefault() instanceof NetRCCredentialsProvider);
		assertTrue(Authenticator.getDefault() instanceof NoPromptAuthenticator);
	}

	@Test
	public void displayInstallsAwtCredentialsChain() {
		Main.installNoConsoleAuth(false);
		assertTrue(CredentialsProvider
				.getDefault() instanceof ChainingCredentialsProvider);
		assertTrue(Authenticator.getDefault() instanceof NoPromptAuthenticator);
	}
}
