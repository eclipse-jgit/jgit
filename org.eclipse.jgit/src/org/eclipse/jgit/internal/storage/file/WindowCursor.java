/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;

/** Active handle to a ByteWindow. */
final class WindowCursor extends ObjectReader implements ObjectReuseAsIs {
	/** Temporary buffer large enough for at least one raw object id. */
	final byte[] tempId = new byte[Constants.OBJECT_ID_LENGTH];

	private Inflater inf;

	private ByteWindow window;

	private DeltaBaseCache baseCache;

	@Nullable
	private final ObjectInserter createdFromInserter;

	final FileObjectDatabase db;

	WindowCursor(FileObjectDatabase db) {
		this.db = db;
		this.createdFromInserter = null;
		this.streamFileThreshold = WindowCache.getStreamFileThreshold();
	}

	WindowCursor(FileObjectDatabase db,
			@Nullable ObjectDirectoryInserter createdFromInserter) {
		this.db = db;
		this.createdFromInserter = createdFromInserter;
		this.streamFileThreshold = WindowCache.getStreamFileThreshold();
	}

	DeltaBaseCache getDeltaBaseCache() {
		if (baseCache == null)
			baseCache = new DeltaBaseCache();
		return baseCache;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectReader newReader() {
		return new WindowCursor(db);
	}

	/** {@inheritDoc} */
	@Override
	public BitmapIndex getBitmapIndex() throws IOException {
		for (PackFile pack : db.getPacks()) {
			PackBitmapIndex index = pack.getBitmapIndex();
			if (index != null)
				return new BitmapIndexImpl(index);
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<CachedPack> getCachedPacksAndUpdate(
			BitmapBuilder needBitmap) throws IOException {
		for (PackFile pack : db.getPacks()) {
			PackBitmapIndex index = pack.getBitmapIndex();
			if (needBitmap.removeAllOrNone(index))
				return Collections.<CachedPack> singletonList(
						new LocalCachedPack(Collections.singletonList(pack)));
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
		db.resolve(matches, id);
		return matches;
	}

	/** {@inheritDoc} */
	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return db.has(objectId);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final ObjectLoader ldr = db.openObject(this, objectId);
		if (ldr == null) {
			if (typeHint == OBJ_ANY)
				throw new MissingObjectException(objectId.copy(),
						JGitText.get().unknownObjectType2);
			throw new MissingObjectException(objectId.copy(), typeHint);
		}
		if (typeHint != OBJ_ANY && ldr.getType() != typeHint)
			throw new IncorrectObjectTypeException(objectId.copy(), typeHint);
		return ldr;
	}

	/** {@inheritDoc} */
	@Override
	public Set<ObjectId> getShallowCommits() throws IOException {
		return db.getShallowCommits();
	}

	/** {@inheritDoc} */
	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		long sz = db.getObjectSize(this, objectId);
		if (sz < 0) {
			if (typeHint == OBJ_ANY)
				throw new MissingObjectException(objectId.copy(),
						JGitText.get().unknownObjectType2);
			throw new MissingObjectException(objectId.copy(), typeHint);
		}
		return sz;
	}

	/** {@inheritDoc} */
	@Override
	public LocalObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
		return new LocalObjectToPack(objectId, type);
	}

	/** {@inheritDoc} */
	@Override
	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		for (ObjectToPack otp : objects) {
			db.selectObjectRepresentation(packer, otp, this);
			monitor.update(1);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		LocalObjectToPack src = (LocalObjectToPack) otp;
		src.pack.copyAsIs(out, src, validate, this);
	}

	/** {@inheritDoc} */
	@Override
	public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
			throws IOException {
		for (ObjectToPack otp : list)
			out.writeObject(otp);
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
	int copy(final PackFile pack, long position, final byte[] dstbuf,
			int dstoff, final int cnt) throws IOException {
		final long length = pack.length;
		int need = cnt;
		while (need > 0 && position < length) {
			pin(pack, position);
			final int r = window.copy(position, dstbuf, dstoff, need);
			position += r;
			dstoff += r;
			need -= r;
		}
		return cnt - need;
	}

	/** {@inheritDoc} */
	@Override
	public void copyPackAsIs(PackOutputStream out, CachedPack pack)
			throws IOException {
		((LocalCachedPack) pack).copyAsIs(out, this);
	}

	void copyPackAsIs(final PackFile pack, final long length,
			final PackOutputStream out) throws IOException {
		long position = 12;
		long remaining = length - (12 + 20);
		while (0 < remaining) {
			pin(pack, position);

			int ptr = (int) (position - window.start);
			int n = (int) Math.min(window.size() - ptr, remaining);
			window.write(out, position, n);
			position += n;
			remaining -= n;
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
	int inflate(final PackFile pack, long position, final byte[] dstbuf,
			boolean headerOnly) throws IOException, DataFormatException {
		prepareInflater();
		pin(pack, position);
		position += window.setInput(position, inf);
		for (int dstoff = 0;;) {
			int n = inf.inflate(dstbuf, dstoff, dstbuf.length - dstoff);
			dstoff += n;
			if (inf.finished() || (headerOnly && dstoff == dstbuf.length))
				return dstoff;
			if (inf.needsInput()) {
				pin(pack, position);
				position += window.setInput(position, inf);
			} else if (n == 0)
				throw new DataFormatException();
		}
	}

	ByteArrayWindow quickCopy(PackFile p, long pos, long cnt)
			throws IOException {
		pin(p, pos);
		if (window instanceof ByteArrayWindow
				&& window.contains(p, pos + (cnt - 1)))
			return (ByteArrayWindow) window;
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

	void pin(final PackFile pack, final long position)
			throws IOException {
		final ByteWindow w = window;
		if (w == null || !w.contains(pack, position)) {
			// If memory is low, we may need what is in our window field to
			// be cleaned up by the GC during the get for the next window.
			// So we always clear it, even though we are just going to set
			// it again.
			//
			window = null;
			window = WindowCache.get(pack, position);
		}
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public ObjectInserter getCreatedFromInserter() {
		return createdFromInserter;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Release the current window cursor.
	 */
	@Override
	public void close() {
		window = null;
		baseCache = null;
		try {
			InflaterCache.release(inf);
		} finally {
			inf = null;
		}
	}
}
