/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;

import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Helper to write multipack indexes.
 */
public class MidxWriter {

	/**
	 * Write a mdix over the packs
	 *
	 * @param pm
	 *            a progress monitor
	 * @param packs
	 *            packs to cover with the midx
	 * @param midxOut
	 *            file to write the resulting midx
	 * @throws IOException
	 *             an error reading any of the input packs or indexes
	 */
	public static void writeMidx(ProgressMonitor pm, Collection<Pack> packs,
			File midxOut) throws IOException {
		PackIndexMerger.Builder builder = PackIndexMerger.builder();
		builder.setProgressMonitor(pm);

		Collection<Pack> packList = packs.stream()
				.sorted(Comparator.comparing(Pack::getPackName)).toList();
		pm.beginTask("Adding packs to midx", packList.size());
		for (Pack pack : packList) {
			PackFile packFile = pack.getPackFile().create(PackExt.INDEX);
			builder.addPack(packFile.getName(), pack.getIndex());
			pm.update(1);
		}
		pm.endTask();

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		try (FileOutputStream out = new FileOutputStream(
				midxOut.getAbsolutePath())) {
			writer.write(pm, out, builder.build());
		}
	}
}
