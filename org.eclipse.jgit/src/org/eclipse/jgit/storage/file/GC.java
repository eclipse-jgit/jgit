/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2011, Shawn O. Pearce <spearce@spearce.org>
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * A garbage collector for git {@link FileRepository}. This class started as a
 * copy of DfsGarbageCollector from Shawn O. Pearce adapted to FileRepositories.
 * Additionally the index is taken into account and reflogs will be handled.
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
	 * @param pm
	 * @throws IOException
	 *
	 */
	public void gc(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;

		pack_refs(pm);
		reflog_expire(pm);
		Collection<PackFile> toBeDeleted = objdb.getPacks();
		Collection<PackFile> newPacks = repack(pm);
		prunePacked(pm, Collections.<ObjectId> emptySet());
		deleteOldPacks(pm, toBeDeleted, newPacks);
		// rerere_gc(pm);
	}

	/**
	 * Delete all old packs. We may during repack() rewrite a Packfile (packfile
	 * names are computed from the id's of the contained objects. If you happen
	 * to write the same objects into a new packfile then what you had in a old
	 * packfile then you create a file with the same name). Then this file
	 * should not be deleted although it existed before gc.
	 *
	 * @param pm
	 * @param oldPacks
	 * @param newPacks
	 * @throws IOException
	 */
	private void deleteOldPacks(ProgressMonitor pm,
			Collection<PackFile> oldPacks, Collection<PackFile> newPacks) throws IOException {
		oldPackLoop:
		for (PackFile oldPack : oldPacks) {
			String oldName = oldPack.getPackName();
			// check whether an old Packfile is also among the list of new
			// packfiles. Then we shouldn't delete it.
			for (PackFile newPack : newPacks)
				if (oldName.equals(newPack.getPackName()))
					continue oldPackLoop;
			FileUtils.delete(nameFor(objdb, oldName, ".pack"));
			FileUtils.delete(nameFor(objdb, oldName, ".idx"));
		}
	}

	/**
	 * Like "git prune-packed" this will prune all loose objects which can be
	 * found in packs.
	 *
	 * @param pm
	 * @param objectsToKeep
	 * @throws IOException
	 *
	 */
	public void prunePacked(ProgressMonitor pm, Set<ObjectId> objectsToKeep)
			throws IOException {
		Collection<PackFile> packs = objdb.getPacks();
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();
		if (fanout == null)
			fanout = new String[0];
		pm.beginTask("prune loose objects", fanout.length);
		for (String d : fanout) {
			pm.update(1);
			if (d.length() != 2)
				continue;
			String[] entries = new File(objects, d).list();
			if (entries == null)
				continue;
			for (String e : entries) {
				boolean found = false;
				if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
					continue;
				ObjectId id;
				try {
					id = ObjectId.fromString(d + e);
				} catch (IllegalArgumentException notAnObject) {
					// ignoring the file that does not represent loose object
					continue;
				}
				for (PackFile p : packs)
					if (p.hasObject(id)) {
						found = true;
						break;
					}
				if (found || objectsToKeep.contains(id))
					FileUtils.delete(objdb.fileFor(id));
			}
		}
		pm.endTask();
	}

	/**
	 * @param pm
	 * @return todo
	 * @throws IOException
	 *
	 */
	public Collection<PackFile> repack(ProgressMonitor pm) throws IOException {
		PackConfig packConfig = new PackConfig(repo);
		if (packConfig.getIndexVersion() != 2)
			throw new IllegalStateException("Only index version 2");

		Map<String, Ref> refsBefore = repo.getAllRefs();
		for (Ref ref : repo.getRefDatabase().getAdditionalRefs())
			refsBefore.put(ref.getName(), ref);

		HashSet<ObjectId> allHeads = new HashSet<ObjectId>();
		HashSet<ObjectId> nonHeads = new HashSet<ObjectId>();
		HashSet<ObjectId> tagTargets = new HashSet<ObjectId>();
		HashSet<ObjectId> indexObjects = listNonHEADIndexObjects();

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
		nonHeads.addAll(indexObjects);

		List<PackFile> ret = new ArrayList<PackFile>(2);
		ret.add(packHeads(pm, allHeads));
		if (!nonHeads.isEmpty()) {
			PackFile rest = packRest(pm, nonHeads, allHeads);
			if (rest!=null)
				ret.add(rest);
		}
		// packGarbage(pm);
		return ret;
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
	@SuppressWarnings("unchecked")
	HashSet<ObjectId> listNonHEADIndexObjects() throws CorruptObjectException,
			IOException {
		RevWalk revWalk = null;
		DirCache dc = null;
		try {
			// Even bare repos may have an index check for the existance of an
			// index file. Only checking for isBare() is wrong.
			if (repo.getIndexFile() == null)
				return ((HashSet<ObjectId>) Collections.EMPTY_SET);
		} catch (NoWorkTreeException e) {
			return ((HashSet<ObjectId>) Collections.EMPTY_SET);
		}
		TreeWalk treeWalk = new TreeWalk(repo);
		try {
			dc = repo.readDirCache();
			treeWalk.addTree(new DirCacheIterator(dc));
			ObjectId headID = repo.resolve(Constants.HEAD);
			if (headID != null) {
				revWalk = new RevWalk(repo);
				treeWalk.addTree(revWalk.parseTree(headID));
				revWalk.dispose();
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

	private PackFile packHeads(ProgressMonitor pm, HashSet<ObjectId> allHeads)
			throws IOException {
		if (allHeads.isEmpty())
			return null;
		return writePack(pm, allHeads, Collections.<ObjectId> emptySet());
	}

	private PackFile writePack(ProgressMonitor pm,
			Set<? extends ObjectId> want, Set<? extends ObjectId> have)
			throws IOException {
		PackWriter pw = new PackWriter(repo);
		try {
			pw.preparePack(pm, want, have);
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
				return objdb.openPack(pack, idx);
			} else
				return null;
		} finally {
			pw.release();
		}
	}

	private PackFile packRest(ProgressMonitor pm, Set<ObjectId> nonHeads,
			Set<ObjectId> allHeads) throws IOException {
		PackWriter pw = new PackWriter(repo);
		try {
			// DfsGarbageCollector calls here pw.excludeObjects(idx).
			// Is there the need to explicitly exclude the objects
			// in the newly created pack file? We are already telling the
			// packwriter that we have already allHeads and that he should
			// stop traversing when he finds a head?
			// My problem: I don't have the PackIndex anymore and PackFile
			// doesn't expose it.
			if (0 < pw.getObjectCount())
				return writePack(pm, nonHeads, allHeads);
			else
				return null;
		} finally {
			pw.release();
		}
	}

	/**
	 * @param pm
	 *
	 */
	public void reflog_expire(ProgressMonitor pm) {
		// TODO Auto-generated method stub
	}

	/**
	 * @param pm
	 *
	 */
	public void pack_refs(ProgressMonitor pm) {
		// TODO Auto-generated method stub
	}

	private static boolean isHead(Ref ref) {
		return ref.getName().startsWith(Constants.R_HEADS);
	}

	private static File nameFor(ObjectDirectory odb, ObjectId name, String t) {
		File packdir = new File(odb.getDirectory(), "pack");
		return new File(packdir, "pack-" + name.name() + t);
	}

	private static File nameFor(ObjectDirectory odb, String name, String t) {
		File packdir = new File(odb.getDirectory(), "pack");
		return new File(packdir, "pack-" + name + t);
	}
}
