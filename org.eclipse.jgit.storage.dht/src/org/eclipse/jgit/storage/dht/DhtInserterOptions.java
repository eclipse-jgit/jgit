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

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.Constants;

class DhtInserterOptions {
	static final DhtInserterOptions DEFAULT = new DhtInserterOptions();

	private static final int MB = 1024 * 1024;

	private static final SecureRandom prng = new SecureRandom();

	/** @return maximum size of a chunk, in bytes. */
	int getChunkSize() {
		return 1 * MB;
	}

	/** @return maximum number of objects to put into a chunk. */
	int getMaxObjectCount() {
		// Do not allow the index to be larger than a chunk itself.
		return (getChunkSize() - 2) / (Constants.OBJECT_ID_LENGTH + 4);
	}

	/** @return compression level used when writing new objects into chunks. */
	int getCompressionLevel() {
		return Deflater.DEFAULT_COMPRESSION;
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
	 * @return maximum number of entries in a {@link ChunkPrefetch} list.
	 */
	int getPrefetchDepth() {
		return 50;
	}

	int getParserCacheSize() {
		return 512;
	}

	int nextChunkSalt() {
		// TODO Is SecureRandom sufficient here? Or should we expand the
		// field size to 8 bytes and also embed some time information, to guard
		// against a short period in SecureRandom?
		//
		return prng.nextInt();
	}

	/** @return number of commits to skip over before making a list. */
	int getObjectListCommitsToSkip() {
		final int avgCommitsPerChunk = 2200;
		return avgCommitsPerChunk * 2;
	}

	/** @return default number of days to skip over commits. */
	int getObjectListSecondsToSkip() {
		return (int) TimeUnit.SECONDS.convert(30, TimeUnit.DAYS);
	}
}
