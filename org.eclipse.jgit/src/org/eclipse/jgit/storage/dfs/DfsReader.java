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

package org.eclipse.jgit.storage.dfs;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.storage.pack.PackExt.PACK;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
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
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.storage.file.PackBitmapIndex;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.BlockList;

/**
 * Reader to access repository content through.
 * <p>
 * See the base {@link ObjectReader} documentation for details. Notably, a
 * reader is not thread safe.
 */
public final class DfsReader extends ObjectReader implements ObjectReuseAsIs {
	/** Temporary buffer large enough for at least one raw object id. */
	final byte[] tempId = new byte[OBJECT_ID_LENGTH];

	/** Database this reader loads objects from. */
	final DfsObjDatabase db;

	private Inflater inf;

	private DfsBlock block;

	private DeltaBaseCache baseCache;

	private DfsPackFile last;

	private boolean wantReadAhead;

	private boolean avoidUnreachable;

	private List<ReadAheadTask.BlockFuture> pendingReadAhead;

	DfsReader(DfsObjDatabase db) {
		this.db = db;
	}

	DfsReaderOptions getOptions() {
		return db.getReaderOptions();
	}

	DeltaBaseCache getDeltaBaseCache() {
		if (baseCache == null)
			baseCache = new DeltaBaseCache(this);
		return baseCache;
	}

	int getStreamFileThreshold() {
		return getOptions().getStreamFileThreshold();
	}

	@Override
	public ObjectReader newReader() {
		return new DfsReader(db);
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
				return new BitmapIndexImpl(bitmapIndex);
		}
		return null;
	}

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

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		if (id.isComplete())
			return Collections.singleton(id.toObjectId());
		boolean noGarbage = avoidUnreachable;
		HashSet<ObjectId> matches = new HashSet<ObjectId>(4);
		for (DfsPackFile pack : db.getPacks()) {
			if (noGarbage && pack.isGarbage())
				continue;
			pack.resolve(this, matches, id, 256);
			if (256 <= matches.size())
				break;
		}
		return matches;
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		if (last != null && last.hasObject(this, objectId))
			return true;
		boolean noGarbage = avoidUnreachable;
		for (DfsPackFile pack : db.getPacks()) {
			if (pack == last || (noGarbage && pack.isGarbage()))
				continue;
			if (pack.hasObject(this, objectId)) {
				last = pack;
				return true;
			}
		}
		return false;
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (last != null) {
			ObjectLoader ldr = last.get(this, objectId);
			if (ldr != null)
				return ldr;
		}

		boolean noGarbage = avoidUnreachable;
		for (DfsPackFile pack : db.getPacks()) {
			if (pack == last || (noGarbage && pack.isGarbage()))
				continue;
			ObjectLoader ldr = pack.get(this, objectId);
			if (ldr != null) {
				last = pack;
				return ldr;
			}
		}

		if (typeHint == OBJ_ANY)
			throw new MissingObjectException(objectId.copy(), "unknown");
		throw new MissingObjectException(objectId.copy(), typeHint);
	}

	@Override
	public Set<ObjectId> getShallowCommits() {
		return Collections.emptySet();
	}

	private static final Comparator<FoundObject<?>> FOUND_OBJECT_SORT = new Comparator<FoundObject<?>>() {
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
		ArrayList<FoundObject<T>> r = new ArrayList<FoundObject<T>>();
		DfsPackFile[] packList = db.getPacks();
		if (packList.length == 0) {
			for (T t : objectIds)
				r.add(new FoundObject<T>(t));
			return r;
		}

		int lastIdx = 0;
		DfsPackFile lastPack = packList[lastIdx];
		boolean noGarbage = avoidUnreachable;

		OBJECT_SCAN: for (T t : objectIds) {
			try {
				long p = lastPack.findOffset(this, t);
				if (0 < p) {
					r.add(new FoundObject<T>(t, lastIdx, lastPack, p));
					continue;
				}
			} catch (IOException e) {
				// Fall though and try to examine other packs.
			}

			for (int i = 0; i < packList.length; i++) {
				if (i == lastIdx)
					continue;
				DfsPackFile pack = packList[i];
				if (noGarbage && pack.isGarbage())
					continue;
				try {
					long p = pack.findOffset(this, t);
					if (0 < p) {
						r.add(new FoundObject<T>(t, i, pack, p));
						lastIdx = i;
						lastPack = pack;
						continue OBJECT_SCAN;
					}
				} catch (IOException e) {
					// Examine other packs.
				}
			}

			r.add(new FoundObject<T>(t));
		}

		Collections.sort(r, FOUND_OBJECT_SORT);
		last = lastPack;
		return r;
	}

	@Override
	public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
			Iterable<T> objectIds, final boolean reportMissing) {
		wantReadAhead = true;

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

			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					return true;
				} else if (findAllError != null) {
					throw findAllError;
				} else {
					cancelReadAhead();
					return false;
				}
			}

			public T getCurrent() {
				return cur.id;
			}

			public ObjectId getObjectId() {
				return cur.id;
			}

			public ObjectLoader open() throws IOException {
				if (cur.pack == null)
					throw new MissingObjectException(cur.id, "unknown");
				return cur.pack.load(DfsReader.this, cur.offset);
			}

			public boolean cancel(boolean mayInterruptIfRunning) {
				cancelReadAhead();
				return true;
			}

			public void release() {
				cancelReadAhead();
			}
		};
	}

	@Override
	public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
			Iterable<T> objectIds, final boolean reportMissing) {
		wantReadAhead = true;

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

			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					if (cur.pack == null)
						throw new MissingObjectException(cur.id, "unknown");
					sz = cur.pack.getObjectSize(DfsReader.this, cur.offset);
					return true;
				} else if (findAllError != null) {
					throw findAllError;
				} else {
					cancelReadAhead();
					return false;
				}
			}

			public T getCurrent() {
				return cur.id;
			}

			public ObjectId getObjectId() {
				return cur.id;
			}

			public long getSize() {
				return sz;
			}

			public boolean cancel(boolean mayInterruptIfRunning) {
				cancelReadAhead();
				return true;
			}

			public void release() {
				cancelReadAhead();
			}
		};
	}

	@Override
	public void walkAdviceBeginCommits(RevWalk walk, Collection<RevCommit> roots) {
		wantReadAhead = true;
	}

	@Override
	public void walkAdviceBeginTrees(ObjectWalk ow, RevCommit min, RevCommit max) {
		wantReadAhead = true;
	}

	@Override
	public void walkAdviceEnd() {
		cancelReadAhead();
	}

	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (last != null) {
			long sz = last.getObjectSize(this, objectId);
			if (0 <= sz)
				return sz;
		}

		for (DfsPackFile pack : db.getPacks()) {
			if (pack == last)
				continue;
			long sz = pack.getObjectSize(this, objectId);
			if (0 <= sz) {
				last = pack;
				return sz;
			}
		}

		if (typeHint == OBJ_ANY)
			throw new MissingObjectException(objectId.copy(), "unknown");
		throw new MissingObjectException(objectId.copy(), typeHint);
	}

	public DfsObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
		return new DfsObjectToPack(objectId, type);
	}

	private static final Comparator<DfsObjectRepresentation> REPRESENTATION_SORT = new Comparator<DfsObjectRepresentation>() {
		public int compare(DfsObjectRepresentation a, DfsObjectRepresentation b) {
			int cmp = a.packIndex - b.packIndex;
			if (cmp == 0)
				cmp = Long.signum(a.offset - b.offset);
			return cmp;
		}
	};

	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		DfsPackFile[] packList = db.getPacks();
		if (packList.length == 0) {
			Iterator<ObjectToPack> itr = objects.iterator();
			if (itr.hasNext())
				throw new MissingObjectException(itr.next(), "unknown");
			return;
		}

		int objectCount = 0;
		int updated = 0;
		int posted = 0;
		List<DfsObjectRepresentation> all = new BlockList<DfsObjectRepresentation>();
		for (ObjectToPack otp : objects) {
			boolean found = false;
			for (int packIndex = 0; packIndex < packList.length; packIndex++) {
				DfsPackFile pack = packList[packIndex];
				long p = pack.findOffset(this, otp);
				if (0 < p) {
					DfsObjectRepresentation r = new DfsObjectRepresentation(otp);
					r.pack = pack;
					r.packIndex = packIndex;
					r.offset = p;
					all.add(r);
					found = true;
				}
			}
			if (!found)
				throw new MissingObjectException(otp, otp.getType());
			if ((++updated & 1) == 1) {
				monitor.update(1); // Update by 50%, the other 50% is below.
				posted++;
			}
			objectCount++;
		}
		Collections.sort(all, REPRESENTATION_SORT);

		try {
			wantReadAhead = true;
			for (DfsObjectRepresentation r : all) {
				r.pack.representation(this, r);
				packer.select(r.object, r);
				if ((++updated & 1) == 1 && posted < objectCount) {
					monitor.update(1);
					posted++;
				}
			}
		} finally {
			cancelReadAhead();
		}
		if (posted < objectCount)
			monitor.update(objectCount - posted);
	}

	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		DfsObjectToPack src = (DfsObjectToPack) otp;
		src.pack.copyAsIs(out, src, validate, this);
	}

	private static final Comparator<ObjectToPack> WRITE_SORT = new Comparator<ObjectToPack>() {
		public int compare(ObjectToPack o1, ObjectToPack o2) {
			DfsObjectToPack a = (DfsObjectToPack) o1;
			DfsObjectToPack b = (DfsObjectToPack) o2;
			int cmp = a.packIndex - b.packIndex;
			if (cmp == 0)
				cmp = Long.signum(a.offset - b.offset);
			return cmp;
		}
	};

	public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
			throws IOException {
		if (list.isEmpty())
			return;

		// Sorting objects by order in the current packs is usually
		// worthwhile. Most packs are going to be OFS_DELTA style,
		// where the base must appear before the deltas. If both base
		// and delta are to be reused, this ensures the base writes in
		// the output first without the recursive write-base-first logic
		// used by PackWriter to ensure OFS_DELTA can be used.
		//
		// Sorting by pack also ensures newer objects go first, which
		// typically matches the desired order.
		//
		// Only do this sorting for OBJ_TREE and OBJ_BLOB. Commits
		// are very likely to already be sorted in a good order in the
		// incoming list, and if they aren't, JGit's PackWriter has fixed
		// the order to be more optimal for readers, so honor that.
		switch (list.get(0).getType()) {
		case OBJ_TREE:
		case OBJ_BLOB:
			Collections.sort(list, WRITE_SORT);
		}

		try {
			wantReadAhead = true;
			for (ObjectToPack otp : list)
				out.writeObject(otp);
		} finally {
			cancelReadAhead();
		}
	}

	public Collection<CachedPack> getCachedPacks() throws IOException {
		DfsPackFile[] packList = db.getPacks();
		List<CachedPack> cached = new ArrayList<CachedPack>(packList.length);
		for (DfsPackFile pack : packList) {
			DfsPackDescription desc = pack.getPackDescription();
			if (canBeCachedPack(desc))
				cached.add(new DfsCachedPack(pack));
		}
		return cached;
	}

	private static boolean canBeCachedPack(DfsPackDescription desc) {
		return desc.getTips() != null && !desc.getTips().isEmpty();
	}

	public void copyPackAsIs(PackOutputStream out, CachedPack pack,
			boolean validate) throws IOException {
		try {
			wantReadAhead = true;
			((DfsCachedPack) pack).copyAsIs(out, validate, this);
		} finally {
			cancelReadAhead();
		}
	}

	/**
	 * Copy bytes from the window to a caller supplied buffer.
	 *
	 * @param pack
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
	int copy(DfsPackFile pack, long position, byte[] dstbuf, int dstoff, int cnt)
			throws IOException {
		if (cnt == 0)
			return 0;

		long length = pack.length;
		if (0 <= length && length <= position)
			return 0;

		int need = cnt;
		do {
			pin(pack, position);
			int r = block.copy(position, dstbuf, dstoff, need);
			position += r;
			dstoff += r;
			need -= r;
			if (length < 0)
				length = pack.length;
		} while (0 < need && position < length);
		return cnt - need;
	}

	void copyPackAsIs(DfsPackFile pack, long length, boolean validate,
			PackOutputStream out) throws IOException {
		MessageDigest md = null;
		if (validate) {
			md = Constants.newMessageDigest();
			byte[] buf = out.getCopyBuffer();
			pin(pack, 0);
			if (block.copy(0, buf, 0, 12) != 12) {
				pack.setInvalid();
				throw new IOException(JGitText.get().packfileIsTruncated);
			}
			md.update(buf, 0, 12);
		}

		long position = 12;
		long remaining = length - (12 + 20);
		while (0 < remaining) {
			pin(pack, position);

			int ptr = (int) (position - block.start);
			int n = (int) Math.min(block.size() - ptr, remaining);
			block.write(out, position, n, md);
			position += n;
			remaining -= n;
		}

		if (md != null) {
			byte[] buf = new byte[20];
			byte[] actHash = md.digest();

			pin(pack, position);
			if (block.copy(position, buf, 0, 20) != 20) {
				pack.setInvalid();
				throw new IOException(JGitText.get().packfileIsTruncated);
			}
			if (!Arrays.equals(actHash, buf)) {
				pack.setInvalid();
				throw new IOException(MessageFormat.format(
						JGitText.get().packfileCorruptionDetected,
						pack.getPackDescription().getFileName(PACK)));
			}
		}
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
	 *            data to.
	 * @param headerOnly
	 *            if true the caller wants only {@code dstbuf.length} bytes.
	 * @return updated <code>dstoff</code> based on the number of bytes
	 *         successfully inflated into <code>dstbuf</code>.
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
		int dstoff = 0;
		for (;;) {
			dstoff = block.inflate(inf, position, dstbuf, dstoff);

			if (headerOnly && dstoff == dstbuf.length)
				return dstoff;
			if (inf.needsInput()) {
				position += block.remaining(position);
				pin(pack, position);
			} else if (inf.finished())
				return dstoff;
			else
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

	void pin(DfsPackFile pack, long position) throws IOException {
		DfsBlock b = block;
		if (b == null || !b.contains(pack.key, position)) {
			// If memory is low, we may need what is in our window field to
			// be cleaned up by the GC during the get for the next window.
			// So we always clear it, even though we are just going to set
			// it again.
			//
			block = null;

			if (pendingReadAhead != null)
				waitForBlock(pack.key, position);
			block = pack.getOrLoadBlock(position, this);
		}
	}

	boolean wantReadAhead() {
		return wantReadAhead;
	}

	void startedReadAhead(List<ReadAheadTask.BlockFuture> blocks) {
		if (pendingReadAhead == null)
			pendingReadAhead = new LinkedList<ReadAheadTask.BlockFuture>();
		pendingReadAhead.addAll(blocks);
	}

	private void cancelReadAhead() {
		if (pendingReadAhead != null) {
			for (ReadAheadTask.BlockFuture f : pendingReadAhead)
				f.cancel(true);
			pendingReadAhead = null;
		}
		wantReadAhead = false;
	}

	private void waitForBlock(DfsPackKey key, long position)
			throws InterruptedIOException {
		Iterator<ReadAheadTask.BlockFuture> itr = pendingReadAhead.iterator();
		while (itr.hasNext()) {
			ReadAheadTask.BlockFuture f = itr.next();
			if (f.contains(key, position)) {
				try {
					f.get();
				} catch (InterruptedException e) {
					throw new InterruptedIOException();
				} catch (ExecutionException e) {
					// Exceptions should never be thrown by get(). Ignore
					// this and let the normal load paths identify any error.
				}
				itr.remove();
				if (pendingReadAhead.isEmpty())
					pendingReadAhead = null;
				break;
			}
		}
	}

	/** Release the current window cursor. */
	@Override
	public void release() {
		cancelReadAhead();
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
