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

import static org.junit.Assert.fail;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertTrue;

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
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.junit.Before;
import org.junit.Test;

public class UploadPackHandleDeletedPackFile
		extends LocalDiskRepositoryTestCase {

	private FileRepository server;

	private TestRepository<FileRepository> remote;

	private Repository client;

	private RevCommit head;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createBareRepository();
		server.getConfig().setString("protocol", null, "version", "2");

		remote = new TestRepository<>(server);
		client = new InMemoryRepository(new DfsRepositoryDescription("client"));

		SampleDataRepositoryTestCase.copyCGitTestPacks(server);
		Collection<Pack> packs = server.getObjectDatabase().getPacks();
		new GC(server).gc();
		removeAllOldPacks(packs);
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
		TestProtocol testProtocol = new TestProtocol<>(
				(Object req, Repository db) -> {
					UploadPack up = new UploadPack(db);
					up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
					Collection<Pack> packs = server.getObjectDatabase()
							.getPacks();
					Pack pack = packs.iterator().next();

					try {
						addNewCommit();

						new GC(remote.getRepository()).gc();

						pack.getPackFile().create(packExt).delete();
					} catch (Exception e) {
						fail("GC or pack file removal failed");
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
		RevCommit commit = remote.commit().message("2").parent(head).create();
		remote.update("master", commit);
	}

	private void removeAllOldPacks(Collection<Pack> packs) {
		for (Pack pack : packs) {
			pack.getPackFile().create(PackExt.INDEX).delete();
			pack.getPackFile().create(PackExt.PACK).delete();
			pack.getPackFile().create(PackExt.BITMAP_INDEX).delete();
			continue;
		}
	}
}