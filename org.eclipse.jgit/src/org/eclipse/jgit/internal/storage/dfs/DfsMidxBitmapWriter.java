/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.internal.storage.pack.PackBitmapCalculator;
import org.eclipse.jgit.internal.storage.pack.PackBitmapIndexWriter;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.pack.PackConfig;

public class DfsMidxBitmapWriter {

	static DfsPackFileMidx createAndAttachBitmaps(DfsRepository db,
			DfsPackFileMidx midx) throws IOException {

		DfsPackDescription midxDesc = midx.getPackDescription();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			List<ObjectId> allTips = db.getRefDatabase().getRefs().stream()
					.map(Ref::getObjectId).toList();

			PackBitmapIndexBuilder writeBitmaps = new PackBitmapIndexBuilder(
					midx.getLocalObjects(ctx));
			int commitCount = writeBitmaps.getCommits().cardinality();

			PackBitmapCalculator calculator = new PackBitmapCalculator(
					new PackConfig());
			calculator.calculate(ctx, NullProgressMonitor.INSTANCE, commitCount,
					new HashSet<>(allTips), new HashSet<>(), writeBitmaps);

			PackBitmapIndexWriter pbiWriter = db.getObjectDatabase()
					.getPackBitmapIndexWriter(midxDesc);
			pbiWriter.write(writeBitmaps, midx.getChecksum(ctx));
		}
		return midx;
	}
}
