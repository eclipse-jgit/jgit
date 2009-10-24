/*
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

class RefDatabase {
	private static final String REFS_SLASH = "refs/";

	private static final String[] refSearchPaths = { "", REFS_SLASH,
			R_TAGS, Constants.R_HEADS, Constants.R_REMOTES };

	private final Repository db;

	private final File gitDir;

	private final File refsDir;

	private Map<String, Ref> looseRefs;
	private Map<String, Long> looseRefsMTime;
	private Map<String, String> looseSymRefs;

	private final File packedRefsFile;

	private Map<String, Ref> packedRefs;

	private long packedRefsLastModified;

	private long packedRefsLength;

	int lastRefModification;

	int lastNotifiedRefModification;

	private int refModificationCounter;

	RefDatabase(final Repository r) {
		db = r;
		gitDir = db.getDirectory();
		refsDir = FS.resolve(gitDir, "refs");
		packedRefsFile = FS.resolve(gitDir, Constants.PACKED_REFS);
		clearCache();
	}

	synchronized void clearCache() {
		looseRefs = new HashMap<String, Ref>();
		looseRefsMTime = new HashMap<String, Long>();
		packedRefs = new HashMap<String, Ref>();
		looseSymRefs = new HashMap<String, String>();
		packedRefsLastModified = 0;
		packedRefsLength = 0;
	}

	Repository getRepository() {
		return db;
	}

	void create() {
		refsDir.mkdir();
		new File(refsDir, "heads").mkdir();
		new File(refsDir, "tags").mkdir();
	}

	ObjectId idOf(final String name) throws IOException {
		refreshPackedRefs();
		final Ref r = readRefBasic(name, 0);
		return r != null ? r.getObjectId() : null;
	}

	/**
	 * Create a command to update, create or delete a ref in this repository.
	 *
	 * @param name
	 *            name of the ref the caller wants to modify.
	 * @return an update command. The caller must finish populating this command
	 *         and then invoke one of the update methods to actually make a
	 *         change.
	 * @throws IOException
	 *             a symbolic ref was passed in and could not be resolved back
	 *             to the base ref, as the symbolic ref could not be read.
	 */
	RefUpdate newUpdate(final String name) throws IOException {
		refreshPackedRefs();
		Ref r = readRefBasic(name, 0);
		if (r == null)
			r = new Ref(Ref.Storage.NEW, name, null);
		return new RefUpdate(this, r, fileForRef(r.getName()));
	}

	void stored(final String origName, final String name, final ObjectId id, final long time) {
		synchronized (this) {
			looseRefs.put(name, new Ref(Ref.Storage.LOOSE, name, name, id));
			looseRefsMTime.put(name, time);
			setModified();
		}
		db.fireRefsMaybeChanged();
	}

	/**
	 * An set of update operations for renaming a ref
	 *
	 * @param fromRef Old ref name
	 * @param toRef New ref name
	 * @return a RefUpdate operation to rename a ref
	 * @throws IOException
	 */
	RefRename newRename(String fromRef, String toRef) throws IOException {
		refreshPackedRefs();
		Ref f = readRefBasic(fromRef, 0);
		Ref t = new Ref(Ref.Storage.NEW, toRef, null);
		RefUpdate refUpdateFrom = new RefUpdate(this, f, fileForRef(f.getName()));
		RefUpdate refUpdateTo = new RefUpdate(this, t, fileForRef(t.getName()));
		return new RefRename(refUpdateTo, refUpdateFrom);
	}

	/**
	 * Writes a symref (e.g. HEAD) to disk
	 *
	 * @param name
	 *            symref name
	 * @param target
	 *            pointed to ref
	 * @throws IOException
	 */
	void link(final String name, final String target) throws IOException {
		final byte[] content = Constants.encode("ref: " + target + "\n");
		lockAndWriteFile(fileForRef(name), content);
		synchronized (this) {
			looseSymRefs.remove(name);
			setModified();
		}
		db.fireRefsMaybeChanged();
	}

	void uncacheSymRef(String name) {
		synchronized(this) {
			looseSymRefs.remove(name);
			setModified();
		}
	}

	void uncacheRef(String name) {
		looseRefs.remove(name);
		looseRefsMTime.remove(name);
		packedRefs.remove(name);
	}

	private void setModified() {
		lastRefModification = refModificationCounter++;
	}

	Ref readRef(final String partialName) throws IOException {
		refreshPackedRefs();
		for (int k = 0; k < refSearchPaths.length; k++) {
			final Ref r = readRefBasic(refSearchPaths[k] + partialName, 0);
			if (r != null && r.getObjectId() != null)
				return r;
		}
		return null;
	}

	/**
	 * @return all known refs (heads, tags, remotes).
	 */
	Map<String, Ref> getAllRefs() {
		return readRefs();
	}

	/**
	 * @return all tags; key is short tag name ("v1.0") and value of the entry
	 *         contains the ref with the full tag name ("refs/tags/v1.0").
	 */
	Map<String, Ref> getTags() {
		final Map<String, Ref> tags = new HashMap<String, Ref>();
		for (final Ref r : readRefs().values()) {
			if (r.getName().startsWith(R_TAGS))
				tags.put(r.getName().substring(R_TAGS.length()), r);
		}
		return tags;
	}

	private Map<String, Ref> readRefs() {
		final HashMap<String, Ref> avail = new HashMap<String, Ref>();
		readPackedRefs(avail);
		readLooseRefs(avail, REFS_SLASH, refsDir);
		try {
			final Ref r = readRefBasic(Constants.HEAD, 0);
			if (r != null && r.getObjectId() != null)
				avail.put(Constants.HEAD, r);
		} catch (IOException e) {
			// ignore here
		}
		db.fireRefsMaybeChanged();
		return avail;
	}

	private synchronized void readPackedRefs(final Map<String, Ref> avail) {
		refreshPackedRefs();
		avail.putAll(packedRefs);
	}

	private void readLooseRefs(final Map<String, Ref> avail,
			final String prefix, final File dir) {
		final File[] entries = dir.listFiles();
		if (entries == null)
			return;

		for (final File ent : entries) {
			final String entName = ent.getName();
			if (".".equals(entName) || "..".equals(entName))
				continue;
			if (ent.isDirectory()) {
				readLooseRefs(avail, prefix + entName + "/", ent);
			} else {
				try {
					final Ref ref = readRefBasic(prefix + entName, 0);
					if (ref != null)
						avail.put(ref.getOrigName(), ref);
				} catch (IOException e) {
					continue;
				}
			}
		}
	}

	Ref peel(final Ref ref) {
		if (ref.isPeeled())
			return ref;
		ObjectId peeled = null;
		try {
			Object target = db.mapObject(ref.getObjectId(), ref.getName());
			while (target instanceof Tag) {
				final Tag tag = (Tag)target;
				peeled = tag.getObjId();
				if (Constants.TYPE_TAG.equals(tag.getType()))
					target = db.mapObject(tag.getObjId(), ref.getName());
				else
					break;
			}
		} catch (IOException e) {
			// Ignore a read error.  Callers will also get the same error
			// if they try to use the result of getPeeledObjectId.
		}
		return new Ref(ref.getStorage(), ref.getName(), ref.getObjectId(), peeled, true);

	}

	private File fileForRef(final String name) {
		if (name.startsWith(REFS_SLASH))
			return new File(refsDir, name.substring(REFS_SLASH.length()));
		return new File(gitDir, name);
	}

	private Ref readRefBasic(final String name, final int depth) throws IOException {
		return readRefBasic(name, name, depth);
	}

	private synchronized Ref readRefBasic(final String origName,
			final String name, final int depth) throws IOException {
		// Prefer loose ref to packed ref as the loose
		// file can be more up-to-date than a packed one.
		//
		Ref ref = looseRefs.get(origName);
		final File loose = fileForRef(name);
		final long mtime = loose.lastModified();
		if (ref != null) {
			Long cachedlastModified = looseRefsMTime.get(name);
			if (cachedlastModified != null && cachedlastModified == mtime) {
				if (packedRefs.containsKey(origName))
					return new Ref(Storage.LOOSE_PACKED, origName, ref
							.getObjectId(), ref.getPeeledObjectId(), ref
							.isPeeled());
				else
					return ref;
			}
			looseRefs.remove(origName);
			looseRefsMTime.remove(origName);
		}

		if (mtime == 0) {
			// If last modified is 0 the file does not exist.
			// Try packed cache.
			//
			ref = packedRefs.get(name);
			if (ref != null)
				if (!ref.getOrigName().equals(origName))
					ref = new Ref(Storage.LOOSE_PACKED, origName, name, ref.getObjectId());
			return ref;
		}

		String line = null;
		try {
			Long cachedlastModified = looseRefsMTime.get(name);
			if (cachedlastModified != null && cachedlastModified == mtime) {
				line = looseSymRefs.get(name);
			}
			if (line == null) {
				line = readLine(loose);
				looseRefsMTime.put(name, mtime);
				looseSymRefs.put(name, line);
			}
		} catch (FileNotFoundException notLoose) {
			return packedRefs.get(name);
		}

		if (line == null || line.length() == 0) {
			looseRefs.remove(origName);
			looseRefsMTime.remove(origName);
			return new Ref(Ref.Storage.LOOSE, origName, name, null);
		}

		if (line.startsWith("ref: ")) {
			if (depth >= 5) {
				throw new IOException("Exceeded maximum ref depth of " + depth
						+ " at " + name + ".  Circular reference?");
			}

			final String target = line.substring("ref: ".length());
			Ref r = readRefBasic(target, target, depth + 1);
			Long cachedMtime = looseRefsMTime.get(name);
			if (cachedMtime != null && cachedMtime != mtime)
				setModified();
			looseRefsMTime.put(name, mtime);
			if (r == null)
				return new Ref(Ref.Storage.LOOSE, origName, target, null);
			if (!origName.equals(r.getName()))
				r = new Ref(Ref.Storage.LOOSE_PACKED, origName, r.getName(), r.getObjectId(), r.getPeeledObjectId(), true);
			return r;
		}

		setModified();

		final ObjectId id;
		try {
			id = ObjectId.fromString(line);
		} catch (IllegalArgumentException notRef) {
			throw new IOException("Not a ref: " + name + ": " + line);
		}

		Storage storage;
		if (packedRefs.containsKey(name))
			storage = Ref.Storage.LOOSE_PACKED;
		else
			storage = Ref.Storage.LOOSE;
		ref = new Ref(storage, name, id);
		looseRefs.put(name, ref);
		looseRefsMTime.put(name, mtime);

		if (!origName.equals(name)) {
			ref = new Ref(Ref.Storage.LOOSE, origName, name, id);
			looseRefs.put(origName, ref);
		}

		return ref;
	}

	private synchronized void refreshPackedRefs() {
		final long currTime = packedRefsFile.lastModified();
		final long currLen = currTime == 0 ? 0 : packedRefsFile.length();
		if (currTime == packedRefsLastModified && currLen == packedRefsLength)
			return;
		if (currTime == 0) {
			packedRefsLastModified = 0;
			packedRefsLength = 0;
			packedRefs = new HashMap<String, Ref>();
			return;
		}

		final Map<String, Ref> newPackedRefs = new HashMap<String, Ref>();
		try {
			final BufferedReader b = openReader(packedRefsFile);
			try {
				String p;
				Ref last = null;
				while ((p = b.readLine()) != null) {
					if (p.charAt(0) == '#')
						continue;

					if (p.charAt(0) == '^') {
						if (last == null)
							throw new IOException("Peeled line before ref.");

						final ObjectId id = ObjectId.fromString(p.substring(1));
						last = new Ref(Ref.Storage.PACKED, last.getName(), last
								.getName(), last.getObjectId(), id, true);
						newPackedRefs.put(last.getName(), last);
						continue;
					}

					final int sp = p.indexOf(' ');
					final ObjectId id = ObjectId.fromString(p.substring(0, sp));
					final String name = copy(p, sp + 1, p.length());
					last = new Ref(Ref.Storage.PACKED, name, name, id);
					newPackedRefs.put(last.getName(), last);
				}
			} finally {
				b.close();
			}
			packedRefsLastModified = currTime;
			packedRefsLength = currLen;
			packedRefs = newPackedRefs;
			setModified();
		} catch (FileNotFoundException noPackedRefs) {
			// Ignore it and leave the new map empty.
			//
			packedRefsLastModified = 0;
			packedRefsLength = 0;
			packedRefs = newPackedRefs;
		} catch (IOException e) {
			throw new RuntimeException("Cannot read packed refs", e);
		}
	}

	private static String copy(final String src, final int off, final int end) {
		return new StringBuilder(end - off).append(src, off, end).toString();
	}

	private void lockAndWriteFile(File file, byte[] content) throws IOException {
		String name = file.getName();
		final LockFile lck = new LockFile(file);
		if (!lck.lock())
			throw new ObjectWritingException("Unable to lock " + name);
		try {
			lck.write(content);
		} catch (IOException ioe) {
			throw new ObjectWritingException("Unable to write " + name, ioe);
		}
		if (!lck.commit())
			throw new ObjectWritingException("Unable to write " + name);
	}

	synchronized void removePackedRef(String name) throws IOException {
		packedRefs.remove(name);
		writePackedRefs();
	}

	private void writePackedRefs() throws IOException {
		new RefWriter(packedRefs.values()) {
			@Override
			protected void writeFile(String name, byte[] content) throws IOException {
				lockAndWriteFile(new File(db.getDirectory(), name), content);
			}
		}.writePackedRefs();
	}

	private static String readLine(final File file)
			throws FileNotFoundException, IOException {
		final byte[] buf = IO.readFully(file, 4096);
		int n = buf.length;

		// remove trailing whitespaces
		while (n > 0 && Character.isWhitespace(buf[n - 1]))
			n--;

		if (n == 0)
			return null;
		return RawParseUtils.decode(buf, 0, n);
	}

	private static BufferedReader openReader(final File fileLocation)
			throws FileNotFoundException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(
				fileLocation), Constants.CHARSET));
	}
}
