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

import static org.eclipse.jgit.storage.dfs.DfsObjDatabase.PackSource.COMPACT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.PackIndex;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
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

	private final List<DfsPackDescription> newPacks;

	private final List<PackWriter.Statistics> newStats;

	private int autoAddSize;

	/**
	 * Initialize a pack compactor.
	 *
	 * @param repository
	 *            repository objects to be packed will be read from.
	 */
	public DfsPackCompactor(DfsRepository repository) {
		repo = repository;
		autoAddSize = 5 * 1024 * 1024; // 5 MiB
		srcPacks = new ArrayList<DfsPackFile>();
		newPacks = new ArrayList<DfsPackDescription>(1);
		newStats = new ArrayList<PackWriter.Statistics>(1);
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
	 * Automatically select packs to be included, and add them.
	 * <p>
	 * Packs are selected based on size, smaller packs get included while bigger
	 * ones are omitted.
	 *
	 * @return {@code this}
	 * @throws IOException
	 *             existing packs cannot be read.
	 */
	public DfsPackCompactor autoAdd() throws IOException {
		DfsObjDatabase objdb = repo.getObjectDatabase();
		for (DfsPackFile pack : objdb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSize() < autoAddSize)
				add(pack);
		}
		return this;
	}

	/**
	 * Compact the pack files together.
	 *
	 * @param pm
	 *            progress monitor to receive updates on as packing may take a
	 *            while, depending on the size of the repository.
	 * @throws IOException
	 *             the packs cannot be compacted.
	 */
	public void compact(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;

		DfsObjDatabase objdb = repo.getObjectDatabase();
		DfsReader ctx = (DfsReader) objdb.newReader();
		try {
			PackConfig pc = new PackConfig(repo);
			pc.setIndexVersion(2);
			pc.setDeltaCompress(false);
			pc.setReuseDeltas(true);
			pc.setReuseObjects(true);

			PackWriter pw = new PackWriter(pc, ctx);
			try {
				pw.setDeltaBaseAsOffset(true);
				pw.setReuseDeltaCommits(false);

				addObjectsToPack(pw, ctx, pm);
				if (pw.getObjectCount() == 0)
					return;

				boolean rollback = true;
				DfsPackDescription pack = objdb.newPack(COMPACT);
				try {
					writePack(objdb, pack, pw, pm);
					writeIndex(objdb, pack, pw);

					PackWriter.Statistics stats = pw.getStatistics();
					pw.release();
					pw = null;

					pack.setPackStats(stats);
					objdb.commitPack(Collections.singletonList(pack), toPrune());
					newPacks.add(pack);
					newStats.add(stats);
					rollback = false;
				} finally {
					if (rollback)
						objdb.rollbackPack(Collections.singletonList(pack));
				}
			} finally {
				if (pw != null)
					pw.release();
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
		return newPacks;
	}

	/** @return statistics corresponding to the {@link #getNewPacks()}. */
	public List<PackWriter.Statistics> getNewPackStatistics() {
		return newStats;
	}

	private List<DfsPackDescription> toPrune() {
		int cnt = srcPacks.size();
		List<DfsPackDescription> all = new ArrayList<DfsPackDescription>(cnt);
		for (DfsPackFile pack : srcPacks)
			all.add(pack.getPackDescription());
		return all;
	}

	private void addObjectsToPack(PackWriter pw, DfsReader ctx,
			ProgressMonitor pm) throws IOException,
			IncorrectObjectTypeException {
		// Sort packs by description ordering, this places newer packs before
		// older packs, allowing the PackWriter to be handed newer objects
		// first and older objects last.
		Collections.sort(srcPacks, new Comparator<DfsPackFile>() {
			public int compare(DfsPackFile a, DfsPackFile b) {
				return a.getPackDescription().compareTo(b.getPackDescription());
			}
		});

		RevWalk rw = new RevWalk(ctx);
		RevFlag added = rw.newFlag("ADDED"); //$NON-NLS-1$

		pm.beginTask(JGitText.get().countingObjects, ProgressMonitor.UNKNOWN);
		for (DfsPackFile src : srcPacks) {
			List<ObjectIdWithOffset> want = new BlockList<ObjectIdWithOffset>();
			for (PackIndex.MutableEntry ent : src.getPackIndex(ctx)) {
				ObjectId id = ent.toObjectId();
				RevObject obj = rw.lookupOrNull(id);
				if (obj == null || !obj.has(added))
					want.add(new ObjectIdWithOffset(id, ent.getOffset()));
			}

			// Sort objects by the order they appear in the pack file, for
			// two benefits. Scanning object type information is faster when
			// the pack is traversed in order, and this allows the PackWriter
			// to be given the new objects in a relatively sane newest-first
			// ordering without additional logic, like unpacking commits and
			// walking a commit queue.
			Collections.sort(want, new Comparator<ObjectIdWithOffset>() {
				public int compare(ObjectIdWithOffset a, ObjectIdWithOffset b) {
					return Long.signum(a.offset - b.offset);
				}
			});

			// Only pack each object at most once into the output file. The
			// PackWriter will later select a representation to reuse, which
			// may be the version in this pack, or may be from another pack if
			// the object was copied here to complete a thin pack and is larger
			// than a delta from another pack. This is actually somewhat common
			// if an object is modified frequently, such as the top level tree.
			for (ObjectIdWithOffset id : want) {
				int type = src.getObjectType(ctx, id.offset);
				RevObject obj = rw.lookupAny(id, type);
				if (!obj.has(added)) {
					pm.update(1);
					pw.addObject(obj);
					obj.add(added);
				}
			}
		}
		pm.endTask();
	}

	private void writePack(DfsObjDatabase objdb, DfsPackDescription pack,
			PackWriter pw, ProgressMonitor pm) throws IOException {
		DfsOutputStream out = objdb.writePackFile(pack);
		try {
			CountingOutputStream cnt = new CountingOutputStream(out);
			pw.writePack(pm, pm, cnt);
			pack.setObjectCount(pw.getObjectCount());
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

	private static class ObjectIdWithOffset extends ObjectId {
		final long offset;

		ObjectIdWithOffset(AnyObjectId id, long ofs) {
			super(id);
			offset = ofs;
		}
	}
}
