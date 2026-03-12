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
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.revwalk.RefAdvancerWalk;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackBitmapCalculator;
import org.eclipse.jgit.internal.storage.pack.PackBitmapIndexWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
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
	 * @param packConfig
	 *            pack config with parameter to build bitmaps. Null to disable
	 *            bitmaps.
	 * @return a pack (uncommitted) with the multipack index of the packs passed
	 *         as parameter.
	 * @throws IOException
	 *             an error opening the packs or writing the stream.
	 */
	public static DfsPackDescription writeMidx(ProgressMonitor pm,
			DfsObjDatabase objdb, List<DfsPackFile> packs,
			@Nullable DfsPackDescription base, @Nullable PackConfig packConfig)
			throws IOException {
		PackIndexMerger.Builder dataBuilder = PackIndexMerger.builder();
		try (DfsReader ctx = objdb.newReader()) {
			for (DfsPackFile pack : packs) {
				dataBuilder.addPack(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
			}
		}

		PackIndexMerger data = dataBuilder.build();
		byte[] checksum;

		DfsPackDescription midxPackDesc = objdb.newPack(GC);
		try (DfsOutputStream out = objdb.writeFile(midxPackDesc,
				MULTI_PACK_INDEX)) {
			MultiPackIndexWriter w = new MultiPackIndexWriter();
			MultiPackIndexWriter.Result result = w.write(pm, out, data);
			midxPackDesc.addFileExt(MULTI_PACK_INDEX);
			midxPackDesc.setFileSize(MULTI_PACK_INDEX, result.bytesWritten());
			midxPackDesc.setObjectCount(result.objectCount());
			checksum = result.checksum();

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

		// TODO(ifrade): At the moment we only support bitmaps on the base
		if (base != null && packConfig != null) {
			createAndAttachBitmaps(objdb.getRepository(), midxPackDesc,
					checksum, data, packs,
					packConfig);
		}

		return midxPackDesc;
	}

	private static void createAndAttachBitmaps(DfsRepository db,
			DfsPackDescription desc, byte[] checksum, PackIndexMerger data,
			List<DfsPackFile> packs, PackConfig cfg) throws IOException {

		// TODO(ifrade): Verify we duplicate the behaviour about tags of regular
		// bitmapping
		List<ObjectId> allHeads = db.getRefDatabase()
				.getRefsByPrefix(Constants.R_HEADS).stream()
				.map(r -> r.getObjectId()).filter(Objects::nonNull).toList();
		if (allHeads.isEmpty()) {
			return;
		}

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectIdOwnerMap<ObjectToPack> byId = new ObjectIdOwnerMap<>();
			List<ObjectToPack> otps = asObjectsToPack(ctx, data, packs, byId);

			RefAdvancerWalk adv = new RefAdvancerWalk(db,
					c -> byId.contains(c));
			Set<RevCommit> inPack = adv.advance(allHeads);

			PackBitmapIndexBuilder writeBitmaps = new PackBitmapIndexBuilder(
					otps);
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

	private static List<ObjectToPack> asObjectsToPack(DfsReader ctx,
			PackIndexMerger data, List<DfsPackFile> packs,
			ObjectIdOwnerMap<ObjectToPack> byId) throws IOException {
		long[] accPackSize = new long[packs.size()];
		for (int i = 1; i < packs.size(); i++) {
			long prevValue = accPackSize[i - 1];
			accPackSize[i] = prevValue
					+ packs.get(i - 1).getPackDescription().getFileSize(PACK);
		}

		List<ObjectToPack> result = new ArrayList<>(
				data.getUniqueObjectCount());
		MultiPackIndex.MidxIterator it = data.bySha1Iterator();
		while (it.hasNext()) {
			MultiPackIndex.MutableEntry entry = it.next();
			int objectType = packs.get(entry.getPackId()).getObjectType(ctx,
					entry.getOffset());
			ObjectToPack o = new ObjectToPack(entry.getObjectId().toObjectId(),
					objectType);
			o.setOffset(accPackSize[entry.getPackId()] + entry.getOffset());
			result.add(o);
			byId.add(o);
		}
		return result;
	}

}
