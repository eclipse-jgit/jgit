/*
 * Copyright (C) 2018, Salesforce.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
