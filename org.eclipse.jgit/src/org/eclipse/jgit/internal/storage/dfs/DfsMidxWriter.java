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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.lib.ProgressMonitor;

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
			@Nullable DfsPackDescription base) throws IOException {
		LinkedHashMap<String, PackIndex> inputs = new LinkedHashMap<>(
				packs.size());
		try (DfsReader ctx = objdb.newReader()) {
			for (DfsPackFile pack : packs) {
				inputs.put(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
			}
		}

		DfsPackDescription midxPackDesc = objdb.newPack(GC);
		try (DfsOutputStream out = objdb.writeFile(midxPackDesc,
				MULTI_PACK_INDEX)) {
			MultiPackIndexWriter w = new MultiPackIndexWriter();
			MultiPackIndexWriter.Result result = w.write(pm, out, inputs);
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
}
