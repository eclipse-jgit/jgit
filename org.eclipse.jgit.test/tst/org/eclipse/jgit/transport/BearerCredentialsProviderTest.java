/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.junit.Test;

public class BearerCredentialsProviderTest {

	private static final String TOKEN = "ya29.some-access-token";

	private final URIish uri = uri();

	private static URIish uri() {
		try {
			return new URIish("https://example.org/repo.git");
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void notInteractive() {
		assertFalse(new BearerCredentialsProvider(TOKEN).isInteractive());
	}

	@Test
	public void supportsOnlyBearerItems() {
		BearerCredentialsProvider p = new BearerCredentialsProvider(TOKEN);
		assertTrue(p.supports(new CredentialItem.Bearer()));
		assertFalse(p.supports(new CredentialItem.Username()));
		assertFalse(p.supports(new CredentialItem.Bearer(),
				new CredentialItem.Password()));
	}

	@Test
	public void getFillsBearerToken() {
		BearerCredentialsProvider p = new BearerCredentialsProvider(TOKEN);
		CredentialItem.Bearer item = new CredentialItem.Bearer();
		assertTrue(p.get(uri, item));
		assertArrayEquals(TOKEN.toCharArray(), item.getValue());
	}

	@Test
	public void getRejectsUnsupportedItem() {
		BearerCredentialsProvider p = new BearerCredentialsProvider(TOKEN);
		assertThrows(UnsupportedCredentialItem.class,
				() -> p.get(uri, new CredentialItem.Username()));
	}

	@Test
	public void bearerIsNotAPromptableCharArrayType() {
		// A CharArrayType would be treated as promptable by interactive providers.
		assertFalse(CredentialItem.CharArrayType.class
				.isAssignableFrom(CredentialItem.Bearer.class));
	}

	@Test
	public void nullTokenRejected() {
		assertThrows(NullPointerException.class,
				() -> new BearerCredentialsProvider((String) null));
		assertThrows(NullPointerException.class,
				() -> new BearerCredentialsProvider((char[]) null));
	}

	@Test
	public void emptyTokenRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new BearerCredentialsProvider(""));
	}

	@Test
	public void crlfTokenRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new BearerCredentialsProvider("tok\r\nInjected: x"));
	}

	@Test
	public void charArrayCtorCopiesToken() {
		char[] src = "tok".toCharArray();
		BearerCredentialsProvider p = new BearerCredentialsProvider(src);
		src[0] = 'X'; // the provider holds its own copy
		CredentialItem.Bearer item = new CredentialItem.Bearer();
		assertTrue(p.get(uri, item));
		assertArrayEquals("tok".toCharArray(), item.getValue());
	}

	@Test
	public void toleratesInformationalMessage() {
		BearerCredentialsProvider p = new BearerCredentialsProvider(TOKEN);
		CredentialItem.InformationalMessage msg =
				new CredentialItem.InformationalMessage("hi");
		assertTrue(p.supports(msg));
		assertTrue(p.supports(new CredentialItem.Bearer(), msg));
		CredentialItem.Bearer item = new CredentialItem.Bearer();
		assertTrue(p.get(uri, item, msg));
		assertArrayEquals(TOKEN.toCharArray(), item.getValue());
	}

	@Test
	public void clearStopsSupplyingToken() {
		BearerCredentialsProvider p = new BearerCredentialsProvider(TOKEN);
		CredentialItem.Bearer before = new CredentialItem.Bearer();
		p.get(uri, before);
		assertArrayEquals(TOKEN.toCharArray(), before.getValue());

		p.clear();
		CredentialItem.Bearer after = new CredentialItem.Bearer();
		assertFalse(p.get(uri, after)); // nothing to supply once cleared
		assertNull(after.getValue());
	}
}
