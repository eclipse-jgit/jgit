/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Traditional file system based {@link ObjectDatabase}.
 * <p>
 * This is the classical object database representation for a Git repository,
 * where objects are stored loose by hashing them into directories by their
 * {@link ObjectId}, or are stored in compressed containers known as
 * {@link PackFile}s.
 * <p>
 * Optionally an object database can reference one or more alternates; other
 * ObjectDatabase instances that are searched in addition to the current
 * database.
 * <p>
 * Databases are divided into two halves: a half that is considered to be fast
 * to search (the {@code PackFile}s), and a half that is considered to be slow
 * to search (loose objects). When alternates are present the fast half is fully
 * searched (recursively through all alternates) before the slow half is
 * considered.
 */
public class ObjectDirectory extends FileObjectDatabase {
	private static final PackList NO_PACKS = new PackList(
			FileSnapshot.DIRTY, new PackFile[0]);

	/** Maximum number of candidates offered as resolutions of abbreviation. */
	private static final int RESOLVE_ABBREV_LIMIT = 256;

	private final Config config;

	private final File objects;

	private final File infoDirectory;

	private final File packDirectory;

	private final File alternatesFile;

	private final File cachedPacksFile;

	private final AtomicReference<PackList> packList;

	private final AtomicReference<CachedPackList> cachedPacks;

	private final FS fs;

	private final AtomicReference<AlternateHandle[]> alternates;

	private final UnpackedObjectCache unpackedObjectCache;

	private final File shallowFile;

	private FileSnapshot shallowFileSnapshot = FileSnapshot.DIRTY;

	private Set<ObjectId> shallowCommitsIds;

	/**
	 * Initialize a reference to an on-disk object directory.
	 *
	 * @param cfg
	 *            configuration this directory consults for write settings.
	 * @param dir
	 *            the location of the <code>objects</code> directory.
	 * @param alternatePaths
	 *            a list of alternate object directories
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param shallowFile
	 *            file which contains IDs of shallow commits, null if shallow
	 *            commits handling should be turned off
	 * @throws IOException
	 *             an alternate object cannot be opened.
	 */
	public ObjectDirectory(final Config cfg, final File dir,
			File[] alternatePaths, FS fs, File shallowFile) throws IOException {
		config = cfg;
		objects = dir;
		infoDirectory = new File(objects, "info");
		packDirectory = new File(objects, "pack");
		alternatesFile = new File(infoDirectory, "alternates");
		cachedPacksFile = new File(infoDirectory, "cached-packs");
		packList = new AtomicReference<PackList>(NO_PACKS);
		cachedPacks = new AtomicReference<CachedPackList>();
		unpackedObjectCache = new UnpackedObjectCache();
		this.fs = fs;
		this.shallowFile = shallowFile;

		alternates = new AtomicReference<AlternateHandle[]>();
		if (alternatePaths != null) {
			AlternateHandle[] alt;

			alt = new AlternateHandle[alternatePaths.length];
			for (int i = 0; i < alternatePaths.length; i++)
				alt[i] = openAlternate(alternatePaths[i]);
			alternates.set(alt);
		}
	}

	/**
	 * @return the location of the <code>objects</code> directory.
	 */
	public final File getDirectory() {
		return objects;
	}

	@Override
	public boolean exists() {
		return objects.exists();
	}

	@Override
	public void create() throws IOException {
		FileUtils.mkdirs(objects);
		FileUtils.mkdir(infoDirectory);
		FileUtils.mkdir(packDirectory);
	}

	@Override
	public ObjectDirectoryInserter newInserter() {
		return new ObjectDirectoryInserter(this, config);
	}

	@Override
	public void close() {
		unpackedObjectCache.clear();

		final PackList packs = packList.get();
		packList.set(NO_PACKS);
		for (final PackFile p : packs.packs)
			p.close();

		// Fully close all loaded alternates and clear the alternate list.
		AlternateHandle[] alt = alternates.get();
		if (alt != null) {
			alternates.set(null);
			for(final AlternateHandle od : alt)
				od.close();
		}
	}

	/**
	 * Compute the location of a loose object file.
	 *
	 * @param objectId
	 *            identity of the loose object to map to the directory.
	 * @return location of the object, if it were to exist as a loose object.
	 */
	@Override
	public File fileFor(final AnyObjectId objectId) {
		return super.fileFor(objectId);
	}

	/**
	 * @return unmodifiable collection of all known pack files local to this
	 *         directory. Most recent packs are presented first. Packs most
	 *         likely to contain more recent objects appear before packs
	 *         containing objects referenced by commits further back in the
	 *         history of the repository.
	 */
	public Collection<PackFile> getPacks() {
		PackList list = packList.get();
		if (list == NO_PACKS)
			list = scanPacks(list);
		PackFile[] packs = list.packs;
		return Collections.unmodifiableCollection(Arrays.asList(packs));
	}

	@Override
	Collection<? extends CachedPack> getCachedPacks() throws IOException {
		CachedPackList list = cachedPacks.get();
		if (list == null || list.snapshot.isModified(cachedPacksFile))
			list = scanCachedPacks(list);

		Collection<CachedPack> result = list.getCachedPacks();
		boolean resultIsCopy = false;

		for (AlternateHandle h : myAlternates()) {
			Collection<CachedPack> altPacks = h.getCachedPacks();
			if (altPacks.isEmpty())
				continue;

			if (result.isEmpty()) {
				result = altPacks;
				continue;
			}

			if (!resultIsCopy) {
				result = new ArrayList<CachedPack>(result);
				resultIsCopy = true;
			}
			result.addAll(altPacks);
		}
		return result;
	}

	private CachedPackList scanCachedPacks(CachedPackList old)
			throws IOException {
		FileSnapshot s = FileSnapshot.save(cachedPacksFile);
		byte[] buf;
		try {
			buf = IO.readFully(cachedPacksFile);
		} catch (FileNotFoundException e) {
			buf = new byte[0];
		}

		if (old != null && old.snapshot.equals(s)
				&& Arrays.equals(old.raw, buf)) {
			old.snapshot.setClean(s);
			return old;
		}

		ArrayList<LocalCachedPack> list = new ArrayList<LocalCachedPack>(4);
		Set<ObjectId> tips = new HashSet<ObjectId>();
		int ptr = 0;
		while (ptr < buf.length) {
			if (buf[ptr] == '#' || buf[ptr] == '\n') {
				ptr = RawParseUtils.nextLF(buf, ptr);
				continue;
			}

			if (buf[ptr] == '+') {
				tips.add(ObjectId.fromString(buf, ptr + 2));
				ptr = RawParseUtils.nextLF(buf, ptr + 2);
				continue;
			}

			List<String> names = new ArrayList<String>(4);
			while (ptr < buf.length && buf[ptr] == 'P') {
				int end = RawParseUtils.nextLF(buf, ptr);
				if (buf[end - 1] == '\n')
					end--;
				names.add(RawParseUtils.decode(buf, ptr + 2, end));
				ptr = RawParseUtils.nextLF(buf, end);
			}

			if (!tips.isEmpty() && !names.isEmpty()) {
				list.add(new LocalCachedPack(this, tips, names));
				tips = new HashSet<ObjectId>();
			}
		}
		list.trimToSize();
		return new CachedPackList(s, Collections.unmodifiableList(list), buf);
	}

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @param idx
	 *            path of the corresponding index file.
	 * @return the pack that was opened and added to the database.
	 * @throws IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public PackFile openPack(final File pack, final File idx)
			throws IOException {
		final String p = pack.getName();
		final String i = idx.getName();

		if (p.length() != 50 || !p.startsWith("pack-") || !p.endsWith(".pack"))
			throw new IOException(MessageFormat.format(JGitText.get().notAValidPack, pack));

		if (i.length() != 49 || !i.startsWith("pack-") || !i.endsWith(".idx"))
			throw new IOException(MessageFormat.format(JGitText.get().notAValidPack, idx));

		if (!p.substring(0, 45).equals(i.substring(0, 45)))
			throw new IOException(MessageFormat.format(JGitText.get().packDoesNotMatchIndex, pack));

		PackFile res = new PackFile(idx, pack);
		insertPack(res);
		return res;
	}

	@Override
	public String toString() {
		return "ObjectDirectory[" + getDirectory() + "]";
	}

	boolean hasObject1(final AnyObjectId objectId) {
		if (unpackedObjectCache.isUnpacked(objectId))
			return true;
		for (final PackFile p : packList.get().packs) {
			try {
				if (p.hasObject(objectId)) {
					return true;
				}
			} catch (IOException e) {
				// The hasObject call should have only touched the index,
				// so any failure here indicates the index is unreadable
				// by this process, and the pack is likewise not readable.
				//
				removePack(p);
				continue;
			}
		}
		return false;
	}

	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id)
			throws IOException {
		// Go through the packs once. If we didn't find any resolutions
		// scan for new packs and check once more.
		//
		int oldSize = matches.size();
		PackList pList = packList.get();
		for (;;) {
			for (PackFile p : pList.packs) {
				try {
					p.resolve(matches, id, RESOLVE_ABBREV_LIMIT);
				} catch (IOException e) {
					// Assume the pack is corrupted.
					//
					removePack(p);
				}
				if (matches.size() > RESOLVE_ABBREV_LIMIT)
					return;
			}
			if (matches.size() == oldSize) {
				PackList nList = scanPacks(pList);
				if (nList == pList || nList.packs.length == 0)
					break;
				pList = nList;
				continue;
			}
			break;
		}

		String fanOut = id.name().substring(0, 2);
		String[] entries = new File(getDirectory(), fanOut).list();
		if (entries != null) {
			for (String e : entries) {
				if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
					continue;
				try {
					ObjectId entId = ObjectId.fromString(fanOut + e);
					if (id.prefixCompare(entId) == 0)
						matches.add(entId);
				} catch (IllegalArgumentException notId) {
					continue;
				}
				if (matches.size() > RESOLVE_ABBREV_LIMIT)
					return;
			}
		}

		for (AlternateHandle alt : myAlternates()) {
			alt.db.resolve(matches, id);
			if (matches.size() > RESOLVE_ABBREV_LIMIT)
				return;
		}
	}

	ObjectLoader openObject1(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		if (unpackedObjectCache.isUnpacked(objectId)) {
			ObjectLoader ldr = openObject2(curs, objectId.name(), objectId);
			if (ldr != null)
				return ldr;
			else
				unpackedObjectCache.remove(objectId);
		}

		PackList pList = packList.get();
		SEARCH: for (;;) {
			for (final PackFile p : pList.packs) {
				try {
					final ObjectLoader ldr = p.get(curs, objectId);
					if (ldr != null)
						return ldr;
				} catch (PackMismatchException e) {
					// Pack was modified; refresh the entire pack list.
					//
					pList = scanPacks(pList);
					continue SEARCH;
				} catch (IOException e) {
					// Assume the pack is corrupted.
					//
					removePack(p);
				}
			}
			return null;
		}
	}

	long getObjectSize1(final WindowCursor curs, final AnyObjectId objectId)
			throws IOException {
		PackList pList = packList.get();
		SEARCH: for (;;) {
			for (final PackFile p : pList.packs) {
				try {
					long sz = p.getObjectSize(curs, objectId);
					if (0 <= sz)
						return sz;
				} catch (PackMismatchException e) {
					// Pack was modified; refresh the entire pack list.
					//
					pList = scanPacks(pList);
					continue SEARCH;
				} catch (IOException e) {
					// Assume the pack is corrupted.
					//
					removePack(p);
				}
			}
			return -1;
		}
	}

	@Override
	long getObjectSize2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException {
		try {
			File path = fileFor(objectName);
			FileInputStream in = new FileInputStream(path);
			try {
				return UnpackedObject.getSize(in, objectId, curs);
			} finally {
				in.close();
			}
		} catch (FileNotFoundException noFile) {
			return -1;
		}
	}

	@Override
	void selectObjectRepresentation(PackWriter packer, ObjectToPack otp,
			WindowCursor curs) throws IOException {
		PackList pList = packList.get();
		SEARCH: for (;;) {
			for (final PackFile p : pList.packs) {
				try {
					LocalObjectRepresentation rep = p.representation(curs, otp);
					if (rep != null)
						packer.select(otp, rep);
				} catch (PackMismatchException e) {
					// Pack was modified; refresh the entire pack list.
					//
					pList = scanPacks(pList);
					continue SEARCH;
				} catch (IOException e) {
					// Assume the pack is corrupted.
					//
					removePack(p);
				}
			}
			break SEARCH;
		}

		for (AlternateHandle h : myAlternates())
			h.db.selectObjectRepresentation(packer, otp, curs);
	}

	boolean hasObject2(final String objectName) {
		return fileFor(objectName).exists();
	}

	ObjectLoader openObject2(final WindowCursor curs,
			final String objectName, final AnyObjectId objectId)
			throws IOException {
		try {
			File path = fileFor(objectName);
			FileInputStream in = new FileInputStream(path);
			try {
				unpackedObjectCache.add(objectId);
				return UnpackedObject.open(in, path, objectId, curs);
			} finally {
				in.close();
			}
		} catch (FileNotFoundException noFile) {
			unpackedObjectCache.remove(objectId);
			return null;
		}
	}

	@Override
	InsertLooseObjectResult insertUnpackedObject(File tmp, ObjectId id,
			boolean createDuplicate) throws IOException {
		// If the object is already in the repository, remove temporary file.
		//
		if (unpackedObjectCache.isUnpacked(id)) {
			FileUtils.delete(tmp);
			return InsertLooseObjectResult.EXISTS_LOOSE;
		}
		if (!createDuplicate && has(id)) {
			FileUtils.delete(tmp);
			return InsertLooseObjectResult.EXISTS_PACKED;
		}

		final File dst = fileFor(id);
		if (dst.exists()) {
			// We want to be extra careful and avoid replacing an object
			// that already exists. We can't be sure renameTo() would
			// fail on all platforms if dst exists, so we check first.
			//
			FileUtils.delete(tmp);
			return InsertLooseObjectResult.EXISTS_LOOSE;
		}
		if (tmp.renameTo(dst)) {
			dst.setReadOnly();
			unpackedObjectCache.add(id);
			return InsertLooseObjectResult.INSERTED;
		}

		// Maybe the directory doesn't exist yet as the object
		// directories are always lazily created. Note that we
		// try the rename first as the directory likely does exist.
		//
		FileUtils.mkdir(dst.getParentFile(), true);
		if (tmp.renameTo(dst)) {
			dst.setReadOnly();
			unpackedObjectCache.add(id);
			return InsertLooseObjectResult.INSERTED;
		}

		if (!createDuplicate && has(id)) {
			FileUtils.delete(tmp);
			return InsertLooseObjectResult.EXISTS_PACKED;
		}

		// The object failed to be renamed into its proper
		// location and it doesn't exist in the repository
		// either. We really don't know what went wrong, so
		// fail.
		//
		FileUtils.delete(tmp);
		return InsertLooseObjectResult.FAILURE;
	}

	boolean tryAgain1() {
		final PackList old = packList.get();
		if (old.snapshot.isModified(packDirectory))
			return old != scanPacks(old);
		return false;
	}

	Config getConfig() {
		return config;
	}

	@Override
	FS getFS() {
		return fs;
	}

	@Override
	Set<ObjectId> getShallowCommits() throws IOException {
		if (shallowFile == null || !shallowFile.isFile())
			return Collections.emptySet();

		if (shallowFileSnapshot == null
				|| shallowFileSnapshot.isModified(shallowFile)) {
			shallowCommitsIds = new HashSet<ObjectId>();

			final BufferedReader reader = open(shallowFile);
			try {
				String line;
				while ((line = reader.readLine()) != null)
					shallowCommitsIds.add(ObjectId.fromString(line));
			} finally {
				reader.close();
			}

			shallowFileSnapshot = FileSnapshot.save(shallowFile);
		}

		return shallowCommitsIds;
	}

	private void insertPack(final PackFile pf) {
		PackList o, n;
		do {
			o = packList.get();

			// If the pack in question is already present in the list
			// (picked up by a concurrent thread that did a scan?) we
			// do not want to insert it a second time.
			//
			final PackFile[] oldList = o.packs;
			final String name = pf.getPackFile().getName();
			for (PackFile p : oldList) {
				if (PackFile.SORT.compare(pf, p) < 0)
					break;
				if (name.equals(p.getPackFile().getName()))
					return;
			}

			final PackFile[] newList = new PackFile[1 + oldList.length];
			newList[0] = pf;
			System.arraycopy(oldList, 0, newList, 1, oldList.length);
			n = new PackList(o.snapshot, newList);
		} while (!packList.compareAndSet(o, n));
	}

	private void removePack(final PackFile deadPack) {
		PackList o, n;
		do {
			o = packList.get();

			final PackFile[] oldList = o.packs;
			final int j = indexOf(oldList, deadPack);
			if (j < 0)
				break;

			final PackFile[] newList = new PackFile[oldList.length - 1];
			System.arraycopy(oldList, 0, newList, 0, j);
			System.arraycopy(oldList, j + 1, newList, j, newList.length - j);
			n = new PackList(o.snapshot, newList);
		} while (!packList.compareAndSet(o, n));
		deadPack.close();
	}

	private static int indexOf(final PackFile[] list, final PackFile pack) {
		for (int i = 0; i < list.length; i++) {
			if (list[i] == pack)
				return i;
		}
		return -1;
	}

	private PackList scanPacks(final PackList original) {
		synchronized (packList) {
			PackList o, n;
			do {
				o = packList.get();
				if (o != original) {
					// Another thread did the scan for us, while we
					// were blocked on the monitor above.
					//
					return o;
				}
				n = scanPacksImpl(o);
				if (n == o)
					return n;
			} while (!packList.compareAndSet(o, n));
			return n;
		}
	}

	private PackList scanPacksImpl(final PackList old) {
		final Map<String, PackFile> forReuse = reuseMap(old);
		final FileSnapshot snapshot = FileSnapshot.save(packDirectory);
		final Set<String> names = listPackDirectory();
		final List<PackFile> list = new ArrayList<PackFile>(names.size() >> 2);
		boolean foundNew = false;
		for (final String indexName : names) {
			// Must match "pack-[0-9a-f]{40}.idx" to be an index.
			//
			if (indexName.length() != 49 || !indexName.endsWith(".idx"))
				continue;

			final String base = indexName.substring(0, indexName.length() - 4);
			final String packName = base + ".pack";
			if (!names.contains(packName)) {
				// Sometimes C Git's HTTP fetch transport leaves a
				// .idx file behind and does not download the .pack.
				// We have to skip over such useless indexes.
				//
				continue;
			}

			final PackFile oldPack = forReuse.remove(packName);
			if (oldPack != null) {
				list.add(oldPack);
				continue;
			}

			final File packFile = new File(packDirectory, packName);
			final File idxFile = new File(packDirectory, indexName);
			list.add(new PackFile(idxFile, packFile));
			foundNew = true;
		}

		// If we did not discover any new files, the modification time was not
		// changed, and we did not remove any files, then the set of files is
		// the same as the set we were given. Instead of building a new object
		// return the same collection.
		//
		if (!foundNew && forReuse.isEmpty() && snapshot.equals(old.snapshot)) {
			old.snapshot.setClean(snapshot);
			return old;
		}

		for (final PackFile p : forReuse.values()) {
			p.close();
		}

		if (list.isEmpty())
			return new PackList(snapshot, NO_PACKS.packs);

		final PackFile[] r = list.toArray(new PackFile[list.size()]);
		Arrays.sort(r, PackFile.SORT);
		return new PackList(snapshot, r);
	}

	private static Map<String, PackFile> reuseMap(final PackList old) {
		final Map<String, PackFile> forReuse = new HashMap<String, PackFile>();
		for (final PackFile p : old.packs) {
			if (p.invalid()) {
				// The pack instance is corrupted, and cannot be safely used
				// again. Do not include it in our reuse map.
				//
				p.close();
				continue;
			}

			final PackFile prior = forReuse.put(p.getPackFile().getName(), p);
			if (prior != null) {
				// This should never occur. It should be impossible for us
				// to have two pack files with the same name, as all of them
				// came out of the same directory. If it does, we promised to
				// close any PackFiles we did not reuse, so close the second,
				// readers are likely to be actively using the first.
				//
				forReuse.put(prior.getPackFile().getName(), prior);
				p.close();
			}
		}
		return forReuse;
	}

	private Set<String> listPackDirectory() {
		final String[] nameList = packDirectory.list();
		if (nameList == null)
			return Collections.emptySet();
		final Set<String> nameSet = new HashSet<String>(nameList.length << 1);
		for (final String name : nameList) {
			if (name.startsWith("pack-"))
				nameSet.add(name);
		}
		return nameSet;
	}

	AlternateHandle[] myAlternates() {
		AlternateHandle[] alt = alternates.get();
		if (alt == null) {
			synchronized (alternates) {
				alt = alternates.get();
				if (alt == null) {
					try {
						alt = loadAlternates();
					} catch (IOException e) {
						alt = new AlternateHandle[0];
					}
					alternates.set(alt);
				}
			}
		}
		return alt;
	}

	private AlternateHandle[] loadAlternates() throws IOException {
		final List<AlternateHandle> l = new ArrayList<AlternateHandle>(4);
		final BufferedReader br = open(alternatesFile);
		try {
			String line;
			while ((line = br.readLine()) != null) {
				l.add(openAlternate(line));
			}
		} finally {
			br.close();
		}
		return l.toArray(new AlternateHandle[l.size()]);
	}

	private static BufferedReader open(final File f)
			throws FileNotFoundException {
		return new BufferedReader(new FileReader(f));
	}

	private AlternateHandle openAlternate(final String location)
			throws IOException {
		final File objdir = fs.resolve(objects, location);
		return openAlternate(objdir);
	}

	private AlternateHandle openAlternate(File objdir) throws IOException {
		final File parent = objdir.getParentFile();
		if (FileKey.isGitRepository(parent, fs)) {
			FileKey key = FileKey.exact(parent, fs);
			FileRepository db = (FileRepository) RepositoryCache.open(key);
			return new AlternateRepository(db);
		}

		ObjectDirectory db = new ObjectDirectory(config, objdir, null, fs, null);
		return new AlternateHandle(db);
	}

	private static final class PackList {
		/** State just before reading the pack directory. */
		final FileSnapshot snapshot;

		/** All known packs, sorted by {@link PackFile#SORT}. */
		final PackFile[] packs;

		PackList(final FileSnapshot monitor, final PackFile[] packs) {
			this.snapshot = monitor;
			this.packs = packs;
		}
	}

	private static final class CachedPackList {
		final FileSnapshot snapshot;

		final Collection<LocalCachedPack> packs;

		final byte[] raw;

		CachedPackList(FileSnapshot sn, List<LocalCachedPack> list, byte[] buf) {
			snapshot = sn;
			packs = list;
			raw = buf;
		}

		@SuppressWarnings("unchecked")
		Collection<CachedPack> getCachedPacks() {
			Collection p = packs;
			return p;
		}
	}

	@Override
	public ObjectDatabase newCachedDatabase() {
		return newCachedFileObjectDatabase();
	}

	FileObjectDatabase newCachedFileObjectDatabase() {
		return new CachedObjectDirectory(this);
	}
}
