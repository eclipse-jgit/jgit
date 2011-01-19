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

import static org.eclipse.jgit.lib.Constants.*;
import org.eclipse.jgit.lib.ObjectId;

/** Tracks information about an ObjectList.. */
public class ObjectListInfo {
	/** Type segment of the list. */
	public static class Segment {
		final int objectType;

		int chunkStart;

		int chunkCount;

		int objectCount;

		Segment(int objectType) {
			this.objectType = objectType;
		}

		TinyProtobuf.Encoder toBytes() {
			TinyProtobuf.Encoder e = TinyProtobuf.encode(16);
			e.stringHex32(1, chunkStart);
			e.int32(2, chunkCount);
			e.int32IfNotZero(3, objectCount);
			return e;
		}

		static Segment fromBytes(int type, TinyProtobuf.Decoder d) {
			Segment s = new Segment(type);
			for (;;) {
				switch (d.next()) {
				case 0:
					return s;
				case 1:
					s.chunkStart = d.stringHex32();
					continue;
				case 2:
					s.chunkCount = d.int32();
					continue;
				case 3:
					s.objectCount = d.int32();
					continue;
				default:
					d.skip();
				}
			}
		}
	}

	/**
	 * Parse an encoded ObjectListInfo.
	 *
	 * @param repo
	 *            the repository the list is from.
	 * @param data
	 *            the data of the ObjectListInfo.
	 * @return the list info object.
	 */
	public static ObjectListInfo fromBytes(RepositoryKey repo, byte[] data) {
		ObjectListInfo info = new ObjectListInfo();
		info.repository = repo;

		TinyProtobuf.Decoder d = TinyProtobuf.decode(data);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				info.startingCommit = d.stringObjectId();
				continue;
			case 2:
				info.commits = Segment.fromBytes(OBJ_COMMIT, d.message());
				continue;
			case 3:
				info.trees = Segment.fromBytes(OBJ_TREE, d.message());
				continue;
			case 4:
				info.blobs = Segment.fromBytes(OBJ_BLOB, d.message());
				continue;
			case 5:
				info.chunkCount = d.int32();
				continue;
			case 6:
				info.objectCount = d.int32();
				continue;
			case 7:
				info.listSizeInBytes = d.int32();
				continue;
			default:
				d.skip();
				continue;
			}
		}
		return info;
	}

	RepositoryKey repository;

	ObjectId startingCommit;

	Segment commits;

	Segment trees;

	Segment blobs;

	int chunkCount;

	int listSizeInBytes;

	int objectCount;

	/** @return the starting object. */
	public ObjectId getStartingCommit() {
		return startingCommit;
	}

	/** @return recommended row key for the list info. */
	public RowKey getRowKey() {
		return ObjectIndexKey.create(repository, startingCommit);
	}

	ObjectListChunkKey getChunkKey(int cid) {
		return new ObjectListChunkKey(repository, startingCommit, cid);
	}

	/**
	 * Convert this ObjectListInfo into a byte array for storage.
	 *
	 * @return the ObjectListInfo data, encoded as a byte array. This does not
	 *         include the RepositoryKey, callers must store that separately.
	 */
	public byte[] toBytes() {
		TinyProtobuf.Encoder e = TinyProtobuf.encode(48);
		e.string(1, startingCommit);
		if (commits != null)
			e.message(2, commits.toBytes());
		if (trees != null)
			e.message(3, trees.toBytes());
		if (blobs != null)
			e.message(4, blobs.toBytes());
		e.int32(5, chunkCount);
		e.int32IfNotZero(6, objectCount);
		e.int32IfNotZero(7, listSizeInBytes);
		return e.asByteArray();
	}

	@Override
	public String toString() {
		return "ObjectListInfo:" + startingCommit.name();
	}
}
