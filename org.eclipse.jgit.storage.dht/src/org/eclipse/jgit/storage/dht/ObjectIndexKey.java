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

import static org.eclipse.jgit.storage.dht.KeyUtils.format32;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse32;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import java.text.MessageFormat;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/** Identifies an ObjectId in the DHT. */
public class ObjectIndexKey extends ObjectId implements RowKey {
	private static final int KEYLEN = 54;

	/**
	 * @param repo
	 * @param objId
	 * @return the key
	 */
	public static ObjectIndexKey create(RepositoryKey repo, AnyObjectId objId) {
		return new ObjectIndexKey(repo.asInt(), objId);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static ObjectIndexKey fromBytes(byte[] key) {
		if (key.length != KEYLEN)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidChunkKey, decode(key)));

		int repo = parse32(key, 5);
		ObjectId chunk = ObjectId.fromString(key, 14);
		return new ObjectIndexKey(repo, chunk);
	}

	private final int repo;

	ObjectIndexKey(int repo, AnyObjectId objId) {
		super(objId);
		this.repo = repo;
	}

	public byte[] asByteArray() {
		byte[] r = new byte[54];
		this.copyTo(r, 14);
		r[13] = '.';
		format32(r, 5, repo);
		// bucket is the leading 4 digits of the SHA-1.
		r[4] = '.';
		r[3] = r[14 + 3];
		r[2] = r[14 + 2];
		r[1] = r[14 + 1];
		r[0] = r[14 + 0];
		return r;
	}

	@Override
	public String toString() {
		return "object-index:" + decode(asByteArray());
	}
}
