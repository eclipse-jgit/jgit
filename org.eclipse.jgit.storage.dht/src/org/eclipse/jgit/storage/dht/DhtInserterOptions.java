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

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.security.SecureRandom;
import java.util.zip.Deflater;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/** Options controlling how objects are inserted into a DHT stored repository. */
public class DhtInserterOptions {
	private static final SecureRandom prng = new SecureRandom();

	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KiB = 1024;

	/** 1024 {@link #KiB} (number of bytes in one mebibyte/megabyte) */
	public static final int MiB = 1024 * KiB;

	private int chunkSize;

	private int writeBufferSize;

	private int compression;

	private int prefetchDepth;

	private long parserCacheLimit;

	/** Create a default inserter configuration. */
	public DhtInserterOptions() {
		setChunkSize(1 * MiB);
		setWriteBufferSize(1 * MiB);
		setCompression(DEFAULT_COMPRESSION);
		setPrefetchDepth(50);
		setParserCacheLimit(512 * getChunkSize());
	}

	/** @return maximum size of a chunk, in bytes. */
	public int getChunkSize() {
		return chunkSize;
	}

	/**
	 * Set the maximum size of a chunk, in bytes.
	 *
	 * @param sizeInBytes
	 *            the maximum size. A chunk's data segment won't exceed this.
	 * @return {@code this}
	 */
	public DhtInserterOptions setChunkSize(int sizeInBytes) {
		chunkSize = Math.max(1024, sizeInBytes);
		return this;
	}

	/** @return maximum number of outstanding write bytes. */
	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	/**
	 * Set the maximum number of outstanding bytes in a {@link WriteBuffer}.
	 *
	 * @param sizeInBytes
	 *            maximum number of bytes.
	 * @return {@code this}
	 */
	public DhtInserterOptions setWriteBufferSize(int sizeInBytes) {
		writeBufferSize = Math.max(1024, sizeInBytes);
		return this;
	}

	/** @return maximum number of objects to put into a chunk. */
	public int getMaxObjectCount() {
		// Do not allow the index to be larger than a chunk itself.
		return getChunkSize() / (OBJECT_ID_LENGTH + 4);
	}

	/** @return compression level used when writing new objects into chunks. */
	public int getCompression() {
		return compression;
	}

	/**
	 * Set the compression level used when writing new objects.
	 *
	 * @param level
	 *            the compression level. Use
	 *            {@link Deflater#DEFAULT_COMPRESSION} to specify a default
	 *            compression setting.
	 * @return {@code this}
	 */
	public DhtInserterOptions setCompression(int level) {
		compression = level;
		return this;
	}

	/**
	 * Maximum number of entries in a chunk's prefetch list.
	 * <p>
	 * Each commit or tree chunk stores an optional prefetch list containing the
	 * next X chunk keys that a reader would need if they were traversing the
	 * project history. This implies that chunk prefetch lists are overlapping.
	 * <p>
	 * The depth at insertion time needs to be deep enough to allow readers to
	 * have sufficient parallel prefetch to keep themselves busy without waiting
	 * on sequential loads. If the depth is not sufficient, readers will stall
	 * while they sequentially look up the next chunk they need.
	 *
	 * @return maximum number of entries in a {@link ChunkMeta} list.
	 */
	public int getPrefetchDepth() {
		return prefetchDepth;
	}

	/**
	 * Maximum number of entries in a chunk's prefetch list.
	 *
	 * @param depth
	 *            maximum depth of the prefetch list.
	 * @return {@code this}
	 */
	public DhtInserterOptions setPrefetchDepth(int depth) {
		prefetchDepth = Math.max(0, depth);
		return this;
	}

	/**
	 * Number of chunks the parser can cache for delta resolution support.
	 *
	 * @return chunks to hold in memory to support delta resolution.
	 */
	public int getParserCacheSize() {
		return (int) (getParserCacheLimit() / getChunkSize());
	}

	/** @return number of bytes the PackParser can cache for delta resolution. */
	public long getParserCacheLimit() {
		return parserCacheLimit;
	}

	/**
	 * Set the number of bytes the PackParser can cache.
	 *
	 * @param limit
	 *            number of bytes the parser can cache.
	 * @return {@code this}
	 */
	public DhtInserterOptions setParserCacheLimit(long limit) {
		parserCacheLimit = Math.max(0, limit);
		return this;
	}

	/** @return next random 32 bits to salt chunk keys. */
	int nextChunkSalt() {
		return prng.nextInt();
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 * @return {@code this}
	 */
	public DhtInserterOptions fromConfig(Config rc) {
		setChunkSize(rc.getInt("core", "dht", "chunkSize", getChunkSize()));
		setWriteBufferSize(rc.getInt("core", "dht", "writeBufferSize", getWriteBufferSize()));
		setCompression(rc.get(CoreConfig.KEY).getCompression());
		setPrefetchDepth(rc.getInt("core", "dht", "packParserPrefetchDepth", getPrefetchDepth()));
		setParserCacheLimit(rc.getLong("core", "dht", "packParserCacheLimit", getParserCacheLimit()));
		return this;
	}
}
