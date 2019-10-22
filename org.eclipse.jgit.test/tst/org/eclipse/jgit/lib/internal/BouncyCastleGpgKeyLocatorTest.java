/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.lib.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class BouncyCastleGpgKeyLocatorTest {

	private static final String USER_ID = "Heinrich Heine <heinrichh@uni-duesseldorf.de>";

	private static boolean match(String userId, String pattern) {
		return BouncyCastleGpgKeyLocator.containsSigningKey(userId, pattern);
	}

	@Test
	public void testFullMatch() throws Exception {
		assertTrue(match(USER_ID,
				"=Heinrich Heine <heinrichh@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID, "=Heinrich Heine"));
		assertFalse(match(USER_ID, "= "));
		assertFalse(match(USER_ID, "=heinrichh@uni-duesseldorf.de"));
	}

	@Test
	public void testEmpty() throws Exception {
		assertFalse(match(USER_ID, ""));
		assertFalse(match(USER_ID, null));
		assertFalse(match("", ""));
		assertFalse(match(null, ""));
		assertFalse(match(null, null));
		assertFalse(match("", "something"));
		assertFalse(match(null, "something"));
	}

	@Test
	public void testFullEmail() throws Exception {
		assertTrue(match(USER_ID, "<heinrichh@uni-duesseldorf.de>"));
		assertTrue(match(USER_ID + " ", "<heinrichh@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID, "<>"));
		assertFalse(match(USER_ID, "<h>"));
		assertFalse(match(USER_ID, "<heinrichh>"));
		assertFalse(match(USER_ID, "<uni-duesseldorf>"));
		assertFalse(match(USER_ID, "<h@u>"));
		assertFalse(match(USER_ID, "<HeinrichH@uni-duesseldorf.de>"));
		assertFalse(match(USER_ID.substring(0, USER_ID.length() - 1),
				"<heinrichh@uni-duesseldorf.de>"));
		assertFalse(match("", "<>"));
		assertFalse(match("", "<heinrichh@uni-duesseldorf.de>"));
	}

	@Test
	public void testPartialEmail() throws Exception {
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
		assertFalse(match(USER_ID, "@HeinrichH"));
		assertFalse(match(USER_ID, "@Heinrich"));
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
	public void testSubstringPlain() throws Exception {
		substringTests("");
	}

	@Test
	public void testSubstringAsterisk() throws Exception {
		substringTests("*");
	}

	@Test
	public void testExplicitFingerprint() throws Exception {
		assertFalse(match("John Fade <j.fade@example.com>", "0xfade"));
		assertFalse(match("John Fade <0xfade@example.com>", "0xfade"));
		assertFalse(match("", "0xfade"));
	}

	@Test
	public void testImplicitFingerprint() throws Exception {
		assertTrue(match("John Fade <j.fade@example.com>", "fade"));
		assertTrue(match("John Fade <0xfade@example.com>", "fade"));
		assertTrue(match("John Fade <j.fade@example.com>", "FADE"));
		assertTrue(match("John Fade <0xfade@example.com>", "FADE"));
	}

	@Test
	public void testZeroX() throws Exception {
		assertTrue(match("John Fade <0xfade@example.com>", "0x"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0x"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0xfade"));
		assertTrue(match("John Fade <0xfade@example.com>", "*0xFADE"));
		assertTrue(match("John Fade <0xfade@example.com>", "@0xfade"));
		assertFalse(match("John Fade <0xfade@example.com>", "@0xFADE"));
		assertFalse(match("", "0x"));
	}
}
