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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.GitDateParser;

/**
 * A garbage collector for git {@link FileRepository}. Instances of this class
 * are not thread-safe. Don't use the same instance from multiple threads.
 *
 * This class started as a copy of DfsGarbageCollector from Shawn O. Pearce
 * adapted to FileRepositories.
 */
public class GC {
	private static final String PRUNE_EXPIRE_DEFAULT = "2.weeks.ago"; //$NON-NLS-1$

	private final FileRepository repo;

	private ProgressMonitor pm;

	private long expireAgeMillis = -1;

	private Date expire;

	/**
	 * the refs which existed during the last call to {@link #repack()}. This is
	 * needed during {@link #prune(Set)} where we can optimize by looking at the
	 * difference between the current refs and the refs which existed during
	 * last {@link #repack()}.
	 */
	private Map<String, Ref> lastPackedRefs;

	/**
	 * Holds the starting time of the last repack() execution. This is needed in
	 * prune() to inspect only those reflog entries which have been added since
	 * last repack().
	 */
	private long lastRepackTime;

	/**
	 * Creates a new garbage collector with default values. An expirationTime of
	 * two weeks and <code>null</code> as progress monitor will be used.
	 *
	 * @param repo
	 *            the repo to work on
	 */
	public GC(FileRepository repo) {
		this.repo = repo;
		this.pm = NullProgressMonitor.INSTANCE;
	}

	/**
	 * Runs a garbage collector on a {@link FileRepository}. It will
	 * <ul>
	 * <li>pack loose references into packed-refs</li>
	 * <li>repack all reachable objects into new pack files and delete the old
	 * pack files</li>
	 * <li>prune all loose objects which are now reachable by packs</li>
	 * </ul>
	 *
	 * @return the collection of {@link PackFile}'s which are newly created
	 * @throws IOException
	 * @throws ParseException
	 *             If the configuration parameter "gc.pruneexpire" couldn't be
	 *             parsed
	 */
	public Collection<PackFile> gc() throws IOException, ParseException {
		pm.start(6 /* tasks */);
		packRefs();
		// TODO: implement reflog_expire(pm, repo);
		Collection<PackFile> newPacks = repack();
		prune(Collections.<ObjectId> emptySet());
		// TODO: implement rerere_gc(pm);
		return newPacks;
	}

	/**
	 * Delete old pack files. What is 'old' is defined by specifying a set of
	 * old pack files and a set of new pack files. Each pack file contained in
	 * old pack files but not contained in new pack files will be deleted.
	 *
	 * @param oldPacks
	 * @param newPacks
	 * @param ignoreErrors
	 *            <code>true</code> if we should ignore the fact that a certain
	 *            pack files or index files couldn't be deleted.
	 *            <code>false</code> if an exception should be thrown in such
	 *            cases
	 * @throws IOException
	 *             if a pack file couldn't be deleted and
	 *             <code>ignoreErrors</code> is set to <code>false</code>
	 */
	private void deleteOldPacks(Collection<PackFile> oldPacks,
			Collection<PackFile> newPacks, boolean ignoreErrors)
			throws IOException {
		int deleteOptions = FileUtils.RETRY | FileUtils.SKIP_MISSING;
		if (ignoreErrors)
			deleteOptions |= FileUtils.IGNORE_ERRORS;
		oldPackLoop: for (PackFile oldPack : oldPacks) {
			String oldName = oldPack.getPackName();
			// check whether an old pack file is also among the list of new
			// pack files. Then we must not delete it.
			for (PackFile newPack : newPacks)
				if (oldName.equals(newPack.getPackName()))
					continue oldPackLoop;

			if (!oldPack.shouldBeKept()) {
				oldPack.close();
				FileUtils.delete(nameFor(oldName, ".pack"), deleteOptions); //$NON-NLS-1$
				FileUtils.delete(nameFor(oldName, ".idx"), deleteOptions); //$NON-NLS-1$
			}
		}
		// close the complete object database. Thats my only chance to force
		// rescanning and to detect that certain pack files are now deleted.
		repo.getObjectDatabase().close();
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
						boolean found = false;
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
	 *
	 * @throws IOException
	 * @throws ParseException
	 *             If the configuration parameter "gc.pruneexpire" couldn't be
	 *             parsed
	 */
	public void prune(Set<ObjectId> objectsToKeep) throws IOException,
			ParseException {
		long expireDate = Long.MAX_VALUE;

		if (expire == null && expireAgeMillis == -1) {
			String pruneExpireStr = repo.getConfig().getString(
					ConfigConstants.CONFIG_GC_SECTION, null,
					ConfigConstants.CONFIG_KEY_PRUNEEXPIRE);
			if (pruneExpireStr == null)
				pruneExpireStr = PRUNE_EXPIRE_DEFAULT;
			expire = GitDateParser.parse(pruneExpireStr, null);
			expireAgeMillis = -1;
		}
		if (expire != null)
			expireDate = expire.getTime();
		if (expireAgeMillis != -1)
			expireDate = System.currentTimeMillis() - expireAgeMillis;

		// Collect all loose objects which are old enough, not referenced from
		// the index and not in objectsToKeep
		Map<ObjectId, File> deletionCandidates = new HashMap<ObjectId, File>();
		Set<ObjectId> indexObjects = null;
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();
		if (fanout != null && fanout.length > 0) {
			pm.beginTask(JGitText.get().pruneLooseUnreferencedObjects,
					fanout.length);
			try {
				for (String d : fanout) {
					pm.update(1);
					if (d.length() != 2)
						continue;
					File[] entries = new File(objects, d).listFiles();
					if (entries == null)
						continue;
					for (File f : entries) {
						String fName = f.getName();
						if (fName.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
							continue;
						if (f.lastModified() >= expireDate)
							continue;
						try {
							ObjectId id = ObjectId.fromString(d + fName);
							if (objectsToKeep.contains(id))
								continue;
							if (indexObjects == null)
								indexObjects = listNonHEADIndexObjects();
							if (indexObjects.contains(id))
								continue;
							deletionCandidates.put(id, f);
						} catch (IllegalArgumentException notAnObject) {
							// ignoring the file that does not represent loose
							// object
							continue;
						}
					}
				}
			} finally {
				pm.endTask();
			}
		}
		if (deletionCandidates.isEmpty())
			return;

		// From the set of current refs remove all those which have been handled
		// during last repack(). Only those refs will survive which have been
		// added or modified since the last repack. Only these can save existing
		// loose refs from being pruned.
		Map<String, Ref> newRefs;
		if (lastPackedRefs == null || lastPackedRefs.isEmpty())
			newRefs = getAllRefs();
		else {
			newRefs = new HashMap<String, Ref>();
			for (Iterator<Map.Entry<String, Ref>> i = getAllRefs().entrySet()
					.iterator(); i.hasNext();) {
				Entry<String, Ref> newEntry = i.next();
				Ref old = lastPackedRefs.get(newEntry.getKey());
				if (!equals(newEntry.getValue(), old))
					newRefs.put(newEntry.getKey(), newEntry.getValue());
			}
		}

		if (!newRefs.isEmpty()) {
			// There are new/modified refs! Check which loose objects are now
			// referenced by these modified refs (or their reflogentries).
			// Remove these loose objects
			// from the deletionCandidates. When the last candidate is removed
			// leave this method.
			ObjectWalk w = new ObjectWalk(repo);
			try {
				for (Ref cr : newRefs.values())
					w.markStart(w.parseAny(cr.getObjectId()));
				if (lastPackedRefs != null)
					for (Ref lpr : lastPackedRefs.values())
						w.markUninteresting(w.parseAny(lpr.getObjectId()));
				removeReferenced(deletionCandidates, w);
			} finally {
				w.dispose();
			}
		}

		if (deletionCandidates.isEmpty())
			return;

		// Since we have not left the method yet there are still
		// deletionCandidates. Last chance for these objects not to be pruned is
		// that they are referenced by reflog entries. Even refs which currently
		// point to the same object as during last repack() may have
		// additional reflog entries not handled during last repack()
		ObjectWalk w = new ObjectWalk(repo);
		try {
			for (Ref ar : getAllRefs().values())
				for (ObjectId id : listRefLogObjects(ar, lastRepackTime))
					w.markStart(w.parseAny(id));
			if (lastPackedRefs != null)
				for (Ref lpr : lastPackedRefs.values())
					w.markUninteresting(w.parseAny(lpr.getObjectId()));
			removeReferenced(deletionCandidates, w);
		} finally {
			w.dispose();
		}

		if (deletionCandidates.isEmpty())
			return;

		// delete all candidates which have survived: these are unreferenced
		// loose objects
		for (File f : deletionCandidates.values())
			f.delete();

		repo.getObjectDatabase().close();
	}

	/**
	 * Remove all entries from a map which key is the id of an object referenced
	 * by the given ObjectWalk
	 *
	 * @param id2File
	 * @param w
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	private void removeReferenced(Map<ObjectId, File> id2File,
			ObjectWalk w) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevObject ro = w.next();
		while (ro != null) {
			if (id2File.remove(ro.getId()) != null)
				if (id2File.isEmpty())
					return;
			ro = w.next();
		}
		ro = w.nextObject();
		while (ro != null) {
			if (id2File.remove(ro.getId()) != null)
				if (id2File.isEmpty())
					return;
			ro = w.nextObject();
		}
	}

	private static boolean equals(Ref r1, Ref r2) {
		if (r1 == null || r2 == null)
			return false;
		if (r1.isSymbolic()) {
			if (!r2.isSymbolic())
				return false;
			return r1.getTarget().getName().equals(r2.getTarget().getName());
		} else {
			if (r2.isSymbolic())
				return false;
			return r1.getObjectId().equals(r2.getObjectId());
		}
	}

	/**
	 * Packs all non-symbolic, loose refs into packed-refs.
	 *
	 * @throws IOException
	 */
	public void packRefs() throws IOException {
		Collection<Ref> refs = repo.getAllRefs().values();
		List<String> refsToBePacked = new ArrayList<String>(refs.size());
		pm.beginTask(JGitText.get().packRefs, refs.size());
		try {
			for (Ref ref : refs) {
				if (!ref.isSymbolic() && ref.getStorage().isLoose())
					refsToBePacked.add(ref.getName());
				pm.update(1);
			}
			((RefDirectory) repo.getRefDatabase()).pack(refsToBePacked);
		} finally {
			pm.endTask();
		}
	}

	/**
	 * Packs all objects which reachable from any of the heads into one pack
	 * file. Additionally all objects which are not reachable from any head but
	 * which are reachable from any of the other refs (e.g. tags), special refs
	 * (e.g. FETCH_HEAD) or index are packed into a separate pack file. Objects
	 * included in pack files which have a .keep file associated are never
	 * repacked. All old pack files which existed before are deleted.
	 *
	 * @return a collection of the newly created pack files
	 * @throws IOException
	 *             when during reading of refs, index, packfiles, objects,
	 *             reflog-entries or during writing to the packfiles
	 *             {@link IOException} occurs
	 */
	public Collection<PackFile> repack() throws IOException {
		Collection<PackFile> toBeDeleted = repo.getObjectDatabase().getPacks();

		long time = System.currentTimeMillis();
		Map<String, Ref> refsBefore = getAllRefs();

		Set<ObjectId> allHeads = new HashSet<ObjectId>();
		Set<ObjectId> nonHeads = new HashSet<ObjectId>();
		Set<ObjectId> tagTargets = new HashSet<ObjectId>();
		Set<ObjectId> indexObjects = listNonHEADIndexObjects();

		for (Ref ref : refsBefore.values()) {
			nonHeads.addAll(listRefLogObjects(ref, 0));
			if (ref.isSymbolic() || ref.getObjectId() == null)
				continue;
			if (ref.getName().startsWith(Constants.R_HEADS))
				allHeads.add(ref.getObjectId());
			else
				nonHeads.add(ref.getObjectId());
			if (ref.getPeeledObjectId() != null)
				tagTargets.add(ref.getPeeledObjectId());
		}

		List<PackIndex> excluded = new LinkedList<PackIndex>();
		for (PackFile f : repo.getObjectDatabase().getPacks())
			if (f.shouldBeKept())
				excluded.add(f.getIndex());

		tagTargets.addAll(allHeads);
		nonHeads.addAll(indexObjects);

		List<PackFile> ret = new ArrayList<PackFile>(2);
		PackFile heads = null;
		if (!allHeads.isEmpty()) {
			heads = writePack(allHeads, Collections.<ObjectId> emptySet(),
					tagTargets, excluded);
			if (heads != null) {
				ret.add(heads);
				excluded.add(0, heads.getIndex());
			}
		}
		if (!nonHeads.isEmpty()) {
			PackFile rest = writePack(nonHeads, allHeads, tagTargets, excluded);
			if (rest != null)
				ret.add(rest);
		}
		deleteOldPacks(toBeDeleted, ret, true);
		prunePacked();

		lastPackedRefs = refsBefore;
		lastRepackTime = time;
		return ret;
	}

	/**
	 * @param ref
	 *            the ref which log should be inspected
	 * @param minTime only reflog entries not older then this time are processed
	 * @return the {@link ObjectId}s contained in the reflog
	 * @throws IOException
	 */
	private Set<ObjectId> listRefLogObjects(Ref ref, long minTime) throws IOException {
		List<ReflogEntry> rlEntries = repo.getReflogReader(ref.getName())
				.getReverseEntries();
		if (rlEntries == null || rlEntries.isEmpty())
			return Collections.<ObjectId> emptySet();
		Set<ObjectId> ret = new HashSet<ObjectId>();
		for (ReflogEntry e : rlEntries) {
			if (e.getWho().getWhen().getTime() < minTime)
				break;
			ret.add(e.getNewId());
			ObjectId oldId = e.getOldId();
			if (oldId != null && !ObjectId.zeroId().equals(oldId))
				ret.add(oldId);
		}
		return ret;
	}

	/**
	 * Returns a map of all refs and additional refs (e.g. FETCH_HEAD,
	 * MERGE_HEAD, ...)
	 *
	 * @return a map where names of refs point to ref objects
	 * @throws IOException
	 */
	private Map<String, Ref> getAllRefs() throws IOException {
		Map<String, Ref> ret = repo.getAllRefs();
		for (Ref ref : repo.getRefDatabase().getAdditionalRefs())
			ret.put(ref.getName(), ref);
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
		try {
			if (repo.getIndexFile() == null)
				return Collections.emptySet();
		} catch (NoWorkTreeException e) {
			return Collections.emptySet();
		}
		TreeWalk treeWalk = new TreeWalk(repo);
		try {
			treeWalk.addTree(new DirCacheIterator(repo.readDirCache()));
			ObjectId headID = repo.resolve(Constants.HEAD);
			if (headID != null) {
				revWalk = new RevWalk(repo);
				treeWalk.addTree(revWalk.parseTree(headID));
				revWalk.dispose();
				revWalk = null;
			}

			treeWalk.setFilter(TreeFilter.ANY_DIFF);
			treeWalk.setRecursive(true);
			Set<ObjectId> ret = new HashSet<ObjectId>();

			while (treeWalk.next()) {
				ObjectId objectId = treeWalk.getObjectId(0);
			    switch (treeWalk.getRawMode(0) & FileMode.TYPE_MASK) {
			      case FileMode.TYPE_MISSING:
			      case FileMode.TYPE_GITLINK:
			        continue;
			      case FileMode.TYPE_TREE:
			      case FileMode.TYPE_FILE:
			      case FileMode.TYPE_SYMLINK:
			        ret.add(objectId);
			        continue;
			      default:
					throw new IOException(MessageFormat.format(
							JGitText.get().corruptObjectInvalidMode3, String
									.format("%o", Integer.valueOf(treeWalk //$NON-NLS-1$
											.getRawMode(0)),
											(objectId == null) ? "null" //$NON-NLS-1$
													: objectId.name(), treeWalk
											.getPathString(), repo
											.getIndexFile())));
			    }
			  }
			return ret;
		} finally {
			if (revWalk != null)
				revWalk.dispose();
			treeWalk.release();
		}
	}

	private PackFile writePack(Set<? extends ObjectId> want,
			Set<? extends ObjectId> have, Set<ObjectId> tagTargets,
			List<PackIndex> excludeObjects) throws IOException {
		File tmpPack = null;
		File tmpIdx = null;
		PackWriter pw = new PackWriter(repo);
		try {
			// prepare the PackWriter
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(false);
			if (tagTargets != null)
				pw.setTagTargets(tagTargets);
			if (excludeObjects != null)
				for (PackIndex idx : excludeObjects)
					pw.excludeObjects(idx);
			pw.preparePack(pm, want, have);
			if (pw.getObjectCount() == 0)
				return null;

			// create temporary files
			String id = pw.computeName().getName();
			File packdir = new File(repo.getObjectsDirectory(), "pack"); //$NON-NLS-1$
			tmpPack = File.createTempFile("gc_", ".pack_tmp", packdir); //$NON-NLS-1$ //$NON-NLS-2$
			tmpIdx = new File(packdir, tmpPack.getName().substring(0,
					tmpPack.getName().lastIndexOf('.'))
					+ ".idx_tmp"); //$NON-NLS-1$

			if (!tmpIdx.createNewFile())
				throw new IOException(MessageFormat.format(
						JGitText.get().cannotCreateIndexfile, tmpIdx.getPath()));

			// write the packfile
			@SuppressWarnings("resource" /* java 7 */)
			FileChannel channel = new FileOutputStream(tmpPack).getChannel();
			OutputStream channelStream = Channels.newOutputStream(channel);
			try {
				pw.writePack(pm, pm, channelStream);
			} finally {
				channel.force(true);
				channelStream.close();
				channel.close();
			}

			// write the packindex
			@SuppressWarnings("resource")
			FileChannel idxChannel = new FileOutputStream(tmpIdx).getChannel();
			OutputStream idxStream = Channels.newOutputStream(idxChannel);
			try {
				pw.writeIndex(idxStream);
			} finally {
				idxChannel.force(true);
				idxStream.close();
				idxChannel.close();
			}

			// rename the temporary files to real files
			File realPack = nameFor(id, ".pack"); //$NON-NLS-1$
			tmpPack.setReadOnly();
			File realIdx = nameFor(id, ".idx"); //$NON-NLS-1$
			realIdx.setReadOnly();
			boolean delete = true;
			try {
				if (!tmpPack.renameTo(realPack))
					return null;
				delete = false;
				if (!tmpIdx.renameTo(realIdx)) {
					File newIdx = new File(realIdx.getParentFile(),
							realIdx.getName() + ".new"); //$NON-NLS-1$
					if (!tmpIdx.renameTo(newIdx))
						newIdx = tmpIdx;
					throw new IOException(MessageFormat.format(
							JGitText.get().panicCantRenameIndexFile, newIdx,
							realIdx));
				}
			} finally {
				if (delete && tmpPack.exists())
					tmpPack.delete();
				if (delete && tmpIdx.exists())
					tmpIdx.delete();
			}
			return repo.getObjectDatabase().openPack(realPack, realIdx);
		} finally {
			pw.release();
			if (tmpPack != null && tmpPack.exists())
				tmpPack.delete();
			if (tmpIdx != null && tmpIdx.exists())
				tmpIdx.delete();
		}
	}

	private File nameFor(String name, String ext) {
		File packdir = new File(repo.getObjectsDirectory(), "pack"); //$NON-NLS-1$
		return new File(packdir, "pack-" + name + ext); //$NON-NLS-1$
	}

	/**
	 * A class holding statistical data for a FileRepository regarding how many
	 * objects are stored as loose or packed objects
	 */
	public class RepoStatistics {
		/**
		 * The number of objects stored in pack files. If the same object is
		 * stored in multiple pack files then it is counted as often as it
		 * occurs in pack files.
		 */
		public long numberOfPackedObjects;

		/**
		 * The number of pack files
		 */
		public long numberOfPackFiles;

		/**
		 * The number of objects stored as loose objects.
		 */
		public long numberOfLooseObjects;

		/**
		 * The sum of the sizes of all files used to persist loose objects.
		 */
		public long sizeOfLooseObjects;

		/**
		 * The sum of the sizes of all pack files.
		 */
		public long sizeOfPackedObjects;

		/**
		 * The number of loose refs.
		 */
		public long numberOfLooseRefs;

		/**
		 * The number of refs stored in pack files.
		 */
		public long numberOfPackedRefs;
	}

	/**
	 * Returns the number of objects stored in pack files. If an object is
	 * contained in multiple pack files it is counted as often as it occurs.
	 *
	 * @return the number of objects stored in pack files
	 * @throws IOException
	 */
	public RepoStatistics getStatistics() throws IOException {
		RepoStatistics ret = new RepoStatistics();
		Collection<PackFile> packs = repo.getObjectDatabase().getPacks();
		for (PackFile f : packs) {
			ret.numberOfPackedObjects += f.getIndex().getObjectCount();
			ret.numberOfPackFiles++;
			ret.sizeOfPackedObjects += f.getPackFile().length();
		}
		File objDir = repo.getObjectsDirectory();
		String[] fanout = objDir.list();
		if (fanout != null && fanout.length > 0) {
			for (String d : fanout) {
				if (d.length() != 2)
					continue;
				File[] entries = new File(objDir, d).listFiles();
				if (entries == null)
					continue;
				for (File f : entries) {
					if (f.getName().length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
						continue;
					ret.numberOfLooseObjects++;
					ret.sizeOfLooseObjects += f.length();
				}
			}
		}

		RefDatabase refDb = repo.getRefDatabase();
		for (Ref r : refDb.getRefs(RefDatabase.ALL).values()) {
			Storage storage = r.getStorage();
			if (storage == Storage.LOOSE || storage == Storage.LOOSE_PACKED)
				ret.numberOfLooseRefs++;
			if (storage == Storage.PACKED || storage == Storage.LOOSE_PACKED)
				ret.numberOfPackedRefs++;
		}

		return ret;
	}

	/**
	 * Set the progress monitor used for garbage collection methods.
	 *
	 * @param pm
	 * @return this
	 */
	public GC setProgressMonitor(ProgressMonitor pm) {
		this.pm = (pm == null) ? NullProgressMonitor.INSTANCE : pm;
		return this;
	}

	/**
	 * During gc() or prune() each unreferenced, loose object which has been
	 * created or modified in the last <code>expireAgeMillis</code> milliseconds
	 * will not be pruned. Only older objects may be pruned. If set to 0 then
	 * every object is a candidate for pruning.
	 *
	 * @param expireAgeMillis
	 *            minimal age of objects to be pruned in milliseconds.
	 */
	public void setExpireAgeMillis(long expireAgeMillis) {
		this.expireAgeMillis = expireAgeMillis;
		expire = null;
	}

	/**
	 * During gc() or prune() each unreferenced, loose object which has been
	 * created or modified after or at <code>expire</code> will not be pruned.
	 * Only older objects may be pruned. If set to null then every object is a
	 * candidate for pruning.
	 *
	 * @param expire
	 *            instant in time which defines object expiration
	 *            objects with modification time before this instant are expired
	 *            objects with modification time newer or equal to this instant
	 *            are not expired
	 */
	public void setExpire(Date expire) {
		this.expire = expire;
		expireAgeMillis = -1;
	}

}
