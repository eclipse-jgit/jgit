/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link SshCertificateUtils}. They use a certificate valid from
 * 2024-09-01 00:00:00 to 2024-09-02 00:00:00 UTC.
 */
public class SshCertificateUtilsTest {

	private OpenSshCertificate certificate;

	@Before
	public void loadCertificate() throws Exception {
		try (InputStream in = this.getClass().getResourceAsStream(
				"certs/expired.cert")) {
			List<AuthorizedKeyEntry> keys = AuthorizedKeyEntry
					.readAuthorizedKeys(in, true);
			if (keys.isEmpty()) {
				certificate = null;
			}
			PublicKey key = keys.get(0).resolvePublicKey(null,
					PublicKeyEntryResolver.FAILING);
			assertTrue(
					"Expected an OpenSshKeyCertificate but got a "
							+ key.getClass().getName(),
					key instanceof OpenSshCertificate);
			certificate = (OpenSshCertificate) key;
		}
	}

	@Test
	public void testValidUserCertificate() {
		assertNull(SshCertificateUtils.verify(certificate, null));
		Instant validTime = Instant.parse("2024-09-01T00:00:00.00Z");
		assertNull(SshCertificateUtils.verify(certificate, validTime));
		assertNull(SshCertificateUtils.checkExpiration(certificate, validTime));
	}

	@Test
	public void testCheckTooEarly() {
		Instant invalidTime = Instant.parse("2024-08-31T23:59:59.00Z");
		assertNotNull(
				SshCertificateUtils.checkExpiration(certificate, invalidTime));
		assertNotNull(SshCertificateUtils.verify(certificate, invalidTime));
	}

	@Test
	public void testCheckExpired() {
		Instant invalidTime = Instant.parse("2024-09-02T00:00:01.00Z");
		assertNotNull(
				SshCertificateUtils.checkExpiration(certificate, invalidTime));
		assertNotNull(SshCertificateUtils.verify(certificate, invalidTime));
	}

	@Test
	public void testInvalidSignature() throws Exception {
		// Modify the serialized certificate, then re-load it again. To check that
		// serialization per se works fine, also check an unmodified version.
		Buffer buffer = new ByteArrayBuffer();
		buffer.putPublicKey(certificate);
		int pos = buffer.rpos();
		PublicKey unchanged = buffer.getPublicKey();
		assertTrue(
				"Expected an OpenSshCertificate but got a "
						+ unchanged.getClass().getName(),
				unchanged instanceof OpenSshCertificate);
		assertNull(SshCertificateUtils.verify((OpenSshCertificate) unchanged,
				null));
		buffer.rpos(pos);
		// Change a byte. The test certificate has the key ID at offset 128.
		// Changing a byte in the key ID should still result in a successful
		// deserialization, but then fail the signature check.
		buffer.array()[pos + 128]++;
		PublicKey changed = buffer.getPublicKey();
		assertTrue(
				"Expected an OpenSshCertificate but got a "
						+ changed.getClass().getName(),
				changed instanceof OpenSshCertificate);
		assertNotNull(
				SshCertificateUtils.verify((OpenSshCertificate) changed, null));
	}
}
