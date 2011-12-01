/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.storage.file;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * A garbarge collector for git {@link FileRepository}. This class started as a
 * copy of {@link DfsGarbageCollector} from Shawn O. Pearce adapted to
 * FileRepositories. Additionally the index is taken into account and reflogs
 * will be handled.
 */
public class GC {
	private FileRepository repo;

	private ObjectDirectory objdb;

	/**
	 * @param repo
	 */
	public GC(FileRepository repo) {
		this.repo = repo;
		objdb = repo.getObjectDatabase();
	}

	/**
	 * @throws IOException
	 *
	 */
	public void gc() throws IOException {
		if (repo != null) {
			pack_refs();
			reflog_expire();
			repack(null);
			prune(null);
			// rerere_gc();
		}
	}

	/**
	 * @param objectsToKeep
	 * @throws IOException
	 *
	 */
	public void prune(Set<ObjectId> objectsToKeep) throws IOException {
		for (PackFile p : objdb.getPacks()) {
			for (MutableEntry e : p) {
				if (objectsToKeep != null) {
					ObjectId oid = e.toObjectId();
					if (!objectsToKeep.contains(oid)) {
						FileUtils.delete(objdb.fileFor(oid));
					}
				} else {
					FileUtils.delete(objdb.fileFor(e.toObjectId()));
				}
			}
		}
	}

	/**
	 * @param pm
	 * @return todo
	 * @throws IOException
	 *
	 */
	public boolean repack(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;

		PackConfig packConfig = new PackConfig(repo);
		if (packConfig.getIndexVersion() != 2)
			throw new IllegalStateException("Only index version 2");

		Map<String, Ref> refsBefore = repo.getAllRefs();
		// Collection<PackFile> packsBefore = objdb.getPacks();
		// why this, why
		// if (packsBefore.isEmpty())
		// return true;

		HashSet<ObjectId> allHeads = new HashSet<ObjectId>();
		HashSet<ObjectId> nonHeads = new HashSet<ObjectId>();
		HashSet<ObjectId> tagTargets = new HashSet<ObjectId>();
		// HashSet<ObjectId> indexObjects = listNonHEADIndexObjects();

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

		packHeads(pm, allHeads);
		// packRest(pm);
		// packGarbage(pm);
		return true;
	}

	/**
	 * Return a list of those objects in the index which differ from whats in
	 * HEAD
	 *
	 * @return a set of ObjectIds of changed objects in the index
	 * @throws IOException
	 * @throws CorruptObjectException
	 * @throws NoWorkTreeException
	 */
	HashSet<ObjectId> listNonHEADIndexObjects() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		RevWalk revWalk = null;
		TreeWalk treeWalk = new TreeWalk(repo);
		try {
			if (!repo.isBare()) {
				DirCache dc = repo.lockDirCache();
				treeWalk.addTree(new DirCacheIterator(dc));
				ObjectId headID = repo.resolve(Constants.HEAD);
				if (headID != null) {
					revWalk = new RevWalk(repo);
					treeWalk.addTree(revWalk.parseTree(headID));
					revWalk.dispose();
				}
			}

			treeWalk.setFilter(TreeFilter.ANY_DIFF);
			treeWalk.setRecursive(true);
			HashSet<ObjectId> ret = new HashSet<ObjectId>();
			while (treeWalk.next()) {
				ObjectId objectId = treeWalk.getObjectId(0);
				if (objectId != ObjectId.zeroId())
					ret.add(objectId);
			}
			return ret;
		} finally {
			if (revWalk != null)
				revWalk.dispose();
			treeWalk.release();
		}
	}

	private void packHeads(ProgressMonitor pm, HashSet<ObjectId> allHeads)
			throws IOException {
		if (allHeads.isEmpty())
			return;
		PackWriter pw = new PackWriter(repo);
		try {
			pw.preparePack(pm, allHeads, Collections.<ObjectId> emptySet());
			if (0 < pw.getObjectCount()) {
				ObjectId id = pw.computeName();
				File pack = nameFor(objdb, id, ".pack");
				BufferedOutputStream out = new BufferedOutputStream(
						new FileOutputStream(pack));
				try {
					pw.writePack(pm, pm, out);
				} finally {
					out.close();
				}
				pack.setReadOnly();

				File idx = nameFor(objdb, id, ".idx");
				out = new BufferedOutputStream(new FileOutputStream(idx));
				try {
					pw.writeIndex(out);
				} finally {
					out.close();
				}
				idx.setReadOnly();
				objdb.openPack(pack, idx);
			}
		} finally {
			pw.release();
		}
	}

	/**
	 *
	 */
	public void reflog_expire() {
		// TODO Auto-generated method stub
	}

	/**
	 *
	 */
	public void pack_refs() {
		// TODO Auto-generated method stub
	}

	private static boolean isHead(Ref ref) {
		return ref.getName().startsWith(Constants.R_HEADS);
	}

	private static File nameFor(ObjectDirectory odb, ObjectId name, String t) {
		File packdir = new File(odb.getDirectory(), "pack");
		return new File(packdir, "pack-" + name.name() + t);
	}

}
