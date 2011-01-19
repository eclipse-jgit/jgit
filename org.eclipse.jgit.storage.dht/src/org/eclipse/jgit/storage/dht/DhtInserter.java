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
import java.util.Collections;
import java.util.LinkedList;
import java.util.zip.Deflater;

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

	private ChunkWriter chunk;

	private WriteBuffer buffer;

	DhtInserter(DhtObjDatabase objdb, RepositoryKey repo, Database db) {
		this.objdb = objdb;
		this.repo = repo;
		this.db = db;
		this.options = DhtInserterOptions.DEFAULT;
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

	private ObjectId insertStream(int type, long len, InputStream in)
			throws IOException {
		init();

		MessageDigest chunkDigest = Constants.newMessageDigest();
		LinkedList<ChunkKey> fragments = new LinkedList<ChunkKey>();
		ChunkWriter c = chunk.isEmpty() ? chunk : newChunk();
		int position = c.position();
		if (!c.whole(type, len))
			throw new DhtException(DhtText.get().cannotInsertObject);

		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(len));
		md.update((byte) 0);

		byte[] inBuf = buffer();
		long size = 0;
		long done = 0;
		while (done < len) {
			if (done == 0 || deflater.needsInput()) {
				int inAvail = in.read(inBuf);
				if (inAvail <= 0) {
					c.clear();
					throw new EOFException();
				}
				md.update(inBuf, 0, inAvail);
				deflater.setInput(inBuf, 0, inAvail);
				done += inAvail;
			}

			if (c.free() == 0) {
				size += c.size();
				fragments.add(c.putData(chunkDigest, null));
			}
			c.deflate(deflater);
		}

		deflater.finish();

		while (!deflater.finished()) {
			if (c.free() == 0) {
				size += c.size();
				fragments.add(c.putData(chunkDigest, null));
			}
			c.deflate(deflater);
		}
		if (!c.isEmpty()) {
			size += c.size();
			fragments.add(c.putData(chunkDigest, null));
		}

		ObjectId objId = ObjectId.fromRaw(md.digest());

		byte[] fragmentBin = ChunkFragments.toByteArray(fragments);
		for (ChunkKey k : fragments)
			db.chunks().putFragments(k, fragmentBin, buffer);

		ChunkKey first = fragments.get(0);
		PackedObjectInfo oe = new PackedObjectInfo(objId);
		oe.setOffset(position);

		// Within the ChunkLink we can cap the size (or weight) at 2 GB.
		int weight = (int) Math.min(size - position, Integer.MAX_VALUE);
		ChunkLink link = new ChunkLink(first, -1, position, weight, null);
		db.objectIndex().add(ObjectIndexKey.create(repo, objId), link, buffer);
		db.chunks().putIndex(first,
				ChunkIndex.create(Collections.singletonList(oe)), buffer);

		return objId;
	}

	@Override
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		if (mustFragmentSize() < len)
			return insertStream(type, len, asStream(data, off, len));

		init();
		ObjectId objId = idFor(type, data, off, len);
		if (!chunk.whole(deflater, type, data, off, len, objId)) {
			chunk.flush(digest());
			if (!chunk.whole(deflater, type, data, off, len, objId))
				return insertStream(type, len, asStream(data, off, len));
		}
		return objId;
	}

	/** @return size that compressing still won't fit into a single chunk. */
	private int mustFragmentSize() {
		return 4 * options.getChunkSize();
	}

	@Override
	public PackParser newPackParser(InputStream in) throws IOException {
		if (buffer == null)
			buffer = db.newWriteBuffer();
		return new DhtPackParser(objdb, in, repo, db, buffer, options);
	}

	@Override
	public void flush() throws IOException {
		if (chunk != null)
			chunk.flush(digest());

		if (buffer != null)
			buffer.flush();
	}

	@Override
	public void release() {
		if (deflater != null) {
			deflater.end();
			deflater = null;
		}

		chunk = null;
		buffer = null;
	}

	private void init() {
		if (deflater == null)
			deflater = new Deflater(options.getCompressionLevel());
		else
			deflater.reset();

		if (buffer == null)
			buffer = db.newWriteBuffer();

		if (chunk == null)
			chunk = newChunk();
	}

	private ChunkWriter newChunk() {
		return new ChunkWriter(repo, db, buffer, options.getChunkSize());
	}

	private static ByteArrayInputStream asStream(byte[] data, int off, int len) {
		return new ByteArrayInputStream(data, off, len);
	}
}
