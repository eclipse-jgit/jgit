/*
 * Copyright (C) 2023, Google LLC and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.transport.InMemoryPack;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Before;
import org.junit.Test;

public class DfsPackParserTest {
  private InMemoryRepository repo;


	@Before
	public void setUp() throws Exception {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		repo = new InMemoryRepository(desc);
		repo.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, 0);
	}

	@Test
	public void parse_writeObjSizeIdx() throws IOException {
		InMemoryPack pack = new InMemoryPack();

		// Sha1 of the blob "a"
		ObjectId blobA = ObjectId
				.fromString("2e65efe2a145dda7ee51d1741299f848e5bf752e");

		pack.header(2);
		pack.write((Constants.OBJ_BLOB) << 4 | 1);
		pack.deflate(new byte[] { 'a' });

		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		pack.copyRaw(blobA);
		pack.deflate(new byte[] { 0x1, 0x1, 0x1, 'b' });
		pack.digest();

		try (ObjectInserter ins = repo.newObjectInserter()) {
			PackParser parser = ins.newPackParser(pack.toInputStream());
			parser.parse(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE);
			ins.flush();
		}

		DfsReader reader = repo.getObjectDatabase().newReader();
		PackList packList = repo.getObjectDatabase().getPackList();
		assertEquals(1, packList.packs.length);
		assertEquals(1, packList.packs[0].getIndexedObjectSize(reader, blobA));
	}
}
