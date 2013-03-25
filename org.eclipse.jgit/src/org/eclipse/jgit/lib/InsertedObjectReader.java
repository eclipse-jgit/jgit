/*
 * Copyright (C) 2013, Google Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader.SmallObject;

/**
 * @see ObjectInserter#insertedObjectReader(ObjectReader, int, int)
 * @since 3.0
 */
public class InsertedObjectReader extends ObjectReader {
	private static class InsertedObject extends ObjectIdOwnerMap.Entry {
		private final SmallObject obj;

		private InsertedObject(AnyObjectId id, int type, byte[] data) {
			super(id);
			this.obj = new SmallObject(type, data);
		}
	}

	private final ObjectInserter inserter;
	private final ObjectReader reader;
	private final ObjectIdOwnerMap<InsertedObject> pending;
	
	private final int objectLimit;
	private final int byteLimit;
	private int bytes;

	InsertedObjectReader(ObjectInserter inserter, ObjectReader reader,
			int objectLimit, int byteLimit) {
		this.inserter = inserter;
		this.reader = reader;
		this.objectLimit = objectLimit;
		this.byteLimit = byteLimit;
		pending = new ObjectIdOwnerMap<InsertedObject>();
	}

	@Override
	public ObjectReader newReader() {
		// Use fallback reader; objects are only cached per thread.
		return reader.newReader();
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		return reader.resolve(id);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		InsertedObject o = pending.get(objectId);
		if (o != null) {
			if (typeHint == OBJ_ANY || o.obj.getType() == typeHint)
				return o.obj;
			throw new IncorrectObjectTypeException(objectId.copy(), typeHint);
		}
		return reader.open(objectId, typeHint);
	}

	@Override
	public Set<ObjectId> getShallowCommits() throws IOException {
		return Collections.emptySet();
	}

	void put(AnyObjectId id, int type, long length, InputStream in)
			throws IOException {
		if (byteLimit > 0 && length > byteLimit)
			return;
		int len = (int) length;
		byte[] buf = new byte[len];
		in.read(buf);
		put(id, type, buf, 0, len);
	}

	void put(AnyObjectId id, int type, byte[] data, int off, int len)
			throws IOException {
		if (byteLimit > 0 && len > byteLimit)
			return;
		InsertedObject o = pending.get(id);
		if (o != null)
			return;
		if (objectLimit > 0 && pending.size() == objectLimit)
			flush(0);
		if (byteLimit > 0) {
			bytes += len;
			if (bytes > byteLimit)
				flush(len);
		}

		byte[] buf;
		if (off == 0 && len == data.length) {
			buf = data;
		} else {
			buf = new byte[len];
			System.arraycopy(data, off, buf, 0, len);
		}
		pending.add(new InsertedObject(id, type, data));
	}

	void flush(int len) throws IOException {
		inserter.doFlush();
		pending.clear();
		bytes = len;
	}
}
