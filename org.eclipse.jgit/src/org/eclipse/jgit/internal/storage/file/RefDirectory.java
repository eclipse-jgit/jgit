/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.Constants.PACKED_REFS;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Traditional file system based {@link RefDatabase}.
 * <p>
 * This is the classical reference database representation for a Git repository.
 * References are stored in two formats: loose, and packed.
 * <p>
 * Loose references are stored as individual files within the {@code refs/}
 * directory. The file name matches the reference name and the file contents is
 * the current {@link ObjectId} in string form.
 * <p>
 * Packed references are stored in a single text file named {@code packed-refs}.
 * In the packed format, each reference is stored on its own line. This file
 * reduces the number of files needed for large reference spaces, reducing the
 * overall size of a Git repository on disk.
 */
public class RefDirectory extends RefDatabase {
	/** Magic string denoting the start of a symbolic reference file. */
	public static final String SYMREF = "ref: "; //$NON-NLS-1$

	/** Magic string denoting the header of a packed-refs file. */
	public static final String PACKED_REFS_HEADER = "# pack-refs with:"; //$NON-NLS-1$

	/** If in the header, denotes the file has peeled data. */
	public static final String PACKED_REFS_PEELED = " peeled"; //$NON-NLS-1$

	/** The names of the additional refs supported by this class */
	private static final String[] additionalRefsNames = new String[] {
			Constants.MERGE_HEAD, Constants.FETCH_HEAD, Constants.ORIG_HEAD,
			Constants.CHERRY_PICK_HEAD };

	private final FileRepository parent;

	private final File gitDir;

	private final File refsDir;

	private final ReflogWriter logWriter;

	private final File packedRefsFile;

	/**
	 * Immutable sorted list of loose references.
	 * <p>
	 * Symbolic references in this collection are stored unresolved, that is
	 * their target appears to be a new reference with no ObjectId. These are
	 * converted into resolved references during a get operation, ensuring the
	 * live value is always returned.
	 */
	private final AtomicReference<RefList<LooseRef>> looseRefs = new AtomicReference<RefList<LooseRef>>();

	/** Immutable sorted list of packed references. */
	private final AtomicReference<PackedRefList> packedRefs = new AtomicReference<PackedRefList>();

	/**
	 * Number of modifications made to this database.
	 * <p>
	 * This counter is incremented when a change is made, or detected from the
	 * filesystem during a read operation.
	 */
	private final AtomicInteger modCnt = new AtomicInteger();

	/**
	 * Last {@link #modCnt} that we sent to listeners.
	 * <p>
	 * This value is compared to {@link #modCnt}, and a notification is sent to
	 * the listeners only when it differs.
	 */
	private final AtomicInteger lastNotifiedModCnt = new AtomicInteger();

	RefDirectory(final FileRepository db) {
		final FS fs = db.getFS();
		parent = db;
		gitDir = db.getDirectory();
		logWriter = new ReflogWriter(db);
		refsDir = fs.resolve(gitDir, R_REFS);
		packedRefsFile = fs.resolve(gitDir, PACKED_REFS);

		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(PackedRefList.NO_PACKED_REFS);
	}

	Repository getRepository() {
		return parent;
	}

	ReflogWriter getLogWriter() {
		return logWriter;
	}

	public void create() throws IOException {
		FileUtils.mkdir(refsDir);
		FileUtils.mkdir(new File(refsDir, R_HEADS.substring(R_REFS.length())));
		FileUtils.mkdir(new File(refsDir, R_TAGS.substring(R_REFS.length())));
		logWriter.create();
	}

	@Override
	public void close() {
		// We have no resources to close.
	}

	void rescan() {
		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(PackedRefList.NO_PACKED_REFS);
	}

	@Override
	public void refresh() {
		super.refresh();
		rescan();
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		RefList<Ref> packed = getPackedRefs();
		RefList<LooseRef> loose = getLooseRefs();

		// Cannot be nested within an existing reference.
		int lastSlash = name.lastIndexOf('/');
		while (0 < lastSlash) {
			String needle = name.substring(0, lastSlash);
			if (loose.contains(needle) || packed.contains(needle))
				return true;
			lastSlash = name.lastIndexOf('/', lastSlash - 1);
		}

		// Cannot be the container of an existing reference.
		String prefix = name + '/';
		int idx;

		idx = -(packed.find(prefix) + 1);
		if (idx < packed.size() && packed.get(idx).getName().startsWith(prefix))
			return true;

		idx = -(loose.find(prefix) + 1);
		if (idx < loose.size() && loose.get(idx).getName().startsWith(prefix))
			return true;

		return false;
	}

	private RefList<LooseRef> getLooseRefs() {
		final RefList<LooseRef> oldLoose = looseRefs.get();

		LooseScanner scan = new LooseScanner(oldLoose);
		scan.scan(ALL);

		RefList<LooseRef> loose;
		if (scan.newLoose != null) {
			loose = scan.newLoose.toRefList();
			if (looseRefs.compareAndSet(oldLoose, loose))
				modCnt.incrementAndGet();
		} else
			loose = oldLoose;
		return loose;
	}

	@Override
	public Ref getRef(final String needle) throws IOException {
		final RefList<Ref> packed = getPackedRefs();
		Ref ref = null;
		for (String prefix : SEARCH_PATH) {
			try {
				ref = readRef(prefix + needle, packed);
				if (ref != null) {
					ref = resolve(ref, 0, null, null, packed);
					break;
				}
			} catch (IOException e) {
				if (!(!needle.contains("/") && "".equals(prefix) && e //$NON-NLS-1$ //$NON-NLS-2$
						.getCause() instanceof InvalidObjectIdException)) {
					throw e;
				}
			}
		}
		fireRefsChanged();
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		final RefList<Ref> packed = getPackedRefs();
		final RefList<LooseRef> oldLoose = looseRefs.get();

		LooseScanner scan = new LooseScanner(oldLoose);
		scan.scan(prefix);

		RefList<LooseRef> loose;
		if (scan.newLoose != null) {
			scan.newLoose.sort();
			loose = scan.newLoose.toRefList();
			if (looseRefs.compareAndSet(oldLoose, loose))
				modCnt.incrementAndGet();
		} else
			loose = oldLoose;
		fireRefsChanged();

		RefList.Builder<Ref> symbolic = scan.symbolic;
		for (int idx = 0; idx < symbolic.size();) {
			final Ref symbolicRef = symbolic.get(idx);
			final Ref resolvedRef = resolve(symbolicRef, 0, prefix, loose, packed);
			if (resolvedRef != null && resolvedRef.getObjectId() != null) {
				symbolic.set(idx, resolvedRef);
				idx++;
			} else {
				// A broken symbolic reference, we have to drop it from the
				// collections the client is about to receive. Should be a
				// rare occurrence so pay a copy penalty.
				symbolic.remove(idx);
				final int toRemove = loose.find(symbolicRef.getName());
				if (0 <= toRemove)
					loose = loose.remove(toRemove);
			}
		}
		symbolic.sort();

		return new RefMap(prefix, packed, upcast(loose), symbolic.toRefList());
	}

	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		List<Ref> ret = new LinkedList<Ref>();
		for (String name : additionalRefsNames) {
			Ref r = getRef(name);
			if (r != null)
				ret.add(r);
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	private RefList<Ref> upcast(RefList<? extends Ref> loose) {
		return (RefList<Ref>) loose;
	}

	private class LooseScanner {
		private final RefList<LooseRef> curLoose;

		private int curIdx;

		final RefList.Builder<Ref> symbolic = new RefList.Builder<Ref>(4);

		RefList.Builder<LooseRef> newLoose;

		LooseScanner(final RefList<LooseRef> curLoose) {
			this.curLoose = curLoose;
		}

		void scan(String prefix) {
			if (ALL.equals(prefix)) {
				scanOne(HEAD);
				scanTree(R_REFS, refsDir);

				// If any entries remain, they are deleted, drop them.
				if (newLoose == null && curIdx < curLoose.size())
					newLoose = curLoose.copy(curIdx);

			} else if (prefix.startsWith(R_REFS) && prefix.endsWith("/")) { //$NON-NLS-1$
				curIdx = -(curLoose.find(prefix) + 1);
				File dir = new File(refsDir, prefix.substring(R_REFS.length()));
				scanTree(prefix, dir);

				// Skip over entries still within the prefix; these have
				// been removed from the directory.
				while (curIdx < curLoose.size()) {
					if (!curLoose.get(curIdx).getName().startsWith(prefix))
						break;
					if (newLoose == null)
						newLoose = curLoose.copy(curIdx);
					curIdx++;
				}

				// Keep any entries outside of the prefix space, we
				// do not know anything about their status.
				if (newLoose != null) {
					while (curIdx < curLoose.size())
						newLoose.add(curLoose.get(curIdx++));
				}
			}
		}

		private boolean scanTree(String prefix, File dir) {
			final String[] entries = dir.list(LockFile.FILTER);
			if (entries == null) // not a directory or an I/O error
				return false;
			if (0 < entries.length) {
				for (int i = 0; i < entries.length; ++i) {
					String e = entries[i];
					File f = new File(dir, e);
					if (f.isDirectory())
						entries[i] += '/';
				}
				Arrays.sort(entries);
				for (String name : entries) {
					if (name.charAt(name.length() - 1) == '/')
						scanTree(prefix + name, new File(dir, name));
					else
						scanOne(prefix + name);
				}
			}
			return true;
		}

		private void scanOne(String name) {
			LooseRef cur;

			if (curIdx < curLoose.size()) {
				do {
					cur = curLoose.get(curIdx);
					int cmp = RefComparator.compareTo(cur, name);
					if (cmp < 0) {
						// Reference is not loose anymore, its been deleted.
						// Skip the name in the new result list.
						if (newLoose == null)
							newLoose = curLoose.copy(curIdx);
						curIdx++;
						cur = null;
						continue;
					}

					if (cmp > 0) // Newly discovered loose reference.
						cur = null;
					break;
				} while (curIdx < curLoose.size());
			} else
				cur = null; // Newly discovered loose reference.

			LooseRef n;
			try {
				n = scanRef(cur, name);
			} catch (IOException notValid) {
				n = null;
			}

			if (n != null) {
				if (cur != n && newLoose == null)
					newLoose = curLoose.copy(curIdx);
				if (newLoose != null)
					newLoose.add(n);
				if (n.isSymbolic())
					symbolic.add(n);
			} else if (cur != null) {
				// Tragically, this file is no longer a loose reference.
				// Kill our cached entry of it.
				if (newLoose == null)
					newLoose = curLoose.copy(curIdx);
			}

			if (cur != null)
				curIdx++;
		}
	}

	@Override
	public Ref peel(final Ref ref) throws IOException {
		final Ref leaf = ref.getLeaf();
		if (leaf.isPeeled() || leaf.getObjectId() == null)
			return ref;

		ObjectIdRef newLeaf = doPeel(leaf);

		// Try to remember this peeling in the cache, so we don't have to do
		// it again in the future, but only if the reference is unchanged.
		if (leaf.getStorage().isLoose()) {
			RefList<LooseRef> curList = looseRefs.get();
			int idx = curList.find(leaf.getName());
			if (0 <= idx && curList.get(idx) == leaf) {
				LooseRef asPeeled = ((LooseRef) leaf).peel(newLeaf);
				RefList<LooseRef> newList = curList.set(idx, asPeeled);
				looseRefs.compareAndSet(curList, newList);
			}
		}

		return recreate(ref, newLeaf);
	}

	private ObjectIdRef doPeel(final Ref leaf) throws MissingObjectException,
			IOException {
		RevWalk rw = new RevWalk(getRepository());
		try {
			RevObject obj = rw.parseAny(leaf.getObjectId());
			if (obj instanceof RevTag) {
				return new ObjectIdRef.PeeledTag(leaf.getStorage(), leaf
						.getName(), leaf.getObjectId(), rw.peel(obj).copy());
			} else {
				return new ObjectIdRef.PeeledNonTag(leaf.getStorage(), leaf
						.getName(), leaf.getObjectId());
			}
		} finally {
			rw.release();
		}
	}

	private static Ref recreate(final Ref old, final ObjectIdRef leaf) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf);
			return new SymbolicRef(old.getName(), dst);
		}
		return leaf;
	}

	void storedSymbolicRef(RefDirectoryUpdate u, FileSnapshot snapshot,
			String target) {
		putLooseRef(newSymbolicRef(snapshot, u.getRef().getName(), target));
		fireRefsChanged();
	}

	public RefDirectoryUpdate newUpdate(String name, boolean detach)
			throws IOException {
		boolean detachingSymbolicRef = false;
		final RefList<Ref> packed = getPackedRefs();
		Ref ref = readRef(name, packed);
		if (ref != null)
			ref = resolve(ref, 0, null, null, packed);
		if (ref == null)
			ref = new ObjectIdRef.Unpeeled(NEW, name, null);
		else {
			detachingSymbolicRef = detach && ref.isSymbolic();
			if (detachingSymbolicRef)
				ref = new ObjectIdRef.Unpeeled(LOOSE, name, ref.getObjectId());
		}
		RefDirectoryUpdate refDirUpdate = new RefDirectoryUpdate(this, ref);
		if (detachingSymbolicRef)
			refDirUpdate.setDetachingSymbolicRef();
		return refDirUpdate;
	}

	@Override
	public RefDirectoryRename newRename(String fromName, String toName)
			throws IOException {
		RefDirectoryUpdate from = newUpdate(fromName, false);
		RefDirectoryUpdate to = newUpdate(toName, false);
		return new RefDirectoryRename(from, to);
	}

	void stored(RefDirectoryUpdate update, FileSnapshot snapshot) {
		final ObjectId target = update.getNewObjectId().copy();
		final Ref leaf = update.getRef().getLeaf();
		putLooseRef(new LooseUnpeeled(snapshot, leaf.getName(), target));
	}

	private void putLooseRef(LooseRef ref) {
		RefList<LooseRef> cList, nList;
		do {
			cList = looseRefs.get();
			nList = cList.put(ref);
		} while (!looseRefs.compareAndSet(cList, nList));
		modCnt.incrementAndGet();
		fireRefsChanged();
	}

	void delete(RefDirectoryUpdate update) throws IOException {
		Ref dst = update.getRef().getLeaf();
		String name = dst.getName();

		// Write the packed-refs file using an atomic update. We might
		// wind up reading it twice, before and after the lock, to ensure
		// we don't miss an edit made externally.
		final PackedRefList packed = getPackedRefs();
		if (packed.contains(name)) {
			LockFile lck = new LockFile(packedRefsFile,
					update.getRepository().getFS());
			if (!lck.lock())
				throw new LockFailedException(packedRefsFile);
			try {
				PackedRefList cur = readPackedRefs();
				int idx = cur.find(name);
				if (0 <= idx)
					commitPackedRefs(lck, cur.remove(idx), packed);
			} finally {
				lck.unlock();
			}
		}

		RefList<LooseRef> curLoose, newLoose;
		do {
			curLoose = looseRefs.get();
			int idx = curLoose.find(name);
			if (idx < 0)
				break;
			newLoose = curLoose.remove(idx);
		} while (!looseRefs.compareAndSet(curLoose, newLoose));

		int levels = levelsIn(name) - 2;
		delete(logWriter.logFor(name), levels);
		if (dst.getStorage().isLoose()) {
			update.unlock();
			delete(fileFor(name), levels);
		}

		modCnt.incrementAndGet();
		fireRefsChanged();
	}

	/**
	 * Adds a set of refs to the set of packed-refs. Only non-symbolic refs are
	 * added. If a ref with the given name already existed in packed-refs it is
	 * updated with the new value. Each loose ref which was added to the
	 * packed-ref file is deleted. If a given ref can't be locked it will not be
	 * added to the pack file.
	 *
	 * @param refs
	 *            the refs to be added. Must be fully qualified.
	 * @throws IOException
	 */
	public void pack(List<String> refs) throws IOException {
		if (refs.size() == 0)
			return;
		FS fs = parent.getFS();

		// Lock the packed refs file and read the content
		LockFile lck = new LockFile(packedRefsFile, fs);
		if (!lck.lock())
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotLock, packedRefsFile));

		try {
			final PackedRefList packed = getPackedRefs();
			RefList<Ref> cur = readPackedRefs();

			// Iterate over all refs to be packed
			for (String refName : refs) {
				Ref ref = readRef(refName, cur);
				if (ref.isSymbolic())
					continue; // can't pack symbolic refs
				// Add/Update it to packed-refs
				int idx = cur.find(refName);
				if (idx >= 0)
					cur = cur.set(idx, peeledPackedRef(ref));
				else
					cur = cur.add(idx, peeledPackedRef(ref));
			}

			// The new content for packed-refs is collected. Persist it.
			commitPackedRefs(lck, cur, packed);

			// Now delete the loose refs which are now packed
			for (String refName : refs) {
				// Lock the loose ref
				File refFile = fileFor(refName);
				if (!fs.exists(refFile))
					continue;
				LockFile rLck = new LockFile(refFile,
						parent.getFS());
				if (!rLck.lock())
					continue;
				try {
					LooseRef currentLooseRef = scanRef(null, refName);
					if (currentLooseRef == null || currentLooseRef.isSymbolic())
						continue;
					Ref packedRef = cur.get(refName);
					ObjectId clr_oid = currentLooseRef.getObjectId();
					if (clr_oid != null
							&& clr_oid.equals(packedRef.getObjectId())) {
						RefList<LooseRef> curLoose, newLoose;
						do {
							curLoose = looseRefs.get();
							int idx = curLoose.find(refName);
							if (idx < 0)
								break;
							newLoose = curLoose.remove(idx);
						} while (!looseRefs.compareAndSet(curLoose, newLoose));
						int levels = levelsIn(refName) - 2;
						delete(fileFor(refName), levels);
					}
				} finally {
					rLck.unlock();
				}
			}
			// Don't fire refsChanged. The refs have not change, only their
			// storage.
		} finally {
			lck.unlock();
		}
	}

	/**
	 * Make sure a ref is peeled and has the Storage PACKED. If the given ref
	 * has this attributes simply return it. Otherwise create a new peeled
	 * {@link ObjectIdRef} where Storage is set to PACKED.
	 *
	 * @param f
	 * @return a ref for Storage PACKED having the same name, id, peeledId as f
	 * @throws MissingObjectException
	 * @throws IOException
	 */
	private Ref peeledPackedRef(Ref f)
			throws MissingObjectException, IOException {
		if (f.getStorage().isPacked() && f.isPeeled())
			return f;
		if (!f.isPeeled())
			f = peel(f);
		if (f.getPeeledObjectId() != null)
			return new ObjectIdRef.PeeledTag(PACKED, f.getName(),
					f.getObjectId(), f.getPeeledObjectId());
		else
			return new ObjectIdRef.PeeledNonTag(PACKED, f.getName(),
					f.getObjectId());
	}

	void log(final RefUpdate update, final String msg, final boolean deref)
			throws IOException {
		logWriter.log(update, msg, deref);
	}

	private Ref resolve(final Ref ref, int depth, String prefix,
			RefList<LooseRef> loose, RefList<Ref> packed) throws IOException {
		if (ref.isSymbolic()) {
			Ref dst = ref.getTarget();

			if (MAX_SYMBOLIC_REF_DEPTH <= depth)
				return null; // claim it doesn't exist

			// If the cached value can be assumed to be current due to a
			// recent scan of the loose directory, use it.
			if (loose != null && dst.getName().startsWith(prefix)) {
				int idx;
				if (0 <= (idx = loose.find(dst.getName())))
					dst = loose.get(idx);
				else if (0 <= (idx = packed.find(dst.getName())))
					dst = packed.get(idx);
				else
					return ref;
			} else {
				dst = readRef(dst.getName(), packed);
				if (dst == null)
					return ref;
			}

			dst = resolve(dst, depth + 1, prefix, loose, packed);
			if (dst == null)
				return null;
			return new SymbolicRef(ref.getName(), dst);
		}
		return ref;
	}

	private PackedRefList getPackedRefs() throws IOException {
		final PackedRefList curList = packedRefs.get();
		if (!curList.snapshot.isModified(packedRefsFile))
			return curList;

		final PackedRefList newList = readPackedRefs();
		if (packedRefs.compareAndSet(curList, newList)
				&& !curList.id.equals(newList.id))
			modCnt.incrementAndGet();
		return newList;
	}

	private PackedRefList readPackedRefs() throws IOException {
		final FileSnapshot snapshot = FileSnapshot.save(packedRefsFile);
		final BufferedReader br;
		final MessageDigest digest = Constants.newMessageDigest();
		try {
			br = new BufferedReader(new InputStreamReader(
					new DigestInputStream(new FileInputStream(packedRefsFile),
							digest), CHARSET));
		} catch (FileNotFoundException noPackedRefs) {
			// Ignore it and leave the new list empty.
			return PackedRefList.NO_PACKED_REFS;
		}
		try {
			return new PackedRefList(parsePackedRefs(br), snapshot,
					ObjectId.fromRaw(digest.digest()));
		} finally {
			br.close();
		}
	}

	private RefList<Ref> parsePackedRefs(final BufferedReader br)
			throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<Ref>();
		Ref last = null;
		boolean peeled = false;
		boolean needSort = false;

		String p;
		while ((p = br.readLine()) != null) {
			if (p.charAt(0) == '#') {
				if (p.startsWith(PACKED_REFS_HEADER)) {
					p = p.substring(PACKED_REFS_HEADER.length());
					peeled = p.contains(PACKED_REFS_PEELED);
				}
				continue;
			}

			if (p.charAt(0) == '^') {
				if (last == null)
					throw new IOException(JGitText.get().peeledLineBeforeRef);

				ObjectId id = ObjectId.fromString(p.substring(1));
				last = new ObjectIdRef.PeeledTag(PACKED, last.getName(), last
						.getObjectId(), id);
				all.set(all.size() - 1, last);
				continue;
			}

			int sp = p.indexOf(' ');
			ObjectId id = ObjectId.fromString(p.substring(0, sp));
			String name = copy(p, sp + 1, p.length());
			ObjectIdRef cur;
			if (peeled)
				cur = new ObjectIdRef.PeeledNonTag(PACKED, name, id);
			else
				cur = new ObjectIdRef.Unpeeled(PACKED, name, id);
			if (last != null && RefComparator.compareTo(last, cur) > 0)
				needSort = true;
			all.add(cur);
			last = cur;
		}

		if (needSort)
			all.sort();
		return all.toRefList();
	}

	private static String copy(final String src, final int off, final int end) {
		// Don't use substring since it could leave a reference to the much
		// larger existing string. Force construction of a full new object.
		return new StringBuilder(end - off).append(src, off, end).toString();
	}

	private void commitPackedRefs(final LockFile lck, final RefList<Ref> refs,
			final PackedRefList oldPackedList) throws IOException {
		new RefWriter(refs) {
			@Override
			protected void writeFile(String name, byte[] content)
					throws IOException {
				lck.setFSync(true);
				lck.setNeedSnapshot(true);
				try {
					lck.write(content);
				} catch (IOException ioe) {
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().unableToWrite, name), ioe);
				}
				try {
					lck.waitForStatChange();
				} catch (InterruptedException e) {
					lck.unlock();
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().interruptedWriting, name));
				}
				if (!lck.commit())
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().unableToWrite, name));

				byte[] digest = Constants.newMessageDigest().digest(content);
				packedRefs.compareAndSet(oldPackedList, new PackedRefList(refs,
						lck.getCommitSnapshot(), ObjectId.fromRaw(digest)));
			}
		}.writePackedRefs();
	}

	private Ref readRef(String name, RefList<Ref> packed) throws IOException {
		final RefList<LooseRef> curList = looseRefs.get();
		final int idx = curList.find(name);
		if (0 <= idx) {
			final LooseRef o = curList.get(idx);
			final LooseRef n = scanRef(o, name);
			if (n == null) {
				if (looseRefs.compareAndSet(curList, curList.remove(idx)))
					modCnt.incrementAndGet();
				return packed.get(name);
			}

			if (o == n)
				return n;
			if (looseRefs.compareAndSet(curList, curList.set(idx, n)))
				modCnt.incrementAndGet();
			return n;
		}

		final LooseRef n = scanRef(null, name);
		if (n == null)
			return packed.get(name);

		// check whether the found new ref is the an additional ref. These refs
		// should not go into looseRefs
		for (int i = 0; i < additionalRefsNames.length; i++)
			if (name.equals(additionalRefsNames[i]))
				return n;

		if (looseRefs.compareAndSet(curList, curList.add(idx, n)))
			modCnt.incrementAndGet();
		return n;
	}

	@SuppressWarnings("null")
	private LooseRef scanRef(LooseRef ref, String name) throws IOException {
		final File path = fileFor(name);
		FileSnapshot currentSnapshot = null;

		if (ref != null) {
			currentSnapshot = ref.getSnapShot();
			if (!currentSnapshot.isModified(path))
				return ref;
			name = ref.getName();
		}

		final int limit = 4096;
		final byte[] buf;
		FileSnapshot otherSnapshot = FileSnapshot.save(path);
		try {
			buf = IO.readSome(path, limit);
		} catch (FileNotFoundException noFile) {
			return null; // doesn't exist; not a reference.
		}

		int n = buf.length;
		if (n == 0)
			return null; // empty file; not a reference.

		if (isSymRef(buf, n)) {
			if (n == limit)
				return null; // possibly truncated ref

			// trim trailing whitespace
			while (0 < n && Character.isWhitespace(buf[n - 1]))
				n--;
			if (n < 6) {
				String content = RawParseUtils.decode(buf, 0, n);
				throw new IOException(MessageFormat.format(JGitText.get().notARef, name, content));
			}
			final String target = RawParseUtils.decode(buf, 5, n);
			if (ref != null && ref.isSymbolic()
					&& ref.getTarget().getName().equals(target)) {
				currentSnapshot.setClean(otherSnapshot);
				return ref;
			}
			return newSymbolicRef(otherSnapshot, name, target);
		}

		if (n < OBJECT_ID_STRING_LENGTH)
			return null; // impossibly short object identifier; not a reference.

		final ObjectId id;
		try {
			id = ObjectId.fromString(buf, 0);
			if (ref != null && !ref.isSymbolic()
					&& ref.getTarget().getObjectId().equals(id)) {
				currentSnapshot.setClean(otherSnapshot);
				return ref;
			}

		} catch (IllegalArgumentException notRef) {
			while (0 < n && Character.isWhitespace(buf[n - 1]))
				n--;
			String content = RawParseUtils.decode(buf, 0, n);

			IOException ioException = new IOException(MessageFormat.format(JGitText.get().notARef,
					name, content));
			ioException.initCause(notRef);
			throw ioException;
		}
		return new LooseUnpeeled(otherSnapshot, name, id);
	}

	private static boolean isSymRef(final byte[] buf, int n) {
		if (n < 6)
			return false;
		return /**/buf[0] == 'r' //
				&& buf[1] == 'e' //
				&& buf[2] == 'f' //
				&& buf[3] == ':' //
				&& buf[4] == ' ';
	}

	/** If the parent should fire listeners, fires them. */
	private void fireRefsChanged() {
		final int last = lastNotifiedModCnt.get();
		final int curr = modCnt.get();
		if (last != curr && lastNotifiedModCnt.compareAndSet(last, curr) && last != 0)
			parent.fireEvent(new RefsChangedEvent());
	}

	/**
	 * Create a reference update to write a temporary reference.
	 *
	 * @return an update for a new temporary reference.
	 * @throws IOException
	 *             a temporary name cannot be allocated.
	 */
	RefDirectoryUpdate newTemporaryUpdate() throws IOException {
		File tmp = File.createTempFile("renamed_", "_ref", refsDir); //$NON-NLS-1$ //$NON-NLS-2$
		String name = Constants.R_REFS + tmp.getName();
		Ref ref = new ObjectIdRef.Unpeeled(NEW, name, null);
		return new RefDirectoryUpdate(this, ref);
	}

	/**
	 * Locate the file on disk for a single reference name.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the loose file location.
	 */
	File fileFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(refsDir, name);
		}
		return new File(gitDir, name);
	}

	static int levelsIn(final String name) {
		int count = 0;
		for (int p = name.indexOf('/'); p >= 0; p = name.indexOf('/', p + 1))
			count++;
		return count;
	}

	static void delete(final File file, final int depth) throws IOException {
		if (!file.delete() && file.isFile())
			throw new IOException(MessageFormat.format(JGitText.get().fileCannotBeDeleted, file));

		File dir = file.getParentFile();
		for (int i = 0; i < depth; ++i) {
			if (!dir.delete())
				break; // ignore problem here
			dir = dir.getParentFile();
		}
	}

	private static class PackedRefList extends RefList<Ref> {
		static final PackedRefList NO_PACKED_REFS = new PackedRefList(
				RefList.emptyList(), FileSnapshot.MISSING_FILE,
				ObjectId.zeroId());

		final FileSnapshot snapshot;

		final ObjectId id;

		PackedRefList(RefList<Ref> src, FileSnapshot s, ObjectId i) {
			super(src);
			snapshot = s;
			id = i;
		}
	}

	private static LooseSymbolicRef newSymbolicRef(FileSnapshot snapshot,
			String name, String target) {
		Ref dst = new ObjectIdRef.Unpeeled(NEW, target, null);
		return new LooseSymbolicRef(snapshot, name, dst);
	}

	private static interface LooseRef extends Ref {
		FileSnapshot getSnapShot();

		LooseRef peel(ObjectIdRef newLeaf);
	}

	private final static class LoosePeeledTag extends ObjectIdRef.PeeledTag
			implements LooseRef {
		private final FileSnapshot snapShot;

		LoosePeeledTag(FileSnapshot snapshot, String refName, ObjectId id,
				ObjectId p) {
			super(LOOSE, refName, id, p);
			this.snapShot = snapshot;
		}

		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private final static class LooseNonTag extends ObjectIdRef.PeeledNonTag
			implements LooseRef {
		private final FileSnapshot snapShot;

		LooseNonTag(FileSnapshot snapshot, String refName, ObjectId id) {
			super(LOOSE, refName, id);
			this.snapShot = snapshot;
		}

		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private final static class LooseUnpeeled extends ObjectIdRef.Unpeeled
			implements LooseRef {
		private FileSnapshot snapShot;

		LooseUnpeeled(FileSnapshot snapShot, String refName, ObjectId id) {
			super(LOOSE, refName, id);
			this.snapShot = snapShot;
		}

		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			if (newLeaf.getPeeledObjectId() != null)
				return new LoosePeeledTag(snapShot, getName(),
						getObjectId(), newLeaf.getPeeledObjectId());
			else
				return new LooseNonTag(snapShot, getName(),
						getObjectId());
		}
	}

	private final static class LooseSymbolicRef extends SymbolicRef implements
			LooseRef {
		private final FileSnapshot snapShot;

		LooseSymbolicRef(FileSnapshot snapshot, String refName, Ref target) {
			super(refName, target);
			this.snapShot = snapshot;
		}

		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			// We should never try to peel the symbolic references.
			throw new UnsupportedOperationException();
		}
	}
}
