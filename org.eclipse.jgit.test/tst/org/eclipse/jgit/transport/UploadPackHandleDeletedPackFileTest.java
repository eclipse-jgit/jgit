/*
 * Copyright (C) 2023, Dariusz Luksza <dariusz.luksza@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UploadPackHandleDeletedPackFileTest
		extends LocalDiskRepositoryTestCase {

	private FileRepository server;

	private TestRepository<FileRepository> remote;

	private Repository client;

	private RevCommit head;

	@Parameter
	public boolean emptyCommit;

	@Parameters(name="empty commit: {0}")
	public static Collection<Boolean[]> initTestData() {
		return Arrays.asList(
				new Boolean[][] { { Boolean.TRUE }, { Boolean.FALSE } });
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createBareRepository();
		server.getConfig().setString("protocol", null, "version", "2");

		remote = new TestRepository<>(server);
		client = new InMemoryRepository(new DfsRepositoryDescription("client"));

		setupServerRepo();
		head = server.parseCommit(server.resolve(HEAD));
	}

	@Test
	public void testV2PackFileRemovedDuringUploadPack() throws Exception {
		doRemovePackFileDuringUploadPack(PackExt.PACK);
	}

	@Test
	public void testV2IdxFileRemovedDuringUploadPack() throws Exception {
		doRemovePackFileDuringUploadPack(PackExt.INDEX);
	}

	@Test
	public void testV2BitmapFileRemovedDuringUploadPack() throws Exception {
		doRemovePackFileDuringUploadPack(PackExt.BITMAP_INDEX);
	}

	private void doRemovePackFileDuringUploadPack(PackExt packExt)
			throws Exception {
		Object ctx = new Object();
		TestProtocol<Object> testProtocol = new TestProtocol<>(
				(Object req, Repository db) -> {
					UploadPack up = new UploadPack(db);
					up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
					Collection<Pack> packs = server.getObjectDatabase()
							.getPacks();
					assertEquals("single pack expected", 1, packs.size());
					Pack pack = packs.iterator().next();

					try {
						addNewCommit();

						new GC(remote.getRepository()).gc().get();

						pack.getPackFile().create(packExt).delete();
					} catch (Exception e) {
						throw new AssertionError(
								"GC or pack file removal failed", e);
					}

					return up;
				}, null);

		URIish uri = testProtocol.register(ctx, server);

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(head.name())));
			assertTrue(client.getObjectDatabase().has(head));
		}
	}

	private void addNewCommit() throws Exception {
		CommitBuilder commit = remote.commit().message("2");
		if (!emptyCommit) {
			commit = commit.add("test2.txt", remote.blob("2"));
		}
		remote.update("master", commit.parent(head).create());
	}

	private void setupServerRepo() throws Exception {
		RevCommit commit0 = remote.commit().message("0")
				.add("test.txt", remote.blob("0"))
				.create();
		remote.update("master", commit0);

		new GC(remote.getRepository()).gc().get(); // create pack files

		head = remote.commit().message("1").parent(commit0)
				.add("test1.txt", remote.blob("1"))
				.create();
		remote.update("master", head);
	}
}