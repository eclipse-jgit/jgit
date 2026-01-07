/*
 * Copyright (C) 2026, David Baker Effendi <david@brokk.ai> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.SignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.SignatureVerifier.TrustLevel;
import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for external GPG signing.
 */
public class GpgExternalSigningTest extends RepositoryTestCase {

	private Signer originalSigner;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		NLS.setLocale(Locale.ROOT);
		originalSigner = Signers.get(GpgFormat.OPENPGP);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		Signers.set(GpgFormat.OPENPGP, originalSigner);
		super.tearDown();
	}

	@Test
	public void testSignerFallback() {
		Signer signer = Signers.get(GpgFormat.OPENPGP);
		assertNotNull("Should have an OPENPGP signer", signer);
	}

	@Test
	public void testVerifierFallback() {
		SignatureVerifier verifier = SignatureVerifiers.get(GpgFormat.OPENPGP);
		assertNotNull("Should have an OPENPGP verifier", verifier);
	}

	@Test
	public void testCommitSigning() throws Exception {
		byte[] fakeSig = "FAKE_SIG".getBytes(StandardCharsets.UTF_8);

		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		TemporaryBuffer.Heap stdout = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
		stdout.write(fakeSig);

		FS.ExecutionResult result = new FS.ExecutionResult(stdout, null, 0);
		when(mockRunner.run(any(), any())).thenReturn(result);
		when(mockRunner.run(any(), any(), anyBoolean())).thenReturn(result);

		Signers.set(GpgFormat.OPENPGP, new TestGpgBinarySigner(mockRunner));

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());
		db.getConfig().setString("gpg", null, "format", "openpgp");

		try (Git git = new Git(db)) {
			writeTrashFile("test.txt", "content");
			git.add().addFilepattern("test.txt").call();
			RevCommit commit = git.commit().setMessage("Signed").setSign(true)
					.call();

			assertArrayEquals(fakeSig, commit.getRawGpgSignature());
		}
	}

	@Test
	public void testCommitSigningFailure() throws Exception {
		String errorMsg = "gpg: signing failed: No secret key";
		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);

		when(mockRunner.run(any(), any())).thenThrow(new IOException(errorMsg));
		when(mockRunner.run(any(), any(), anyBoolean()))
				.thenThrow(new IOException(errorMsg));

		Signers.set(GpgFormat.OPENPGP, new TestGpgBinarySigner(mockRunner));

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());
		db.getConfig().setString("gpg", null, "format", "openpgp");

		try (Git git = new Git(db)) {
			writeTrashFile("test.txt", "content");
			git.add().addFilepattern("test.txt").call();
			try {
				git.commit().setMessage("Signed").setSign(true).call();
				fail("Expected JGitInternalException");
			} catch (JGitInternalException e) {
				assertTrue(e.getMessage().contains(errorMsg));
			}
		}
	}

	@Test
	public void testCanLocateSigningKeyFailure() throws Exception {
		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		FS.ExecutionResult result = new FS.ExecutionResult(null, null, 1);
		when(mockRunner.run(any(), any())).thenReturn(result);
		when(mockRunner.run(any(), any(), anyBoolean())).thenReturn(result);

		TestGpgBinarySigner signer = new TestGpgBinarySigner(mockRunner);
		Signers.set(GpgFormat.OPENPGP, signer);

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());
		db.getConfig().setString("gpg", null, "format", "openpgp");

		GpgConfig config = new GpgConfig(db.getConfig());

		assertFalse(signer.canLocateSigningKey(db, config, new PersonIdent(db),
				null, null));
	}

	@Test
	public void testVerifierParsing() throws Exception {
		String statusOutput = "[GNUPG:] GOODSIG 12345678 Test User <test@example.com>\n"
				+ "[GNUPG:] VALIDSIG FINGERPRINT 1715851000\n"
				+ "[GNUPG:] TRUST_ULTIMATE\n";

		TemporaryBuffer.Heap stdout = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
		stdout.write(statusOutput.getBytes(StandardCharsets.UTF_8));

		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		FS.ExecutionResult result = new FS.ExecutionResult(stdout, null, 0);
		when(mockRunner.run(any(), any(), anyBoolean())).thenReturn(result);

		GpgBinarySignatureVerifier verifier = new GpgBinarySignatureVerifier() {
			@Override
			protected GpgProcessRunner createProcessRunner() {
				return mockRunner;
			}
		};

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());

		SignatureVerification v = verifier.verify(db,
				new GpgConfig(db.getConfig()), "data".getBytes(),
				"sig".getBytes());

		assertNotNull(v);
		assertTrue(v.verified());
		assertEquals("12345678", v.signer());
		assertEquals("Test User <test@example.com>", v.keyUser());
		assertEquals("FINGERPRINT", v.keyFingerprint());
	}

	@Test
	public void testVerifierParsingExtended() throws Exception {
		// SIG_ID, TRUST_FULLY, and missing VALIDSIG (fallback fingerprint)
		String statusOutput = "[GNUPG:] SIG_ID 1 2 1715851000\n"
				+ "[GNUPG:] GOODSIG 12345678 Test User <test@example.com>\n"
				+ "[GNUPG:] TRUST_FULLY\n";

		TemporaryBuffer.Heap stdout = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
		stdout.write(statusOutput.getBytes(StandardCharsets.UTF_8));

		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		FS.ExecutionResult result = new FS.ExecutionResult(stdout, null, 0);
		when(mockRunner.run(any(), any(), anyBoolean())).thenReturn(result);

		GpgBinarySignatureVerifier verifier = new GpgBinarySignatureVerifier() {
			@Override
			protected GpgProcessRunner createProcessRunner() {
				return mockRunner;
			}
		};

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());

		SignatureVerification v = verifier.verify(db,
				new GpgConfig(db.getConfig()), "data".getBytes(),
				"sig".getBytes());

		assertNotNull(v);
		assertTrue(v.verified());
		assertEquals("12345678", v.signer());
		assertEquals("12345678", v.keyFingerprint());
		assertEquals(TrustLevel.FULL, v.trustLevel());
		assertNotNull(v.creationDate());
		assertEquals(1715851000000L, v.creationDate().getTime());
	}

	@Test
	public void testVerifierMultipleSignatures() throws Exception {
		// Test that multiple signatures are detected and treated as unverified
		String statusOutput = "[GNUPG:] GOODSIG 12345678 User One\n"
				+ "[GNUPG:] GOODSIG 87654321 User Two\n";

		TemporaryBuffer.Heap stdout = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
		stdout.write(statusOutput.getBytes(StandardCharsets.UTF_8));

		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		FS.ExecutionResult result = new FS.ExecutionResult(stdout, null, 0);
		when(mockRunner.run(any(), any(), anyBoolean())).thenReturn(result);

		GpgBinarySignatureVerifier verifier = new GpgBinarySignatureVerifier() {
			@Override
			protected GpgProcessRunner createProcessRunner() {
				return mockRunner;
			}
		};

		db.getConfig().setString("gpg", null, "program",
				new File("dummy-gpg").getAbsolutePath());

		SignatureVerification v = verifier.verify(db,
				new GpgConfig(db.getConfig()), "data".getBytes(),
				"sig".getBytes());

		assertNotNull(v);
		assertFalse("Multiple signatures should not be valid", v.verified());
		assertNotNull("Should have an error message for multiple signatures",
				v.message());
	}

	private static class TestGpgBinarySigner extends GpgBinarySigner {
		private final GpgProcessRunner runner;

		public TestGpgBinarySigner(GpgProcessRunner runner) {
			this.runner = runner;
		}

		@Override
		protected GpgProcessRunner createProcessRunner() {
			return runner;
		}
	}
}
