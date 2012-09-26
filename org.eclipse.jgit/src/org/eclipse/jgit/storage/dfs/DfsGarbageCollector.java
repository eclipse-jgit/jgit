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

package org.eclipse.jgit.storage.dfs;

import static org.eclipse.jgit.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.storage.pack.PackExt.INDEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.storage.file.PackIndex;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.io.CountingOutputStream;

/** Repack and garbage collect a repository. */
public class DfsGarbageCollector {
	private final DfsRepository repo;

	private final DfsRefDatabase refdb;

	private final DfsObjDatabase objdb;

	private final List<DfsPackDescription> newPackDesc;

	private final List<PackWriter.Statistics> newPackStats;

	private final List<PackWriter.ObjectIdSet> newPackObj;

	private DfsReader ctx;

	private PackConfig packConfig;

	private Map<String, Ref> refsBefore;

	private List<DfsPackFile> packsBefore;

	private Set<ObjectId> allHeads;

	private Set<ObjectId> nonHeads;

	/** Sum of object counts in {@link #packsBefore}. */
	private long objectsBefore;

	/** Sum of object counts iN {@link #newPackDesc}. */
	private long objectsPacked;

	private Set<ObjectId> tagTargets;

	/**
	 * Initialize a garbage collector.
	 *
	 * @param repository
	 *            repository objects to be packed will be read from.
	 */
	public DfsGarbageCollector(DfsRepository repository) {
		repo = repository;
		refdb = repo.getRefDatabase();
		objdb = repo.getObjectDatabase();
		newPackDesc = new ArrayList<DfsPackDescription>(4);
		newPackStats = new ArrayList<PackWriter.Statistics>(4);
		newPackObj = new ArrayList<PackWriter.ObjectIdSet>(4);

		packConfig = new PackConfig(repo);
		packConfig.setIndexVersion(2);
	}

	/** @return configuration used to generate the new pack file. */
	public PackConfig getPackConfig() {
		return packConfig;
	}

	/**
	 * @param newConfig
	 *            the new configuration to use when creating the pack file.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setPackConfig(PackConfig newConfig) {
		packConfig = newConfig;
		return this;
	}

	/**
	 * Create a single new pack file containing all of the live objects.
	 * <p>
	 * This method safely decides which packs can be expired after the new pack
	 * is created by validating the references have not been modified in an
	 * incompatible way.
	 *
	 * @param pm
	 *            progress monitor to receive updates on as packing may take a
	 *            while, depending on the size of the repository.
	 * @return true if the repack was successful without race conditions. False
	 *         if a race condition was detected and the repack should be run
	 *         again later.
	 * @throws IOException
	 *             a new pack cannot be created.
	 */
	public boolean pack(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;
		if (packConfig.getIndexVersion() != 2)
			throw new IllegalStateException("Only index version 2");

		ctx = (DfsReader) objdb.newReader();
		try {
			refdb.clearCache();
			objdb.clearCache();

			refsBefore = repo.getAllRefs();
			packsBefore = Arrays.asList(objdb.getPacks());
			if (packsBefore.isEmpty())
				return true;

			allHeads = new HashSet<ObjectId>();
			nonHeads = new HashSet<ObjectId>();
			tagTargets = new HashSet<ObjectId>();
			for (Ref ref : refsBefore.values()) {
				if (ref.isSymbolic() || ref.getObjectId() == null)
					continue;
				if (isHead(ref))
					allHeads.add(ref.getObjectId());
				else
					nonHeads.add(ref.getObjectId());
				if (ref.getPeeledObjectId() != null)
					tagTargets.add(ref.getPeeledObjectId());
			}
			tagTargets.addAll(allHeads);

			boolean rollback = true;
			try {
				packHeads(pm);
				packRest(pm);
				packGarbage(pm);
				objdb.commitPack(newPackDesc, toPrune());
				rollback = false;
				return true;
			} finally {
				if (rollback)
					objdb.rollbackPack(newPackDesc);
			}
		} finally {
			ctx.release();
		}
	}

	/** @return all of the source packs that fed into this compaction. */
	public List<DfsPackDescription> getSourcePacks() {
		return toPrune();
	}

	/** @return new packs created by this compaction. */
	public List<DfsPackDescription> getNewPacks() {
		return newPackDesc;
	}

	/** @return statistics corresponding to the {@link #getNewPacks()}. */
	public List<PackWriter.Statistics> getNewPackStatistics() {
		return newPackStats;
	}

	private List<DfsPackDescription> toPrune() {
		int cnt = packsBefore.size();
		List<DfsPackDescription> all = new ArrayList<DfsPackDescription>(cnt);
		for (DfsPackFile pack : packsBefore)
			all.add(pack.getPackDescription());
		return all;
	}

	private void packHeads(ProgressMonitor pm) throws IOException {
		if (allHeads.isEmpty())
			return;

		PackWriter pw = newPackWriter();
		try {
			pw.preparePack(pm, allHeads, Collections.<ObjectId> emptySet());
			if (0 < pw.getObjectCount())
				writePack(GC, pw, pm).setTips(allHeads);
		} finally {
			pw.release();
		}
	}

	private void packRest(ProgressMonitor pm) throws IOException {
		if (nonHeads.isEmpty() || objectsPacked == getObjectsBefore())
			return;

		PackWriter pw = newPackWriter();
		try {
			for (PackWriter.ObjectIdSet packedObjs : newPackObj)
				pw.excludeObjects(packedObjs);
			pw.preparePack(pm, nonHeads, allHeads);
			if (0 < pw.getObjectCount())
				writePack(GC, pw, pm);
		} finally {
			pw.release();
		}
	}

	private void packGarbage(ProgressMonitor pm) throws IOException {
		if (objectsPacked == getObjectsBefore())
			return;

		// TODO(sop) This is ugly. The garbage pack needs to be deleted.
		PackWriter pw = newPackWriter();
		try {
			RevWalk pool = new RevWalk(ctx);
			for (DfsPackFile oldPack : packsBefore) {
				PackIndex oldIdx = oldPack.getPackIndex(ctx);
				pm.beginTask("Finding garbage", (int) oldIdx.getObjectCount());
				for (PackIndex.MutableEntry ent : oldIdx) {
					pm.update(1);
					ObjectId id = ent.toObjectId();
					if (pool.lookupOrNull(id) != null || anyPackHas(id))
						continue;

					int type = oldPack.getObjectType(ctx, ent.getOffset());
					pw.addObject(pool.lookupAny(id, type));
				}
				pm.endTask();
			}
			if (0 < pw.getObjectCount())
				writePack(UNREACHABLE_GARBAGE, pw, pm);
		} finally {
			pw.release();
		}
	}

	private boolean anyPackHas(AnyObjectId id) {
		for (PackWriter.ObjectIdSet packedObjs : newPackObj)
			if (packedObjs.contains(id))
				return true;
		return false;
	}

	private static boolean isHead(Ref ref) {
		return ref.getName().startsWith(Constants.R_HEADS);
	}

	private long getObjectsBefore() {
		if (objectsBefore == 0) {
			for (DfsPackFile p : packsBefore)
				objectsBefore += p.getPackDescription().getObjectCount();
		}
		return objectsBefore;
	}

	private PackWriter newPackWriter() {
		PackWriter pw = new PackWriter(packConfig, ctx);
		pw.setDeltaBaseAsOffset(true);
		pw.setReuseDeltaCommits(false);
		pw.setTagTargets(tagTargets);
		return pw;
	}

	private DfsPackDescription writePack(PackSource source, PackWriter pw,
			ProgressMonitor pm) throws IOException {
		DfsOutputStream out;
		DfsPackDescription pack = repo.getObjectDatabase().newPack(source);
		newPackDesc.add(pack);

		out = objdb.writeFile(pack, PACK);
		try {
			pw.writePack(pm, pm, out);
		} finally {
			out.close();
		}

		out = objdb.writeFile(pack, INDEX);
		try {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writeIndex(cnt);
			pack.setFileSize(INDEX, cnt.getCount());
			pack.setIndexVersion(pw.getIndexVersion());
		} finally {
			out.close();
		}

		if (pw.prepareBitmapIndex(pm)) {
			out = objdb.writeFile(pack, BITMAP_INDEX);
			try {
				CountingOutputStream cnt = new CountingOutputStream(out);
				pw.writeIndex(cnt);
				pack.setFileSize(BITMAP_INDEX, cnt.getCount());
			} finally {
				out.close();
			}
		}

		final ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> packedObjs = pw
				.getObjectSet();
		newPackObj.add(new PackWriter.ObjectIdSet() {
			public boolean contains(AnyObjectId objectId) {
				return packedObjs.contains(objectId);
			}
		});

		PackWriter.Statistics stats = pw.getStatistics();
		pack.setPackStats(stats);
		pack.setFileSize(PACK, stats.getTotalBytes());
		pack.setObjectCount(stats.getTotalObjects());
		pack.setDeltaCount(stats.getTotalDeltas());
		objectsPacked += stats.getTotalObjects();
		newPackStats.add(stats);

		DfsBlockCache.getInstance().getOrCreate(pack, null);
		return pack;
	}
}
