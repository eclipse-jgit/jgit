/*
 * Copyright (C) 2019, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.reftable.Reftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableBatchRefUpdate;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableStack;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;

/**
 * {@link org.eclipse.jgit.lib.BatchRefUpdate} for
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase}.
 */
public class DfsReftableBatchRefUpdate extends ReftableBatchRefUpdate {
	private static final int AVG_BYTES = 36;

	private final DfsReftableDatabase refdb;

	private final DfsObjDatabase odb;

	private final ReftableConfig reftableConfig;

	/**
	 * Initialize batch update.
	 *
	 * @param refdb
	 *            database the update will modify.
	 * @param odb
	 *            object database to store the reftable.
	 */
	protected DfsReftableBatchRefUpdate(DfsReftableDatabase refdb,
			DfsObjDatabase odb) {
		super(refdb, refdb.getLock(), refdb.getRepository());
		this.refdb = refdb;
		this.odb = odb;
		reftableConfig = refdb.getReftableConfig();
	}

	@Override
	protected void applyUpdates(List<Ref> newRefs, List<ReceiveCommand> pending)
			throws IOException {
		Set<DfsPackDescription> prune = Collections.emptySet();
		DfsPackDescription pack = odb.newPack(PackSource.INSERT);
		try (DfsOutputStream out = odb.writeFile(pack, REFTABLE)) {
			ReftableConfig cfg = DfsPackCompactor
					.configureReftable(reftableConfig, out);

			ReftableWriter.Stats stats;
			if (refdb.compactDuringCommit()
					&& newRefs.size() * AVG_BYTES <= cfg.getRefBlockSize()
					&& canCompactTopOfStack(cfg)) {
				ByteArrayOutputStream tmp = new ByteArrayOutputStream();
				ReftableWriter rw = new ReftableWriter(cfg, tmp);
				write(rw, newRefs, pending);
				stats = compactTopOfStack(out, cfg, tmp.toByteArray());
				prune = toPruneTopOfStack();
			} else {
				ReftableWriter rw = new ReftableWriter(cfg, out);
				write(rw, newRefs, pending);
				stats = rw.getStats();
			}
			pack.addFileExt(REFTABLE);
			pack.setReftableStats(stats);
		}

		odb.commitPack(Collections.singleton(pack), prune);
		odb.addReftable(pack, prune);
		refdb.clearCache();
	}

	private boolean canCompactTopOfStack(ReftableConfig cfg)
			throws IOException {
		DfsReftableStack stack = refdb.stack();
		List<Reftable> readers = stack.readers();
		if (readers.isEmpty()) {
			return false;
		}

		int lastIdx = readers.size() - 1;
		DfsReftable last = stack.files().get(lastIdx);
		DfsPackDescription desc = last.getPackDescription();
		if (desc.getPackSource() != PackSource.INSERT
				|| !packOnlyContainsReftable(desc)) {
			return false;
		}

		Reftable table = readers.get(lastIdx);
		int bs = cfg.getRefBlockSize();
		return table instanceof ReftableReader
				&& ((ReftableReader) table).size() <= 3 * bs;
	}

	private ReftableWriter.Stats compactTopOfStack(OutputStream out,
			ReftableConfig cfg, byte[] newTable) throws IOException {
		List<Reftable> stack = refdb.stack().readers();
		Reftable last = stack.get(stack.size() - 1);

		List<Reftable> tables = new ArrayList<>(2);
		tables.add(last);
		tables.add(new ReftableReader(BlockSource.from(newTable)));

		ReftableCompactor compactor = new ReftableCompactor();
		compactor.setConfig(cfg);
		compactor.setIncludeDeletes(true);
		compactor.addAll(tables);
		compactor.compact(out);
		return compactor.getStats();
	}

	private Set<DfsPackDescription> toPruneTopOfStack() throws IOException {
		List<DfsReftable> stack = refdb.stack().files();
		DfsReftable last = stack.get(stack.size() - 1);
		return Collections.singleton(last.getPackDescription());
	}

	private boolean packOnlyContainsReftable(DfsPackDescription desc) {
		for (PackExt ext : PackExt.values()) {
			if (ext != REFTABLE && desc.hasFileExt(ext)) {
				return false;
			}
		}
		return true;
	}
}
