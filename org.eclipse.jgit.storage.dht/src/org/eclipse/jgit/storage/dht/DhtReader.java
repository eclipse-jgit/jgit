/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.PackWriter;

class DhtReader extends ObjectReader implements ObjectReuseAsIs {
	private final DhtRepository repository;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtReaderOptions options;

	private final DeltaBaseCache deltaBaseCache;

	private Inflater inflater;

	private PackChunk lastChunk;

	DhtReader(DhtRepository repository, RepositoryKey repo, Database db) {
		this.repository = repository;
		this.repo = repo;
		this.db = db;
		this.options = DhtReaderOptions.DEFAULT;
		this.deltaBaseCache = new DeltaBaseCache(options);
	}

	DhtReaderOptions getOptions() {
		return options;
	}

	DeltaBaseCache getDeltaBaseCache() {
		return deltaBaseCache;
	}

	Inflater inflater() {
		if (inflater == null)
			inflater = InflaterCache.get();
		else
			inflater.reset();
		return inflater;
	}

	@Override
	public void release() {
		InflaterCache.release(inflater);
		inflater = null;

		lastChunk = null;

		super.release();
	}

	@Override
	public ObjectReader newReader() {
		return new DhtReader(repository, repo, db);
	}

	@Override
	public boolean has(AnyObjectId objId, int typeHint) throws IOException {
		if (lastChunk != null && lastChunk.contains(repo, objId))
			return true;

		if (objId instanceof RefData.IdWithChunk
				|| repository.getRefDatabase().find(objId) != null)
			return true;

		// TODO(spearce) This is very expensive. Is it worthwhile?
		if (ChunkCache.get().find(repo, objId) != null)
			return true;

		return !find(ObjectIndexKey.create(repo, objId)).isEmpty();
	}

	@Override
	public ObjectLoader open(AnyObjectId objId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (lastChunk != null) {
			int pos = lastChunk.findOffset(repo, objId);
			if (0 <= pos)
				return lastChunk.read(pos, this, typeHint);
			lastChunk = null;
		}

		ChunkAndOffset p = getChunk(objId, typeHint);
		ObjectLoader ldr = p.chunk.read(p.offset, this, typeHint);
		lastChunk = p.chunk;
		return ldr;
	}

	ChunkAndOffset getChunk(AnyObjectId objId, int typeHint)
			throws DhtException, MissingObjectException {
		if (lastChunk != null) {
			int pos = lastChunk.findOffset(repo, objId);
			if (0 <= pos)
				return new ChunkAndOffset(lastChunk, pos);
			lastChunk = null;
		}

		ChunkKey key;
		if (objId instanceof RefData.IdWithChunk)
			key = ((RefData.IdWithChunk) objId).getChunkKey();
		else
			key = repository.getRefDatabase().find(objId);
		if (key != null) {
			try {
				PackChunk chunk = getChunk(key);
				int pos = chunk.findOffset(repo, objId);
				if (0 <= pos)
					return new ChunkAndOffset(chunk, pos);
			} catch (DhtMissingChunkException brokenHint) {
				// Fall through and do the slow lookup. Our hint
				// in the reference is stale.

				// TODO(spearce) Update the stale reference hint.
			}
		}

		ChunkAndOffset r = ChunkCache.get().find(repo, objId);
		if (r != null)
			return r;

		for (ChunkLink link : find(ObjectIndexKey.create(repo, objId))) {
			PackChunk chunk = load(link.getChunkKey());
			if (chunk == null)
				continue;
			if (chunk.hasIndex())
				chunk = ChunkCache.get().put(chunk);
			return new ChunkAndOffset(chunk, link.getOffset());
		}

		throw missing(objId, typeHint);
	}

	ChunkKey findChunk(AnyObjectId objId) throws DhtException {
		ChunkKey key = repository.getRefDatabase().find(objId);
		if (key != null)
			return key;

		if (lastChunk != null && 0 <= lastChunk.findOffset(repo, objId))
			return lastChunk.getChunkKey();

		ChunkAndOffset r = ChunkCache.get().find(repo, objId);
		if (r != null)
			return r.chunk.getChunkKey();

		for (ChunkLink link : find(ObjectIndexKey.create(repo, objId)))
			return link.getChunkKey();
		return null;
	}

	static MissingObjectException missing(AnyObjectId objId, int typeHint) {
		if (typeHint == OBJ_ANY)
			return new MissingObjectException(objId.copy(),
					DhtText.get().objectTypeUnknown);
		else
			return new MissingObjectException(objId.copy(), typeHint);
	}

	PackChunk getChunk(ChunkKey key) throws DhtException {
		if (lastChunk != null && key.equals(lastChunk.getChunkKey()))
			return lastChunk;

		PackChunk chunk = ChunkCache.get().get(key);
		if (chunk != null)
			return chunk;

		chunk = load(key);
		if (chunk != null) {
			if (chunk.hasIndex())
				return ChunkCache.get().put(chunk);
			return chunk;
		}

		throw new DhtMissingChunkException(key);
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		// Because ObjectIndexKey is prefixed by 4 bytes of hex
		// data from the object itself we may be forced to require
		// at least 4 digits before we can resolve an abbreviation.
		//
		// Unfortunately not every DHT can do a range scan here.
		//
		throw new DhtException.TODO("resolve abbreviations");
	}

	public DhtObjectToPack newObjectToPack(RevObject obj) {
		// TODO(spearce) If the object is in lastChunk we may be able
		// to prefill the selection to use lastChunk and avoid looking it up.
		return new DhtObjectToPack(obj);
	}

	@SuppressWarnings("unchecked")
	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		Iterable itr = objects;
		new RepresentationSelector(packer, repo, db, monitor, this).select(itr);
	}

	public void writeObjects(PackOutputStream out, Iterable<ObjectToPack> list)
			throws IOException {
		for (ObjectToPack otp : list)
			out.writeObject(otp);
	}

	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp)
			throws IOException, StoredObjectRepresentationNotAvailableException {
		try {
			DhtObjectToPack obj = (DhtObjectToPack) otp;
			getChunk(obj.chunk).copyAsIs(out, obj, this);
		} catch (DhtMissingChunkException missingChunk) {
			throw new StoredObjectRepresentationNotAvailableException(otp);
		}
	}

	private List<ChunkLink> find(ObjectIndexKey idxKey) throws DhtException {
		Sync<Map<ObjectIndexKey, Collection<ChunkLink>>> sync = Sync.create();
		db.objectIndex().get(Collections.singleton(idxKey), sync);
		try {
			Collection<ChunkLink> m;

			m = sync.get(options.getTimeout()).get(idxKey);
			if (m == null || m.isEmpty())
				return Collections.emptyList();

			List<ChunkLink> t = new ArrayList<ChunkLink>(m);
			ChunkLink.sort(t);
			return t;
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private PackChunk load(ChunkKey chunkKey) throws DhtException {
		Sync<Collection<PackChunk>> sync = Sync.create();
		db.chunks().get(Collections.singleton(chunkKey), sync);
		try {
			return firstOrNull(sync.get(options.getTimeout()));
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private static <T> T firstOrNull(Collection<T> c) {
		if (c.isEmpty())
			return null;
		if (c instanceof List)
			return ((List<T>) c).get(0);
		return c.iterator().next();
	}

	static class ChunkAndOffset {
		final PackChunk chunk;

		final int offset;

		ChunkAndOffset(PackChunk chunk, int offset) {
			this.chunk = chunk;
			this.offset = offset;
		}
	}
}
