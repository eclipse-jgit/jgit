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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * A garbage collector for git {@link FileRepository}. This class started as a
 * copy of DfsGarbageCollector from Shawn O. Pearce adapted to FileRepositories.
 * Additionally the index is taken into account and reflogs are handled.
 */
public class GC {
	private FileRepository repo;

	private ProgressMonitor pm;

	/**
	 * Creates a new garbage collector
	 *
	 * @param pm
	 *            a progressmonitor
	 * @param repo
	 *            the repo to work on
	 */

	public GC(FileRepository repo, ProgressMonitor pm) {
		this.repo = repo;
		this.pm = (pm == null) ? NullProgressMonitor.INSTANCE : pm;
	}

	/**
	 * Runs a garbage collector on a {@link FileRepository}. It will
	 * <ul>
	 * <li>repack all reachable objects into packfiles and delete the old
	 * packfiles</li>
	 * <li>prunes all loose objects which are now reachable by packs</li>
	 * </ul>
	 *
	 * @param expireAgeMillis
	 *            each unreferenced, loose object which has been created or
	 *            modified in the last <expireAgeMillis> milliseconds will not
	 *            be pruned. Only older objects may be pruned. If set to 0 then
	 *            every object is a candidate for pruning.
	 * @return the collection of {@link PackFile}'s which are created newly
	 * @throws IOException
	 *
	 */
	public Collection<PackFile> gc(long expireAgeMillis) throws IOException {
		packRefs();
		// TODO: implement reflog_expire(pm, repo);
		Collection<PackFile> newPacks = repack();
		prune(Collections.<ObjectId> emptySet(), expireAgeMillis);
		// TODO: implement rerere_gc(pm);
		return newPacks;
	}

	/**
	 * Delete all old packs. We may during repack() rewrite a Packfile (packfile
	 * names are computed from the id's of the contained objects. If you happen
	 * to write the same objects into a new packfile then what you had in a old
	 * packfile then you create a file with the same name). Then this file
	 * should not be deleted although it existed before gc.
	 *
	 * @param oldPacks
	 * @param newPacks
	 * @param ignoreErrors
	 *            <code>true</code> if we should ignore the fact that a certain
	 *            packfile or indexfile couldn't be deleted. If set to
	 *            <code>false</code> and {@link IOException} is thrown in such
	 *            cases
	 * @throws IOException
	 */
	private void deleteOldPacks(Collection<PackFile> oldPacks,
			Collection<PackFile> newPacks, boolean ignoreErrors)
			throws IOException {

		int deleteOptions = FileUtils.RETRY | FileUtils.SKIP_MISSING;
		if (ignoreErrors)
			deleteOptions |= FileUtils.IGNORE_ERRORS;
		oldPackLoop: for (PackFile oldPack : oldPacks) {
			String oldName = oldPack.getPackName();
			// check whether an old Packfile is also among the list of new
			// packfiles. Then we shouldn't delete it.
			for (PackFile newPack : newPacks)
				if (oldName.equals(newPack.getPackName()))
					continue oldPackLoop;
			FileUtils.delete(nameFor(oldName, ".pack"), deleteOptions);
			FileUtils.delete(nameFor(oldName, ".idx"), deleteOptions);
		}
	}

	/**
	 * Like "git prune-packed" this method tries to prune all loose objects
	 * which can be found in packs. If certain objects can't be pruned (e.g.
	 * because the filesystem delete operation fails) this is silently ignored.
	 *
	 * @throws IOException
	 */
	public void prunePacked() throws IOException {
		ObjectDirectory objdb = repo.getObjectDatabase();
		Collection<PackFile> packs = objdb.getPacks();
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();

		if (fanout != null && fanout.length > 0) {
			if (pm == null)
				pm = NullProgressMonitor.INSTANCE;
			pm.beginTask(JGitText.get().pruneLoosePackedObjects, fanout.length);
			try {
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
							// ignoring the file that does not represent loose
							// object
							continue;
						}
						for (PackFile p : packs)
							if (p.hasObject(id)) {
								found = true;
								break;
							}
						if (found)
							FileUtils.delete(objdb.fileFor(id), FileUtils.RETRY
									| FileUtils.SKIP_MISSING
									| FileUtils.IGNORE_ERRORS);
					}
				}

			} finally {
				pm.endTask();
			}
		}
	}

	/**
	 * Like "git prune" this method tries to prune all loose objects which are
	 * unreferenced. If certain objects can't be pruned (e.g. because the
	 * filesystem delete operation fails) this is silently ignored.
	 *
	 * @param objectsToKeep
	 *            a set of objects which should explicitly not be pruned
	 * @param expireAgeMillis
	 *            each unreferenced, loose object which has been created or
	 *            modified in the last <expireAgeMillis> milliseconds will not
	 *            be pruned. Only older objects may be pruned. If set to 0 then
	 *            every object is a candidate for pruning.
	 * @throws IOException
	 */
	public void prune(Set<ObjectId> objectsToKeep, long expireAgeMillis)
			throws IOException {
		ObjectDirectory objdb = repo.getObjectDatabase();
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();
		long expireDate = System.currentTimeMillis() - expireAgeMillis;

		if (fanout != null && fanout.length > 0) {
			if (pm == null)
				pm = NullProgressMonitor.INSTANCE;
			pm.beginTask(JGitText.get().pruneLooseUnreferencedObjects,
					fanout.length);
			ObjectWalk w = null;
			try {
				// Expensive: create a Objectwalk which walks over all
				// referenced objects.
				w = new ObjectWalk(repo);
				for (Ref f : repo.getAllRefs().values())
					w.markStart(w.parseAny(f.getObjectId()));
				for (Ref f : repo.getRefDatabase().getAdditionalRefs())
					w.markStart(w.parseAny(f.getObjectId()));
				for (ObjectId oid : listNonHEADIndexObjects())
					w.markStart(w.parseAny(oid));
				w.checkConnectivity();
				for (String d : fanout) {
					pm.update(1);
					if (d.length() != 2)
						continue;
					String[] entries = new File(objects, d).list();
					if (entries == null)
						continue;
					for (String e : entries) {
						if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
							continue;
						ObjectId id;
						try {
							id = ObjectId.fromString(d + e);
						} catch (IllegalArgumentException notAnObject) {
							// ignoring the file that does not represent loose
							// object
							continue;
						}

						if (w.lookupOrNull(id) == null
								&& !objectsToKeep.contains(id)) {
							File f = objdb.fileFor(id);
							if (f.lastModified() < expireDate)
								FileUtils.delete(f, FileUtils.RETRY
										| FileUtils.SKIP_MISSING
										| FileUtils.IGNORE_ERRORS);
						}
					}
				}

			} finally {
				pm.endTask();
				if (w != null)
					w.dispose();
			}
		}
	}

	/**
	 * packs all non-symbolic, loose refs into the packed-refs.
	 *
	 * @throws IOException
	 */
	public void packRefs() throws IOException {
		Set<Entry<String, Ref>> refEntries = repo.getAllRefs().entrySet();
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;
		pm.beginTask("pack refs", refEntries.size());
		try {
			Collection<RefDirectoryUpdate> updates = new LinkedList<RefDirectoryUpdate>();
			for (Map.Entry<String, Ref> entry : refEntries) {
				Ref ref = entry.getValue();
				if (!ref.isSymbolic() && ref.getStorage().isLoose()) {
					updates.add(new RefDirectoryUpdate((RefDirectory) repo
							.getRefDatabase(), ref));
				}
				pm.update(1);
			}
			((RefDirectory) repo.getRefDatabase()).pack(updates);
		} finally {
			pm.endTask();
		}
	}

	/**
	 * Packs all objects which reachable from any of the heads into one
	 * packfile. Additionally all objects which are not reachable from any head
	 * but which are reachable from any of the other refs (e.g. tags), special
	 * refs (e.g. FETCH_HEAD) or index are packed into a separate packfile. All
	 * old packfiles which existed before are deleted.
	 *
	 * @return a collection of the newly created packfiles
	 * @throws IOException
	 *
	 */
	public Collection<PackFile> repack() throws IOException {
		Collection<PackFile> toBeDeleted = repo.getObjectDatabase().getPacks();

		Map<String, Ref> refsBefore = repo.getAllRefs();
		for (Ref ref : repo.getRefDatabase().getAdditionalRefs())
			refsBefore.put(ref.getName(), ref);

		Set<ObjectId> allHeads = new HashSet<ObjectId>();
		Set<ObjectId> nonHeads = new HashSet<ObjectId>();
		Set<ObjectId> tagTargets = new HashSet<ObjectId>();
		Set<ObjectId> indexObjects = listNonHEADIndexObjects();

		for (Ref ref : refsBefore.values()) {
			if (ref.isSymbolic() || ref.getObjectId() == null)
				continue;
			if (ref.getName().startsWith(Constants.R_HEADS))
				allHeads.add(ref.getObjectId());
			else
				nonHeads.add(ref.getObjectId());
			if (ref.getPeeledObjectId() != null)
				tagTargets.add(ref.getPeeledObjectId());
			List<ReflogEntry> rlEntries = repo.getReflogReader(ref.getName())
					.getReverseEntries();
			if (rlEntries != null)
				for (ReflogEntry e : rlEntries)
					nonHeads.add(e.getNewId());
		}
		tagTargets.addAll(allHeads);
		nonHeads.addAll(indexObjects);

		List<PackFile> ret = new ArrayList<PackFile>(2);
		if (!allHeads.isEmpty()) {
			PackFile heads = writePack(allHeads,
					Collections.<ObjectId> emptySet(), tagTargets);
			if (heads != null)
				ret.add(heads);
		}
		if (!nonHeads.isEmpty()) {
			// DfsGarbageCollector calls here pw.excludeObjects(idx).
			// Is there the need to explicitly exclude the objects
			// in the newly created pack file? We are already telling the
			// packwriter that we have already allHeads and that he should
			// stop traversing when he finds a head?
			// My problem: I don't have the PackIndex anymore and PackFile
			// doesn't expose it.
			PackFile rest = writePack(nonHeads, allHeads, tagTargets);
			if (rest != null)
				ret.add(rest);
		}
		deleteOldPacks(toBeDeleted, ret, true);
		prunePacked();
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
	private Set<ObjectId> listNonHEADIndexObjects()
			throws CorruptObjectException, IOException {
		RevWalk revWalk = null;
		DirCache dc = null;
		try {
			// Even bare repos may have an index check for the existance of an
			// index file. Only checking for isBare() is wrong.
			if (repo.getIndexFile() == null)
				return Collections.emptySet();
		} catch (NoWorkTreeException e) {
			return Collections.emptySet();
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
			Set<ObjectId> ret = new HashSet<ObjectId>();
			while (treeWalk.next()) {
				ObjectId objectId = treeWalk.getObjectId(0);
				if (!ObjectId.zeroId().equals(objectId))
					ret.add(objectId);
			}
			return ret;
		} finally {
			if (revWalk != null)
				revWalk.dispose();
			treeWalk.release();
		}
	}

	private PackFile writePack(Set<? extends ObjectId> want,
			Set<? extends ObjectId> have, Set<ObjectId> tagTargets)
			throws IOException {

		PackWriter pw = new PackWriter(repo);
		pw.setDeltaBaseAsOffset(true);
		pw.setReuseDeltaCommits(false);
		if (tagTargets != null)
			pw.setTagTargets(tagTargets);
		try {
			pw.preparePack(pm, want, have);
			if (0 < pw.getObjectCount()) {
				String id = pw.computeName().getName();
				File pack = nameFor(id, ".pack");
				File idx = nameFor(id, ".idx");
				if (!pack.createNewFile()) {
					for (PackFile f : repo.getObjectDatabase().getPacks())
						if (f.getPackName().equals(id))
							return f;
					throw new IOException(
							MessageFormat.format(
									JGitText.get().cannotCreatePackfile,
									pack.getPath()));
				}
				if (!idx.createNewFile())
					throw new IOException(
							MessageFormat.format(
									JGitText.get().cannotCreateIndexfile,
									idx.getPath()));
				BufferedOutputStream out = new BufferedOutputStream(
						new FileOutputStream(pack));
				try {
					pw.writePack(pm, pm, out);
				} finally {
					out.close();
				}
				pack.setReadOnly();

				out = new BufferedOutputStream(new FileOutputStream(idx));
				try {
					pw.writeIndex(out);
				} finally {
					out.close();
				}
				idx.setReadOnly();
				return repo.getObjectDatabase().openPack(pack, idx);
			} else
				return null;
		} finally {
			pw.release();
		}
	}

	private File nameFor(String name, String t) {
		File packdir = new File(repo.getObjectsDirectory(), "pack");
		return new File(packdir, "pack-" + name + t);
	}

	class RepoStatistics {
		public long nrOfPackedObjects;

		public long nrOfPackFiles;

		public long nrOfLooseObjects;
	}

	/**
	 * Returns the number of objects stored in packfiles. If an object is
	 * contained in multiple packfiles it is counted as often as it occurs.
	 *
	 * @return the number of objects stored in packfiles
	 * @throws IOException
	 */
	public RepoStatistics getStatistics() throws IOException {
		RepoStatistics ret = new RepoStatistics();
		ret.nrOfPackedObjects = 0;
		for (PackFile f : repo.getObjectDatabase().getPacks())
			ret.nrOfPackedObjects += f.getObjectCount();
		ret.nrOfPackFiles = repo.getObjectDatabase().getPacks().size();
		ret.nrOfLooseObjects = 0;
		File objDir = repo.getObjectsDirectory();
		String[] fanout = objDir.list();
		if (fanout != null && fanout.length > 0) {
			for (String d : fanout) {
				if (d.length() != 2)
					continue;
				String[] entries = new File(objDir, d).list();
				if (entries == null)
					continue;
				for (String e : entries) {
					if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
						continue;
					ret.nrOfLooseObjects++;
				}
			}
		}
		return ret;
	}
}
