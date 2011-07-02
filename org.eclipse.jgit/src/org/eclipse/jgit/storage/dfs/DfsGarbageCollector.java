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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
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

	private final List<DfsPackFile> newPackList;

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
		newPackList = new ArrayList<DfsPackFile>(4);

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
			RevWalk rw = new RevWalk(ctx);
			Set<ObjectId> toPack = reduce(rw, allHeads);
			rw.reset();

			ObjectWalk ow = rw.toObjectWalkWithSameObjects();
			rw = null;

			pw.preparePack(pm, ow, toPack, Collections.<ObjectId> emptyList());
			ow = null;

			if (0 < pw.getObjectCount())
				writePack(GC, pw, pm).setTips(toPack);
		} finally {
			pw.release();
		}
	}

	private void packRest(ProgressMonitor pm) throws IOException {
		if (nonHeads.isEmpty() || objectsPacked == getObjectsBefore())
			return;

		PackWriter pw = newPackWriter();
		try {
			for (DfsPackFile pack : newPackList)
				pw.excludeObjects(pack.getPackIndex(ctx));
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
		List<PackIndex> newIdx = new ArrayList<PackIndex>(newPackList.size());
		for (DfsPackFile pack : newPackList)
			newIdx.add(pack.getPackIndex(ctx));

		PackWriter pw = newPackWriter();
		try {
			RevWalk pool = new RevWalk(ctx);
			for (DfsPackFile oldPack : packsBefore) {
				PackIndex oldIdx = oldPack.getPackIndex(ctx);
				pm.beginTask("Finding garbage", (int) oldIdx.getObjectCount());
				for (PackIndex.MutableEntry ent : oldIdx) {
					pm.update(1);
					ObjectId id = ent.toObjectId();
					if (pool.lookupOrNull(id) != null || anyIndexHas(newIdx, id))
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

	private static boolean anyIndexHas(List<PackIndex> list, AnyObjectId id) {
		for (PackIndex idx : list)
			if (idx.hasObject(id))
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

	/**
	 * Reduce the input branch tips to the fewest required for the graph.
	 * <p>
	 * This algorithm filters out branch heads that are already fully merged
	 * into another branch head. This commonly occurs when there happens to be a
	 * maintenance branch and a main development branch, the maintenance branch
	 * is frequently merged into the main branch.
	 * <p>
	 * The merge reduction is useful for the cached pack tip list, where a
	 * shorter list of tips to consider during clone requests is worth the
	 * slightly higher cost to sort the tips during GC.
	 *
	 * @param rw
	 * @param heads
	 * @return reduced set of {@code heads}.
	 * @throws IOException
	 */
	private Set<ObjectId> reduce(RevWalk rw, Set<ObjectId> heads)
			throws IOException {
		RevFlag tip = rw.newFlag("tip");
		RevFlag reachable = rw.newFlag("reachable");

		// Parse all head objects, selecting commits for traversal tests.
		Set<ObjectId> out = new HashSet<ObjectId>();
		List<RevCommit> commits = new ArrayList<RevCommit>(heads.size());
		for (ObjectId id : heads) {
			RevObject o = rw.parseAny(id);
			RevObject c = rw.peel(o);
			if (c instanceof RevCommit) {
				commits.add((RevCommit) c);
				c.add(tip);
			} else
				out.add(id);
		}

		// If there is only 1 (or no) commit, skip the remaining work.
		if (commits.size() < 2)
			return heads;

		// Sort commits descending and pick the oldest time. Searching
		// for containment will start on the most recent commit and go
		// through history only until the oldest date. This may not find
		// the smallest set of tips due to clock skew, but will work on
		// most commit graphs.
		Collections.sort(commits, new Comparator<RevCommit>() {
			public int compare(RevCommit a, RevCommit b) {
				return b.getCommitTime() - a.getCommitTime();
			}
		});
		int minTime = commits.get(commits.size() - 1).getCommitTime();

		for (RevCommit c : commits) {
			// If the commit is already reachable from another, skip
			// processing its history, the tip will be discarded.
			if (c.has(reachable))
				continue;

			rw.resetRetain(tip, reachable);
			rw.markStart(c);

			RevCommit o;
			while ((o = rw.next()) != null) {
				if (o.has(tip) & o != c)
					o.add(reachable);
				if (o.getCommitTime() < minTime)
					break;
			}
		}

		// If a commit is not reachable from another, keep it as one of
		// the tips the packer should traverse from.
		for (RevCommit c : commits) {
			if (!c.has(reachable))
				out.add(c.copy());
		}
		return out;
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

		out = objdb.writePackFile(pack);
		try {
			pw.writePack(pm, pm, out);
		} finally {
			out.close();
		}

		out = objdb.writePackIndex(pack);
		try {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writeIndex(cnt);
			pack.setIndexSize(cnt.getCount());
		} finally {
			out.close();
		}

		pack.setPackStats(pw.getStatistics());
		pack.setPackSize(pw.getStatistics().getTotalBytes());
		pack.setObjectCount(pw.getStatistics().getTotalObjects());
		pack.setDeltaCount(pw.getStatistics().getTotalDeltas());
		objectsPacked += pw.getStatistics().getTotalObjects();
		newPackList.add(DfsBlockCache.getInstance().getOrCreate(pack, null));
		return pack;
	}
}
