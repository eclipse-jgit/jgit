/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static java.util.stream.Collectors.joining;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Manages objects stored in
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsPackFile} on a storage
 * system.
 */
public abstract class DfsObjDatabase extends ObjectDatabase {
	private static final PackList NO_PACKS = new PackList(
			new DfsPackFile[0],
			new DfsReftable[0]) {
		@Override
		boolean dirty() {
			return true;
		}

		@Override
		void clearDirty() {
			// Always dirty.
		}

		@Override
		public void markDirty() {
			// Always dirty.
		}
	};

	/**
	 * Sources for a pack file.
	 * <p>
	 * <strong>Note:</strong> When sorting packs by source, do not use the default
	 * comparator based on {@link Enum#compareTo}. Prefer {@link
	 * #DEFAULT_COMPARATOR} or your own {@link ComparatorBuilder}.
	 */
	public static enum PackSource {
		/** The pack is created by ObjectInserter due to local activity. */
		INSERT,

		/**
		 * The pack is created by PackParser due to a network event.
		 * <p>
		 * A received pack can be from either a push into the repository, or a
		 * fetch into the repository, the direction doesn't matter. A received
		 * pack was built by the remote Git implementation and may not match the
		 * storage layout preferred by this version. Received packs are likely
		 * to be either compacted or garbage collected in the future.
		 */
		RECEIVE,

		/**
		 * The pack was created by compacting multiple packs together.
		 * <p>
		 * Packs created by compacting multiple packs together aren't nearly as
		 * efficient as a fully garbage collected repository, but may save disk
		 * space by reducing redundant copies of base objects.
		 *
		 * @see DfsPackCompactor
		 */
		COMPACT,

		/**
		 * Pack was created by Git garbage collection by this implementation.
		 * <p>
		 * This source is only used by the {@link DfsGarbageCollector} when it
		 * builds a pack file by traversing the object graph and copying all
		 * reachable objects into a new pack stream.
		 *
		 * @see DfsGarbageCollector
		 */
		GC,

		/** Created from non-heads by {@link DfsGarbageCollector}. */
		GC_REST,

		/**
		 * RefTreeGraph pack was created by Git garbage collection.
		 *
		 * @see DfsGarbageCollector
		 */
		GC_TXN,

		/**
		 * Pack was created by Git garbage collection.
		 * <p>
		 * This pack contains only unreachable garbage that was found during the
		 * last GC pass. It is retained in a new pack until it is safe to prune
		 * these objects from the repository.
		 */
		UNREACHABLE_GARBAGE;

		/**
		 * Default comparator for sources.
		 * <p>
		 * Sorts generally newer, smaller types such as {@code INSERT} and {@code
		 * RECEIVE} earlier; older, larger types such as {@code GC} later; and
		 * {@code UNREACHABLE_GARBAGE} at the end.
		 */
		public static final Comparator<PackSource> DEFAULT_COMPARATOR =
				new ComparatorBuilder()
						.add(INSERT, RECEIVE)
						.add(COMPACT)
						.add(GC)
						.add(GC_REST)
						.add(GC_TXN)
						.add(UNREACHABLE_GARBAGE)
						.build();

		/**
		 * Builder for describing {@link PackSource} ordering where some values are
		 * explicitly considered equal to others.
		 */
		public static class ComparatorBuilder {
			private final Map<PackSource, Integer> ranks = new HashMap<>();
			private int counter;

			/**
			 * Add a collection of sources that should sort as equal.
			 * <p>
			 * Sources in the input will sort after sources listed in previous calls
			 * to this method.
			 *
			 * @param sources
			 *            sources in this equivalence class.
			 * @return this.
			 */
			public ComparatorBuilder add(PackSource... sources) {
				for (PackSource s : sources) {
					ranks.put(s, Integer.valueOf(counter));
				}
				counter++;
				return this;
			}

			/**
			 * Build the comparator.
			 *
			 * @return new comparator instance.
			 * @throws IllegalArgumentException
			 *             not all {@link PackSource} instances were explicitly assigned
			 *             an equivalence class.
			 */
			public Comparator<PackSource> build() {
				return new PackSourceComparator(ranks);
			}
		}

		private static class PackSourceComparator implements Comparator<PackSource> {
			private final Map<PackSource, Integer> ranks;

			private PackSourceComparator(Map<PackSource, Integer> ranks) {
				if (!ranks.keySet().equals(
							new HashSet<>(Arrays.asList(PackSource.values())))) {
					throw new IllegalArgumentException();
				}
				this.ranks = new HashMap<>(ranks);
			}

			@Override
			public int compare(PackSource a, PackSource b) {
				return ranks.get(a).compareTo(ranks.get(b));
			}

			@Override
			public String toString() {
				return Arrays.stream(PackSource.values())
						.map(s -> s + "=" + ranks.get(s)) //$NON-NLS-1$
						.collect(joining(", ", getClass().getSimpleName() + "{", "}")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}

	private final AtomicReference<PackList> packList;

	private final DfsRepository repository;

	private DfsReaderOptions readerOptions;

	private Comparator<DfsPackDescription> packComparator;

	/**
	 * Initialize an object database for our repository.
	 *
	 * @param repository
	 *            repository owning this object database.
	 * @param options
	 *            how readers should access the object database.
	 */
	protected DfsObjDatabase(DfsRepository repository,
			DfsReaderOptions options) {
		this.repository = repository;
		this.packList = new AtomicReference<>(NO_PACKS);
		this.readerOptions = options;
		this.packComparator = DfsPackDescription.objectLookupComparator();
	}

	/**
	 * Get configured reader options, such as read-ahead.
	 *
	 * @return configured reader options, such as read-ahead.
	 */
	public DfsReaderOptions getReaderOptions() {
		return readerOptions;
	}

	/**
	 * Set the comparator used when searching for objects across packs.
	 * <p>
	 * An optimal comparator will find more objects without having to load large
	 * idx files from storage only to find that they don't contain the object.
	 * See {@link DfsPackDescription#objectLookupComparator()} for the default
	 * heuristics.
	 *
	 * @param packComparator
	 *            comparator.
	 */
	public void setPackComparator(Comparator<DfsPackDescription> packComparator) {
		this.packComparator = packComparator;
	}

	/** {@inheritDoc} */
	@Override
	public DfsReader newReader() {
		return new DfsReader(this);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectInserter newInserter() {
		return new DfsInserter(this);
	}

	/**
	 * Scan and list all available pack files in the repository.
	 *
	 * @return list of available packs. The returned array is shared with the
	 *         implementation and must not be modified by the caller.
	 * @throws java.io.IOException
	 *             the pack list cannot be initialized.
	 */
	public DfsPackFile[] getPacks() throws IOException {
		return getPackList().packs;
	}

	/**
	 * Scan and list all available reftable files in the repository.
	 *
	 * @return list of available reftables. The returned array is shared with
	 *         the implementation and must not be modified by the caller.
	 * @throws java.io.IOException
	 *             the pack list cannot be initialized.
	 */
	public DfsReftable[] getReftables() throws IOException {
		return getPackList().reftables;
	}

	/**
	 * Scan and list all available pack files in the repository.
	 *
	 * @return list of available packs, with some additional metadata. The
	 *         returned array is shared with the implementation and must not be
	 *         modified by the caller.
	 * @throws java.io.IOException
	 *             the pack list cannot be initialized.
	 */
	public PackList getPackList() throws IOException {
		return scanPacks(NO_PACKS);
	}

	/**
	 * Get repository owning this object database.
	 *
	 * @return repository owning this object database.
	 */
	protected DfsRepository getRepository() {
		return repository;
	}

	/**
	 * List currently known pack files in the repository, without scanning.
	 *
	 * @return list of available packs. The returned array is shared with the
	 *         implementation and must not be modified by the caller.
	 */
	public DfsPackFile[] getCurrentPacks() {
		return getCurrentPackList().packs;
	}

	/**
	 * List currently known reftable files in the repository, without scanning.
	 *
	 * @return list of available reftables. The returned array is shared with
	 *         the implementation and must not be modified by the caller.
	 */
	public DfsReftable[] getCurrentReftables() {
		return getCurrentPackList().reftables;
	}

	/**
	 * List currently known pack files in the repository, without scanning.
	 *
	 * @return list of available packs, with some additional metadata. The
	 *         returned array is shared with the implementation and must not be
	 *         modified by the caller.
	 */
	public PackList getCurrentPackList() {
		return packList.get();
	}

	/**
	 * Does the requested object exist in this database?
	 * <p>
	 * This differs from ObjectDatabase's implementation in that we can selectively
	 * ignore unreachable (garbage) objects.
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @param avoidUnreachableObjects
	 *            if true, ignore objects that are unreachable.
	 * @return true if the specified object is stored in this database.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public boolean has(AnyObjectId objectId, boolean avoidUnreachableObjects)
			throws IOException {
		try (ObjectReader or = newReader()) {
			or.setAvoidUnreachableObjects(avoidUnreachableObjects);
			return or.has(objectId);
		}
	}

	/**
	 * Generate a new unique name for a pack file.
	 *
	 * @param source
	 *            where the pack stream is created.
	 * @return a unique name for the pack file. Must not collide with any other
	 *         pack file name in the same DFS.
	 * @throws java.io.IOException
	 *             a new unique pack description cannot be generated.
	 */
	protected abstract DfsPackDescription newPack(PackSource source)
			throws IOException;

	/**
	 * Generate a new unique name for a pack file.
	 *
	 * <p>
	 * Default implementation of this method would be equivalent to
	 * {@code newPack(source).setEstimatedPackSize(estimatedPackSize)}. But the
	 * clients can override this method to use the given
	 * {@code estomatedPackSize} value more efficiently in the process of
	 * creating a new
	 * {@link org.eclipse.jgit.internal.storage.dfs.DfsPackDescription} object.
	 *
	 * @param source
	 *            where the pack stream is created.
	 * @param estimatedPackSize
	 *            the estimated size of the pack.
	 * @return a unique name for the pack file. Must not collide with any other
	 *         pack file name in the same DFS.
	 * @throws java.io.IOException
	 *             a new unique pack description cannot be generated.
	 */
	protected DfsPackDescription newPack(PackSource source,
			long estimatedPackSize) throws IOException {
		DfsPackDescription pack = newPack(source);
		pack.setEstimatedPackSize(estimatedPackSize);
		return pack;
	}

	/**
	 * Commit a pack and index pair that was written to the DFS.
	 * <p>
	 * Committing the pack/index pair makes them visible to readers. The JGit
	 * DFS code always writes the pack, then the index. This allows a simple
	 * commit process to do nothing if readers always look for both files to
	 * exist and the DFS performs atomic creation of the file (e.g. stream to a
	 * temporary file and rename to target on close).
	 * <p>
	 * During pack compaction or GC the new pack file may be replacing other
	 * older files. Implementations should remove those older files (if any) as
	 * part of the commit of the new file.
	 * <p>
	 * This method is a trivial wrapper around
	 * {@link #commitPackImpl(Collection, Collection)} that calls the
	 * implementation and fires events.
	 *
	 * @param desc
	 *            description of the new packs.
	 * @param replaces
	 *            if not null, list of packs to remove.
	 * @throws java.io.IOException
	 *             the packs cannot be committed. On failure a rollback must
	 *             also be attempted by the caller.
	 */
	protected void commitPack(Collection<DfsPackDescription> desc,
			Collection<DfsPackDescription> replaces) throws IOException {
		commitPackImpl(desc, replaces);
		getRepository().fireEvent(new DfsPacksChangedEvent());
	}

	/**
	 * Implementation of pack commit.
	 *
	 * @see #commitPack(Collection, Collection)
	 * @param desc
	 *            description of the new packs.
	 * @param replaces
	 *            if not null, list of packs to remove.
	 * @throws java.io.IOException
	 *             the packs cannot be committed.
	 */
	protected abstract void commitPackImpl(Collection<DfsPackDescription> desc,
			Collection<DfsPackDescription> replaces) throws IOException;

	/**
	 * Try to rollback a pack creation.
	 * <p>
	 * JGit DFS always writes the pack first, then the index. If the pack does
	 * not yet exist, then neither does the index. A safe DFS implementation
	 * would try to remove both files to ensure they are really gone.
	 * <p>
	 * A rollback does not support failures, as it only occurs when there is
	 * already a failure in progress. A DFS implementor may wish to log
	 * warnings/error messages when a rollback fails, but should not send new
	 * exceptions up the Java callstack.
	 *
	 * @param desc
	 *            pack to delete.
	 */
	protected abstract void rollbackPack(Collection<DfsPackDescription> desc);

	/**
	 * List the available pack files.
	 * <p>
	 * The returned list must support random access and must be mutable by the
	 * caller. It is sorted in place using the natural sorting of the returned
	 * DfsPackDescription objects.
	 *
	 * @return available packs. May be empty if there are no packs.
	 * @throws java.io.IOException
	 *             the packs cannot be listed and the object database is not
	 *             functional to the caller.
	 */
	protected abstract List<DfsPackDescription> listPacks() throws IOException;

	/**
	 * Open a pack, pack index, or other related file for reading.
	 *
	 * @param desc
	 *            description of pack related to the data that will be read.
	 *            This is an instance previously obtained from
	 *            {@link #listPacks()}, but not necessarily from the same
	 *            DfsObjDatabase instance.
	 * @param ext
	 *            file extension that will be read i.e "pack" or "idx".
	 * @return channel to read the file.
	 * @throws java.io.FileNotFoundException
	 *             the file does not exist.
	 * @throws java.io.IOException
	 *             the file cannot be opened.
	 */
	protected abstract ReadableChannel openFile(
			DfsPackDescription desc, PackExt ext)
			throws FileNotFoundException, IOException;

	/**
	 * Open a pack, pack index, or other related file for writing.
	 *
	 * @param desc
	 *            description of pack related to the data that will be written.
	 *            This is an instance previously obtained from
	 *            {@link #newPack(PackSource)}.
	 * @param ext
	 *            file extension that will be written i.e "pack" or "idx".
	 * @return channel to write the file.
	 * @throws java.io.IOException
	 *             the file cannot be opened.
	 */
	protected abstract DfsOutputStream writeFile(
			DfsPackDescription desc, PackExt ext) throws IOException;

	void addPack(DfsPackFile newPack) throws IOException {
		PackList o, n;
		do {
			o = packList.get();
			if (o == NO_PACKS) {
				// The repository may not have needed any existing objects to
				// complete the current task of creating a pack (e.g. push of a
				// pack with no external deltas). Because we don't scan for
				// newly added packs on missed object lookups, scan now to
				// make sure all older packs are available in the packList.
				o = scanPacks(o);

				// Its possible the scan identified the pack we were asked to
				// add, as the pack was already committed via commitPack().
				// If this is the case return without changing the list.
				for (DfsPackFile p : o.packs) {
					if (p.key.equals(newPack.key)) {
						return;
					}
				}
			}

			DfsPackFile[] packs = new DfsPackFile[1 + o.packs.length];
			packs[0] = newPack;
			System.arraycopy(o.packs, 0, packs, 1, o.packs.length);
			n = new PackListImpl(packs, o.reftables);
		} while (!packList.compareAndSet(o, n));
	}

	void addReftable(DfsPackDescription add, Set<DfsPackDescription> remove)
			throws IOException {
		PackList o, n;
		do {
			o = packList.get();
			if (o == NO_PACKS) {
				o = scanPacks(o);
				for (DfsReftable t : o.reftables) {
					if (t.getPackDescription().equals(add)) {
						return;
					}
				}
			}

			List<DfsReftable> tables = new ArrayList<>(1 + o.reftables.length);
			for (DfsReftable t : o.reftables) {
				if (!remove.contains(t.getPackDescription())) {
					tables.add(t);
				}
			}
			tables.add(new DfsReftable(add));
			n = new PackListImpl(o.packs, tables.toArray(new DfsReftable[0]));
		} while (!packList.compareAndSet(o, n));
	}

	PackList scanPacks(PackList original) throws IOException {
		PackList o, n;
		synchronized (packList) {
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
		}
		getRepository().fireEvent(new DfsPacksChangedEvent());
		return n;
	}

	private PackList scanPacksImpl(PackList old) throws IOException {
		DfsBlockCache cache = DfsBlockCache.getInstance();
		Map<DfsPackDescription, DfsPackFile> packs = packMap(old);
		Map<DfsPackDescription, DfsReftable> reftables = reftableMap(old);

		List<DfsPackDescription> scanned = listPacks();
		Collections.sort(scanned, packComparator);

		List<DfsPackFile> newPacks = new ArrayList<>(scanned.size());
		List<DfsReftable> newReftables = new ArrayList<>(scanned.size());
		boolean foundNew = false;
		for (DfsPackDescription dsc : scanned) {
			DfsPackFile oldPack = packs.remove(dsc);
			if (oldPack != null) {
				newPacks.add(oldPack);
			} else if (dsc.hasFileExt(PackExt.PACK)) {
				newPacks.add(new DfsPackFile(cache, dsc));
				foundNew = true;
			}

			DfsReftable oldReftable = reftables.remove(dsc);
			if (oldReftable != null) {
				newReftables.add(oldReftable);
			} else if (dsc.hasFileExt(PackExt.REFTABLE)) {
				newReftables.add(new DfsReftable(cache, dsc));
				foundNew = true;
			}
		}

		if (newPacks.isEmpty() && newReftables.isEmpty())
			return new PackListImpl(NO_PACKS.packs, NO_PACKS.reftables);
		if (!foundNew) {
			old.clearDirty();
			return old;
		}
		Collections.sort(newReftables, reftableComparator());
		return new PackListImpl(
				newPacks.toArray(new DfsPackFile[0]),
				newReftables.toArray(new DfsReftable[0]));
	}

	private static Map<DfsPackDescription, DfsPackFile> packMap(PackList old) {
		Map<DfsPackDescription, DfsPackFile> forReuse = new HashMap<>();
		for (DfsPackFile p : old.packs) {
			if (!p.invalid()) {
				forReuse.put(p.desc, p);
			}
		}
		return forReuse;
	}

	private static Map<DfsPackDescription, DfsReftable> reftableMap(PackList old) {
		Map<DfsPackDescription, DfsReftable> forReuse = new HashMap<>();
		for (DfsReftable p : old.reftables) {
			if (!p.invalid()) {
				forReuse.put(p.desc, p);
			}
		}
		return forReuse;
	}

	/**
	 * Get comparator to sort {@link DfsReftable} by priority.
	 *
	 * @return comparator to sort {@link DfsReftable} by priority.
	 */
	protected Comparator<DfsReftable> reftableComparator() {
		return Comparator.comparing(
				DfsReftable::getPackDescription,
				DfsPackDescription.reftableComparator());
	}

	/**
	 * Clears the cached list of packs, forcing them to be scanned again.
	 */
	protected void clearCache() {
		packList.set(NO_PACKS);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		packList.set(NO_PACKS);
	}

	/** Snapshot of packs scanned in a single pass. */
	public static abstract class PackList {
		/** All known packs, sorted. */
		public final DfsPackFile[] packs;

		/** All known reftables, sorted. */
		public final DfsReftable[] reftables;

		private long lastModified = -1;

		PackList(DfsPackFile[] packs, DfsReftable[] reftables) {
			this.packs = packs;
			this.reftables = reftables;
		}

		/** @return last modified time of all packs, in milliseconds. */
		public long getLastModified() {
			if (lastModified < 0) {
				long max = 0;
				for (DfsPackFile pack : packs) {
					max = Math.max(max, pack.getPackDescription().getLastModified());
				}
				lastModified = max;
			}
			return lastModified;
		}

		abstract boolean dirty();
		abstract void clearDirty();

		/**
		 * Mark pack list as dirty.
		 * <p>
		 * Used when the caller knows that new data might have been written to the
		 * repository that could invalidate open readers depending on this pack list,
		 * for example if refs are newly scanned.
		 */
		public abstract void markDirty();
	}

	private static final class PackListImpl extends PackList {
		private volatile boolean dirty;

		PackListImpl(DfsPackFile[] packs, DfsReftable[] reftables) {
			super(packs, reftables);
		}

		@Override
		boolean dirty() {
			return dirty;
		}

		@Override
		void clearDirty() {
			dirty = false;
		}

		@Override
		public void markDirty() {
			dirty = true;
		}
	}
}
