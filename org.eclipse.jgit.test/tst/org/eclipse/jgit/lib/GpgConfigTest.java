/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

public class GpgConfigTest {

	private static Config parse(String content) throws ConfigInvalidException {
		final Config c = new Config(null);
		c.fromText(content);
		return c;
	}

	@Test
	public void isSignCommits_defaultIsFalse() throws Exception {
		Config c = parse("");

		assertFalse(new GpgConfig(c).isSignCommits());
	}

	@Test
	public void isSignCommits_false() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = x509\n" //
				+ "[commit]\n" //
				+ "  gpgSign = false\n" //
		);

		assertFalse(new GpgConfig(c).isSignCommits());
	}

	@Test
	public void isSignCommits_true() throws Exception {
		Config c = parse("" //
				+ "[commit]\n" //
				+ "  gpgSign = true\n" //
		);

		assertTrue(new GpgConfig(c).isSignCommits());
	}

	@Test
	public void testGetKeyFormat_defaultsToOpenpgp() throws Exception {
		Config c = parse("");

		assertEquals(GpgConfig.GpgFormat.OPENPGP,
				new GpgConfig(c).getKeyFormat());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKeyFormat_failsForInvalidValue() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = invalid\n" //
		);

		new GpgConfig(c).getKeyFormat();
		fail("Call should not have succeeded!");
	}

	@Test
	public void testGetKeyFormat_openpgp() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = openpgp\n" //
		);

		assertEquals(GpgConfig.GpgFormat.OPENPGP,
				new GpgConfig(c).getKeyFormat());
	}

	@Test
	public void testGetKeyFormat_x509() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = x509\n" //
		);

		assertEquals(GpgConfig.GpgFormat.X509, new GpgConfig(c).getKeyFormat());
	}

	@Test
	public void testGetSigningKey() throws Exception {
		Config c = parse("" //
				+ "[user]\n" //
				+ "  signingKey = 0x2345\n" //
		);

		assertEquals("0x2345", new GpgConfig(c).getSigningKey());
	}

	@Test
	public void testGetSigningKey_defaultToNull() throws Exception {
		Config c = parse("");

		assertNull(new GpgConfig(c).getSigningKey());
	}
}
