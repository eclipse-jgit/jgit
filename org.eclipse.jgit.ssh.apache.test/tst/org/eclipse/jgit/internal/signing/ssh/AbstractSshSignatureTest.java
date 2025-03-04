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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Common setup for SSH signature tests.
 */
public abstract class AbstractSshSignatureTest extends RepositoryTestCase {

	@Rule
	public TemporaryFolder keys = new TemporaryFolder();

	protected File certs;

	protected Instant commitTime;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		copyResource("allowed_signers", keys.getRoot());
		copyResource("other_key", keys.getRoot());
		copyResource("other_key.pub", keys.getRoot());
		copyResource("other_key-cert.pub", keys.getRoot());
		copyResource("signing_key", keys.getRoot());
		copyResource("signing_key.pub", keys.getRoot());
		certs = keys.newFolder("certs");
		copyResource("certs/expired.cert", certs);
		copyResource("certs/no_principals.cert", certs);
		copyResource("certs/other.cert", certs);
		copyResource("certs/other-ca.cert", certs);
		copyResource("certs/tester.cert", certs);
		copyResource("certs/two_principals.cert", certs);
		Repository repo = db;
		StoredConfig config = repo.getConfig();
		config.setString("gpg", null, "format", "ssh");
		config.setString("gpg", "ssh", "allowedSignersFile",
				keys.getRoot().toPath().resolve("allowed_signers").toString()
						.replace('\\', '/'));
		config.save();
		// Run all tests with commit times on 2024-10-02T12:00:00Z. The test
		// certificates are valid from 2024-09-01 to 2024-10-31, except the
		// "expired" certificate which is valid only on 2024-09-01.
		commitTime = Instant.parse("2024-10-02T12:00:00.00Z");
	}

	private void copyResource(String name, File directory) throws IOException {
		try (InputStream in = this.getClass().getResourceAsStream(name)) {
			int i = name.lastIndexOf('/');
			String fileName = i < 0 ? name : name.substring(i + 1);
			Files.copy(in, directory.toPath().resolve(fileName));
		}
	}

	protected RevCommit createSignedCommit(String certificate,
			String signingKey) throws Exception {
		Repository repo = db;
		Path key = keys.getRoot().toPath().resolve(signingKey);
		if (certificate != null) {
			Files.copy(certs.toPath().resolve(certificate),
					keys.getRoot().toPath().resolve(signingKey),
					StandardCopyOption.REPLACE_EXISTING);
		}
		PersonIdent commitAuthor = new PersonIdent("tester",
				"tester@example.com", commitTime, ZoneOffset.UTC);
		try (Git git = Git.wrap(repo)) {
			writeTrashFile("foo.txt", "foo");
			git.add().addFilepattern("foo.txt").call();
			CommitCommand commit = git.commit();
			commit.setAuthor(commitAuthor);
			commit.setCommitter(commitAuthor);
			commit.setMessage("Message");
			commit.setSign(Boolean.TRUE);
			commit.setSigningKey(key.toAbsolutePath().toString());
			return commit.call();
		}
	}

	protected RevCommit checkSshSignature(RevCommit c) {
		byte[] sig = c.getRawGpgSignature();
		assertNotNull(sig);
		String signature = new String(sig, StandardCharsets.US_ASCII);
		assertTrue("Not an SSH signature:\n" + signature,
				signature.startsWith(Constants.SSH_SIGNATURE_PREFIX));
		return c;
	}
}
