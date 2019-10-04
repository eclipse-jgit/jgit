/*
 * Copyright (C) 2011, Google Inc.
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

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.COMPACT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;
import static org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation.PACK_DELTA;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.io.CountingOutputStream;

/**
 * Combine several pack files into one pack.
 * <p>
 * The compactor combines several pack files together by including all objects
 * contained in each pack file into the same output pack. If an object appears
 * multiple times, it is only included once in the result. Because the new pack
 * is constructed by enumerating the indexes of the source packs, it is quicker
 * than doing a full repack of the repository, however the result is not nearly
 * as space efficient as new delta compression is disabled.
 * <p>
 * This method is suitable for quickly combining several packs together after
 * receiving a number of small fetch or push operations into a repository,
 * allowing the system to maintain reasonable read performance without expending
 * a lot of time repacking the entire repository.
 */
public class DfsPackCompactor {
	private final DfsRepository repo;
	private final List<DfsPackFile> srcPacks;
	private final List<DfsReftable> srcReftables;
	private final List<ObjectIdSet> exclude;

	private PackStatistics newStats;
	private DfsPackDescription outDesc;

	private int autoAddSize;
	private ReftableConfig reftableConfig;

	private RevWalk rw;
	private RevFlag added;
	private RevFlag isBase;

	/**
	 * Initialize a pack compactor.
	 *
	 * @param repository
	 *            repository objects to be packed will be read from.
	 */
	public DfsPackCompactor(DfsRepository repository) {
		repo = repository;
		autoAddSize = 5 * 1024 * 1024; // 5 MiB
		srcPacks = new ArrayList<>();
		srcReftables = new ArrayList<>();
		exclude = new ArrayList<>(4);
	}

	/**
	 * Set configuration to write a reftable.
	 *
	 * @param cfg
	 *            configuration to write a reftable. Reftable compacting is
	 *            disabled (default) when {@code cfg} is {@code null}.
	 * @return {@code this}
	 */
	public DfsPackCompactor setReftableConfig(ReftableConfig cfg) {
		reftableConfig = cfg;
		return this;
	}

	/**
	 * Add a pack to be compacted.
	 * <p>
	 * All of the objects in this pack will be copied into the resulting pack.
	 * The resulting pack will order objects according to the source pack's own
	 * description ordering (which is based on creation date), and then by the
	 * order the objects appear in the source pack.
	 *
	 * @param pack
	 *            a pack to combine into the resulting pack.
	 * @return {@code this}
	 */
	public DfsPackCompactor add(DfsPackFile pack) {
		srcPacks.add(pack);
		return this;
	}

	/**
	 * Add a reftable to be compacted.
	 *
	 * @param table
	 *            a reftable to combine.
	 * @return {@code this}
	 */
	public DfsPackCompactor add(DfsReftable table) {
		srcReftables.add(table);
		return this;
	}

	/**
	 * Automatically select pack and reftables to be included, and add them.
	 * <p>
	 * Packs are selected based on size, smaller packs get included while bigger
	 * ones are omitted.
	 *
	 * @return {@code this}
	 * @throws java.io.IOException
	 *             existing packs cannot be read.
	 */
	public DfsPackCompactor autoAdd() throws IOException {
		DfsObjDatabase objdb = repo.getObjectDatabase();
		for (DfsPackFile pack : objdb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getFileSize(PACK) < autoAddSize)
				add(pack);
			else
				exclude(pack);
		}

		if (reftableConfig != null) {
			for (DfsReftable table : objdb.getReftables()) {
				DfsPackDescription d = table.getPackDescription();
				if (d.getPackSource() != GC
						&& d.getFileSize(REFTABLE) < autoAddSize) {
					add(table);
				}
			}
		}
		return this;
	}

	/**
	 * Exclude objects from the compacted pack.
	 *
	 * @param set
	 *            objects to not include.
	 * @return {@code this}.
	 */
	public DfsPackCompactor exclude(ObjectIdSet set) {
		exclude.add(set);
		return this;
	}

	/**
	 * Exclude objects from the compacted pack.
	 *
	 * @param pack
	 *            objects to not include.
	 * @return {@code this}.
	 * @throws java.io.IOException
	 *             pack index cannot be loaded.
	 */
	public DfsPackCompactor exclude(DfsPackFile pack) throws IOException {
		final PackIndex idx;
		try (DfsReader ctx = (DfsReader) repo.newObjectReader()) {
			idx = pack.getPackIndex(ctx);
		}
		return exclude(idx);
	}

	/**
	 * Compact the pack files together.
	 *
	 * @param pm
	 *            progress monitor to receive updates on as packing may take a
	 *            while, depending on the size of the repository.
	 * @throws java.io.IOException
	 *             the packs cannot be compacted.
	 */
	public void compact(ProgressMonitor pm) throws IOException {
		if (pm == null) {
			pm = NullProgressMonitor.INSTANCE;
		}

		DfsObjDatabase objdb = repo.getObjectDatabase();
		try (DfsReader ctx = objdb.newReader()) {
			if (reftableConfig != null && !srcReftables.isEmpty()) {
				compactReftables(ctx);
			}
			compactPacks(ctx, pm);

			List<DfsPackDescription> commit = getNewPacks();
			Collection<DfsPackDescription> remove = toPrune();
			if (!commit.isEmpty() || !remove.isEmpty()) {
				objdb.commitPack(commit, remove);
			}
		} finally {
			rw = null;
		}
	}

	private void compactPacks(DfsReader ctx, ProgressMonitor pm)
			throws IOException, IncorrectObjectTypeException {
		DfsObjDatabase objdb = repo.getObjectDatabase();
		PackConfig pc = new PackConfig(repo);
		pc.setIndexVersion(2);
		pc.setDeltaCompress(false);
		pc.setReuseDeltas(true);
		pc.setReuseObjects(true);

		try (PackWriter pw = new PackWriter(pc, ctx)) {
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(false);

			addObjectsToPack(pw, ctx, pm);
			if (pw.getObjectCount() == 0) {
				return;
			}

			boolean rollback = true;
			initOutDesc(objdb);
			try {
				writePack(objdb, outDesc, pw, pm);
				writeIndex(objdb, outDesc, pw);

				PackStatistics stats = pw.getStatistics();

				outDesc.setPackStats(stats);
				newStats = stats;
				rollback = false;
			} finally {
				if (rollback) {
					objdb.rollbackPack(Collections.singletonList(outDesc));
				}
			}
		}
	}

	private long estimatePackSize() {
		// Every pack file contains 12 bytes of header and 20 bytes of trailer.
		// Include the final pack file header and trailer size here and ignore
		// the same from individual pack files.
		long size = 32;
		for (DfsPackFile pack : srcPacks) {
			size += pack.getPackDescription().getFileSize(PACK) - 32;
		}
		return size;
	}

	private void compactReftables(DfsReader ctx) throws IOException {
		DfsObjDatabase objdb = repo.getObjectDatabase();
		Collections.sort(srcReftables, objdb.reftableComparator());

		initOutDesc(objdb);
		try (DfsReftableStack stack = DfsReftableStack.open(ctx, srcReftables);
		     DfsOutputStream out = objdb.writeFile(outDesc, REFTABLE)) {
			ReftableCompactor compact = new ReftableCompactor(out);
			compact.addAll(stack.readers());
			compact.setIncludeDeletes(true);
			compact.setConfig(configureReftable(reftableConfig, out));
			compact.compact();
			outDesc.addFileExt(REFTABLE);
			outDesc.setReftableStats(compact.getStats());
		}
	}

	private void initOutDesc(DfsObjDatabase objdb) throws IOException {
		if (outDesc == null) {
			outDesc = objdb.newPack(COMPACT, estimatePackSize());
		}
	}

	/**
	 * Get all of the source packs that fed into this compaction.
	 *
	 * @return all of the source packs that fed into this compaction.
	 */
	public Collection<DfsPackDescription> getSourcePacks() {
		Set<DfsPackDescription> src = new HashSet<>();
		for (DfsPackFile pack : srcPacks) {
			src.add(pack.getPackDescription());
		}
		for (DfsReftable table : srcReftables) {
			src.add(table.getPackDescription());
		}
		return src;
	}

	/**
	 * Get new packs created by this compaction.
	 *
	 * @return new packs created by this compaction.
	 */
	public List<DfsPackDescription> getNewPacks() {
		return outDesc != null
				? Collections.singletonList(outDesc)
				: Collections.emptyList();
	}

	/**
	 * Get statistics corresponding to the {@link #getNewPacks()}.
	 * May be null if statistics are not available.
	 *
	 * @return statistics corresponding to the {@link #getNewPacks()}.
	 *
	 */
	public List<PackStatistics> getNewPackStatistics() {
		return outDesc != null
				? Collections.singletonList(newStats)
				: Collections.emptyList();
	}

	private Collection<DfsPackDescription> toPrune() {
		Set<DfsPackDescription> packs = new HashSet<>();
		for (DfsPackFile pack : srcPacks) {
			packs.add(pack.getPackDescription());
		}

		Set<DfsPackDescription> reftables = new HashSet<>();
		for (DfsReftable table : srcReftables) {
			reftables.add(table.getPackDescription());
		}

		for (Iterator<DfsPackDescription> i = packs.iterator(); i.hasNext();) {
			DfsPackDescription d = i.next();
			if (d.hasFileExt(REFTABLE) && !reftables.contains(d)) {
				i.remove();
			}
		}

		for (Iterator<DfsPackDescription> i = reftables.iterator();
				i.hasNext();) {
			DfsPackDescription d = i.next();
			if (d.hasFileExt(PACK) && !packs.contains(d)) {
				i.remove();
			}
		}

		Set<DfsPackDescription> toPrune = new HashSet<>();
		toPrune.addAll(packs);
		toPrune.addAll(reftables);
		return toPrune;
	}

	private void addObjectsToPack(PackWriter pw, DfsReader ctx,
			ProgressMonitor pm) throws IOException,
			IncorrectObjectTypeException {
		// Sort packs by description ordering, this places newer packs before
		// older packs, allowing the PackWriter to be handed newer objects
		// first and older objects last.
		Collections.sort(
				srcPacks,
				Comparator.comparing(
						DfsPackFile::getPackDescription,
						DfsPackDescription.objectLookupComparator()));

		rw = new RevWalk(ctx);
		added = rw.newFlag("ADDED"); //$NON-NLS-1$
		isBase = rw.newFlag("IS_BASE"); //$NON-NLS-1$
		List<RevObject> baseObjects = new BlockList<>();

		pm.beginTask(JGitText.get().countingObjects, ProgressMonitor.UNKNOWN);
		for (DfsPackFile src : srcPacks) {
			List<ObjectIdWithOffset> want = toInclude(src, ctx);
			if (want.isEmpty())
				continue;

			PackReverseIndex rev = src.getReverseIdx(ctx);
			DfsObjectRepresentation rep = new DfsObjectRepresentation(src);
			for (ObjectIdWithOffset id : want) {
				int type = src.getObjectType(ctx, id.offset);
				RevObject obj = rw.lookupAny(id, type);
				if (obj.has(added))
					continue;

				pm.update(1);
				pw.addObject(obj);
				obj.add(added);

				src.representation(rep, id.offset, ctx, rev);
				if (rep.getFormat() != PACK_DELTA)
					continue;

				RevObject base = rw.lookupAny(rep.getDeltaBase(), type);
				if (!base.has(added) && !base.has(isBase)) {
					baseObjects.add(base);
					base.add(isBase);
				}
			}
		}
		for (RevObject obj : baseObjects) {
			if (!obj.has(added)) {
				pm.update(1);
				pw.addObject(obj);
				obj.add(added);
			}
		}
		pm.endTask();
	}

	private List<ObjectIdWithOffset> toInclude(DfsPackFile src, DfsReader ctx)
			throws IOException {
		PackIndex srcIdx = src.getPackIndex(ctx);
		List<ObjectIdWithOffset> want = new BlockList<>(
				(int) srcIdx.getObjectCount());
		SCAN: for (PackIndex.MutableEntry ent : srcIdx) {
			ObjectId id = ent.toObjectId();
			RevObject obj = rw.lookupOrNull(id);
			if (obj != null && (obj.has(added) || obj.has(isBase)))
				continue;
			for (ObjectIdSet e : exclude)
				if (e.contains(id))
					continue SCAN;
			want.add(new ObjectIdWithOffset(id, ent.getOffset()));
		}
		Collections.sort(want, (ObjectIdWithOffset a,
				ObjectIdWithOffset b) -> Long.signum(a.offset - b.offset));
		return want;
	}

	private static void writePack(DfsObjDatabase objdb,
			DfsPackDescription pack,
			PackWriter pw, ProgressMonitor pm) throws IOException {
		try (DfsOutputStream out = objdb.writeFile(pack, PACK)) {
			pw.writePack(pm, pm, out);
			pack.addFileExt(PACK);
			pack.setBlockSize(PACK, out.blockSize());
		}
	}

	private static void writeIndex(DfsObjDatabase objdb,
			DfsPackDescription pack,
			PackWriter pw) throws IOException {
		try (DfsOutputStream out = objdb.writeFile(pack, INDEX)) {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writeIndex(cnt);
			pack.addFileExt(INDEX);
			pack.setFileSize(INDEX, cnt.getCount());
			pack.setBlockSize(INDEX, out.blockSize());
			pack.setIndexVersion(pw.getIndexVersion());
		}
	}

	static ReftableConfig configureReftable(ReftableConfig cfg,
			DfsOutputStream out) {
		int bs = out.blockSize();
		if (bs > 0) {
			cfg = new ReftableConfig(cfg);
			cfg.setRefBlockSize(bs);
			cfg.setAlignBlocks(true);
		}
		return cfg;
	}

	private static class ObjectIdWithOffset extends ObjectId {
		final long offset;

		ObjectIdWithOffset(AnyObjectId id, long ofs) {
			super(id);
			offset = ofs;
		}
	}
}
