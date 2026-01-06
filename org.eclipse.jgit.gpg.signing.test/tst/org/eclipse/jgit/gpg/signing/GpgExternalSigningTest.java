/*
 * Copyright (C) 2024, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.signing;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.lib.Signers;
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
		originalSigner = Signers.get(GpgFormat.OPENPGP);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		Signers.set(GpgFormat.OPENPGP, originalSigner);
		super.tearDown();
	}

	@Test
	public void testCommitSigning() throws Exception {
		byte[] fakeSig = "FAKE_SIG".getBytes(StandardCharsets.UTF_8);

		GpgProcessRunner mockRunner = mock(GpgProcessRunner.class);
		TemporaryBuffer stdout = mock(TemporaryBuffer.class);
		when(stdout.length()).thenReturn((long) fakeSig.length);
		when(stdout.toByteArray()).thenReturn(fakeSig);

		FS.ExecutionResult result = new FS.ExecutionResult(stdout, null, 0);
		when(mockRunner.run(any(), any())).thenReturn(result);

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

	private static class TestGpgBinarySigner extends GpgBinarySigner {
		private final GpgProcessRunner runner;

		public TestGpgBinarySigner(GpgProcessRunner runner) {
			this.runner = runner;
		}

		@Override
		protected GpgProcessRunner createProcessRunner(String gpgExecutable) {
			return runner;
		}
	}
}
