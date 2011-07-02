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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.PackIndex;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.io.CountingOutputStream;

/** Repack and garbage collect a repository. */
public class DfsGarbageCollector {
	private final DfsRepository repo;

	private PackConfig packConfig;

	/** Milliseconds old a pack has to be before it can be expired. */
	private long pruneExpire;

	private Map<String, Ref> refsBefore;

	private List<DfsPackDescription> prunePacks;

	private DfsPackFile newPack;

	private PackIndex newIndex;

	private DfsReader ctx;

	private RevWalk rw;

	/**
	 * Initialize a garbage collector.
	 *
	 * @param repository
	 *            repository objects to be packed will be read from.
	 */
	public DfsGarbageCollector(DfsRepository repository) {
		repo = repository;

		packConfig = new PackConfig(repo);
		packConfig.setIndexVersion(2);

		pruneExpire = 60 * 60 * 1000L; // 60 minutes.
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

	/** @return r */
	public long getPruneExpire() {
		return pruneExpire;
	}

	/**
	 * @param expires
	 * @return {@code this}
	 */
	public DfsGarbageCollector setPruneExpire(long expires) {
		pruneExpire = Math.max(0, expires);
		return this;
	}

	/**
	 * Create a single new pack file containing all of the live objects.
	 * <p>
	 * After packing is complete, callers may use {@link #getPacksToPrune()} to
	 * discover the list of packs that can be safely removed from the source
	 * repository in order to collect garbage and reduce disk usage. All of
	 * these packs are either redundant or contain objects that no longer are
	 * reachable from the references.
	 * <p>
	 * This method safely decides which packs can be expired after the new pack
	 * is created by validating the references have not been modified in an
	 * incompatible way. If it may be unsafe to prune redundant packs, the
	 * caller will receive an empty collection from {@link #getPacksToPrune()}
	 * and can decide to either commit the new pack, or rollback its creation.
	 * Either way the caller should repack the repository again in the near
	 * future to try and clean up the existing packs.
	 *
	 * @param pm
	 *            progress monitor to receive updates on as packing may take a
	 *            while, depending on the size of the repository.
	 * @return description of the newly created pack file. Its pack and index
	 *         have been written, but the description has not yet been committed
	 *         to the object database, and the pack has not yet been added to
	 *         the open database instance.
	 * @throws IOException
	 *             a new pack cannot be created.
	 */
	public DfsPackDescription pack(ProgressMonitor pm) throws IOException {
		if (packConfig.getIndexVersion() != 2)
			throw new IllegalStateException("Only index version 2");
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;

		DfsObjDatabase objdb = repo.getObjectDatabase();
		try {
			// Ensure a reasonably safe read by clearing the cache first.
			repo.getRefDatabase().clearCache();
			refsBefore = repo.getAllRefs();
			Set<ObjectId> tipsPacked = new HashSet<ObjectId>();
			for (Ref ref : refsBefore.values()) {
				if (ref.getObjectId() != null)
					tipsPacked.add(ref.getObjectId());
			}

			objdb.clearCache();
			DfsPackFile[] packsBefore = objdb.getPacks();
			ctx = (DfsReader) objdb.newReader();

			PackWriter pw = new PackWriter(packConfig, ctx);
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(false);
			pw.preparePack(pm, tipsPacked, Collections.<ObjectId> emptyList());

			boolean rollback = true;
			DfsPackDescription pack = objdb.newPack(pw.getObjectCount());
			try {
				writePack(objdb, pack, pw, pm);
				writeIndex(objdb, pack, pw);

				newPack = DfsBlockCache.getInstance().getOrCreate(pack, null);

				if (isSafeToPruneOldPacks(tipsPacked))
					prunePacks = filterPacks(packsBefore);
				else
					prunePacks = Collections.emptyList();

				rollback = false;
				return pack;
			} finally {
				if (rollback)
					objdb.rollbackPack(pack);
			}
		} finally {
			ctx.release();
			ctx = null;
			rw = null;
			newIndex = null;
		}
	}

	/** @return packs to prune when the new pack is committed. */
	public List<DfsPackDescription> getPacksToPrune() {
		return prunePacks;
	}

	private List<DfsPackDescription> filterPacks(DfsPackFile[] packList)
			throws IOException {
		long expireTime = newPack.getPackDescription().getLastModified()
				- pruneExpire;
		List<DfsPackDescription> safe;

		safe = new ArrayList<DfsPackDescription>(packList.length);
		PACK_LIST: for (DfsPackFile oldPack : packList) {
			// If the pack predates the expire time (and existed before the
			// repack started) its reachable objects should have been included
			// by any reference tips scanned. Pruning the pack should be OK.
			if (oldPack.getPackDescription().getLastModified() <= expireTime) {
				safe.add(oldPack.getPackDescription());
				continue;
			}

			// Only if all of the objects in the pack are included in the
			// resulting pack is it safe to remove this pack.
			if (newIndex == null)
				newIndex = newPack.getPackIndex(ctx);
			for (PackIndex.MutableEntry ent : oldPack.getPackIndex(ctx)) {
				if (!newIndex.hasObject(ent.toObjectId()))
					continue PACK_LIST;
			}
			safe.add(oldPack.getPackDescription());
		}
		return safe;
	}

	private boolean isSafeToPruneOldPacks(Set<ObjectId> tipsPacked)
			throws IOException {
		// Clearing the cache is necessary to ensure current information
		// gets read from the repository, otherwise this decision may be
		// incorrect and result in corruption.
		repo.getRefDatabase().clearCache();

		for (Ref ref : repo.getAllRefs().values()) {
			// Don't worry about symbolic references or references
			// that lack an ObjectId. They cannot cause objects to
			// be required by the repository post GC.
			if (ref.isSymbolic() || ref.getObjectId() == null)
				continue;

			// If the current tip of the reference was included in
			// the tips that were packed, all required objects are
			// still included in the output. This is very common so
			// we test for it early to save validation time.
			if (tipsPacked.contains(ref.getObjectId()))
				continue;

			// If the reference was a simple fast-forward from its
			// old position, all required objects are either in this
			// pack or in a new pack that is not scheduled for removal.
			if (isFastForward(ref))
				continue;

			// If the tip of the reference was included in the new
			// pack, the reference has everything it needs because
			// the new pack is self-contained.
			if (newIndex == null)
				newIndex = newPack.getPackIndex(ctx);
			if (newIndex.hasObject(ref.getObjectId()))
				continue;

			// The reference cannot be satisfied, as older garbage
			// objects may have been made live again by it.
			return false;
		}

		// Every current reference is satisfied.
		return true;
	}

	private boolean isFastForward(Ref newRef) throws IOException {
		Ref oldRef = refsBefore.get(newRef.getName());
		if (oldRef == null || oldRef.getObjectId() == null)
			return false;

		if (ctx == null)
			ctx = (DfsReader) repo.newObjectReader();
		if (rw == null)
			rw = new RevWalk(ctx);

		try {
			RevObject oldObj = rw.parseAny(oldRef.getObjectId());
			RevObject newObj = rw.parseAny(newRef.getObjectId());

			if (oldObj instanceof RevCommit && newObj instanceof RevCommit)
				return rw.isMergedInto((RevCommit) oldObj, (RevCommit) newObj);
			else
				return false;
		} catch (IncorrectObjectTypeException notCommit) {
			return false;
		}
	}

	private void writePack(DfsObjDatabase objdb, DfsPackDescription pack,
			PackWriter pw, ProgressMonitor pm) throws IOException {
		DfsOutputStream out = objdb.writePackFile(pack);
		try {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writePack(pm, pm, cnt);
			pack.setPackSize(cnt.getCount());
		} finally {
			out.close();
		}
	}

	private void writeIndex(DfsObjDatabase objdb, DfsPackDescription pack,
			PackWriter pw) throws IOException {
		DfsOutputStream out = objdb.writePackIndex(pack);
		try {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writeIndex(cnt);
			pack.setIndexSize(cnt.getCount());
		} finally {
			out.close();
		}
	}
}
