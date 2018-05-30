/*
 * Copyright (C) 2008-2011, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackList;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.AsyncObjectSizeQueue;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.BlockList;

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

	/** {@inheritDoc} */
	@Override
	public ObjectReader newReader() {
		return db.newReader();
	}

	/** {@inheritDoc} */
	@Override
	public void setAvoidUnreachableObjects(boolean avoid) {
		avoidUnreachable = avoid;
	}

	/** {@inheritDoc} */
	@Override
	public BitmapIndex getBitmapIndex() throws IOException {
		for (DfsPackFile pack : db.getPacks()) {
			PackBitmapIndex bitmapIndex = pack.getBitmapIndex(this);
			if (bitmapIndex != null)
				return new BitmapIndexImpl(bitmapIndex);
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<CachedPack> getCachedPacksAndUpdate(
		BitmapBuilder needBitmap) throws IOException {
		for (DfsPackFile pack : db.getPacks()) {
			PackBitmapIndex bitmapIndex = pack.getBitmapIndex(this);
			if (needBitmap.removeAllOrNone(bitmapIndex))
				return Collections.<CachedPack> singletonList(
						new DfsCachedPack(pack));
		}
		return Collections.emptyList();
	}

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		if (last != null
				&& !skipGarbagePack(last)
				&& last.hasObject(this, objectId))
			return true;
		PackList packList = db.getPackList();
		if (hasImpl(packList, objectId)) {
			return true;
		} else if (packList.dirty()) {
			stats.scanPacks++;
			return hasImpl(db.scanPacks(packList), objectId);
		}
		return false;
	}

	private boolean hasImpl(PackList packList, AnyObjectId objectId)
			throws IOException {
		for (DfsPackFile pack : packList.packs) {
			if (pack == last || skipGarbagePack(pack))
				continue;
			if (pack.hasObject(this, objectId)) {
				last = pack;
				return true;
			}
		}
		return false;
	}

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
	@Override
	public Set<ObjectId> getShallowCommits() {
		return Collections.emptySet();
	}

	private static final Comparator<FoundObject<?>> FOUND_OBJECT_SORT = new Comparator<FoundObject<?>>() {
		@Override
		public int compare(FoundObject<?> a, FoundObject<?> b) {
			int cmp = a.packIndex - b.packIndex;
			if (cmp == 0)
				cmp = Long.signum(a.offset - b.offset);
			return cmp;
		}
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
		Collection<T> pending = new LinkedList<>();
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
			Collection<T> pending, List<FoundObject<T>> r) {
		DfsPackFile[] packs = packList.packs;
		if (packs.length == 0) {
			return;
		}
		int lastIdx = 0;
		DfsPackFile lastPack = packs[lastIdx];

		OBJECT_SCAN: for (Iterator<T> it = pending.iterator(); it.hasNext();) {
			T t = it.next();
			if (!skipGarbagePack(lastPack)) {
				try {
					long p = lastPack.findOffset(this, t);
					if (0 < p) {
						r.add(new FoundObject<>(t, lastIdx, lastPack, p));
						it.remove();
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
						it.remove();
						lastIdx = i;
						lastPack = pack;
						continue OBJECT_SCAN;
					}
				} catch (IOException e) {
					// Examine other packs.
				}
			}
		}

		last = lastPack;
	}

	private boolean skipGarbagePack(DfsPackFile pack) {
		return avoidUnreachable && pack.isGarbage();
	}

	/** {@inheritDoc} */
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
		return new AsyncObjectLoaderQueue<T>() {
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

	/** {@inheritDoc} */
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
		return new AsyncObjectSizeQueue<T>() {
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

	/** {@inheritDoc} */
	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (last != null && !skipGarbagePack(last)) {
			long sz = last.getObjectSize(this, objectId);
			if (0 <= sz) {
				return sz;
			}
		}

		PackList packList = db.getPackList();
		long sz = getObjectSizeImpl(packList, objectId);
		if (0 <= sz) {
			return sz;
		}
		if (packList.dirty()) {
			sz = getObjectSizeImpl(packList, objectId);
			if (0 <= sz) {
				return sz;
			}
		}

		if (typeHint == OBJ_ANY) {
			throw new MissingObjectException(objectId.copy(),
					JGitText.get().unknownObjectType2);
		}
		throw new MissingObjectException(objectId.copy(), typeHint);
	}

	private long getObjectSizeImpl(PackList packList, AnyObjectId objectId)
			throws IOException {
		for (DfsPackFile pack : packList.packs) {
			if (pack == last || skipGarbagePack(pack)) {
				continue;
			}
			long sz = pack.getObjectSize(this, objectId);
			if (0 <= sz) {
				last = pack;
				return sz;
			}
		}
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public DfsObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
		return new DfsObjectToPack(objectId, type);
	}

	private static final Comparator<DfsObjectToPack> OFFSET_SORT = new Comparator<DfsObjectToPack>() {
		@Override
		public int compare(DfsObjectToPack a, DfsObjectToPack b) {
			return Long.signum(a.getOffset() - b.getOffset());
		}
	};

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
			List<DfsObjectToPack> tmp = findAllFromPack(pack, objects, skipFound);
			if (tmp.isEmpty())
				continue;
			Collections.sort(tmp, OFFSET_SORT);
			PackReverseIndex rev = pack.getReverseIdx(this);
			DfsObjectRepresentation rep = new DfsObjectRepresentation(pack);
			for (DfsObjectToPack otp : tmp) {
				pack.representation(rep, otp.getOffset(), this, rev);
				otp.setOffset(0);
				packer.select(otp, rep);
				if (!otp.isFound()) {
					otp.setFound();
					monitor.update(1);
				}
			}
		}
	}

	private static final Comparator<DfsPackFile> PACK_SORT_FOR_REUSE = new Comparator<DfsPackFile>() {
		@Override
		public int compare(DfsPackFile af, DfsPackFile bf) {
			DfsPackDescription ad = af.getPackDescription();
			DfsPackDescription bd = bf.getPackDescription();
			PackSource as = ad.getPackSource();
			PackSource bs = bd.getPackSource();

			if (as == bs && DfsPackDescription.isGC(as)) {
				// Push smaller GC files last; these likely have higher quality
				// delta compression and the contained representation should be
				// favored over other files.
				return Long.signum(bd.getFileSize(PACK) - ad.getFileSize(PACK));
			}

			// DfsPackDescription.compareTo already did a reasonable sort.
			// Rely on Arrays.sort being stable, leaving equal elements.
			return 0;
		}
	};

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

	private List<DfsObjectToPack> findAllFromPack(DfsPackFile pack,
			Iterable<ObjectToPack> objects, boolean skipFound)
					throws IOException {
		List<DfsObjectToPack> tmp = new BlockList<>();
		PackIndex idx = pack.getPackIndex(this);
		for (ObjectToPack obj : objects) {
			DfsObjectToPack otp = (DfsObjectToPack) obj;
			if (skipFound && otp.isFound()) {
				continue;
			}
			long p = idx.findOffset(otp);
			if (0 < p && !pack.isCorrupt(p)) {
				otp.setOffset(p);
				tmp.add(otp);
			}
		}
		return tmp;
	}

	/** {@inheritDoc} */
	@Override
	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		DfsObjectToPack src = (DfsObjectToPack) otp;
		src.pack.copyAsIs(out, src, validate, this);
	}

	/** {@inheritDoc} */
	@Override
	public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
			throws IOException {
		for (ObjectToPack otp : list)
			out.writeObject(otp);
	}

	/** {@inheritDoc} */
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
		prepareInflater();
		pin(pack, position);
		position += block.setInput(position, inf);
		for (int dstoff = 0;;) {
			int n = inf.inflate(dstbuf, dstoff, dstbuf.length - dstoff);
			dstoff += n;
			if (inf.finished() || (headerOnly && dstoff == dstbuf.length)) {
				stats.inflatedBytes += dstoff;
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
