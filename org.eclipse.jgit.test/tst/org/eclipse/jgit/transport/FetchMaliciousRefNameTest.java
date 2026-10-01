/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression tests for a fetch-path traversal: a malicious remote advertises a
 * ref whose name contains {@code ../} and points it at a reachable object. A
 * client fetching with a wildcard refspec that preserves the advertised name
 * must not turn that name into a filesystem path that escapes the repository.
 *
 * <p>The advertised ref is injected directly with
 * {@link UploadPack#setAdvertisedRefs(Map)}: {@link Ref} objects do not validate
 * their names, so this reproduces a hostile server without having to write an
 * invalid ref to the server's own storage. The tests run against both protocol
 * v0 and v2, and cover the loose (single ref) and packed (multiple refs) write
 * paths.
 */
@RunWith(Parameterized.class)
public class FetchMaliciousRefNameTest extends LocalDiskRepositoryTestCase {

	/** Traversal ref that escapes to {@code deep/a} when the client is nested at
	 * {@code deep/a/b/c/client} (six {@code ../} up from {@code .git/refs/tags}). */
	private static final String TRAVERSAL_REF =
			"refs/tags/../../../../../../ESCAPED";

	@Parameter
	public String protocolVersion;

	@Parameters(name = "protocol.version={0}")
	public static Collection<String> data() {
		return Arrays.asList("0", "2");
	}

	private FileRepository server;

	private TestRepository<FileRepository> remote;

	private FileRepository client;

	private RevCommit head;

	private File escapedFile;

	private final Object ctx = new Object();

	private TestProtocol<Object> testProtocol;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createBareRepository();
		server.getConfig().setString("protocol", null, "version",
				protocolVersion);
		remote = new TestRepository<>(server);
		head = remote.branch("refs/heads/master").commit().message("one")
				.create();

		// Nest the client so a fixed number of "../" lands the escaped write at
		// a known location under the test temp directory.
		File clientGitDir = new File(getTemporaryDirectory(),
				"deep/a/b/c/client/.git");
		FileUtils.mkdirs(clientGitDir.getParentFile());
		client = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(clientGitDir)
				.setWorkTree(clientGitDir.getParentFile()).build();
		client.create(false);
		client.getConfig().setString("protocol", null, "version",
				protocolVersion);

		escapedFile = new File(getTemporaryDirectory(), "deep/a/ESCAPED");
	}

	@After
	public void tearDownProtocol() {
		if (testProtocol != null) {
			Transport.unregister(testProtocol);
		}
		if (client != null) {
			client.close();
		}
	}

	/**
	 * A single advertised traversal ref takes the loose-ref write path and must
	 * not create a file outside the client repository.
	 */
	@Test
	public void testFetchRejectsTraversalRefName() throws Exception {
		Map<String, Ref> advertised = new HashMap<>();
		advertise(advertised, HEAD);
		advertise(advertised, TRAVERSAL_REF);

		fetchWildcardTags(advertised);

		assertNoEscape();
		assertNull("traversal ref must not be created locally",
				client.exactRef(TRAVERSAL_REF));
	}

	/**
	 * A funny ref advertised alongside valid ones must be dropped while the
	 * valid refs are still fetched (git's "Ignoring funny ref" /
	 * drop-and-continue behavior). The extra valid refs push the local update
	 * onto the packed-refs path; nothing escapes and no invalid name is packed.
	 */
	@Test
	public void testFetchDropsFunnyRefButKeepsValidRefs() throws Exception {
		Map<String, Ref> advertised = new HashMap<>();
		advertise(advertised, HEAD);
		advertise(advertised, TRAVERSAL_REF);
		advertise(advertised, "refs/tags/good");
		advertise(advertised, "refs/tags/alsogood");

		fetchWildcardTags(advertised);

		assertNoEscape();
		assertNull("traversal ref must not be created locally",
				client.exactRef(TRAVERSAL_REF));
		assertNotNull("valid ref must still be fetched",
				client.exactRef("refs/tags/good"));
		assertNotNull("valid ref must still be fetched",
				client.exactRef("refs/tags/alsogood"));
	}

	private void advertise(Map<String, Ref> refs, String name) {
		refs.put(name, new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, name,
				head.toObjectId()));
	}

	private void assertNoEscape() throws Exception {
		assertFalse("fetch wrote a file outside the client repository: "
				+ escapedFile, escapedFile.exists());
		File packedRefs = new File(client.getDirectory(), "packed-refs");
		if (packedRefs.exists()) {
			String contents = new String(Files.readAllBytes(packedRefs.toPath()),
					UTF_8);
			assertFalse("traversal ref must not be written to packed-refs",
					contents.contains("ESCAPED"));
		}
	}

	private void fetchWildcardTags(Map<String, Ref> advertised)
			throws Exception {
		testProtocol = new TestProtocol<>((Object req, Repository db) -> {
			UploadPack up = new UploadPack(db);
			up.setAdvertisedRefs(advertised);
			return up;
		}, null);
		URIish uri = testProtocol.register(ctx, server);
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			// Tolerate either drop-and-continue or fail-closed; the security
			// property under test is that nothing escapes the repository.
			try {
				tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(
						new RefSpec("+refs/tags/*:refs/tags/*")));
			} catch (TransportException rejected) {
				// acceptable: an implementation may fail the fetch instead of
				// silently dropping the mapping.
			}
		}
	}
}
