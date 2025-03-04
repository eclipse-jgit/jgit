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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.VerificationResult;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.SignatureUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies signatures made with C git and OpenSSH 9.0 to ensure we arrive at
 * the same good/bad decisions, and that we can verify signatures not created by
 * ourselves.
 * <p>
 * Clones a JGit repo from a git bundle file created with C git, then checks all
 * the commits and their signatures. (All commits in that bundle have SSH
 * signatures.)
 * </p>
 */
public class VerifyGitSignaturesTest extends LocalDiskRepositoryTestCase {

	private static final Logger LOG = LoggerFactory
			.getLogger(VerifyGitSignaturesTest.class);

	@Rule
	public TemporaryFolder bundleDir = new TemporaryFolder();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		try (InputStream in = this.getClass()
				.getResourceAsStream("repo.bundle")) {
			Files.copy(in, bundleDir.getRoot().toPath().resolve("repo.bundle"));
		}
		try (InputStream in = this.getClass()
				.getResourceAsStream("allowed_signers")) {
			Files.copy(in,
					bundleDir.getRoot().toPath().resolve("allowed_signers"));
		}
	}

	/**
	 * Tests signatures created by C git using OpenSSH 9.0.
	 */
	@Test
	public void testGitSignatures() throws Exception {
		File gitDir = new File(getTemporaryDirectory(), "repo.git");
		try (Git git = Git.cloneRepository().setBare(true)
				.setGitDir(gitDir)
				.setURI(new File(bundleDir.getRoot(), "repo.bundle").toURI()
						.toString())
				.setBranch("master")
				.call()) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("gpg", "ssh", "allowedSignersFile",
					bundleDir.getRoot().toPath().resolve("allowed_signers")
							.toAbsolutePath().toString().replace('\\', '/'));
			config.save();
			List<String> commits = new ArrayList<>();
			Map<String, PersonIdent> committers = new HashMap<>();
			git.log().all().call().forEach(c -> {
				commits.add(c.getName());
				committers.put(c.getName(), c.getCommitterIdent());
			});
			Map<String, Boolean> expected = new HashMap<>();
			// These two commits do have multiple principals. GIT just reports
			// the first one; we report both.
			expected.put("9f79a7b661a22ab1ddf8af880d23678ae7696b71",
					Boolean.TRUE);
			expected.put("435108d157440e77d61a914b6a5736bc831c874d",
					Boolean.TRUE);
			// This commit has a wrong commit message; the certificate used
			// did _not_ have two principals, but only a single principal
			// foo@example.org.
			expected.put("779dac7de40ebc3886af87d5e6680a09f8b13a3e",
					Boolean.TRUE);
			// Signed with other_key-cert.pub: we still don't know the key,
			// but we do know the certificate's CA key, and trust it, so it's
			// accepted as a signature from the principal(s) listed in the
			// certificate.
			expected.put("951f06d5b5598b721b98d98b04e491f234c1926a",
					Boolean.TRUE);
			// Signature with other_key.pub not listed in allowed_signers
			expected.put("984e629c6d543a7f77eb49a8c9316f2ae4416375",
					Boolean.FALSE);
			// Signed with other-ca.cert (CA key not in allowed_signers), but
			// the certified key _is_ listed in allowed_signers.
			expected.put("1d7ac6d91747a9c9a777df238fbdaeffa7731a6c",
					Boolean.FALSE);
			expected.put("a297bcfbf5c4a850f9770655fef7315328a4b3fb",
					Boolean.TRUE);
			expected.put("852729d54676cb83826ed821dc7734013e97950d",
					Boolean.TRUE);
			// Signature with a certificate without principals.
			expected.put("e39a049f75fe127eb74b30aba4b64e171d4281dd",
					Boolean.FALSE);
			// Signature made with expired.cert (expired at the commit time).
			// git/OpenSSH 9.0 allows to create such signatures, but reports
			// them as FALSE. Our SshSigner doesn't allow creating such
			// signatures.
			expected.put("303ea5e61feacdad4cb012b4cb6b0cea3fbcef9f",
					Boolean.FALSE);
			expected.put("1ae4b120a869b72a7a2d4ad4d7a8c9d454384333",
					Boolean.TRUE);
			Map<String, VerificationResult> results = git.verifySignature()
					.addNames(commits).call();
			GitDateFormatter dateFormat = new GitDateFormatter(
					GitDateFormatter.Format.ISO);
			for (String oid : commits) {
				VerificationResult v = results.get(oid);
				assertNotNull(v);
				assertNull(v.getException());
				SignatureVerifier.SignatureVerification sv = v
						.getVerification();
				assertNotNull(sv);
				LOG.info("Commit {}\n{}", oid, SignatureUtils.toString(sv,
						committers.get(oid), dateFormat));
				Boolean wanted = expected.get(oid);
				assertNotNull(wanted);
				assertEquals(wanted, Boolean.valueOf(sv.verified()));
			}
		}
	}
}
