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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.jupiter.api.Test;

public class GpgConfigTest {

	private static Config parse(String content) throws ConfigInvalidException {
		final Config c = new Config(null);
		c.fromText(content);
		return c;
	}

	@Test
	void isSignCommits_defaultIsFalse() throws Exception {
		Config c = parse("");

		assertFalse(new GpgConfig(c).isSignCommits());
	}

	@Test
	void isSignCommits_false() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = x509\n" //
				+ "[commit]\n" //
				+ "  gpgSign = false\n" //
		);

		assertFalse(new GpgConfig(c).isSignCommits());
	}

	@Test
	void isSignCommits_true() throws Exception {
		Config c = parse("" //
				+ "[commit]\n" //
				+ "  gpgSign = true\n" //
		);

		assertTrue(new GpgConfig(c).isSignCommits());
	}

	@Test
	void testGetKeyFormat_defaultsToOpenpgp() throws Exception {
		Config c = parse("");

		assertEquals(GpgConfig.GpgFormat.OPENPGP,
				new GpgConfig(c).getKeyFormat());
	}

	@Test
	void testGetKeyFormat_failsForInvalidValue() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> {
			Config c = parse("" //
					+ "[gpg]\n" //
					+ "  format = invalid\n" //
			);

			new GpgConfig(c).getKeyFormat();
			fail("Call should not have succeeded!");
		});
	}

	@Test
	void testGetKeyFormat_openpgp() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = openpgp\n" //
		);

		assertEquals(GpgConfig.GpgFormat.OPENPGP,
				new GpgConfig(c).getKeyFormat());
	}

	@Test
	void testGetKeyFormat_x509() throws Exception {
		Config c = parse("" //
				+ "[gpg]\n" //
				+ "  format = x509\n" //
		);

		assertEquals(GpgConfig.GpgFormat.X509, new GpgConfig(c).getKeyFormat());
	}

	@Test
	void testGetSigningKey() throws Exception {
		Config c = parse("" //
				+ "[user]\n" //
				+ "  signingKey = 0x2345\n" //
		);

		assertEquals("0x2345", new GpgConfig(c).getSigningKey());
	}

	@Test
	void testGetSigningKey_defaultToNull() throws Exception {
		Config c = parse("");

		assertNull(new GpgConfig(c).getSigningKey());
	}
}
