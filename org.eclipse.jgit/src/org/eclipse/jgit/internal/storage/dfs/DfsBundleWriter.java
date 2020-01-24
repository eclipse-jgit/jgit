/*
 * Copyright (c) 2020, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.BundleWriter;

/** Writes {@link DfsRepository} to a Git bundle. */
public class DfsBundleWriter {
	/**
	 * Writes the entire {@link DfsRepository} to a Git bundle.
	 * <p>
	 * This method try to avoid traversing the pack files as much as possible
	 * and dumps all objects as-is to a Git bundle.
	 *
	 * @param pm
	 *            progress monitor
	 * @param os
	 *            Git bundle output
	 * @param db
	 *            repository
	 * @throws IOException
	 *             thrown if the output stream throws one.
	 */
	public static void writeEntireRepositoryAsBundle(ProgressMonitor pm,
			OutputStream os, DfsRepository db) throws IOException {
		BundleWriter bw = new BundleWriter(db);
		db.getRefDatabase().getRefs().forEach(bw::include);
		List<CachedPack> packs = new ArrayList<>();
		for (DfsPackFile p : db.getObjectDatabase().getPacks()) {
			packs.add(new DfsCachedPack(p));
		}
		bw.addObjectsAsIs(packs);
		bw.writeBundle(pm, os);
	}

	private DfsBundleWriter() {
	}
}
