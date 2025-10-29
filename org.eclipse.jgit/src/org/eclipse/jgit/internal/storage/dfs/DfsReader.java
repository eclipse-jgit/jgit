/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackList;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.PackLoadListener.DfsBlockData;
import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.AsyncObjectSizeQueue;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Reader to access repository content through.
 * <p>
 * See the base {@link org.eclipse.jgit.lib.ObjectReader} documentation for
 * details. Notably, a reader is not thread safe.
 */
public class DfsReader extends ObjectReader implements ObjectReuseAsIs {
	private static final int MAX_RESOLVE_MATCHES = 256;

	/** Temporary buffer large enough for at least one raw object id. */
	final byte[] tempId = new byte[OBJECT_ID_LENGTH];

	/** Database this reader loads objects from. */
	final DfsObjDatabase db;

	final DfsReaderIoStats.Accumulator stats = new DfsReaderIoStats.Accumulator();

	private Inflater inf;
	private DfsBlock block;
	private DeltaBaseCache baseCache;
	private DfsPackFile last;
	private boolean avoidUnreachable;
	private List<PackLoadListener> packLoadListeners = new ArrayList<>();

	/**
	 * Initialize a new DfsReader
	 *
	 * @param db
	 *            parent DfsObjDatabase.
	 */
	protected DfsReader(DfsObjDatabase db) {
		this.db = db;
		this.streamFileThreshold = db.getReaderOptions().getStreamFileThreshold();
	}

	DfsReaderOptions getOptions() {
		return db.getReaderOptions();
	}

	DeltaBaseCache getDeltaBaseCache() {
		if (baseCache == null)
			baseCache = new DeltaBaseCache(this);
		return baseCache;
	}

	@Override
	public ObjectReader newReader() {
		return db.newReader();
	}

	@Override
	public void setAvoidUnreachableObjects(boolean avoid) {
		avoidUnreachable = avoid;
	}

	@Override
	public BitmapIndex getBitmapIndex() throws IOException {
		for (DfsPackFile pack : db.getPacks()) {
			PackBitmapIndex bitmapIndex = pack.getBitmapIndex(this);
			if (bitmapIndex != null)
				return createBitmapIndex(bitmapIndex);
		}
		return null;
	}

	/**
	 * Give subclasses a chance to record pack index stats
	 *
	 * @param packBitmapIndex
	 *            packBitmapIndex found in a pack (never null)
	 * @return an instance of BitmapIndex
	 */
	protected BitmapIndex createBitmapIndex(
			@NonNull PackBitmapIndex packBitmapIndex) {
		return new BitmapIndexImpl(packBitmapIndex);
	}

	@Override
	public Optional<CommitGraph> getCommitGraph() throws IOException {
		for (DfsPackFile pack : db.getPacks()) {
			CommitGraph cg = pack.getCommitGraph(this);
			if (cg != null) {
				return Optional.of(cg);
			}
		}
		return Optional.empty();
	}

	@Override
	public Collection<CachedPack> getCachedPacksAndUpdate(
		BitmapBuilder needBitmap) throws IOException {
		for (DfsPackFile pack : db.getPacks()) {
			// p != pack in the multipack index case
			List<DfsPackFile> p = pack.fullyIncludedIn(this, needBitmap);
			if (!p.isEmpty()) {
				return p.stream().map(DfsCachedPack::new)
						.collect(Collectors.toUnmodifiableList());
			}
		}
		return Collections.emptyList();
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		if (id.isComplete())
			return Collections.singleton(id.toObjectId());
		HashSet<ObjectId> matches = new HashSet<>(4);
		PackList packList = db.getPackList();
		resolveImpl(packList, id, matches);
		if (matches.size() < MAX_RESOLVE_MATCHES && packList.dirty()) {
			stats.scanPacks++;
			resolveImpl(db.scanPacks(packList), id, matches);
		}
		return matches;
	}

	private void resolveImpl(PackList packList, AbbreviatedObjectId id,
			HashSet<ObjectId> matches) throws IOException {
		for (DfsPackFile pack : packList.packs) {
			if (skipGarbagePack(pack)) {
				continue;
			}
			pack.resolve(this, matches, id, MAX_RESOLVE_MATCHES);
			if (matches.size() >= MAX_RESOLVE_MATCHES) {
				break;
			}
		}
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return findPack(objectId) >= 0;
	}

	private int findPack(AnyObjectId objectId) throws IOException {
		if (last != null
				&& !skipGarbagePack(last)) {
			int idxPos = last.findIdxPosition(this, objectId);
			if (idxPos >= 0) {
				return idxPos;
			}
		}

		PackList packList = db.getPackList();
		int idxPos = findInPackList(packList, objectId);
		if (idxPos >= 0) {
			return idxPos;
		} else if (packList.dirty()) {
			stats.scanPacks++;
			idxPos = findInPackList(db.scanPacks(packList), objectId);
			return idxPos;
		}
		return -1;
	}

	// Leave "last" pointing to the pack and return the idx position of the
	// object (-1 if not found)
	private int findInPackList(PackList packList, AnyObjectId objectId)
			throws IOException {
		for (DfsPackFile pack : packList.packs) {
			if (pack == last || skipGarbagePack(pack))
				continue;
			int idxPos = pack.findIdxPosition(this, objectId);
			if (idxPos >= 0) {
				last = pack;
				return idxPos;
			}
		}
		return -1;
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		ObjectLoader ldr;
		if (last != null && !skipGarbagePack(last)) {
			ldr = last.get(this, objectId);
			if (ldr != null) {
				return checkType(ldr, objectId, typeHint);
			}
		}

		PackList packList = db.getPackList();
		ldr = openImpl(packList, objectId);
		if (ldr != null) {
			return checkType(ldr, objectId, typeHint);
		}
		if (packList.dirty()) {
			stats.scanPacks++;
			ldr = openImpl(db.scanPacks(packList), objectId);
			if (ldr != null) {
				return checkType(ldr, objectId, typeHint);
			}
		}

		if (typeHint == OBJ_ANY)
			throw new MissingObjectException(objectId.copy(),
					JGitText.get().unknownObjectType2);
		throw new MissingObjectException(objectId.copy(), typeHint);
	}

	private static ObjectLoader checkType(ObjectLoader ldr, AnyObjectId id,
			int typeHint) throws IncorrectObjectTypeException {
		if (typeHint != OBJ_ANY && ldr.getType() != typeHint) {
			throw new IncorrectObjectTypeException(id.copy(), typeHint);
		}
		return ldr;
	}

	private ObjectLoader openImpl(PackList packList, AnyObjectId objectId)
			throws IOException {
		for (DfsPackFile pack : packList.packs) {
			if (pack == last || skipGarbagePack(pack)) {
				continue;
			}
			ObjectLoader ldr = pack.get(this, objectId);
			if (ldr != null) {
				last = pack;
				return ldr;
			}
		}
		return null;
	}

	@Override
	public Set<ObjectId> getShallowCommits() {
		return Collections.emptySet();
	}

	private static final Comparator<FoundObject<?>> FOUND_OBJECT_SORT = (
			FoundObject<?> a, FoundObject<?> b) -> {
		int cmp = a.packIndex - b.packIndex;
		if (cmp == 0)
			cmp = Long.signum(a.offset - b.offset);
		return cmp;
	};

	private static class FoundObject<T extends ObjectId> {
		final T id;
		final DfsPackFile pack;
		final long offset;
		final int packIndex;

		FoundObject(T objectId, int packIdx, DfsPackFile pack, long offset) {
			this.id = objectId;
			this.pack = pack;
			this.offset = offset;
			this.packIndex = packIdx;
		}

		FoundObject(T objectId) {
			this.id = objectId;
			this.pack = null;
			this.offset = 0;
			this.packIndex = 0;
		}
	}

	private <T extends ObjectId> Iterable<FoundObject<T>> findAll(
			Iterable<T> objectIds) throws IOException {
		HashSet<T> pending = new HashSet<>();
		for (T id : objectIds) {
			pending.add(id);
		}

		PackList packList = db.getPackList();
		List<FoundObject<T>> r = new ArrayList<>();
		findAllImpl(packList, pending, r);
		if (!pending.isEmpty() && packList.dirty()) {
			stats.scanPacks++;
			findAllImpl(db.scanPacks(packList), pending, r);
		}
		for (T t : pending) {
			r.add(new FoundObject<>(t));
		}
		Collections.sort(r, FOUND_OBJECT_SORT);
		return r;
	}

	private <T extends ObjectId> void findAllImpl(PackList packList,
			HashSet<T> pending, List<FoundObject<T>> r) {
		DfsPackFile[] packs = packList.packs;
		if (packs.length == 0) {
			return;
		}
		int lastIdx = 0;
		DfsPackFile lastPack = packs[lastIdx];
		HashSet<T> toRemove = new HashSet<>();
		OBJECT_SCAN: for (T t : pending) {
			if (!skipGarbagePack(lastPack)) {
				try {
					long p = lastPack.findOffset(this, t);
					if (0 < p) {
						r.add(new FoundObject<>(t, lastIdx, lastPack, p));
						toRemove.add(t);
						continue;
					}
				} catch (IOException e) {
					// Fall though and try to examine other packs.
				}
			}

			for (int i = 0; i < packs.length; i++) {
				if (i == lastIdx)
					continue;
				DfsPackFile pack = packs[i];
				if (skipGarbagePack(pack))
					continue;
				try {
					long p = pack.findOffset(this, t);
					if (0 < p) {
						r.add(new FoundObject<>(t, i, pack, p));
						toRemove.add(t);
						lastIdx = i;
						lastPack = pack;
						continue OBJECT_SCAN;
					}
				} catch (IOException e) {
					// Examine other packs.
				}
			}
		}
		pending.removeAll(toRemove);

		last = lastPack;
	}

	private boolean skipGarbagePack(DfsPackFile pack) {
		return avoidUnreachable && pack.isGarbage();
	}

	@Override
	public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
			Iterable<T> objectIds, final boolean reportMissing) {
		Iterable<FoundObject<T>> order;
		IOException error = null;
		try {
			order = findAll(objectIds);
		} catch (IOException e) {
			order = Collections.emptyList();
			error = e;
		}

		final Iterator<FoundObject<T>> idItr = order.iterator();
		final IOException findAllError = error;
		return new AsyncObjectLoaderQueue<>() {
			private FoundObject<T> cur;

			@Override
			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					return true;
				} else if (findAllError != null) {
					throw findAllError;
				} else {
					return false;
				}
			}

			@Override
			public T getCurrent() {
				return cur.id;
			}

			@Override
			public ObjectId getObjectId() {
				return cur.id;
			}

			@Override
			public ObjectLoader open() throws IOException {
				if (cur.pack == null)
					throw new MissingObjectException(cur.id,
							JGitText.get().unknownObjectType2);
				return cur.pack.load(DfsReader.this, cur.offset);
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return true;
			}

			@Override
			public void release() {
				// Nothing to clean up.
			}
		};
	}

	@Override
	public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
			Iterable<T> objectIds, final boolean reportMissing) {
		Iterable<FoundObject<T>> order;
		IOException error = null;
		try {
			order = findAll(objectIds);
		} catch (IOException e) {
			order = Collections.emptyList();
			error = e;
		}

		final Iterator<FoundObject<T>> idItr = order.iterator();
		final IOException findAllError = error;
		return new AsyncObjectSizeQueue<>() {
			private FoundObject<T> cur;
			private long sz;

			@Override
			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					if (cur.pack == null)
						throw new MissingObjectException(cur.id,
								JGitText.get().unknownObjectType2);
					sz = cur.pack.getObjectSize(DfsReader.this, cur.offset);
					return true;
				} else if (findAllError != null) {
					throw findAllError;
				} else {
					return false;
				}
			}

			@Override
			public T getCurrent() {
				return cur.id;
			}

			@Override
			public ObjectId getObjectId() {
				return cur.id;
			}

			@Override
			public long getSize() {
				return sz;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return true;
			}

			@Override
			public void release() {
				// Nothing to clean up.
			}
		};
	}

	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		int idxPos = findPack(objectId);
		if (idxPos < 0) {
			if (typeHint == OBJ_ANY) {
				throw new MissingObjectException(objectId.copy(),
						JGitText.get().unknownObjectType2);
			}
			throw new MissingObjectException(objectId.copy(), typeHint);
		}

		if (typeHint != Constants.OBJ_BLOB || !safeHasObjectSizeIndex(last)) {
			return last.getObjectSize(this, objectId);
		}

		long sz = safeGetIndexedObjectSize(last, idxPos);
		if (sz >= 0) {
			return sz;
		}
		return last.getObjectSize(this, objectId);
	}


	@Override
	public boolean isNotLargerThan(AnyObjectId objectId, int typeHint,
			long limit) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		int idxPos = findPack(objectId);
		if (idxPos < 0) {
			if (typeHint == OBJ_ANY) {
				throw new MissingObjectException(objectId.copy(),
						JGitText.get().unknownObjectType2);
			}
			throw new MissingObjectException(objectId.copy(), typeHint);
		}

		stats.isNotLargerThanCallCount += 1;
		if (typeHint != Constants.OBJ_BLOB || !safeHasObjectSizeIndex(last)) {
			return last.getObjectSize(this, objectId) <= limit;
		}

		long sz = safeGetIndexedObjectSize(last, idxPos);
		if (sz >= 0) {
			return sz <= limit;
		}

		if (isLimitInsideIndexThreshold(last, limit)) {
			// With threshold T, not-found means object < T
			// If limit L > T, then object < T < L
			return true;
		}

		return last.getObjectSize(this, objectId) <= limit;
	}

	private boolean safeHasObjectSizeIndex(DfsPackFile pack) {
		try {
			return pack.hasObjectSizeIndex(this);
		} catch (IOException e) {
			return false;
		}
	}

	private long safeGetIndexedObjectSize(DfsPackFile pack,
			int idxPos) {
		long sz;
		try {
			sz = pack.getIndexedObjectSize(this, idxPos);
		} catch (IOException e) {
			// If there is any error in the index, we should have seen it
			// on hasObjectSizeIndex.
			throw new IllegalStateException(e);
		}
		if (sz < 0) {
			stats.objectSizeIndexMiss += 1;
		} else {
			stats.objectSizeIndexHit += 1;
		}
		return sz;
	}

	private boolean isLimitInsideIndexThreshold(DfsPackFile pack, long limit) {
		try {
			return pack.getObjectSizeIndexThreshold(this) <= limit;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public DfsObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
		return new DfsObjectToPack(objectId, type);
	}

	@Override
	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		// Don't check dirty bit on PackList; assume ObjectToPacks all came
		// from the current list.
		List<DfsPackFile> packs = sortPacksForSelectRepresentation();
		trySelectRepresentation(packer, monitor, objects, packs, false);

		List<DfsPackFile> garbage = garbagePacksForSelectRepresentation();
		if (!garbage.isEmpty() && checkGarbagePacks(objects)) {
			trySelectRepresentation(packer, monitor, objects, garbage, true);
		}
	}

	private void trySelectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects,
			List<DfsPackFile> packs, boolean skipFound) throws IOException {
		for (DfsPackFile pack : packs) {
			List<DfsObjectToPack> inPack = pack.findAllFromPack(this, objects, skipFound);
			if (inPack.isEmpty())
				continue;
			DfsObjectRepresentation rep = new DfsObjectRepresentation(pack);
			for (DfsObjectToPack otp : inPack) {
				// Populate rep.{offset,length} from the pack
				pack.fillRepresentation(rep, otp.getOffset(), this);
				otp.setOffset(0);
				packer.select(otp, rep); // Set otp.offset from rep
				if (!otp.isFound()) {
					otp.setFound();
					monitor.update(1);
				}
			}
		}
	}

	private static final Comparator<DfsPackFile> PACK_SORT_FOR_REUSE =
		Comparator.comparing(
				DfsPackFile::getPackDescription, DfsPackDescription.reuseComparator());

	private List<DfsPackFile> sortPacksForSelectRepresentation()
			throws IOException {
		DfsPackFile[] packs = db.getPacks();
		List<DfsPackFile> sorted = new ArrayList<>(packs.length);
		for (DfsPackFile p : packs) {
			if (p.getPackDescription().getPackSource() != UNREACHABLE_GARBAGE) {
				sorted.add(p);
			}
		}
		Collections.sort(sorted, PACK_SORT_FOR_REUSE);
		return sorted;
	}

	private List<DfsPackFile> garbagePacksForSelectRepresentation()
			throws IOException {
		DfsPackFile[] packs = db.getPacks();
		List<DfsPackFile> garbage = new ArrayList<>(packs.length);
		for (DfsPackFile p : packs) {
			if (p.getPackDescription().getPackSource() == UNREACHABLE_GARBAGE) {
				garbage.add(p);
			}
		}
		return garbage;
	}

	private static boolean checkGarbagePacks(Iterable<ObjectToPack> objects) {
		for (ObjectToPack otp : objects) {
			if (!((DfsObjectToPack) otp).isFound()) {
				return true;
			}
		}
		return false;
	}



	@Override
	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		DfsObjectToPack src = (DfsObjectToPack) otp;
		src.pack.copyAsIs(out, src, validate, this);
	}

	@Override
	public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
			throws IOException {
		for (ObjectToPack otp : list)
			out.writeObject(otp);
	}

	@Override
	public void copyPackAsIs(PackOutputStream out, CachedPack pack)
			throws IOException {
		((DfsCachedPack) pack).copyAsIs(out, this);
	}

	/**
	 * Copy bytes from the window to a caller supplied buffer.
	 *
	 * @param file
	 *            the file the desired window is stored within.
	 * @param position
	 *            position within the file to read from.
	 * @param dstbuf
	 *            destination buffer to copy into.
	 * @param dstoff
	 *            offset within <code>dstbuf</code> to start copying into.
	 * @param cnt
	 *            number of bytes to copy. This value may exceed the number of
	 *            bytes remaining in the window starting at offset
	 *            <code>pos</code>.
	 * @return number of bytes actually copied; this may be less than
	 *         <code>cnt</code> if <code>cnt</code> exceeded the number of bytes
	 *         available.
	 * @throws IOException
	 *             this cursor does not match the provider or id and the proper
	 *             window could not be acquired through the provider's cache.
	 */
	int copy(BlockBasedFile file, long position, byte[] dstbuf, int dstoff,
			int cnt) throws IOException {
		if (cnt == 0)
			return 0;

		long length = file.length;
		if (0 <= length && length <= position)
			return 0;

		int need = cnt;
		do {
			pin(file, position);
			int r = block.copy(position, dstbuf, dstoff, need);
			position += r;
			dstoff += r;
			need -= r;
			if (length < 0)
				length = file.length;
		} while (0 < need && position < length);
		return cnt - need;
	}

	/**
	 * Inflate a region of the pack starting at {@code position}.
	 *
	 * @param pack
	 *            the file the desired window is stored within.
	 * @param position
	 *            position within the file to read from.
	 * @param dstbuf
	 *            destination buffer the inflater should output decompressed
	 *            data to. Must be large enough to store the entire stream,
	 *            unless headerOnly is true.
	 * @param headerOnly
	 *            if true the caller wants only {@code dstbuf.length} bytes.
	 * @return number of bytes inflated into <code>dstbuf</code>.
	 * @throws IOException
	 *             this cursor does not match the provider or id and the proper
	 *             window could not be acquired through the provider's cache.
	 * @throws DataFormatException
	 *             the inflater encountered an invalid chunk of data. Data
	 *             stream corruption is likely.
	 */
	int inflate(DfsPackFile pack, long position, byte[] dstbuf,
			boolean headerOnly) throws IOException, DataFormatException {
		long start = System.nanoTime();
		prepareInflater();
		pin(pack, position);
		position += block.setInput(position, inf);
		for (int dstoff = 0;;) {
			int n = inf.inflate(dstbuf, dstoff, dstbuf.length - dstoff);
			dstoff += n;
			if (inf.finished() || (headerOnly && dstoff == dstbuf.length)) {
				stats.inflatedBytes += dstoff;
				stats.inflationMicros += BlockBasedFile.elapsedMicros(start);
				return dstoff;
			} else if (inf.needsInput()) {
				pin(pack, position);
				position += block.setInput(position, inf);
			} else if (n == 0)
				throw new DataFormatException();
		}
	}

	DfsBlock quickCopy(DfsPackFile p, long pos, long cnt)
			throws IOException {
		pin(p, pos);
		if (block.contains(p.key, pos + (cnt - 1)))
			return block;
		return null;
	}

	Inflater inflater() {
		prepareInflater();
		return inf;
	}

	private void prepareInflater() {
		if (inf == null)
			inf = InflaterCache.get();
		else
			inf.reset();
	}

	void pin(BlockBasedFile file, long position) throws IOException {
		if (block == null || !block.contains(file.key, position)) {
			// If memory is low, we may need what is in our window field to
			// be cleaned up by the GC during the get for the next window.
			// So we always clear it, even though we are just going to set
			// it again.
			block = null;
			block = file.getOrLoadBlock(position, this);
		}
	}

	void unpin() {
		block = null;
	}

	/**
	 * Get IO statistics accumulated by this reader.
	 *
	 * @return IO statistics accumulated by this reader.
	 */
	public DfsReaderIoStats getIoStats() {
		return new DfsReaderIoStats(stats);
	}

	/** Announces when data is loaded by reader */
	protected interface PackLoadListener {
		/**
		 * Immutable copy of a DFS block metadata
		 */
		class DfsBlockData {
			private final int identityHash;
			private final int size;

			static DfsBlockData of(DfsBlock src) {
				return new DfsBlockData(src);
			}

			private DfsBlockData(DfsBlock src) {
				this.identityHash = System.identityHashCode(src);
				this.size = src.size();
			}

			public int getIdentityHash() {
				return identityHash;
			}

			public int getSize() {
				return size;
			}
		}

		/**
		 * This is called when an index reference (e.g. primary index, reverse
		 * index, ...) is set in the reader, regarless if loaded from scratch or
		 * copied from cache.
		 *
		 * During the lifetime of the reader, the reference for an index should
		 * be set only once.
		 *
		 * @param packName
		 *            Name of the pack
		 * @param src
		 *            Source of the pack (e.g. GC, COMPACT, ...)
		 * @param ext
		 *            Extension in the pack (e.g. IDX, RIDX, ...)
		 * @param size
		 *            Size of the data loaded (usually as bytes in disk)
		 * @param loadedIdx
		 *            reference to the loaded index
		 */
		void onIndexLoad(String packName, PackSource src, PackExt ext, long size,
				Object loadedIdx);

		/**
		 * This is called when a dfs block is loaded into the reader.
		 *
		 * The reader keeps only one block at a time in memory, so during a
		 * request the same block could be loaded multiple times.
		 *
		 * @param packName
		 *            Name of the pack this block belongs to
		 * @param src
		 *            Source of the pack (e.g. GC, COMPACT, ...)
		 * @param ext
		 *            Extension in the pack (e.g. PACK or REFTABLE)
		 * @param position
		 *            Offset in the file requested by caller
		 * @param dfsBlockData
		 *            Metadata of the block
		 */
		void onBlockLoad(String packName, PackSource src, PackExt ext,
				long position, DfsBlockData dfsBlockData);
	}

	void emitIndexLoad(DfsPackDescription packDescription, PackExt ext,
			Object loadedIdx) {
		packLoadListeners.forEach(
				listener -> listener.onIndexLoad(packDescription.getFileName(ext),
						packDescription.getPackSource(), ext,
						packDescription.getFileSize(ext), loadedIdx));
	}

	void emitBlockLoad(BlockBasedFile file, long position, DfsBlock dfsBlock) {
		packLoadListeners
				.forEach(listener -> listener.onBlockLoad(file.getFileName(),
						file.desc.getPackSource(), file.ext, position,
						DfsBlockData.of(dfsBlock)));
	}

	/**
	 * Add listener to record loads by this reader
	 *
	 * @param listener a listener
	 */
	protected void addPackLoadListener(PackLoadListener listener) {
		packLoadListeners.add(listener);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Release the current window cursor.
	 */
	@Override
	public void close() {
		last = null;
		block = null;
		baseCache = null;
		try {
			InflaterCache.release(inf);
		} finally {
			inf = null;
		}
	}
}
