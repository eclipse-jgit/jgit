/*
 * Copyright (C) 2026, Nishchay Singh
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.console;

import static org.junit.Assert.assertEquals;

import java.net.Authenticator.RequestorType;
import java.net.URI;

import org.junit.Test;

public class ConsoleAuthenticatorTest {
	@Test
	public void serverRequestUsesUrlWhenAvailable() throws Exception {
		assertEquals("https://example.com/repo.git",
				ConsoleAuthenticator.formatRealm(RequestorType.SERVER,
						"example.com", 443,
						URI.create("https://example.com/repo.git").toURL()));
	}

	@Test
	public void serverRequestUsesHostWhenUrlIsMissing() {
		assertEquals("github.com", ConsoleAuthenticator.formatRealm(
				RequestorType.SERVER, "github.com", 443, null));
	}

	@Test
	public void proxyRequestUsesHostAndPort() {
		assertEquals("PROXY proxy.example.com:8080",
				ConsoleAuthenticator.formatRealm(RequestorType.PROXY,
						"proxy.example.com", 8080, null));
	}
}
