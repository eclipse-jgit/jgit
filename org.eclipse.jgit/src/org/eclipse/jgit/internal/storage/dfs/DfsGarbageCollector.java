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
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_TXN;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.RECEIVE;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.dfs.DfsPackCompactor.configureReftable;
import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;
import static org.eclipse.jgit.internal.storage.pack.PackWriter.NONE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.internal.storage.reftree.RefTreeNames;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.io.CountingOutputStream;

/**
 * Repack and garbage collect a repository.
 */
public class DfsGarbageCollector {
	private final DfsRepository repo;
	private final RefDatabase refdb;
	private final DfsObjDatabase objdb;

	private final List<DfsPackDescription> newPackDesc;
	private final List<PackStatistics> newPackStats;
	private final List<ObjectIdSet> newPackObj;

	private DfsReader ctx;

	private PackConfig packConfig;
	private ReftableConfig reftableConfig;
	private boolean convertToReftable = true;
	private boolean includeDeletes;
	private long reftableInitialMinUpdateIndex = 1;
	private long reftableInitialMaxUpdateIndex = 1;

	// See packIsCoalesceableGarbage(), below, for how these two variables
	// interact.
	private long coalesceGarbageLimit = 50 << 20;
	private long garbageTtlMillis = TimeUnit.DAYS.toMillis(1);

	private long startTimeMillis;
	private List<DfsPackFile> packsBefore;
	private List<DfsReftable> reftablesBefore;
	private List<DfsPackFile> expiredGarbagePacks;

	private Collection<Ref> refsBefore;
	private Set<ObjectId> allHeadsAndTags;
	private Set<ObjectId> allTags;
	private Set<ObjectId> nonHeads;
	private Set<ObjectId> txnHeads;
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
		newPackDesc = new ArrayList<>(4);
		newPackStats = new ArrayList<>(4);
		newPackObj = new ArrayList<>(4);

		packConfig = new PackConfig(repo);
		packConfig.setIndexVersion(2);
	}

	/**
	 * Get configuration used to generate the new pack file.
	 *
	 * @return configuration used to generate the new pack file.
	 */
	public PackConfig getPackConfig() {
		return packConfig;
	}

	/**
	 * Set the new configuration to use when creating the pack file.
	 *
	 * @param newConfig
	 *            the new configuration to use when creating the pack file.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setPackConfig(PackConfig newConfig) {
		packConfig = newConfig;
		return this;
	}

	/**
	 * Set configuration to write a reftable.
	 *
	 * @param cfg
	 *            configuration to write a reftable. Reftable writing is
	 *            disabled (default) when {@code cfg} is {@code null}.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setReftableConfig(ReftableConfig cfg) {
		reftableConfig = cfg;
		return this;
	}

	/**
	 * Whether the garbage collector should convert references to reftable.
	 *
	 * @param convert
	 *            if {@code true}, {@link #setReftableConfig(ReftableConfig)}
	 *            has been set non-null, and a GC reftable doesn't yet exist,
	 *            the garbage collector will make one by scanning the existing
	 *            references, and writing a new reftable. Default is
	 *            {@code true}.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setConvertToReftable(boolean convert) {
		convertToReftable = convert;
		return this;
	}

	/**
	 * Whether the garbage collector will include tombstones for deleted
	 * references in the reftable.
	 *
	 * @param include
	 *            if {@code true}, the garbage collector will include tombstones
	 *            for deleted references in the reftable. Default is
	 *            {@code false}.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setIncludeDeletes(boolean include) {
		includeDeletes = include;
		return this;
	}

	/**
	 * Set minUpdateIndex for the initial reftable created during conversion.
	 *
	 * @param u
	 *            minUpdateIndex for the initial reftable created by scanning
	 *            {@link org.eclipse.jgit.internal.storage.dfs.DfsRefDatabase#getRefs(String)}.
	 *            Ignored unless caller has also set
	 *            {@link #setReftableConfig(ReftableConfig)}. Defaults to
	 *            {@code 1}. Must be {@code u >= 0}.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setReftableInitialMinUpdateIndex(long u) {
		reftableInitialMinUpdateIndex = Math.max(u, 0);
		return this;
	}

	/**
	 * Set maxUpdateIndex for the initial reftable created during conversion.
	 *
	 * @param u
	 *            maxUpdateIndex for the initial reftable created by scanning
	 *            {@link org.eclipse.jgit.internal.storage.dfs.DfsRefDatabase#getRefs(String)}.
	 *            Ignored unless caller has also set
	 *            {@link #setReftableConfig(ReftableConfig)}. Defaults to
	 *            {@code 1}. Must be {@code u >= 0}.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setReftableInitialMaxUpdateIndex(long u) {
		reftableInitialMaxUpdateIndex = Math.max(0, u);
		return this;
	}

	/**
	 * Get coalesce garbage limit
	 *
	 * @return coalesce garbage limit, packs smaller than this size will be
	 *         repacked.
	 */
	public long getCoalesceGarbageLimit() {
		return coalesceGarbageLimit;
	}

	/**
	 * Set the byte size limit for garbage packs to be repacked.
	 * <p>
	 * Any UNREACHABLE_GARBAGE pack smaller than this limit will be repacked at
	 * the end of the run. This allows the garbage collector to coalesce
	 * unreachable objects into a single file.
	 * <p>
	 * If an UNREACHABLE_GARBAGE pack is already larger than this limit it will
	 * be left alone by the garbage collector. This avoids unnecessary disk IO
	 * reading and copying the objects.
	 * <p>
	 * If limit is set to 0 the UNREACHABLE_GARBAGE coalesce is disabled.<br>
	 * If limit is set to {@link java.lang.Long#MAX_VALUE}, everything is
	 * coalesced.
	 * <p>
	 * Keeping unreachable garbage prevents race conditions with repository
	 * changes that may suddenly need an object whose only copy was stored in
	 * the UNREACHABLE_GARBAGE pack.
	 *
	 * @param limit
	 *            size in bytes.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setCoalesceGarbageLimit(long limit) {
		coalesceGarbageLimit = limit;
		return this;
	}

	/**
	 * Get time to live for garbage packs.
	 *
	 * @return garbage packs older than this limit (in milliseconds) will be
	 *         pruned as part of the garbage collection process if the value is
	 *         &gt; 0, otherwise garbage packs are retained.
	 */
	public long getGarbageTtlMillis() {
		return garbageTtlMillis;
	}

	/**
	 * Set the time to live for garbage objects.
	 * <p>
	 * Any UNREACHABLE_GARBAGE older than this limit will be pruned at the end
	 * of the run.
	 * <p>
	 * If timeToLiveMillis is set to 0, UNREACHABLE_GARBAGE purging is disabled.
	 *
	 * @param ttl
	 *            Time to live whatever unit is specified.
	 * @param unit
	 *            The specified time unit.
	 * @return {@code this}
	 */
	public DfsGarbageCollector setGarbageTtl(long ttl, TimeUnit unit) {
		garbageTtlMillis = unit.toMillis(ttl);
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
	 * @throws java.io.IOException
	 *             a new pack cannot be created.
	 */
	public boolean pack(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;
		if (packConfig.getIndexVersion() != 2)
			throw new IllegalStateException(
					JGitText.get().supportOnlyPackIndexVersion2);

		startTimeMillis = SystemReader.getInstance().getCurrentTime();
		ctx = objdb.newReader();
		try {
			refdb.refresh();
			objdb.clearCache();

			refsBefore = getAllRefs();
			readPacksBefore();
			readReftablesBefore();

			Set<ObjectId> allHeads = new HashSet<>();
			allHeadsAndTags = new HashSet<>();
			allTags = new HashSet<>();
			nonHeads = new HashSet<>();
			txnHeads = new HashSet<>();
			tagTargets = new HashSet<>();
			for (Ref ref : refsBefore) {
				if (ref.isSymbolic() || ref.getObjectId() == null) {
					continue;
				}
				if (isHead(ref)) {
					allHeads.add(ref.getObjectId());
				} else if (isTag(ref)) {
					allTags.add(ref.getObjectId());
				} else if (RefTreeNames.isRefTree(refdb, ref.getName())) {
					txnHeads.add(ref.getObjectId());
				} else {
					nonHeads.add(ref.getObjectId());
				}
				if (ref.getPeeledObjectId() != null) {
					tagTargets.add(ref.getPeeledObjectId());
				}
			}
			// Don't exclude tags that are also branch tips.
			allTags.removeAll(allHeads);
			allHeadsAndTags.addAll(allHeads);
			allHeadsAndTags.addAll(allTags);

			// Hoist all branch tips and tags earlier in the pack file
			tagTargets.addAll(allHeadsAndTags);

			// Combine the GC_REST objects into the GC pack if requested
			if (packConfig.getSinglePack()) {
				allHeadsAndTags.addAll(nonHeads);
				nonHeads.clear();
			}

			boolean rollback = true;
			try {
				packHeads(pm);
				packRest(pm);
				packRefTreeGraph(pm);
				packGarbage(pm);
				objdb.commitPack(newPackDesc, toPrune());
				rollback = false;
				return true;
			} finally {
				if (rollback)
					objdb.rollbackPack(newPackDesc);
			}
		} finally {
			ctx.close();
		}
	}

	private Collection<Ref> getAllRefs() throws IOException {
		Collection<Ref> refs = refdb.getRefs();
		List<Ref> addl = refdb.getAdditionalRefs();
		if (!addl.isEmpty()) {
			List<Ref> all = new ArrayList<>(refs.size() + addl.size());
			all.addAll(refs);
			// add additional refs which start with refs/
			for (Ref r : addl) {
				if (r.getName().startsWith(Constants.R_REFS)) {
					all.add(r);
				}
			}
			return all;
		}
		return refs;
	}

	private void readPacksBefore() throws IOException {
		DfsPackFile[] packs = objdb.getPacks();
		packsBefore = new ArrayList<>(packs.length);
		expiredGarbagePacks = new ArrayList<>(packs.length);

		long now = SystemReader.getInstance().getCurrentTime();
		for (DfsPackFile p : packs) {
			DfsPackDescription d = p.getPackDescription();
			if (d.getPackSource() != UNREACHABLE_GARBAGE) {
				packsBefore.add(p);
			} else if (packIsExpiredGarbage(d, now)) {
				expiredGarbagePacks.add(p);
			} else if (packIsCoalesceableGarbage(d, now)) {
				packsBefore.add(p);
			}
		}
	}

	private void readReftablesBefore() throws IOException {
		DfsReftable[] tables = objdb.getReftables();
		reftablesBefore = new ArrayList<>(Arrays.asList(tables));
	}

	private boolean packIsExpiredGarbage(DfsPackDescription d, long now) {
		// Consider the garbage pack as expired when it's older than
		// garbagePackTtl. This check gives concurrent inserter threads
		// sufficient time to identify an object is not in the graph and should
		// have a new copy written, rather than relying on something from an
		// UNREACHABLE_GARBAGE pack.
		return d.getPackSource() == UNREACHABLE_GARBAGE
				&& garbageTtlMillis > 0
				&& now - d.getLastModified() >= garbageTtlMillis;
	}

	private boolean packIsCoalesceableGarbage(DfsPackDescription d, long now) {
		// An UNREACHABLE_GARBAGE pack can be coalesced if its size is less than
		// the coalesceGarbageLimit and either garbageTtl is zero or if the pack
		// is created in a close time interval (on a single calendar day when
		// the garbageTtl is more than one day or one third of the garbageTtl).
		//
		// When the garbageTtl is more than 24 hours, garbage packs that are
		// created within a single calendar day are coalesced together. This
		// would make the effective ttl of the garbage pack as garbageTtl+23:59
		// and limit the number of garbage to a maximum number of
		// garbageTtl_in_days + 1 (assuming all of them are less than the size
		// of coalesceGarbageLimit).
		//
		// When the garbageTtl is less than or equal to 24 hours, garbage packs
		// that are created within a one third of garbageTtl are coalesced
		// together. This would make the effective ttl of the garbage packs as
		// garbageTtl + (garbageTtl / 3) and would limit the number of garbage
		// packs to a maximum number of 4 (assuming all of them are less than
		// the size of coalesceGarbageLimit).

		if (d.getPackSource() != UNREACHABLE_GARBAGE
				|| d.getFileSize(PackExt.PACK) >= coalesceGarbageLimit) {
			return false;
		}

		if (garbageTtlMillis == 0) {
			return true;
		}

		long lastModified = d.getLastModified();
		long dayStartLastModified = dayStartInMillis(lastModified);
		long dayStartToday = dayStartInMillis(now);

		if (dayStartLastModified != dayStartToday) {
			return false; // this pack is not created today.
		}

		if (garbageTtlMillis > TimeUnit.DAYS.toMillis(1)) {
			return true; // ttl is more than one day and pack is created today.
		}

		long timeInterval = garbageTtlMillis / 3;
		if (timeInterval == 0) {
			return false; // ttl is too small, don't try to coalesce.
		}

		long modifiedTimeSlot = (lastModified - dayStartLastModified) / timeInterval;
		long presentTimeSlot = (now - dayStartToday) / timeInterval;
		return modifiedTimeSlot == presentTimeSlot;
	}

	private static long dayStartInMillis(long timeInMillis) {
		Calendar cal = new GregorianCalendar(
				SystemReader.getInstance().getTimeZone());
		cal.setTimeInMillis(timeInMillis);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	/**
	 * Get all of the source packs that fed into this compaction.
	 *
	 * @return all of the source packs that fed into this compaction.
	 */
	public Set<DfsPackDescription> getSourcePacks() {
		return toPrune();
	}

	/**
	 * Get new packs created by this compaction.
	 *
	 * @return new packs created by this compaction.
	 */
	public List<DfsPackDescription> getNewPacks() {
		return newPackDesc;
	}

	/**
	 * Get statistics corresponding to the {@link #getNewPacks()}.
	 * <p>
	 * The elements can be null if the stat is not available for the pack file.
	 *
	 * @return statistics corresponding to the {@link #getNewPacks()}.
	 */
	public List<PackStatistics> getNewPackStatistics() {
		return newPackStats;
	}

	private Set<DfsPackDescription> toPrune() {
		Set<DfsPackDescription> toPrune = new HashSet<>();
		for (DfsPackFile pack : packsBefore) {
			toPrune.add(pack.getPackDescription());
		}
		if (reftableConfig != null) {
			for (DfsReftable table : reftablesBefore) {
				toPrune.add(table.getPackDescription());
			}
		}
		for (DfsPackFile pack : expiredGarbagePacks) {
			toPrune.add(pack.getPackDescription());
		}
		return toPrune;
	}

	private void packHeads(ProgressMonitor pm) throws IOException {
		if (allHeadsAndTags.isEmpty()) {
			writeReftable();
			return;
		}

		try (PackWriter pw = newPackWriter()) {
			pw.setTagTargets(tagTargets);
			pw.preparePack(pm, allHeadsAndTags, NONE, NONE, allTags);
			if (0 < pw.getObjectCount()) {
				long estSize = estimateGcPackSize(INSERT, RECEIVE, COMPACT, GC);
				writePack(GC, pw, pm, estSize);
			} else {
				writeReftable();
			}
		}
	}

	private void packRest(ProgressMonitor pm) throws IOException {
		if (nonHeads.isEmpty())
			return;

		try (PackWriter pw = newPackWriter()) {
			for (ObjectIdSet packedObjs : newPackObj)
				pw.excludeObjects(packedObjs);
			pw.preparePack(pm, nonHeads, allHeadsAndTags);
			if (0 < pw.getObjectCount())
				writePack(GC_REST, pw, pm,
						estimateGcPackSize(INSERT, RECEIVE, COMPACT, GC_REST));
		}
	}

	private void packRefTreeGraph(ProgressMonitor pm) throws IOException {
		if (txnHeads.isEmpty())
			return;

		try (PackWriter pw = newPackWriter()) {
			for (ObjectIdSet packedObjs : newPackObj)
				pw.excludeObjects(packedObjs);
			pw.preparePack(pm, txnHeads, NONE);
			if (0 < pw.getObjectCount())
				writePack(GC_TXN, pw, pm, 0 /* unknown pack size */);
		}
	}

	private void packGarbage(ProgressMonitor pm) throws IOException {
		PackConfig cfg = new PackConfig(packConfig);
		cfg.setReuseDeltas(true);
		cfg.setReuseObjects(true);
		cfg.setDeltaCompress(false);
		cfg.setBuildBitmaps(false);

		try (PackWriter pw = new PackWriter(cfg, ctx);
				RevWalk pool = new RevWalk(ctx)) {
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(true);
			pm.beginTask(JGitText.get().findingGarbage, objectsBefore());
			long estimatedPackSize = 12 + 20; // header and trailer sizes.
			for (DfsPackFile oldPack : packsBefore) {
				PackIndex oldIdx = oldPack.getPackIndex(ctx);
				PackReverseIndex oldRevIdx = oldPack.getReverseIdx(ctx);
				long maxOffset = oldPack.getPackDescription().getFileSize(PACK)
						- 20; // pack size - trailer size.
				for (PackIndex.MutableEntry ent : oldIdx) {
					pm.update(1);
					ObjectId id = ent.toObjectId();
					if (pool.lookupOrNull(id) != null || anyPackHas(id))
						continue;

					long offset = ent.getOffset();
					int type = oldPack.getObjectType(ctx, offset);
					pw.addObject(pool.lookupAny(id, type));
					long objSize = oldRevIdx.findNextOffset(offset, maxOffset)
							- offset;
					estimatedPackSize += objSize;
				}
			}
			pm.endTask();
			if (0 < pw.getObjectCount())
				writePack(UNREACHABLE_GARBAGE, pw, pm, estimatedPackSize);
		}
	}

	private boolean anyPackHas(AnyObjectId id) {
		for (ObjectIdSet packedObjs : newPackObj)
			if (packedObjs.contains(id))
				return true;
		return false;
	}

	private static boolean isHead(Ref ref) {
		return ref.getName().startsWith(Constants.R_HEADS);
	}

	private static boolean isTag(Ref ref) {
		return ref.getName().startsWith(Constants.R_TAGS);
	}

	private int objectsBefore() {
		int cnt = 0;
		for (DfsPackFile p : packsBefore)
			cnt += p.getPackDescription().getObjectCount();
		return cnt;
	}

	private PackWriter newPackWriter() {
		PackWriter pw = new PackWriter(packConfig, ctx);
		pw.setDeltaBaseAsOffset(true);
		pw.setReuseDeltaCommits(false);
		return pw;
	}

	private long estimateGcPackSize(PackSource first, PackSource... rest) {
		EnumSet<PackSource> sourceSet = EnumSet.of(first, rest);
		// Every pack file contains 12 bytes of header and 20 bytes of trailer.
		// Include the final pack file header and trailer size here and ignore
		// the same from individual pack files.
		long size = 32;
		for (DfsPackDescription pack : getSourcePacks()) {
			if (sourceSet.contains(pack.getPackSource())) {
				size += pack.getFileSize(PACK) - 32;
			}
		}
		return size;
	}

	private DfsPackDescription writePack(PackSource source, PackWriter pw,
			ProgressMonitor pm, long estimatedPackSize) throws IOException {
		DfsPackDescription pack = repo.getObjectDatabase().newPack(source,
				estimatedPackSize);

		if (source == GC && reftableConfig != null) {
			writeReftable(pack);
		}

		try (DfsOutputStream out = objdb.writeFile(pack, PACK)) {
			pw.writePack(pm, pm, out);
			pack.addFileExt(PACK);
			pack.setBlockSize(PACK, out.blockSize());
		}

		try (DfsOutputStream out = objdb.writeFile(pack, INDEX)) {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writeIndex(cnt);
			pack.addFileExt(INDEX);
			pack.setFileSize(INDEX, cnt.getCount());
			pack.setBlockSize(INDEX, out.blockSize());
			pack.setIndexVersion(pw.getIndexVersion());
		}

		if (pw.prepareBitmapIndex(pm)) {
			try (DfsOutputStream out = objdb.writeFile(pack, BITMAP_INDEX)) {
				CountingOutputStream cnt = new CountingOutputStream(out);
				pw.writeBitmapIndex(cnt);
				pack.addFileExt(BITMAP_INDEX);
				pack.setFileSize(BITMAP_INDEX, cnt.getCount());
				pack.setBlockSize(BITMAP_INDEX, out.blockSize());
			}
		}

		PackStatistics stats = pw.getStatistics();
		pack.setPackStats(stats);
		pack.setLastModified(startTimeMillis);
		newPackDesc.add(pack);
		newPackStats.add(stats);
		newPackObj.add(pw.getObjectSet());
		return pack;
	}

	private void writeReftable() throws IOException {
		if (reftableConfig != null) {
			DfsPackDescription pack = objdb.newPack(GC);
			newPackDesc.add(pack);
			newPackStats.add(null);
			writeReftable(pack);
		}
	}

	private void writeReftable(DfsPackDescription pack) throws IOException {
		if (convertToReftable && !hasGcReftable()) {
			writeReftable(pack, refsBefore);
			return;
		}

		try (ReftableStack stack = ReftableStack.open(ctx, reftablesBefore)) {
			ReftableCompactor compact = new ReftableCompactor();
			compact.addAll(stack.readers());
			compact.setIncludeDeletes(includeDeletes);
			compactReftable(pack, compact);
		}
	}

	private boolean hasGcReftable() {
		for (DfsReftable table : reftablesBefore) {
			if (table.getPackDescription().getPackSource() == GC) {
				return true;
			}
		}
		return false;
	}

	private void writeReftable(DfsPackDescription pack, Collection<Ref> refs)
			throws IOException {
		try (DfsOutputStream out = objdb.writeFile(pack, REFTABLE)) {
			ReftableConfig cfg = configureReftable(reftableConfig, out);
			ReftableWriter writer = new ReftableWriter(cfg)
					.setMinUpdateIndex(reftableInitialMinUpdateIndex)
					.setMaxUpdateIndex(reftableInitialMaxUpdateIndex)
					.begin(out)
					.sortAndWriteRefs(refs)
					.finish();
			pack.addFileExt(REFTABLE);
			pack.setReftableStats(writer.getStats());
		}
	}

	private void compactReftable(DfsPackDescription pack,
			ReftableCompactor compact) throws IOException {
		try (DfsOutputStream out = objdb.writeFile(pack, REFTABLE)) {
			compact.setConfig(configureReftable(reftableConfig, out));
			compact.compact(out);
			pack.addFileExt(REFTABLE);
			pack.setReftableStats(compact.getStats());
		}
	}
}
