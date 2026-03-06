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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.PackBitmapCalculator;
import org.eclipse.jgit.internal.storage.pack.PackBitmapIndexWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;

/**
 * Create a pack with a multipack index, setting the required fields in the
 * description.
 */
public class DfsMidxWriter {

	private DfsMidxWriter() {
	}

	/**
	 * Create a pack with the multipack index
	 *
	 * @param pm
	 *            a progress monitor
	 * @param objdb
	 *            an object database
	 * @param packs
	 *            the packs to cover
	 * @param base
	 *            parent of this midx in the chain (if any).
	 * @return a pack (uncommitted) with the multipack index of the packs passed
	 *         as parameter.
	 * @throws IOException
	 *             an error opening the packs or writing the stream.
	 */
	public static DfsPackDescription writeMidx(ProgressMonitor pm,
			DfsObjDatabase objdb, List<DfsPackFile> packs,
			@Nullable DfsPackDescription base)
			throws IOException {
		PackIndexMerger.Builder dataBuilder = PackIndexMerger.builder();
		try (DfsReader ctx = objdb.newReader()) {
			for (DfsPackFile pack : packs) {
				dataBuilder.addPack(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
			}
		}

		DfsPackDescription midxPackDesc = objdb.newPack(GC);
		try (DfsOutputStream out = objdb.writeFile(midxPackDesc,
				MULTI_PACK_INDEX)) {
			MultiPackIndexWriter w = new MultiPackIndexWriter();
			MultiPackIndexWriter.Result result = w.write(pm, out,
					dataBuilder.build());
			midxPackDesc.addFileExt(MULTI_PACK_INDEX);
			midxPackDesc.setFileSize(MULTI_PACK_INDEX, result.bytesWritten());
			midxPackDesc.setObjectCount(result.objectCount());

			Map<String, DfsPackDescription> byName = packs.stream()
					.map(DfsPackFile::getPackDescription)
					.collect(toMap(DfsPackDescription::getPackName,
							Function.identity()));
			List<DfsPackDescription> coveredPacks = result.packNames().stream()
					.map(byName::get).collect(toList());
			midxPackDesc.setCoveredPacks(coveredPacks);
			if (base != null) {
				midxPackDesc.setMultiPackIndexBase(base);
			}
		}

		return midxPackDesc;
	}

	/**
	 * Calculate bitmps for the midx and add them as a stream in the description
	 *
	 * @param db
	 *            the repository
	 * @param desc
	 *            the pack description of the midx
	 * @param cfg
	 *            pack config with the parameters for the bitmaps
	 * @throws IOException
	 *             an error reading from storage
	 */
	public static void createAndAttachBitmaps(DfsRepository db,
			DfsPackDescription desc, PackConfig cfg) throws IOException {

		DfsObjDatabase objdb = db.getObjectDatabase();
		// We need a DfsPackFile to reread the contents
		DfsPackFileMidx midxPack = db.getObjectDatabase().createDfsPackFileMidx(
				DfsBlockCache.getInstance(), desc, new ArrayList<>());

		// TODO(ifrade): Verify we duplicate the behaviour about tags of regular
		// bitmapping
		List<ObjectId> allHeads = db.getRefDatabase()
				.getRefsByPrefix(Constants.R_HEADS).stream()
				.map(r -> r.getObjectId()).filter(Objects::nonNull).toList();
		if (allHeads.isEmpty()) {
			return;
		}

		try (DfsReader ctx = objdb.newReader()) {
			RefAdvancerWalk adv = new RefAdvancerWalk(db,
					c -> midxPack.hasObject(ctx, c));
			Set<RevCommit> inPack = adv.advance(allHeads);

			byte[] checksum = midxPack.getChecksum(ctx);
			PackBitmapIndexBuilder writeBitmaps = new PackBitmapIndexBuilder(
					midxPack.getLocalObjects(ctx));
			int commitCount = writeBitmaps.getCommits().cardinality();

			PackBitmapCalculator calculator = new PackBitmapCalculator(cfg);
			// This will do ctx.getBitmapIndex() to reuse/copy previous bitmaps
			calculator.calculate(ctx, NullProgressMonitor.INSTANCE, commitCount,
					inPack, new HashSet<>(), writeBitmaps);
			PackBitmapIndexWriter pbiWriter = db.getObjectDatabase()
					.getPackBitmapIndexWriter(desc);
			pbiWriter.write(writeBitmaps, checksum);
		}
	}

}
