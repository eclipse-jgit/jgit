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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class BouncyCastleGpgKeyLocatorTest {

	private static final String USER_ID = "Heinrich Heine <heinrichh@uni-duesseldorf.de>";

	@Test
	public void testFilterOpenPgpBlobs() throws Exception {
		Path f = Files.createTempFile("keybox", ".kbx");
		try (OutputStream out = Files.newOutputStream(f)) {
			// 32-byte keybox file header: length, version/flags, "KBXf"
			// magic at offset 8.
			byte[] header = new byte[32];
			NB.encodeInt32(header, 0, 32);
			header[8] = 'K';
			header[9] = 'B';
			header[10] = 'X';
			header[11] = 'f';
			out.write(header);
			// A type-2 (OpenPGP) blob with arbitrary content.
			byte[] openPgp = new byte[40];
			NB.encodeInt32(openPgp, 0, 40);
			openPgp[4] = 2;
			out.write(openPgp);
			// A type-3 (X.509) blob which is dropped by the filter.
			byte[] x509 = new byte[36];
			NB.encodeInt32(x509, 0, 36);
			x509[4] = 3;
			out.write(x509);
		}

		byte[] filtered = BouncyCastleGpgKeyLocator.filterOpenPgpBlobs(f);
		assertEquals(32 + 40, filtered.length);
		assertEquals(32, NB.decodeInt32(filtered, 0));
		assertEquals(2, filtered[32 + 4]);
		Files.delete(f);

		// Not a keybox: the filter must return null.
		Path garbage = Files.createTempFile("garbage", ".kbx");
		try (OutputStream out = Files.newOutputStream(garbage)) {
			out.write(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
		}
		assertTrue(BouncyCastleGpgKeyLocator
				.filterOpenPgpBlobs(garbage) == null);
		Files.delete(garbage);
	}

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
		assertTrue(match(USER_ID, "<HeinrichH@uni-duesseldorf.de>"));
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
		assertFalse(match("John Fade <0xfade@example.com>", "0xFADE"));
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
		assertTrue(match("John Fade <0xfade@example.com>", "@0xFADE"));
		assertFalse(match("", "0x"));
	}
}
