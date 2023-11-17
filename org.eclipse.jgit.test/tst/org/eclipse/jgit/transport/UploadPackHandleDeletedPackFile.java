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
import static org.junit.Assert.assertEquals;
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
		server = createWorkRepository();
		server.getConfig().setString("protocol", null, "version", "2");

		remote = new TestRepository<>(server);
		client = new InMemoryRepository(new DfsRepositoryDescription("client"));

		setupServerRepo();
	}

	@Test
	public void testV2PackFileRemovedDuringUpload() throws Exception {
		doRemovePackFileWhileFetching(PackExt.PACK);
	}

	@Test
	public void testV2IdxFileRemovedDuringUpload() throws Exception {
		doRemovePackFileWhileFetching(PackExt.INDEX);
	}

	@Test
	public void testV2BitmapFileRemovedDuringUpload() throws Exception {
		doRemovePackFileWhileFetching(PackExt.BITMAP_INDEX);
	}

	private void doRemovePackFileWhileFetching(final PackExt packExt)
			throws Exception {
		Object ctx = new Object();
		TestProtocol testProtocol = new TestProtocol<>(
				(Object req, Repository db) -> {
					UploadPack up = new UploadPack(db);
					up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);

					Collection<Pack> packs = server.getObjectDatabase()
							.getPacks();
					assertEquals(1, packs.size());
					final Pack pack = packs.iterator().next();

					// repack repository and remove old pack
					try {
						RevCommit commit2 = remote.commit().message("2")
								.parent(head).create();
						remote.update("master", commit2);

						new GC(remote.getRepository()).gc();
						pack.getPackFile().create(packExt).delete();
					} catch (Exception e) {
						fail("GC or pack file removal failed");
					}

					return up;
				}, null);

		URIish uri = testProtocol.register(ctx, server);

		// Fetch of the parent of the shallow commit
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(head.name())));
			assertTrue(client.getObjectDatabase().has(head));
		}
	}

	private void setupServerRepo() throws Exception {
		RevCommit commit0 = remote.commit().message("0")
				.add("test.txt", remote.blob("test.txt"))
				.create();
		remote.update("master", commit0);

		new GC(remote.getRepository()).gc(); // create pack files

		head = remote.commit().message("1").parent(commit0)
				.create();
		remote.update("master", head);
	}
}