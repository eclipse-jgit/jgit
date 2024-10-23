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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Tests for the {@link SshSigner}.
 */
public class SshSignerTest extends AbstractSshSignatureTest {

	@Test
	public void testPlainSignature() throws Exception {
		checkSshSignature(createSignedCommit(null, "signing_key.pub"));
	}

	@Test
	public void testExpiredSignature() throws Exception {
		Throwable t = assertThrows(Throwable.class,
				() -> createSignedCommit("expired.cert",
						"signing_key-cert.pub"));
		// The exception or one of its causes should mention "[Ee]xpired" and
		// "[Cc]ertificate" in the message
		while (t != null) {
			String message = t.getMessage();
			if (message.contains("xpired") && message.contains("ertificate")) {
				return;
			}
			t = t.getCause();
		}
		fail("Expected exception message not found");
	}

	@Test
	public void testCertificateSignature() throws Exception {
		checkSshSignature(createSignedCommit("tester.cert", "signing_key.pub"));
	}

	@Test
	public void testNoPrincipalsSignature() throws Exception {
		// Certificate has no principals; should still work
		checkSshSignature(
				createSignedCommit("no_principals.cert", "signing_key.pub"));
	}

	@Test
	public void testOtherSignature() throws Exception {
		// Certificate has a principal different that tester@example.com; should
		// still work
		checkSshSignature(createSignedCommit("other.cert", "signing_key.pub"));
	}
}
