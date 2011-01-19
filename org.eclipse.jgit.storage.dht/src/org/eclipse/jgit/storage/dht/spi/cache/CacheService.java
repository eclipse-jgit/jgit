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

package org.eclipse.jgit.storage.dht.spi.cache;

import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.StreamingCallback;

/** Connects to the network based memory cache server(s). */
public interface CacheService {
	/**
	 * Lookup one or more cache keys and return the results.
	 * <p>
	 * Callers are responsible for breaking up very large collections of chunk
	 * keys into smaller units, based on the reader's batch size option.
	 *
	 * @param keys
	 *            keys to locate.
	 * @param callback
	 *            receives the results when ready. If this is an instance of
	 *            {@link StreamingCallback}, implementors should try to deliver
	 *            results early.
	 */
	void get(Collection<CacheKey> keys,
			AsyncCallback<Map<CacheKey, byte[]>> callback);

	/**
	 * Modify one or more cache keys.
	 *
	 * @param changes
	 *            changes to apply to the cache.
	 * @param callback
	 *            receives notification when the changes have been applied.
	 */
	void modify(Collection<Change> changes, AsyncCallback<Void> callback);

	/** A change to the cache. */
	public static class Change {
		/** Operation the change describes. */
		public static enum Type {
			/** Store (or replace) the key. */
			PUT,

			/** Only store the key if not already stored. */
			PUT_IF_ABSENT,

			/** Remove the associated key. */
			REMOVE;
		}

		/**
		 * Initialize a put operation.
		 *
		 * @param key
		 *            the key to store.
		 * @param data
		 *            the value to store.
		 * @return the operation.
		 */
		public static Change put(CacheKey key, byte[] data) {
			return new Change(Type.PUT, key, data);
		}

		/**
		 * Initialize a put operation.
		 *
		 * @param key
		 *            the key to store.
		 * @param data
		 *            the value to store.
		 * @return the operation.
		 */
		public static Change putIfAbsent(CacheKey key, byte[] data) {
			return new Change(Type.PUT_IF_ABSENT, key, data);
		}

		/**
		 * Initialize a remove operation.
		 *
		 * @param key
		 *            the key to remove.
		 * @return the operation.
		 */
		public static Change remove(CacheKey key) {
			return new Change(Type.REMOVE, key, null);
		}

		private final Type type;

		private final CacheKey key;

		private final byte[] data;

		/**
		 * Initialize a new change.
		 *
		 * @param type
		 * @param key
		 * @param data
		 */
		public Change(Type type, CacheKey key, byte[] data) {
			this.type = type;
			this.key = key;
			this.data = data;
		}

		/** @return type of change that will take place. */
		public Type getType() {
			return type;
		}

		/** @return the key that will be modified. */
		public CacheKey getKey() {
			return key;
		}

		/** @return new data value if this is a PUT type of change. */
		public byte[] getData() {
			return data;
		}

		@Override
		public String toString() {
			return getType() + " " + getKey();
		}
	}
}
