/*
 * Copyright (C) 2019, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

public class BouncyCastleGpgKeyLocatorTest {

	private static final String USER_ID = "Heinrich Heine <heinrichh@uni-duesseldorf.de>";

	private static boolean match(String userId, String pattern) {
		return BouncyCastleGpgKeyLocator.containsSigningKey(userId, pattern);
	}

	@Test
	void testFullMatch() throws Exception {
		assertTrue(match(USER_ID,
				"=Heinrich Heine <heinrichh@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID, "=Heinrich Heine"));
		assertFalse(match(USER_ID, "= "));
		assertFalse(match(USER_ID, "=heinrichh@uni-duesseldorf.de"));
	}

	@Test
	void testEmpty() throws Exception {
		assertFalse(match(USER_ID, ""));
		assertFalse(match(USER_ID, null));
		assertFalse(match("", ""));
		assertFalse(match(null, ""));
		assertFalse(match(null, null));
		assertFalse(match("", "something"));
		assertFalse(match(null, "something"));
	}

	@Test
	void testFullEmail() throws Exception {
		assertTrue(match(USER_ID, "<heinrichh@uni-duesseldorf.de>"));
		assertTrue(match(USER_ID + " ", "<heinrichh@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID, "<>"));
		assertFalse(match(USER_ID, "<h>"));
		assertFalse(match(USER_ID, "<heinrichh>"));
		assertFalse(match(USER_ID, "<uni-duesseldorf>"));
		assertFalse(match(USER_ID, "<h@u>"));
		assertTrue(match(USER_ID, "<HeinrichH@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID.substring(0, USER_ID.length() - 1),
				"<heinrichh@uni-duesseldorf.de>"));
		assertFalse(match("", "<>"));
		assertFalse(match("", "<heinrichh@uni-duesseldorf.de>"));
	}

	@Test
	void testPartialEmail() throws Exception {
		assertTrue(match(USER_ID, "@heinrichh@uni-duesseldorf.de"));
		assertTrue(match(USER_ID, "@heinrichh"));
		assertTrue(match(USER_ID, "@duesseldorf"));
		assertTrue(match(USER_ID, "@uni-d"));
		assertTrue(match(USER_ID, "@h"));
		assertTrue(match(USER_ID, "@."));
		assertTrue(match(USER_ID, "@h@u"));
		assertFalse(match(USER_ID, "@ "));
		assertFalse(match(USER_ID, "@"));
		assertFalse(match(USER_ID, "@Heine"));
		assertTrue(match(USER_ID, "@HeinrichH"));
		assertTrue(match(USER_ID, "@Heinrich"));
		assertFalse(match("", "@"));
		assertFalse(match("", "@h"));
	}

	private void substringTests(String prefix) throws Exception {
		assertTrue(match(USER_ID, prefix + "heinrichh@uni-duesseldorf.de"));
		assertTrue(match(USER_ID, prefix + "heinrich"));
		assertTrue(match(USER_ID, prefix + "HEIN"));
		assertTrue(match(USER_ID, prefix + "Heine <"));
		assertTrue(match(USER_ID, prefix + "UNI"));
		assertTrue(match(USER_ID, prefix + "uni"));
		assertTrue(match(USER_ID, prefix + "rich He"));
		assertTrue(match(USER_ID, prefix + "h@u"));
		assertTrue(match(USER_ID, prefix + USER_ID));
		assertTrue(match(USER_ID, prefix + USER_ID.toUpperCase(Locale.ROOT)));
		assertFalse(match(USER_ID, prefix + ""));
		assertFalse(match(USER_ID, prefix + " "));
		assertFalse(match(USER_ID, prefix + "yy"));
		assertFalse(match("", prefix + ""));
		assertFalse(match("", prefix + "uni"));
	}

	@Test
	void testSubstringPlain() throws Exception {
		substringTests("");
	}

	@Test
	void testSubstringAsterisk() throws Exception {
		substringTests("*");
	}

	@Test
	void testExplicitFingerprint() throws Exception {
		assertFalse(match("John Fade <j.fade@example.com>", "0xfade"));
		assertFalse(match("John Fade <0xfade@example.com>", "0xfade"));
		assertFalse(match("John Fade <0xfade@example.com>", "0xFADE"));
		assertFalse(match("", "0xfade"));
	}

	@Test
	void testImplicitFingerprint() throws Exception {
		assertTrue(match("John Fade <j.fade@example.com>", "fade"));
		assertTrue(match("John Fade <0xfade@example.com>", "fade"));
		assertTrue(match("John Fade <j.fade@example.com>", "FADE"));
		assertTrue(match("John Fade <0xfade@example.com>", "FADE"));
	}

	@Test
	void testZeroX() throws Exception {
		assertTrue(match("John Fade <0xfade@example.com>", "0x"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0x"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0xfade"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0xFADE"));
		assertTrue(match("John Fade <0xfade@example.com>", "@0xfade"));
		assertTrue(match("John Fade <0xfade@example.com>", "@0xFADE"));
		assertFalse(match("", "0x"));
	}
}
