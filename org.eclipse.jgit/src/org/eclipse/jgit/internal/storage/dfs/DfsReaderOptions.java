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

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_DELTA_BASE_CACHE_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_STREAM_FILE_TRESHOLD;

import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.Config;

/** Options controlling how objects are read from a DFS stored repository. */
public class DfsReaderOptions {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KiB = 1024;

	/** 1024 {@link #KiB} (number of bytes in one mebibyte/megabyte) */
	public static final int MiB = 1024 * KiB;

	private int deltaBaseCacheLimit;

	private int streamFileThreshold;

	/** Create a default reader configuration. */
	public DfsReaderOptions() {
		setDeltaBaseCacheLimit(10 * MiB);
		setStreamFileThreshold(CoreConfig.getDefaultStreamFileThreshold());
	}

	/** @return maximum number of bytes to hold in per-reader DeltaBaseCache. */
	public int getDeltaBaseCacheLimit() {
		return deltaBaseCacheLimit;
	}

	/**
	 * Set the maximum number of bytes in the DeltaBaseCache.
	 *
	 * @param maxBytes
	 *            the new limit.
	 * @return {@code this}
	 */
	public DfsReaderOptions setDeltaBaseCacheLimit(int maxBytes) {
		deltaBaseCacheLimit = Math.max(0, maxBytes);
		return this;
	}

	/** @return the size threshold beyond which objects must be streamed. */
	public int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	/**
	 * @param newLimit
	 *            new byte limit for objects that must be streamed. Objects
	 *            smaller than this size can be obtained as a contiguous byte
	 *            array, while objects bigger than this size require using an
	 *            {@link org.eclipse.jgit.lib.ObjectStream}.
	 * @return {@code this}
	 */
	public DfsReaderOptions setStreamFileThreshold(final int newLimit) {
		streamFileThreshold = Math.max(0, newLimit);
		return this;
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
	public DfsReaderOptions fromConfig(Config rc) {
		setDeltaBaseCacheLimit(rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_DELTA_BASE_CACHE_LIMIT,
				getDeltaBaseCacheLimit()));

		long maxMem = Runtime.getRuntime().maxMemory();
		long sft = rc.getLong(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_FILE_TRESHOLD,
				getStreamFileThreshold());
		sft = Math.min(sft, maxMem / 4); // don't use more than 1/4 of the heap
		sft = Math.min(sft, Integer.MAX_VALUE); // cannot exceed array length
		setStreamFileThreshold((int) sft);
		return this;
	}
}
