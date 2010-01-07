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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.LOGS;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.Constants.PACKED_REFS;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Constants.encode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

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
class RefDirectory extends RefDatabase {
	private static final String SYMREF = "ref: ";//$NON-NLS-1$

	private static final String PACKED_REFS_HEADER = "# pack-refs with:";//$NON-NLS-1$

	private final Repository parent;

	private final File gitDir;

	private final File refsDir;

	private final File logsDir;

	private final File logsRefsDir;

	private final File packedRefsFile;

	/** Lock to protect {@link #cache}. */
	private final Object cacheLock;

	/**
	 * Map of reference name (e.g. {@code refs/heads/master}) to definition.
	 * <p>
	 * Each named reference stored may have up to two {@link Ref} instances in
	 * the holder. The first, {@link RefHolder#packRef} exists only if the
	 * reference was parsed out of the {@code packed-refs} file. The second,
	 * {@link RefHolder#currRef} is always pointing to the current definition.
	 * <p>
	 * Symbolic references (such as {@code HEAD}) are stored by creating a
	 * {@link SymbolicRef} whose target is always a {@link Ref.Storage#NEW}
	 * {@link ObjectIdRef} with a null ObjectId. When reading the symbolic
	 * reference from this map the target is resolved dynamically by calling the
	 * {@link #resolve(Ref, int, String)} method, and a cloned reference is
	 * returned to the caller.
	 */
	private Map<String, RefHolder> cache;

	/** Last length of the packed-refs file when we read it. */
	private long packedRefsLastLength;

	/** Last modified time of the packed-refs file when we read it. */
	private long packedRefsLastModified;

	/**
	 * Number of modifications made to this database.
	 * <p>
	 * This counter is incremented when a change is made, or detected from the
	 * filesystem during a read operation.
	 */
	private volatile int modCnt;

	/**
	 * Last {@link #modCnt} that we sent to listeners.
	 * <p>
	 * This value is compared to {@link #modCnt}, and a notification is sent to
	 * the listeners only when it differs.
	 */
	private final AtomicInteger lastNotifiedModCnt = new AtomicInteger();

	RefDirectory(final Repository db) {
		parent = db;
		gitDir = db.getDirectory();
		refsDir = FS.resolve(gitDir, R_REFS);
		logsDir = FS.resolve(gitDir, LOGS);
		logsRefsDir = FS.resolve(gitDir, LOGS + '/' + R_REFS);
		packedRefsFile = FS.resolve(gitDir, PACKED_REFS);

		cacheLock = new Object();
		cache = new HashMap<String, RefHolder>();
	}

	Repository getRepository() {
		return parent;
	}

	public void create() throws IOException {
		refsDir.mkdir();
		logsDir.mkdir();
		logsRefsDir.mkdir();

		new File(refsDir, R_HEADS.substring(R_REFS.length())).mkdir();
		new File(refsDir, R_TAGS.substring(R_REFS.length())).mkdir();
		new File(logsRefsDir, R_HEADS.substring(R_REFS.length())).mkdir();
	}

	void rescan() {
		synchronized (cacheLock) {
			cache = new HashMap<String, RefHolder>();
			packedRefsLastModified = 0;
			packedRefsLastLength = 0;
		}
	}

	@Override
	public Ref getRef(String name) throws IOException {
		Ref ref = null;
		synchronized (cacheLock) {
			scanPackedRefs();

			for (String prefix : SEARCH_PATH) {
				ref = scanRef(prefix + name);
				if (ref != null) {
					ref = resolve(ref, 0, null);
					break;
				}
			}
		}
		fireRefsChanged();
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		Map<String, Ref> out;
		synchronized (cacheLock) {
			scanPackedRefs();

			if (prefix.equals(ALL)) {
				scanLooseRefs(R_REFS, refsDir);
				scanRef(HEAD);
			} else if (prefix.startsWith(R_REFS)) {
				String name = prefix.substring(R_REFS.length());
				scanLooseRefs(prefix, new File(refsDir, name));
			}

			out = new HashMap<String, Ref>(Math.max(cache.size() * 2, 16));
			for (RefHolder holder : cache.values()) {
				Ref ref = holder.currRef;
				if (ref.getName().startsWith(prefix)) {
					ref = resolve(ref, 0, prefix);
					if (ref != null && ref.getObjectId() != null)
						out.put(ref.getName().substring(prefix.length()), ref);
				}
			}
		}
		fireRefsChanged();
		return out;
	}

	@Override
	public Ref peel(final Ref ref) throws IOException {
		final Ref cur = ref.getLeaf();
		if (cur.isPeeled() || cur.getObjectId() == null)
			return ref;

		RevWalk rw = new RevWalk(getRepository());
		RevObject obj = rw.parseAny(cur.getObjectId());
		if (obj instanceof RevTag) {
			do {
				obj = rw.parseAny(((RevTag) obj).getObject());
			} while (obj instanceof RevTag);
		} else {
			obj = null;
		}

		ObjectIdRef newLeaf = new ObjectIdRef(cur.getStorage(), cur.getName(),
				cur.getObjectId(), obj != null ? obj.copy() : null, true);

		// Try to remember this peeling in the cache, so we don't have to do
		// it again in the future. But we only bother with the update if the
		// reference hasn't changed since we last read it.
		synchronized (cacheLock) {
			RefHolder h = cache.get(cur.getName());
			if (h != null && cur.getObjectId().equals(h.currRef.getObjectId()))
				h.currRef = newLeaf;
		}

		return recreate(ref, newLeaf);
	}

	private static Ref recreate(final Ref old, final ObjectIdRef leaf) {
		if (old instanceof SymbolicRef) {
			Ref dst = recreate(((SymbolicRef) old).getTarget(), leaf);
			return new SymbolicRef(dst, old.getName());
		}
		return leaf;
	}

	@Override
	public void link(String name, String target) throws IOException {
		long m = write(fileFor(name), encode(SYMREF + target + '\n'));

		synchronized (cacheLock) {
			RefHolder holder = cache.get(name);
			if (holder != null)
				name = holder.currRef.getName();
			else {
				holder = new RefHolder();
				cache.put(name, holder);
			}

			holder.currRef = newSymbolicRef(name, target);
			holder.looseModified = m;
			modCnt++;
		}
		fireRefsChanged();
	}

	@Override
	public RefDirectoryUpdate newUpdate(String name, boolean detach)
			throws IOException {
		Ref ref;
		synchronized (cacheLock) {
			scanPackedRefs();
			ref = scanRef(name);
			if (ref != null)
				ref = resolve(ref, 0, null);
		}
		if (ref == null)
			ref = new ObjectIdRef(Ref.Storage.NEW, name, null);
		else if (detach && ref instanceof SymbolicRef)
			ref = new ObjectIdRef(Ref.Storage.LOOSE, name, ref.getObjectId());
		return new RefDirectoryUpdate(this, ref);
	}

	@Override
	public RefDirectoryRename newRename(String fromName, String toName)
			throws IOException {
		RefDirectoryUpdate from = newUpdate(fromName, false);
		RefDirectoryUpdate to = newUpdate(toName, false);
		return new RefDirectoryRename(from, to);
	}

	void stored(RefDirectoryUpdate update, long lastModified) {
		Ref dst = update.getRef().getLeaf();
		String name = dst.getName();
		synchronized (cacheLock) {
			RefHolder holder = cache.get(name);
			if (holder != null)
				name = holder.currRef.getName();
			else {
				holder = new RefHolder();
				cache.put(name, holder);
			}

			Ref.Storage storage;
			if (holder.packRef != null)
				storage = Ref.Storage.LOOSE_PACKED;
			else
				storage = Ref.Storage.LOOSE;
			ObjectId id = update.getNewObjectId().copy();
			holder.currRef = new ObjectIdRef(storage, name, id);
			holder.looseModified = lastModified;
			modCnt++;
		}
		fireRefsChanged();
	}

	void delete(RefDirectoryUpdate update) throws IOException {
		Ref dst = update.getRef().getLeaf();
		String name = dst.getName();

		synchronized (cacheLock) {
			scanPackedRefs();
			RefHolder holder = cache.remove(name);
			if (holder.packRef != null)
				savePackedRefs();
		}

		int levels = levelsIn(name) - 2;
		delete(logFor(name), levels);
		if (dst.getStorage().isLoose()) {
			update.unlock();
			delete(fileFor(name), levels);
		}

		synchronized (cacheLock) {
			modCnt++;
		}
		fireRefsChanged();
	}

	void log(final RefUpdate update, final String msg) throws IOException {
		final ObjectId oldId = update.getOldObjectId();
		final ObjectId newId = update.getNewObjectId();
		final Ref ref = update.getRef();

		PersonIdent ident = update.getRefLogIdent();
		if (ident == null)
			ident = new PersonIdent(parent);
		else
			ident = new PersonIdent(ident);

		final StringBuilder r = new StringBuilder();
		r.append(ObjectId.toString(oldId));
		r.append(' ');
		r.append(ObjectId.toString(newId));
		r.append(' ');
		r.append(ident.toExternalString());
		r.append('\t');
		r.append(msg);
		r.append('\n');
		final byte[] rec = encode(r.toString());

		if (ref instanceof SymbolicRef)
			log(ref.getName(), rec);
		log(ref.getLeaf().getName(), rec);
	}

	private void log(final String refName, final byte[] rec) throws IOException {
		final File log = logFor(refName);
		final boolean write;
		if (isLogAllRefUpdates() && shouldAutoCreateLog(refName))
			write = true;
		else if (log.isFile())
			write = true;
		else
			write = false;

		if (write) {
			FileOutputStream out;
			try {
				out = new FileOutputStream(log, true);
			} catch (FileNotFoundException err) {
				final File dir = log.getParentFile();
				if (dir.exists())
					throw err;
				if (!dir.mkdirs() && !dir.isDirectory())
					throw new IOException("Cannot create directory " + dir);
				out = new FileOutputStream(log, true);
			}
			try {
				out.write(rec);
			} finally {
				out.close();
			}
		}
	}

	private boolean isLogAllRefUpdates() {
		return parent.getConfig().getCore().isLogAllRefUpdates();
	}

	private boolean shouldAutoCreateLog(final String refName) {
		return refName.equals(HEAD) //
				|| refName.startsWith(R_HEADS) //
				|| refName.startsWith(R_REMOTES);
	}

	private Ref resolve(final Ref ref, int depth, String prefix)
			throws IOException {
		if (ref instanceof SymbolicRef) {
			Ref dst = ((SymbolicRef) ref).getTarget();

			if (MAX_SYMBOLIC_REF_DEPTH <= depth)
				return null; // claim it doesn't exist

			// If the cached value can be assumed to be current due to a
			// recent scan of the loose directory, use it.
			if (prefix != null && dst.getName().startsWith(prefix)) {
				RefHolder h = cache.get(dst.getName());
				if (h == null)
					return ref;
				dst = h.currRef;
			} else {
				dst = scanRef(dst.getName());
				if (dst == null)
					return ref;
			}

			dst = resolve(dst, depth + 1, prefix);
			if (dst == null)
				return null;
			return new SymbolicRef(dst, ref.getName());
		}
		return ref;
	}

	/* ensure the current packed-refs is loaded into memory. */
	private void scanPackedRefs() throws IOException {
		long sz = packedRefsFile.length();
		long mt = packedRefsFile.lastModified();
		if (sz != packedRefsLastLength && mt != packedRefsLastModified)
			reloadPackedRefs(sz, mt);
	}

	private void reloadPackedRefs(long length, long modified)
			throws IOException {
		Map<String, RefHolder> next;
		try {
			final BufferedReader b = openReader(packedRefsFile);
			try {
				next = parsePackedRefs(b);
			} finally {
				b.close();
			}
		} catch (FileNotFoundException noPackedRefs) {
			// Ignore it and leave the new map empty.
			next = new HashMap<String, RefHolder>();
		}

		cache = next;
		packedRefsLastModified = modified;
		packedRefsLastLength = length;
		modCnt++;
	}

	private Map<String, RefHolder> parsePackedRefs(final BufferedReader b)
			throws IOException {
		Map<String, RefHolder> all = new HashMap<String, RefHolder>();
		Ref last = null;
		boolean peeled = false;

		String p;
		while ((p = b.readLine()) != null) {
			if (p.charAt(0) == '#') {
				if (p.startsWith(PACKED_REFS_HEADER)) {
					p = p.substring(PACKED_REFS_HEADER.length());
					peeled = p.contains(" peeled"); //$NON-NLS-1$
				}
				continue;
			}

			if (p.charAt(0) == '^') {
				if (last == null)
					throw new IOException("Peeled line before ref.");

				ObjectId id = ObjectId.fromString(p.substring(1));
				last = new ObjectIdRef(Ref.Storage.PACKED, last.getName(), last
						.getObjectId(), id, true);
				all.put(last.getName(), packedHolder(last));
				continue;
			}

			int sp = p.indexOf(' ');
			ObjectId id = ObjectId.fromString(p.substring(0, sp));
			String name = copy(p, sp + 1, p.length());
			last = new ObjectIdRef(Ref.Storage.PACKED, name, id, null, peeled);
			all.put(last.getName(), packedHolder(last));
		}
		return all;
	}

	private void savePackedRefs() throws IOException {
		ArrayList<Ref> toPack = new ArrayList<Ref>(cache.size());
		for (RefHolder holder : cache.values())
			if (holder.packRef != null)
				toPack.add(holder.packRef);
		new RefWriter(toPack) {
			@Override
			protected void writeFile(String name, byte[] content)
					throws IOException {
				packedRefsLastModified = write(packedRefsFile, content);
				packedRefsLastLength = content.length;
			}
		}.writePackedRefs();
	}

	private void scanLooseRefs(final String prefix, final File dir) {
		final File[] entries = dir.listFiles(LockFile.FILTER);
		if (entries != null) {
			for (final File ent : entries) {
				final String name = ent.getName();
				if (ent.isDirectory()) {
					scanLooseRefs(prefix + name + '/', ent);
				} else {
					try {
						scanRef(prefix + name);
					} catch (IOException e) {
						continue;
					}
				}
			}
		}
	}

	private Ref scanRef(String name) throws IOException {
		final File path = fileFor(name);
		final long modified = path.lastModified();
		Ref ref;

		RefHolder holder = cache.get(name);
		if (holder != null) {
			ref = holder.currRef;
			if (holder.looseModified == modified)
				return ref;
			name = ref.getName();
		} else {
			if (modified == 0)
				return null;
			ref = null;
		}

		String content = read(path);
		if ("".equals(content)) {
			// Loose file doesn't exist, or exists but is empty.
			// We might still have a packed copy, or its gone.
			if (holder != null) {
				ref = holder.packRef;
				if (ref != null) {
					holder.currRef = ref;
					holder.looseModified = modified;
					modCnt++;
					return ref;
				}
				cache.remove(name);
				modCnt++;
			}
			return null;
		}

		if (content.startsWith(SYMREF)) {
			ref = newSymbolicRef(name, copy(content, SYMREF.length()));

		} else {
			final ObjectId id;
			try {
				if (content.length() > OBJECT_ID_STRING_LENGTH)
					content = content.substring(0, OBJECT_ID_STRING_LENGTH);
				id = ObjectId.fromString(content);
			} catch (IllegalArgumentException notRef) {
				throw new IOException("Not a ref: " + name + ": " + content);
			}

			if (holder != null) {
				final Ref.Storage storage;
				if (holder.packRef != null)
					storage = Ref.Storage.LOOSE_PACKED;
				else
					storage = Ref.Storage.LOOSE;

				if (id.equals(ref.getObjectId()))
					ref = new ObjectIdRef(storage, name, id, ref
							.getPeeledObjectId(), ref.isPeeled());
				else
					ref = new ObjectIdRef(storage, name, id);
			} else {
				ref = new ObjectIdRef(Ref.Storage.LOOSE, name, id);
			}
		}

		if (holder == null) {
			holder = new RefHolder();
			cache.put(name, holder);
		}

		holder.currRef = ref;
		holder.looseModified = modified;
		modCnt++;
		return ref;
	}

	/** If the parent should fire listeners, fires them. */
	private void fireRefsChanged() {
		final int curr = modCnt;
		final int last = lastNotifiedModCnt.get();
		if (last != curr && lastNotifiedModCnt.compareAndSet(last, curr))
			parent.fireRefsChanged();
	}

	/**
	 * Create a reference update to write a temporary reference.
	 *
	 * @return an update for a new temporary reference.
	 * @throws IOException
	 *             a temporary name cannot be allocated.
	 */
	RefDirectoryUpdate newTemporaryUpdate() throws IOException {
		File tmp = File.createTempFile("renamed_", "_ref", refsDir);
		String name = Constants.R_REFS + tmp.getName();
		Ref ref = new ObjectIdRef(Ref.Storage.NEW, name, null);
		return new RefDirectoryUpdate(this, ref);
	}

	/**
	 * Locate the file on disk for a single reference name.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the file location.
	 */
	File fileFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(refsDir, name);
		}
		return new File(gitDir, name);
	}

	File logFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(logsRefsDir, name);
		}
		return new File(logsDir, name);
	}

	static int levelsIn(final String name) {
		int count = 0;
		for (int p = name.indexOf('/'); p >= 0; p = name.indexOf('/', p + 1))
			count++;
		return count;
	}

	private static String copy(String src, int off) {
		return copy(src, off, src.length());
	}

	private static String copy(final String src, final int off, final int end) {
		return new StringBuilder(end - off).append(src, off, end).toString();
	}

	private static String read(final File file) throws IOException {
		final byte[] buf;
		try {
			buf = IO.readFully(file, 4096);
		} catch (FileNotFoundException noFile) {
			return "";
		}

		// remove trailing whitespace
		int n = buf.length;
		while (n > 0 && Character.isWhitespace(buf[n - 1]))
			n--;
		if (n == 0)
			return "";
		return RawParseUtils.decode(buf, 0, n);
	}

	private static BufferedReader openReader(final File path)
			throws FileNotFoundException {
		FileInputStream in = new FileInputStream(path);
		return new BufferedReader(new InputStreamReader(in, CHARSET));
	}

	private static long write(File file, byte[] content) throws IOException {
		String name = file.getName();
		LockFile lck = new LockFile(file);
		lck.setNeedStatInformation(true);
		if (!lck.lock())
			throw new ObjectWritingException("Unable to lock " + name);
		try {
			lck.write(content);
		} catch (IOException ioe) {
			throw new ObjectWritingException("Unable to write " + name, ioe);
		}
		try {
			lck.waitForStatChange();
		} catch (InterruptedException e) {
			lck.unlock();
			throw new ObjectWritingException("Interrupted writing " + name);
		}
		if (!lck.commit())
			throw new ObjectWritingException("Unable to write " + name);
		return lck.getCommitLastModified();
	}

	static void delete(final File file, final int depth) throws IOException {
		if (!file.delete() && file.isFile())
			throw new IOException("File cannot be deleted: " + file);

		File dir = file.getParentFile();
		for (int i = 0; i < depth; ++i) {
			if (!dir.delete())
				break; // ignore problem here
			dir = dir.getParentFile();
		}
	}

	private static Ref newSymbolicRef(String name, String target) {
		Ref dst = new ObjectIdRef(Ref.Storage.NEW, target, null);
		return new SymbolicRef(dst, name);
	}

	private static RefHolder packedHolder(Ref r) {
		RefHolder h = new RefHolder();
		h.packRef = r;
		h.currRef = r;
		return h;
	}

	private static class RefHolder {
		/** Original ref read from the packed-refs file; null if only loose. */
		Ref packRef;

		/** Current value of the reference; never null. */
		Ref currRef;

		/** Last time the loose file was modified; 0 if not exists. */
		long looseModified;
	}
}
