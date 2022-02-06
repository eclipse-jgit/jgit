/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.LOGS;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traditional file system based {@link org.eclipse.jgit.lib.RefDatabase}.
 * <p>
 * This is the classical reference database representation for a Git repository.
 * References are stored in two formats: loose, and packed.
 * <p>
 * Loose references are stored as individual files within the {@code refs/}
 * directory. The file name matches the reference name and the file contents is
 * the current {@link org.eclipse.jgit.lib.ObjectId} in string form.
 * <p>
 * Packed references are stored in a single text file named {@code packed-refs}.
 * In the packed format, each reference is stored on its own line. This file
 * reduces the number of files needed for large reference spaces, reducing the
 * overall size of a Git repository on disk.
 */
public class RefDirectory extends RefDatabase {
	private static final Logger LOG = LoggerFactory
			.getLogger(RefDirectory.class);

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

	@SuppressWarnings("boxing")
	private static final List<Integer> RETRY_SLEEP_MS =
			Collections.unmodifiableList(Arrays.asList(0, 100, 200, 400, 800, 1600));

	private final FileRepository parent;

	private final File gitDir;

	final File refsDir;

	final File packedRefsFile;

	final File logsDir;

	final File logsRefsDir;

	/**
	 * Immutable sorted list of loose references.
	 * <p>
	 * Symbolic references in this collection are stored unresolved, that is
	 * their target appears to be a new reference with no ObjectId. These are
	 * converted into resolved references during a get operation, ensuring the
	 * live value is always returned.
	 */
	private final AtomicReference<RefList<LooseRef>> looseRefs = new AtomicReference<>();

	/** Immutable sorted list of packed references. */
	final AtomicReference<PackedRefList> packedRefs = new AtomicReference<>();

	/**
	 * Lock for coordinating operations within a single process that may contend
	 * on the {@code packed-refs} file.
	 * <p>
	 * All operations that write {@code packed-refs} must still acquire a
	 * {@link LockFile} on {@link #packedRefsFile}, even after they have acquired
	 * this lock, since there may be multiple {@link RefDirectory} instances or
	 * other processes operating on the same repo on disk.
	 * <p>
	 * This lock exists so multiple threads in the same process can wait in a fair
	 * queue without trying, failing, and retrying to acquire the on-disk lock. If
	 * {@code RepositoryCache} is used, this lock instance will be used by all
	 * threads.
	 */
	final ReentrantLock inProcessPackedRefsLock = new ReentrantLock(true);

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

	private List<Integer> retrySleepMs = RETRY_SLEEP_MS;

	RefDirectory(FileRepository db) {
		final FS fs = db.getFS();
		parent = db;
		gitDir = db.getDirectory();
		refsDir = fs.resolve(gitDir, R_REFS);
		logsDir = fs.resolve(gitDir, LOGS);
		logsRefsDir = fs.resolve(gitDir, LOGS + '/' + R_REFS);
		packedRefsFile = fs.resolve(gitDir, PACKED_REFS);

		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(NO_PACKED_REFS);
	}

	Repository getRepository() {
		return parent;
	}

	ReflogWriter newLogWriter(boolean force) {
		return new ReflogWriter(this, force);
	}

	/**
	 * Locate the log file on disk for a single reference name.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the log file location.
	 */
	public File logFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(logsRefsDir, name);
		}
		return new File(logsDir, name);
	}

	/** {@inheritDoc} */
	@Override
	public void create() throws IOException {
		FileUtils.mkdir(refsDir);
		FileUtils.mkdir(new File(refsDir, R_HEADS.substring(R_REFS.length())));
		FileUtils.mkdir(new File(refsDir, R_TAGS.substring(R_REFS.length())));
		newLogWriter(false).create();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		clearReferences();
	}

	private void clearReferences() {
		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(NO_PACKED_REFS);
	}

	/** {@inheritDoc} */
	@Override
	public void refresh() {
		super.refresh();
		clearReferences();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isNameConflicting(String name) throws IOException {
		// Cannot be nested within an existing reference.
		int lastSlash = name.lastIndexOf('/');
		while (0 < lastSlash) {
			String needle = name.substring(0, lastSlash);
			if (exactRef(needle) != null) {
				return true;
			}
			lastSlash = name.lastIndexOf('/', lastSlash - 1);
		}

		// Cannot be the container of an existing reference.
		return getRefsStreamByPrefix(name + '/').findFirst().isPresent();
	}

	@Nullable
	private Ref readAndResolve(String name, RefList<Ref> packed) throws IOException {
		try {
			Ref ref = readRef(name, packed);
			if (ref != null) {
				ref = resolve(ref, 0, null, null, packed);
			}
			return ref;
		} catch (IOException e) {
			if (name.contains("/") //$NON-NLS-1$
					|| !(e.getCause() instanceof InvalidObjectIdException)) {
				throw e;
			}

			// While looking for a ref outside of refs/ (e.g., 'config'), we
			// found a non-ref file (e.g., a config file) instead.  Treat this
			// as a ref-not-found condition.
			return null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		try {
			return readAndResolve(name, getPackedRefs());
		} finally {
			fireRefsChanged();
		}
	}

	/** {@inheritDoc} */
	@Override
	@NonNull
	public Map<String, Ref> exactRef(String... refs) throws IOException {
		try {
			RefList<Ref> packed = getPackedRefs();
			Map<String, Ref> result = new HashMap<>(refs.length);
			for (String name : refs) {
				Ref ref = readAndResolve(name, packed);
				if (ref != null) {
					result.put(name, ref);
				}
			}
			return result;
		} finally {
			fireRefsChanged();
		}
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public Ref firstExactRef(String... refs) throws IOException {
		try {
			RefList<Ref> packed = getPackedRefs();
			for (String name : refs) {
				Ref ref = readAndResolve(name, packed);
				if (ref != null) {
					return ref;
				}
			}
			return null;
		} finally {
			fireRefsChanged();
		}
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		final RefList<LooseRef> oldLoose = looseRefs.get();
		LooseScanner scan = new LooseScanner(oldLoose);
		scan.scan(prefix);
		final RefList<Ref> packed = getPackedRefs();

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

	/** {@inheritDoc} */
	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		List<Ref> ret = new LinkedList<>();
		for (String name : additionalRefsNames) {
			Ref r = exactRef(name);
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

		final RefList.Builder<Ref> symbolic = new RefList.Builder<>(4);

		RefList.Builder<LooseRef> newLoose;

		LooseScanner(RefList<LooseRef> curLoose) {
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

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
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

	private ObjectIdRef doPeel(Ref leaf) throws MissingObjectException,
			IOException {
		try (RevWalk rw = new RevWalk(getRepository())) {
			RevObject obj = rw.parseAny(leaf.getObjectId());
			if (obj instanceof RevTag) {
				return new ObjectIdRef.PeeledTag(leaf.getStorage(), leaf
						.getName(), leaf.getObjectId(), rw.peel(obj).copy());
			}
			return new ObjectIdRef.PeeledNonTag(leaf.getStorage(),
					leaf.getName(), leaf.getObjectId());
		}
	}

	private static Ref recreate(Ref old, ObjectIdRef leaf) {
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

	/** {@inheritDoc} */
	@Override
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
		}
		RefDirectoryUpdate refDirUpdate = new RefDirectoryUpdate(this, ref);
		if (detachingSymbolicRef)
			refDirUpdate.setDetachingSymbolicRef();
		return refDirUpdate;
	}

	/** {@inheritDoc} */
	@Override
	public RefDirectoryRename newRename(String fromName, String toName)
			throws IOException {
		RefDirectoryUpdate from = newUpdate(fromName, false);
		RefDirectoryUpdate to = newUpdate(toName, false);
		return new RefDirectoryRename(from, to);
	}

	/** {@inheritDoc} */
	@Override
	public PackedBatchRefUpdate newBatchUpdate() {
		return new PackedBatchRefUpdate(this);
	}

	/** {@inheritDoc} */
	@Override
	public boolean performsAtomicTransactions() {
		return true;
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
		Ref dst = update.getRef();
		if (!update.isDetachingSymbolicRef()) {
			dst = dst.getLeaf();
		}
		String name = dst.getName();

		// Write the packed-refs file using an atomic update. We might
		// wind up reading it twice, before and after the lock, to ensure
		// we don't miss an edit made externally.
		final PackedRefList packed = getPackedRefs();
		if (packed.contains(name)) {
			inProcessPackedRefsLock.lock();
			try {
				LockFile lck = lockPackedRefsOrThrow();
				try {
					PackedRefList cur = readPackedRefs();
					int idx = cur.find(name);
					if (0 <= idx) {
						commitPackedRefs(lck, cur.remove(idx), packed, true);
					}
				} finally {
					lck.unlock();
				}
			} finally {
				inProcessPackedRefsLock.unlock();
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
		delete(logFor(name), levels);
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
	 * @throws java.io.IOException
	 */
	public void pack(List<String> refs) throws IOException {
		pack(refs, Collections.emptyMap());
	}

	PackedRefList pack(Map<String, LockFile> heldLocks) throws IOException {
		return pack(heldLocks.keySet(), heldLocks);
	}

	private PackedRefList pack(Collection<String> refs,
			Map<String, LockFile> heldLocks) throws IOException {
		for (LockFile ol : heldLocks.values()) {
			ol.requireLock();
		}
		if (refs.isEmpty()) {
			return null;
		}
		FS fs = parent.getFS();

		// Lock the packed refs file and read the content
		inProcessPackedRefsLock.lock();
		try {
			LockFile lck = lockPackedRefsOrThrow();
			try {
				final PackedRefList packed = getPackedRefs();
				RefList<Ref> cur = readPackedRefs();

				// Iterate over all refs to be packed
				boolean dirty = false;
				for (String refName : refs) {
					Ref oldRef = readRef(refName, cur);
					if (oldRef == null) {
						continue; // A non-existent ref is already correctly packed.
					}
					if (oldRef.isSymbolic()) {
						continue; // can't pack symbolic refs
					}
					// Add/Update it to packed-refs
					Ref newRef = peeledPackedRef(oldRef);
					if (newRef == oldRef) {
						// No-op; peeledPackedRef returns the input ref only if it's already
						// packed, and readRef returns a packed ref only if there is no
						// loose ref.
						continue;
					}

					dirty = true;
					int idx = cur.find(refName);
					if (idx >= 0) {
						cur = cur.set(idx, newRef);
					} else {
						cur = cur.add(idx, newRef);
					}
				}
				if (!dirty) {
					// All requested refs were already packed accurately
					return packed;
				}

				// The new content for packed-refs is collected. Persist it.
				PackedRefList result = commitPackedRefs(lck, cur, packed,
						false);

				// Now delete the loose refs which are now packed
				for (String refName : refs) {
					// Lock the loose ref
					File refFile = fileFor(refName);
					if (!fs.exists(refFile)) {
						continue;
					}

					LockFile rLck = heldLocks.get(refName);
					boolean shouldUnlock;
					if (rLck == null) {
						rLck = new LockFile(refFile);
						if (!rLck.lock()) {
							continue;
						}
						shouldUnlock = true;
					} else {
						shouldUnlock = false;
					}

					try {
						LooseRef currentLooseRef = scanRef(null, refName);
						if (currentLooseRef == null || currentLooseRef.isSymbolic()) {
							continue;
						}
						Ref packedRef = cur.get(refName);
						ObjectId clr_oid = currentLooseRef.getObjectId();
						if (clr_oid != null
								&& clr_oid.equals(packedRef.getObjectId())) {
							RefList<LooseRef> curLoose, newLoose;
							do {
								curLoose = looseRefs.get();
								int idx = curLoose.find(refName);
								if (idx < 0) {
									break;
								}
								newLoose = curLoose.remove(idx);
							} while (!looseRefs.compareAndSet(curLoose, newLoose));
							int levels = levelsIn(refName) - 2;
							delete(refFile, levels, rLck);
						}
					} finally {
						if (shouldUnlock) {
							rLck.unlock();
						}
					}
				}
				// Don't fire refsChanged. The refs have not change, only their
				// storage.
				return result;
			} finally {
				lck.unlock();
			}
		} finally {
			inProcessPackedRefsLock.unlock();
		}
	}

	@Nullable
	LockFile lockPackedRefs() throws IOException {
		LockFile lck = new LockFile(packedRefsFile);
		for (int ms : getRetrySleepMs()) {
			sleep(ms);
			if (lck.lock()) {
				return lck;
			}
		}
		return null;
	}

	private LockFile lockPackedRefsOrThrow() throws IOException {
		LockFile lck = lockPackedRefs();
		if (lck == null) {
			throw new LockFailedException(packedRefsFile);
		}
		return lck;
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
		if (f.getStorage().isPacked() && f.isPeeled()) {
			return f;
		}
		if (!f.isPeeled()) {
			f = peel(f);
		}
		ObjectId peeledObjectId = f.getPeeledObjectId();
		if (peeledObjectId != null) {
			return new ObjectIdRef.PeeledTag(PACKED, f.getName(),
					f.getObjectId(), peeledObjectId);
		}
		return new ObjectIdRef.PeeledNonTag(PACKED, f.getName(),
				f.getObjectId());
	}

	void log(boolean force, RefUpdate update, String msg, boolean deref)
			throws IOException {
		newLogWriter(force).log(update, msg, deref);
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

	PackedRefList getPackedRefs() throws IOException {
		boolean trustFolderStat = getRepository().getConfig().getBoolean(
				ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, true);

		final PackedRefList curList = packedRefs.get();
		if (trustFolderStat && !curList.snapshot.isModified(packedRefsFile)) {
			return curList;
		}

		final PackedRefList newList = readPackedRefs();
		if (packedRefs.compareAndSet(curList, newList)
				&& !curList.id.equals(newList.id)) {
			modCnt.incrementAndGet();
		}
		return newList;
	}

	private PackedRefList readPackedRefs() throws IOException {
		try {
			PackedRefList result = FileUtils.readWithRetries(packedRefsFile,
					f -> {
						FileSnapshot snapshot = FileSnapshot.save(f);
						MessageDigest digest = Constants.newMessageDigest();
						try (BufferedReader br = new BufferedReader(
								new InputStreamReader(
										new DigestInputStream(
												new FileInputStream(f), digest),
										UTF_8))) {
							return new PackedRefList(parsePackedRefs(br),
									snapshot,
									ObjectId.fromRaw(digest.digest()));
						}
					});
			return result != null ? result : NO_PACKED_REFS;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(MessageFormat
					.format(JGitText.get().cannotReadFile, packedRefsFile), e);
		}
	}

	private RefList<Ref> parsePackedRefs(BufferedReader br)
			throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<>();
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
			if (sp < 0) {
				throw new IOException(MessageFormat.format(
						JGitText.get().packedRefsCorruptionDetected,
						packedRefsFile.getAbsolutePath()));
			}
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

	private static String copy(String src, int off, int end) {
		// Don't use substring since it could leave a reference to the much
		// larger existing string. Force construction of a full new object.
		return new StringBuilder(end - off).append(src, off, end).toString();
	}

	PackedRefList commitPackedRefs(final LockFile lck, final RefList<Ref> refs,
			final PackedRefList oldPackedList, boolean changed)
			throws IOException {
		// Can't just return packedRefs.get() from this method; it might have been
		// updated again after writePackedRefs() returns.
		AtomicReference<PackedRefList> result = new AtomicReference<>();
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
					throw new ObjectWritingException(
							MessageFormat.format(
									JGitText.get().interruptedWriting, name),
							e);
				}
				if (!lck.commit())
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().unableToWrite, name));

				byte[] digest = Constants.newMessageDigest().digest(content);
				PackedRefList newPackedList = new PackedRefList(
						refs, lck.getCommitSnapshot(), ObjectId.fromRaw(digest));

				// This thread holds the file lock, so no other thread or process should
				// be able to modify the packed-refs file on disk. If the list changed,
				// it means something is very wrong, so throw an exception.
				//
				// However, we can't use a naive compareAndSet to check whether the
				// update was successful, because another thread might _read_ the
				// packed refs file that was written out by this thread while holding
				// the lock, and update the packedRefs reference to point to that. So
				// compare the actual contents instead.
				PackedRefList afterUpdate = packedRefs.updateAndGet(
						p -> p.id.equals(oldPackedList.id) ? newPackedList : p);
				if (!afterUpdate.id.equals(newPackedList.id)) {
					throw new ObjectWritingException(
							MessageFormat.format(JGitText.get().unableToWrite, name));
				}
				if (changed) {
					modCnt.incrementAndGet();
				}
				result.set(newPackedList);
			}
		}.writePackedRefs();
		return result.get();
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
		for (String additionalRefsName : additionalRefsNames) {
			if (name.equals(additionalRefsName)) {
				return n;
			}
		}

		if (looseRefs.compareAndSet(curList, curList.add(idx, n)))
			modCnt.incrementAndGet();
		return n;
	}

	LooseRef scanRef(LooseRef ref, String name) throws IOException {
		final File path = fileFor(name);
		FileSnapshot currentSnapshot = null;

		if (ref != null) {
			currentSnapshot = ref.getSnapShot();
			if (!currentSnapshot.isModified(path))
				return ref;
			name = ref.getName();
		}

		final int limit = 4096;

		class LooseItems {
			final FileSnapshot snapshot;

			final byte[] buf;

			LooseItems(FileSnapshot snapshot, byte[] buf) {
				this.snapshot = snapshot;
				this.buf = buf;
			}
		}
		LooseItems loose = null;
		try {
			loose = FileUtils.readWithRetries(path,
					f -> new LooseItems(FileSnapshot.save(f),
							IO.readSome(f, limit)));
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(
					MessageFormat.format(JGitText.get().cannotReadFile, path),
					e);
		}
		if (loose == null) {
			return null;
		}
		int n = loose.buf.length;
		if (n == 0)
			return null; // empty file; not a reference.

		if (isSymRef(loose.buf, n)) {
			if (n == limit)
				return null; // possibly truncated ref

			// trim trailing whitespace
			while (0 < n && Character.isWhitespace(loose.buf[n - 1]))
				n--;
			if (n < 6) {
				String content = RawParseUtils.decode(loose.buf, 0, n);
				throw new IOException(MessageFormat.format(JGitText.get().notARef, name, content));
			}
			final String target = RawParseUtils.decode(loose.buf, 5, n);
			if (ref != null && ref.isSymbolic()
					&& ref.getTarget().getName().equals(target)) {
				assert(currentSnapshot != null);
				currentSnapshot.setClean(loose.snapshot);
				return ref;
			}
			return newSymbolicRef(loose.snapshot, name, target);
		}

		if (n < OBJECT_ID_STRING_LENGTH)
			return null; // impossibly short object identifier; not a reference.

		final ObjectId id;
		try {
			id = ObjectId.fromString(loose.buf, 0);
			if (ref != null && !ref.isSymbolic()
					&& id.equals(ref.getTarget().getObjectId())) {
				assert(currentSnapshot != null);
				currentSnapshot.setClean(loose.snapshot);
				return ref;
			}

		} catch (IllegalArgumentException notRef) {
			while (0 < n && Character.isWhitespace(loose.buf[n - 1]))
				n--;
			String content = RawParseUtils.decode(loose.buf, 0, n);

			throw new IOException(MessageFormat.format(JGitText.get().notARef,
					name, content), notRef);
		}
		return new LooseUnpeeled(loose.snapshot, name, id);
	}

	private static boolean isSymRef(byte[] buf, int n) {
		if (n < 6)
			return false;
		return /**/buf[0] == 'r' //
				&& buf[1] == 'e' //
				&& buf[2] == 'f' //
				&& buf[3] == ':' //
				&& buf[4] == ' ';
	}

	/**
	 * Detect if we are in a clone command execution
	 *
	 * @return {@code true} if we are currently cloning a repository
	 * @throws IOException
	 */
	boolean isInClone() throws IOException {
		return hasDanglingHead() && !packedRefsFile.exists() && !hasLooseRef();
	}

	private boolean hasDanglingHead() throws IOException {
		Ref head = exactRef(Constants.HEAD);
		if (head != null) {
			ObjectId id = head.getObjectId();
			return id == null || id.equals(ObjectId.zeroId());
		}
		return false;
	}

	private boolean hasLooseRef() throws IOException {
		try (Stream<Path> stream = Files.walk(refsDir.toPath())) {
			return stream.anyMatch(Files::isRegularFile);
		}
	}

	/** If the parent should fire listeners, fires them. */
	void fireRefsChanged() {
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

	static int levelsIn(String name) {
		int count = 0;
		for (int p = name.indexOf('/'); p >= 0; p = name.indexOf('/', p + 1))
			count++;
		return count;
	}

	static void delete(File file, int depth) throws IOException {
		delete(file, depth, null);
	}

	private static void delete(File file, int depth, LockFile rLck)
			throws IOException {
		if (!file.delete() && file.isFile()) {
			throw new IOException(MessageFormat.format(
					JGitText.get().fileCannotBeDeleted, file));
		}

		if (rLck != null) {
			rLck.unlock(); // otherwise cannot delete dir below
		}
		File dir = file.getParentFile();
		for (int i = 0; i < depth; ++i) {
			try {
				Files.deleteIfExists(dir.toPath());
			} catch (DirectoryNotEmptyException e) {
				// Don't log; normal case when there are other refs with the
				// same prefix
				break;
			} catch (IOException e) {
				LOG.warn(MessageFormat.format(JGitText.get().unableToRemovePath,
						dir), e);
				break;
			}
			dir = dir.getParentFile();
		}
	}

	/**
	 * Get times to sleep while retrying a possibly contentious operation.
	 * <p>
	 * For retrying an operation that might have high contention, such as locking
	 * the {@code packed-refs} file, the caller may implement a retry loop using
	 * the returned values:
	 *
	 * <pre>
	 * for (int toSleepMs : getRetrySleepMs()) {
	 *   sleep(toSleepMs);
	 *   if (isSuccessful(doSomething())) {
	 *     return success;
	 *   }
	 * }
	 * return failure;
	 * </pre>
	 *
	 * The first value in the returned iterable is 0, and the caller should treat
	 * a fully-consumed iterator as a timeout.
	 *
	 * @return iterable of times, in milliseconds, that the caller should sleep
	 *         before attempting an operation.
	 */
	Iterable<Integer> getRetrySleepMs() {
		return retrySleepMs;
	}

	void setRetrySleepMs(List<Integer> retrySleepMs) {
		if (retrySleepMs == null || retrySleepMs.isEmpty()
				|| retrySleepMs.get(0).intValue() != 0) {
			throw new IllegalArgumentException();
		}
		this.retrySleepMs = retrySleepMs;
	}

	/**
	 * Sleep with {@link Thread#sleep(long)}, converting {@link
	 * InterruptedException} to {@link InterruptedIOException}.
	 *
	 * @param ms
	 *            time to sleep, in milliseconds; zero or negative is a no-op.
	 * @throws InterruptedIOException
	 *             if sleeping was interrupted.
	 */
	static void sleep(long ms) throws InterruptedIOException {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			InterruptedIOException ie = new InterruptedIOException();
			ie.initCause(e);
			throw ie;
		}
	}

	static class PackedRefList extends RefList<Ref> {

		private final FileSnapshot snapshot;

		private final ObjectId id;

		private PackedRefList(RefList<Ref> src, FileSnapshot s, ObjectId i) {
			super(src);
			snapshot = s;
			id = i;
		}
	}

	private static final PackedRefList NO_PACKED_REFS = new PackedRefList(
			RefList.emptyList(), FileSnapshot.MISSING_FILE,
			ObjectId.zeroId());

	private static LooseSymbolicRef newSymbolicRef(FileSnapshot snapshot,
			String name, String target) {
		Ref dst = new ObjectIdRef.Unpeeled(NEW, target, null);
		return new LooseSymbolicRef(snapshot, name, dst);
	}

	private static interface LooseRef extends Ref {
		FileSnapshot getSnapShot();

		LooseRef peel(ObjectIdRef newLeaf);
	}

	private static final class LoosePeeledTag extends ObjectIdRef.PeeledTag
			implements LooseRef {
		private final FileSnapshot snapShot;

		LoosePeeledTag(FileSnapshot snapshot, @NonNull String refName,
				@NonNull ObjectId id, @NonNull ObjectId p) {
			super(LOOSE, refName, id, p);
			this.snapShot = snapshot;
		}

		@Override
		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		@Override
		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private static final class LooseNonTag extends ObjectIdRef.PeeledNonTag
			implements LooseRef {
		private final FileSnapshot snapShot;

		LooseNonTag(FileSnapshot snapshot, @NonNull String refName,
				@NonNull ObjectId id) {
			super(LOOSE, refName, id);
			this.snapShot = snapshot;
		}

		@Override
		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		@Override
		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private static final class LooseUnpeeled extends ObjectIdRef.Unpeeled
			implements LooseRef {
		private FileSnapshot snapShot;

		LooseUnpeeled(FileSnapshot snapShot, @NonNull String refName,
				@NonNull ObjectId id) {
			super(LOOSE, refName, id);
			this.snapShot = snapShot;
		}

		@Override
		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		@NonNull
		@Override
		public ObjectId getObjectId() {
			ObjectId id = super.getObjectId();
			assert id != null; // checked in constructor
			return id;
		}

		@Override
		public LooseRef peel(ObjectIdRef newLeaf) {
			ObjectId peeledObjectId = newLeaf.getPeeledObjectId();
			ObjectId objectId = getObjectId();
			if (peeledObjectId != null) {
				return new LoosePeeledTag(snapShot, getName(),
						objectId, peeledObjectId);
			}
			return new LooseNonTag(snapShot, getName(), objectId);
		}
	}

	private static final class LooseSymbolicRef extends SymbolicRef implements
			LooseRef {
		private final FileSnapshot snapShot;

		LooseSymbolicRef(FileSnapshot snapshot, @NonNull String refName,
				@NonNull Ref target) {
			super(refName, target);
			this.snapShot = snapshot;
		}

		@Override
		public FileSnapshot getSnapShot() {
			return snapShot;
		}

		@Override
		public LooseRef peel(ObjectIdRef newLeaf) {
			// We should never try to peel the symbolic references.
			throw new UnsupportedOperationException();
		}
	}
}
