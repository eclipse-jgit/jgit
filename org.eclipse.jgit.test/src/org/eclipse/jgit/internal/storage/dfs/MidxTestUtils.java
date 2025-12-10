/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Helpers to write multipack indexes
 */
public class MidxTestUtils {
	private MidxTestUtils() {
	}

	/**
	 * Write a single pack into the repo with the blob as contents
	 *
	 * @param db
	 *            repository
	 * @param blob
	 *            blob to write into the pack
	 * @return object id of the blob written in the pack
	 * @throws IOException
	 *             a problem writing in the repo
	 */
	static ObjectId writePackWithBlob(DfsRepository db, String blob)
			throws IOException {
		return writePackWithBlobs(db, blob)[0];
	}

	/**
	 * Write multiple blobs into a single pack in the repo
	 *
	 * @param db
	 *            repository
	 * @param blobs
	 *            blobs to write into the pack
	 * @return object ids of the blobs written in the pack, in the same order as
	 *         the input parameters
	 * @throws IOException
	 *             a problem writing in the repo
	 */
	static ObjectId[] writePackWithBlobs(DfsRepository db, String... blobs)
			throws IOException {
		ObjectId[] oids = new ObjectId[blobs.length];

		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		for (int i = 0; i < blobs.length; i++) {
			oids[i] = ins.insert(OBJ_BLOB, blobs[i].getBytes(UTF_8));
		}
		ins.flush();
		return oids;
	}

	/**
	 * Write a midx covering the only pack in the repo
	 *
	 * @param db
	 *            a repository with a single pack
	 * @return a midx covering that single pack
	 * @throws IOException
	 *             a problem writing in the repo
	 */
	static DfsPackFileMidx writeSinglePackMidx(DfsRepository db)
			throws IOException {
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals("More than one pack in db", 1, packs.length);
		return writeSinglePackMidx(db, packs[0]);
	}

	/**
	 * Write a midx covering a single pack
	 *
	 * @param db
	 *            a repository to write the midx covering the pack
	 * @param pack
	 *            a pack in the repository that will be covered by a new midx
	 * @return a midx covering that single pack
	 * @throws IOException
	 *             a problem writing in the repo
	 */
	static DfsPackFileMidx writeSinglePackMidx(DfsRepository db,
			DfsPackFile pack) throws IOException {
		return writeMultipackIndex(db, new DfsPackFile[] { pack }, null);
	}

	/**
	 * Write a midx in the repository
	 *
	 * @param db
	 *            the repository
	 * @param packs
	 *            packs to be covered by this midx
	 * @param base
	 *            base of the newly created midx
	 * @return the new midx instance
	 * @throws IOException
	 *             a problem writing in the repo
	 */
	static DfsPackFileMidx writeMultipackIndex(DfsRepository db,
			DfsPackFile[] packs, DfsPackFileMidx base) throws IOException {
		LinkedHashMap<String, PackIndex> forMidx = new LinkedHashMap<>(
				packs.length);
		Map<String, DfsPackDescription> descByName = new HashMap<>(
				packs.length);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			for (DfsPackFile pack : packs) {
				forMidx.put(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
				descByName.put(pack.getPackDescription().getPackName(),
						pack.getPackDescription());
			}
		}
		MultiPackIndexWriter w = new MultiPackIndexWriter();
		DfsPackDescription desc = db.getObjectDatabase().newPack(GC);
		try (DfsOutputStream out = db.getObjectDatabase().writeFile(desc,
				PackExt.MULTI_PACK_INDEX)) {
			MultiPackIndexWriter.Result midxStats = w
					.write(NullProgressMonitor.INSTANCE, out, forMidx);
			desc.setCoveredPacks(midxStats.packNames().stream()
					.map(descByName::get).toList());
			desc.setObjectCount(midxStats.objectCount());
			desc.addFileExt(PackExt.MULTI_PACK_INDEX);
		}
		db.getObjectDatabase().commitPack(List.of(desc), null);
		return DfsPackFileMidx.create(DfsBlockCache.getInstance(), desc,
				Arrays.asList(packs), base);
	}
}
