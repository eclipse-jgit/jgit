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

package org.eclipse.jgit.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;

/**
 * Traditional file system based {@link ObjectDatabase}.
 * <p>
 * This is the classical object database representation for a Git repository,
 * where objects are stored loose by hashing them into directories by their
 * {@link ObjectId}, or are stored in compressed containers known as
 * {@link PackFile}s.
 */
public class ObjectDirectory extends ObjectDatabase {
	private static final PackList NO_PACKS = new PackList(-1, -1, new PackFile[0]);

	private final File objects;

	private final File infoDirectory;

	private final File packDirectory;

	private final File alternatesFile;

	private final AtomicReference<PackList> packList;

	private final File[] alternateObjectDir;

	/**
	 * Initialize a reference to an on-disk object directory.
	 *
	 * @param dir
	 *            the location of the <code>objects</code> directory.
	 * @param alternateObjectDir
	 *            a list of alternate object directories
	 */
	public ObjectDirectory(final File dir, File[] alternateObjectDir) {
		objects = dir;
		this.alternateObjectDir = alternateObjectDir;
		infoDirectory = new File(objects, "info");
		packDirectory = new File(objects, "pack");
		alternatesFile = new File(infoDirectory, "alternates");
		packList = new AtomicReference<PackList>(NO_PACKS);
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
		objects.mkdirs();
		infoDirectory.mkdir();
		packDirectory.mkdir();
	}

	@Override
	public void closeSelf() {
		final PackList packs = packList.get();
		packList.set(NO_PACKS);
		for (final PackFile p : packs.packs)
			p.close();
	}

	/**
	 * Compute the location of a loose object file.
	 *
	 * @param objectId
	 *            identity of the loose object to map to the directory.
	 * @return location of the object, if it were to exist as a loose object.
	 */
	public File fileFor(final AnyObjectId objectId) {
		return fileFor(objectId.name());
	}

	private File fileFor(final String objectName) {
		final String d = objectName.substring(0, 2);
		final String f = objectName.substring(2);
		return new File(new File(objects, d), f);
	}

	/**
	 * @return unmodifiable collection of all known pack files local to this
	 *         directory. Most recent packs are presented first. Packs most
	 *         likely to contain more recent objects appear before packs
	 *         containing objects referenced by commits further back in the
	 *         history of the repository.
	 */
	public Collection<PackFile> getPacks() {
		final PackFile[] packs = packList.get().packs;
		return Collections.unmodifiableCollection(Arrays.asList(packs));
	}

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @param idx
	 *            path of the corresponding index file.
	 * @throws IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public void openPack(final File pack, final File idx) throws IOException {
		final String p = pack.getName();
		final String i = idx.getName();

		if (p.length() != 50 || !p.startsWith("pack-") || !p.endsWith(".pack"))
			throw new IOException("Not a valid pack " + pack);

		if (i.length() != 49 || !i.startsWith("pack-") || !i.endsWith(".idx"))
			throw new IOException("Not a valid pack " + idx);

		if (!p.substring(0, 45).equals(i.substring(0, 45)))
			throw new IOException("Pack " + pack + "does not match index");

		insertPack(new PackFile(idx, pack));
	}

	@Override
	public String toString() {
		return "ObjectDirectory[" + getDirectory() + "]";
	}

	@Override
	protected boolean hasObject1(final AnyObjectId objectId) {
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

	@Override
	protected ObjectLoader openObject1(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		PackList pList = packList.get();
		SEARCH: for (;;) {
			for (final PackFile p : pList.packs) {
				try {
					final PackedObjectLoader ldr = p.get(curs, objectId);
					if (ldr != null) {
						ldr.materialize(curs);
						return ldr;
					}
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

	@Override
	void openObjectInAllPacks1(final Collection<PackedObjectLoader> out,
			final WindowCursor curs, final AnyObjectId objectId)
			throws IOException {
		PackList pList = packList.get();
		SEARCH: for (;;) {
			for (final PackFile p : pList.packs) {
				try {
					final PackedObjectLoader ldr = p.get(curs, objectId);
					if (ldr != null) {
						out.add(ldr);
					}
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
	}

	@Override
	protected boolean hasObject2(final String objectName) {
		return fileFor(objectName).exists();
	}

	@Override
	protected ObjectLoader openObject2(final WindowCursor curs,
			final String objectName, final AnyObjectId objectId)
			throws IOException {
		try {
			return new UnpackedObjectLoader(fileFor(objectName), objectId);
		} catch (FileNotFoundException noFile) {
			return null;
		}
	}

	@Override
	protected boolean tryAgain1() {
		final PackList old = packList.get();
		if (old.tryAgain(packDirectory.lastModified()))
			return old != scanPacks(old);
		return false;
	}

	private void insertPack(final PackFile pf) {
		PackList o, n;
		do {
			o = packList.get();
			final PackFile[] oldList = o.packs;
			final PackFile[] newList = new PackFile[1 + oldList.length];
			newList[0] = pf;
			System.arraycopy(oldList, 0, newList, 1, oldList.length);
			n = new PackList(o.lastRead, o.lastModified, newList);
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
			n = new PackList(o.lastRead, o.lastModified, newList);
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
		final long lastRead = System.currentTimeMillis();
		final long lastModified = packDirectory.lastModified();
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
		if (!foundNew && lastModified == old.lastModified && forReuse.isEmpty())
			return old.updateLastRead(lastRead);

		for (final PackFile p : forReuse.values()) {
			p.close();
		}

		if (list.isEmpty())
			return new PackList(lastRead, lastModified, NO_PACKS.packs);

		final PackFile[] r = list.toArray(new PackFile[list.size()]);
		Arrays.sort(r, PackFile.SORT);
		return new PackList(lastRead, lastModified, r);
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
				// close any PackFiles we did not reuse, so close the one we
				// just evicted out of the reuse map.
				//
				prior.close();
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

	@Override
	protected ObjectDatabase[] loadAlternates() throws IOException {
		final List<ObjectDatabase> l = new ArrayList<ObjectDatabase>(4);
		if (alternateObjectDir != null) {
			for (File d : alternateObjectDir) {
				l.add(openAlternate(d));
			}
		} else {
			final BufferedReader br = open(alternatesFile);
			try {
				String line;
				while ((line = br.readLine()) != null) {
					l.add(openAlternate(line));
				}
			} finally {
				br.close();
			}
		}

		if (l.isEmpty()) {
			return NO_ALTERNATES;
		}
		return l.toArray(new ObjectDatabase[l.size()]);
	}

	private static BufferedReader open(final File f)
			throws FileNotFoundException {
		return new BufferedReader(new FileReader(f));
	}

	private ObjectDatabase openAlternate(final String location)
			throws IOException {
		final File objdir = FS.resolve(objects, location);
		return openAlternate(objdir);
	}

	private ObjectDatabase openAlternate(File objdir) throws IOException {
		final File parent = objdir.getParentFile();
		if (FileKey.isGitRepository(parent)) {
			final Repository db = RepositoryCache.open(FileKey.exact(parent));
			return new AlternateRepositoryDatabase(db);
		}
		return new ObjectDirectory(objdir, null);
	}

	private static final class PackList {
		/** Last wall-clock time the directory was read. */
		volatile long lastRead;

		/** Last modification time of {@link ObjectDirectory#packDirectory}. */
		final long lastModified;

		/** All known packs, sorted by {@link PackFile#SORT}. */
		final PackFile[] packs;

		private boolean cannotBeRacilyClean;

		PackList(final long lastRead, final long lastModified,
				final PackFile[] packs) {
			this.lastRead = lastRead;
			this.lastModified = lastModified;
			this.packs = packs;
			this.cannotBeRacilyClean = notRacyClean(lastRead);
		}

		private boolean notRacyClean(final long read) {
			return read - lastModified > 2 * 60 * 1000L;
		}

		PackList updateLastRead(final long now) {
			if (notRacyClean(now))
				cannotBeRacilyClean = true;
			lastRead = now;
			return this;
		}

		boolean tryAgain(final long currLastModified) {
			// Any difference indicates the directory was modified.
			//
			if (lastModified != currLastModified)
				return true;

			// We have already determined the last read was far enough
			// after the last modification that any new modifications
			// are certain to change the last modified time.
			//
			if (cannotBeRacilyClean)
				return false;

			if (notRacyClean(lastRead)) {
				// Our last read should have marked cannotBeRacilyClean,
				// but this thread may not have seen the change. The read
				// of the volatile field lastRead should have fixed that.
				//
				return false;
			}

			// We last read this directory too close to its last observed
			// modification time. We may have missed a modification. Scan
			// the directory again, to ensure we still see the same state.
			//
			return true;
		}
	}

	@Override
	public ObjectDatabase newCachedDatabase() {
		return new CachedObjectDirectory(this);
	}
}
