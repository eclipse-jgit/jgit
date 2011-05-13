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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.zip.Deflater;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ObjectInfo.ObjectType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.IO;

class DhtInserter extends ObjectInserter {
	private final DhtObjDatabase objdb;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtInserterOptions options;

	private Deflater deflater;

	private WriteBuffer dbWriteBuffer;

	private ChunkFormatter activeChunk;

	DhtInserter(DhtObjDatabase objdb) {
		this.objdb = objdb;
		this.repo = objdb.getRepository().getRepositoryKey();
		this.db = objdb.getDatabase();
		this.options = objdb.getInserterOptions();
	}

	@Override
	public ObjectId insert(int type, long len, InputStream in)
			throws IOException {
		if (Integer.MAX_VALUE < len || mustFragmentSize() < len)
			return insertStream(type, len, in);

		byte[] tmp;
		try {
			tmp = new byte[(int) len];
		} catch (OutOfMemoryError tooLarge) {
			return insertStream(type, len, in);
		}
		IO.readFully(in, tmp, 0, tmp.length);
		return insert(type, tmp, 0, tmp.length);
	}

	private ObjectId insertStream(final int type, final long inflatedSize,
			final InputStream in) throws IOException {

		// TODO Permit multiple chunks to be buffered here at once.
		// It might be possible to compress and hold all chunks for
		// an object, which would then allow them to write their
		// ChunkInfo and chunks in parallel, as well as avoid the
		// rewrite with the ChunkFragments at the end.

		MessageDigest chunkDigest = Constants.newMessageDigest();
		LinkedList<ChunkKey> fragmentList = new LinkedList<ChunkKey>();

		ChunkFormatter chunk = newChunk();
		int position = chunk.position();
		if (!chunk.whole(type, inflatedSize))
			throw new DhtException(DhtText.get().cannotInsertObject);

		MessageDigest objDigest = digest();
		objDigest.update(Constants.encodedTypeString(type));
		objDigest.update((byte) ' ');
		objDigest.update(Constants.encodeASCII(inflatedSize));
		objDigest.update((byte) 0);

		Deflater def = deflater();
		byte[] inBuf = buffer();
		long packedSize = 0;
		long done = 0;
		while (done < inflatedSize) {
			if (done == 0 || def.needsInput()) {
				int inAvail = in.read(inBuf);
				if (inAvail <= 0)
					throw new EOFException();
				objDigest.update(inBuf, 0, inAvail);
				def.setInput(inBuf, 0, inAvail);
				done += inAvail;
			}

			if (chunk.free() == 0) {
				packedSize += chunk.size();
				chunk.setObjectType(type);
				chunk.setFragment();
				fragmentList.add(chunk.end(chunkDigest));
				chunk.safePut(db, dbBuffer());
				chunk = newChunk();
			}
			chunk.appendDeflateOutput(def);
		}

		def.finish();

		while (!def.finished()) {
			if (chunk.free() == 0) {
				packedSize += chunk.size();
				chunk.setObjectType(type);
				chunk.setFragment();
				fragmentList.add(chunk.end(chunkDigest));
				chunk.safePut(db, dbBuffer());
				chunk = newChunk();
			}
			chunk.appendDeflateOutput(def);
		}

		ObjectId objId = ObjectId.fromRaw(objDigest.digest());
		PackedObjectInfo oe = new PackedObjectInfo(objId);
		oe.setOffset(position);

		if (!chunk.isEmpty()) {
			packedSize += chunk.size();
			chunk.setObjectType(type);

			if (fragmentList.isEmpty()) {
				ChunkKey key = chunk.end(chunkDigest);
				chunk.setChunkIndex(Collections.singletonList(oe));
				chunk.safePut(db, dbBuffer());

				GitStore.ObjectInfo.Builder b = GitStore.ObjectInfo.newBuilder();
				b.setObjectType(ObjectType.valueOf(type));
				b.setOffset(position);
				b.setPackedSize(packedSize);
				b.setInflatedSize(inflatedSize);
				ObjectInfo info = new ObjectInfo(key, b.build());
				ObjectIndexKey objKey = ObjectIndexKey.create(repo, objId);
				db.objectIndex().add(objKey, info, dbBuffer());
				return objId;
			}

			chunk.setFragment();
			fragmentList.add(chunk.end(chunkDigest));
			chunk.safePut(db, dbBuffer());
		}
		chunk = null;

		ChunkKey firstChunkKey = fragmentList.get(0);

		ChunkMeta.Builder metaBuilder = ChunkMeta.newBuilder();
		for (ChunkKey k : fragmentList)
			metaBuilder.addFragment(k.asString());
		ChunkMeta meta = metaBuilder.build();

		for (ChunkKey key : fragmentList) {
			PackChunk.Members builder = new PackChunk.Members();
			builder.setChunkKey(key);
			builder.setMeta(meta);

			if (firstChunkKey.equals(key))
				builder.setChunkIndex(ChunkIndex.create(Arrays.asList(oe)));

			db.chunk().put(builder, dbBuffer());
		}

		GitStore.ObjectInfo.Builder b = GitStore.ObjectInfo.newBuilder();
		b.setObjectType(ObjectType.valueOf(type));
		b.setOffset(position);
		b.setPackedSize(packedSize);
		b.setInflatedSize(inflatedSize);
		ObjectInfo info = new ObjectInfo(firstChunkKey, b.build());
		ObjectIndexKey objKey = ObjectIndexKey.create(repo, objId);
		db.objectIndex().add(objKey, info, dbBuffer());

		return objId;
	}

	@Override
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		// TODO Is it important to avoid duplicate objects here?
		// IIRC writing out a DirCache just blindly writes all of the
		// tree objects to the inserter, relying on the inserter to
		// strip out duplicates. We might need to buffer trees as
		// long as possible, then collapse the buffer by looking up
		// any existing objects and avoiding inserting those.

		if (mustFragmentSize() < len)
			return insertStream(type, len, asStream(data, off, len));

		ObjectId objId = idFor(type, data, off, len);

		if (activeChunk == null)
			activeChunk = newChunk();

		if (activeChunk.whole(deflater(), type, data, off, len, objId))
			return objId;

		// TODO Allow more than one chunk pending at a time, this would
		// permit batching puts of the ChunkInfo records.

		if (!activeChunk.isEmpty()) {
			activeChunk.end(digest());
			activeChunk.safePut(db, dbBuffer());
			activeChunk = newChunk();
			if (activeChunk.whole(deflater(), type, data, off, len, objId))
				return objId;
		}

		return insertStream(type, len, asStream(data, off, len));
	}

	/** @return size that compressing still won't fit into a single chunk. */
	private int mustFragmentSize() {
		return 4 * options.getChunkSize();
	}

	@Override
	public PackParser newPackParser(InputStream in) throws IOException {
		return new DhtPackParser(objdb, in);
	}

	@Override
	public void flush() throws IOException {
		if (activeChunk != null && !activeChunk.isEmpty()) {
			activeChunk.end(digest());
			activeChunk.safePut(db, dbBuffer());
			activeChunk = null;
		}

		if (dbWriteBuffer != null)
			dbWriteBuffer.flush();
	}

	@Override
	public void release() {
		if (deflater != null) {
			deflater.end();
			deflater = null;
		}

		dbWriteBuffer = null;
		activeChunk = null;
	}

	private Deflater deflater() {
		if (deflater == null)
			deflater = new Deflater(options.getCompression());
		else
			deflater.reset();
		return deflater;
	}

	private WriteBuffer dbBuffer() {
		if (dbWriteBuffer == null)
			dbWriteBuffer = db.newWriteBuffer();
		return dbWriteBuffer;
	}

	private ChunkFormatter newChunk() {
		ChunkFormatter fmt;

		fmt = new ChunkFormatter(repo, options);
		fmt.setSource(GitStore.ChunkInfo.Source.INSERT);
		return fmt;
	}

	private static ByteArrayInputStream asStream(byte[] data, int off, int len) {
		return new ByteArrayInputStream(data, off, len);
	}
}
