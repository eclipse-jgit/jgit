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

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.jgit.revwalk.ObjectListIterator;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.spi.Context;
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

	private Collection<ObjectListInfo> objectLists;

	private Inflater inflater;

	private PackChunk lastChunk;

	private Prefetcher prefetcher;

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
		walkAdviceEnd();

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

		// TODO(spearce) This is expensive. Is it worthwhile?
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

	@Override
	public void walkAdviceBeginCommits(RevWalk walk, Collection<RevCommit> roots)
			throws IOException {
		walkAdviceEnd();

		prefetcher = new Prefetcher(db, options, OBJ_COMMIT);
		prefetcher.push(this, roots);
	}

	@Override
	public void walkAdviceBeginTrees(ObjectWalk ow, RevCommit min, RevCommit max)
			throws IOException {
		walkAdviceEnd();

		prefetcher = new Prefetcher(db, options, OBJ_TREE);
		if (min != null && min.getTree() != null)
			prefetcher.push(this, min.getTree());
	}

	@Override
	public void walkAdviceEnd() {
		if (prefetcher != null) {
			prefetcher.cancel();
			prefetcher = null;
		}
	}

	@Override
	public Set<ObjectId> getAvailableObjectLists() {
		if (objectLists == null) {
			try {
				objectLists = db.repository().getObjectLists(repo);
			} catch (DhtException e) {
				return Collections.emptySet();
			} catch (TimeoutException e) {
				return Collections.emptySet();
			}
		}

		Set<ObjectId> r = new HashSet<ObjectId>();
		for (ObjectListInfo info : objectLists)
			r.add(info.getStartingCommit());
		return r;
	}

	@Override
	public ObjectListIterator openObjectList(AnyObjectId listName,
			ObjectWalk walker) throws IOException {
		for (ObjectListInfo info : objectLists) {
			if (info.getStartingCommit().equals(listName))
				return new DhtObjectListIterator(db, info, walker, options);
		}
		throw new DhtException(MessageFormat.format(
				DhtText.get().missingObjectList, listName));
	}

	ChunkAndOffset getChunk(AnyObjectId objId, int typeHint)
			throws DhtException, MissingObjectException {
		return getChunk(objId, typeHint, true /* load */);
	}

	ChunkAndOffset getChunkGently(AnyObjectId objId, int typeHint)
			throws DhtException, MissingObjectException {
		return getChunk(objId, typeHint, false /* do not load */);
	}

	private ChunkAndOffset getChunk(AnyObjectId objId, int typeHint,
			boolean loadIfRequired) throws DhtException, MissingObjectException {
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

		if (!loadIfRequired)
			return null;

		if (prefetcher != null && prefetcher.isType(typeHint)) {
			r = prefetcher.find(repo, objId);
			if (r != null)
				return r;
		}

		for (ObjectInfo link : find(ObjectIndexKey.create(repo, objId))) {
			PackChunk chunk;

			if (prefetcher != null && prefetcher.isType(typeHint)) {
				chunk = prefetcher.get(link.getChunkKey());
				if (chunk == null) {
					chunk = load(link.getChunkKey());
					if (chunk == null)
						continue;
					if (prefetcher != null && prefetcher.isType(typeHint))
						prefetcher.push(chunk.getPrefetch());
				}
			} else {
				chunk = load(link.getChunkKey());
				if (chunk == null)
					continue;
			}

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

		for (ObjectInfo link : find(ObjectIndexKey.create(repo, objId)))
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

	Prefetcher beginPrefetch() {
		walkAdviceEnd();
		prefetcher = new Prefetcher(db, options, 0);
		return prefetcher;
	}

	@SuppressWarnings("unchecked")
	public void writeObjects(PackOutputStream out, List<ObjectToPack> objects)
			throws IOException {
		List itr = objects;
		new ObjectWriter(this).writeObjects(out, itr);
		walkAdviceEnd();
	}

	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp)
			throws IOException, StoredObjectRepresentationNotAvailableException {
		try {
			DhtObjectToPack obj = (DhtObjectToPack) otp;
			PackChunk.copyAsIs(getChunk(obj.chunk), out, obj, this);
		} catch (DhtMissingChunkException missingChunk) {
			throw new StoredObjectRepresentationNotAvailableException(otp);
		}
	}

	private List<ObjectInfo> find(ObjectIndexKey idxKey) throws DhtException {
		Context opt = Context.READ_REPAIR;
		Sync<Map<ObjectIndexKey, Collection<ObjectInfo>>> sync = Sync.create();
		db.objectIndex().get(opt, Collections.singleton(idxKey), sync);
		try {
			Collection<ObjectInfo> m;

			m = sync.get(options.getTimeout()).get(idxKey);
			if (m == null || m.isEmpty())
				return Collections.emptyList();

			List<ObjectInfo> t = new ArrayList<ObjectInfo>(m);
			ObjectInfo.sort(t);
			return t;
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private PackChunk load(ChunkKey chunkKey) throws DhtException {
		Context opt = Context.READ_REPAIR;
		Sync<Collection<PackChunk.Members>> sync = Sync.create();
		db.chunk().get(opt, Collections.singleton(chunkKey), sync);
		try {
			Collection<PackChunk.Members> c = sync.get(options.getTimeout());
			if (c.isEmpty())
				return null;
			if (c instanceof List)
				return ((List<PackChunk.Members>) c).get(0).build();
			return c.iterator().next().build();
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
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
