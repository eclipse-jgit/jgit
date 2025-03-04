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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.VerificationResult;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.StringUtils;
import org.junit.Test;

/**
 * Tests for the {@link SshSignatureVerifier}.
 */
public class SshSignatureVerifierTest extends AbstractSshSignatureTest {

	@Test
	public void testPlainSignature() throws Exception {
		RevCommit c = checkSshSignature(
				createSignedCommit(null, "signing_key.pub"));
		try (Git git = new Git(db)) {
			Map<String, VerificationResult> results = git.verifySignature()
					.addName(c.getName()).call();
			assertEquals(1, results.size());
			VerificationResult verified = results.get(c.getName());
			assertNotNull(verified);
			assertNull(verified.getException());
			SignatureVerifier.SignatureVerification v = verified
					.getVerification();
			assertTrue(v.verified());
			assertFalse(v.expired());
			assertTrue(StringUtils.isEmptyOrNull(v.message()));
			assertEquals("tester@example.com", v.keyUser());
			assertEquals("SHA256:GKW0xy+XKnJGs0CJqP6j5bd4FdiwWNaUbwvUbHvhQKo",
					v.keyFingerprint());
			assertEquals(SignatureVerifier.TrustLevel.FULL, v.trustLevel());
			assertEquals(commitTime, v.creationDate().toInstant());
		}
	}

	@Test
	public void testCertificateSignature() throws Exception {
		RevCommit c = checkSshSignature(
				createSignedCommit("tester.cert", "signing_key-cert.pub"));
		try (Git git = new Git(db)) {
			Map<String, VerificationResult> results = git.verifySignature()
					.addName(c.getName()).call();
			assertEquals(1, results.size());
			VerificationResult verified = results.get(c.getName());
			assertNotNull(verified);
			assertNull(verified.getException());
			SignatureVerifier.SignatureVerification v = verified
					.getVerification();
			assertTrue(v.verified());
			assertFalse(v.expired());
			assertTrue(StringUtils.isEmptyOrNull(v.message()));
			assertEquals("tester@example.com", v.keyUser());
			assertEquals("SHA256:GKW0xy+XKnJGs0CJqP6j5bd4FdiwWNaUbwvUbHvhQKo",
					v.keyFingerprint());
			assertEquals(SignatureVerifier.TrustLevel.FULL, v.trustLevel());
			assertEquals(commitTime, v.creationDate().toInstant());
		}
	}

	@Test
	public void testNoPrincipalsSignature() throws Exception {
		RevCommit c = checkSshSignature(createSignedCommit("no_principals.cert",
				"signing_key-cert.pub"));
		try (Git git = new Git(db)) {
			Map<String, VerificationResult> results = git.verifySignature()
					.addName(c.getName()).call();
			assertEquals(1, results.size());
			VerificationResult verified = results.get(c.getName());
			assertNotNull(verified);
			assertNull(verified.getException());
			SignatureVerifier.SignatureVerification v = verified
					.getVerification();
			assertFalse(v.verified());
			assertFalse(v.expired());
			assertNull(v.keyUser());
			assertEquals("SHA256:GKW0xy+XKnJGs0CJqP6j5bd4FdiwWNaUbwvUbHvhQKo",
					v.keyFingerprint());
			assertEquals(SignatureVerifier.TrustLevel.NEVER, v.trustLevel());
			assertTrue(v.message().contains("*@example.com"));
			assertEquals(commitTime, v.creationDate().toInstant());
		}
	}

	@Test
	public void testOtherCertificateSignature() throws Exception {
		RevCommit c = checkSshSignature(
				createSignedCommit("other.cert", "signing_key-cert.pub"));
		try (Git git = new Git(db)) {
			Map<String, VerificationResult> results = git.verifySignature()
					.addName(c.getName()).call();
			assertEquals(1, results.size());
			VerificationResult verified = results.get(c.getName());
			assertNotNull(verified);
			assertNull(verified.getException());
			SignatureVerifier.SignatureVerification v = verified
					.getVerification();
			assertTrue(v.verified());
			assertFalse(v.expired());
			assertTrue(StringUtils.isEmptyOrNull(v.message()));
			assertEquals("other@example.com", v.keyUser());
			assertEquals("SHA256:GKW0xy+XKnJGs0CJqP6j5bd4FdiwWNaUbwvUbHvhQKo",
					v.keyFingerprint());
			assertEquals(SignatureVerifier.TrustLevel.FULL, v.trustLevel());
			assertEquals(commitTime, v.creationDate().toInstant());
		}
	}

	@Test
	public void testTwoPrincipalsCertificateSignature() throws Exception {
		RevCommit c = checkSshSignature(createSignedCommit(
				"two_principals.cert", "signing_key-cert.pub"));
		try (Git git = new Git(db)) {
			Map<String, VerificationResult> results = git.verifySignature()
					.addName(c.getName()).call();
			assertEquals(1, results.size());
			VerificationResult verified = results.get(c.getName());
			assertNotNull(verified);
			assertNull(verified.getException());
			SignatureVerifier.SignatureVerification v = verified
					.getVerification();
			assertTrue(v.verified());
			assertFalse(v.expired());
			assertTrue(StringUtils.isEmptyOrNull(v.message()));
			assertEquals("foo@example.com,tester@example.com", v.keyUser());
			assertEquals("SHA256:GKW0xy+XKnJGs0CJqP6j5bd4FdiwWNaUbwvUbHvhQKo",
					v.keyFingerprint());
			assertEquals(SignatureVerifier.TrustLevel.FULL, v.trustLevel());
			assertEquals(commitTime, v.creationDate().toInstant());
		}
	}
}
