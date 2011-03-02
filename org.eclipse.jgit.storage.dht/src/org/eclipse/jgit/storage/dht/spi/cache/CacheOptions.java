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

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.dht.Timeout;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/** Options to configure the cache. */
public class CacheOptions {
	private Timeout timeout;

	private int writeBufferSize;

	/** Initialize default options. */
	public CacheOptions() {
		setTimeout(Timeout.milliseconds(500));
		setWriteBufferSize(512 * 1024);
	}

	/** @return default timeout for all operations. */
	public Timeout getTimeout() {
		return timeout;
	}

	/**
	 * Set the default timeout to wait on long operations.
	 *
	 * @param maxWaitTime
	 *            new wait time.
	 * @return {@code this}
	 */
	public CacheOptions setTimeout(Timeout maxWaitTime) {
		if (maxWaitTime == null || maxWaitTime.getTime() < 0)
			throw new IllegalArgumentException();
		timeout = maxWaitTime;
		return this;
	}

	/** @return size in bytes to buffer operations. */
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
	public CacheOptions setWriteBufferSize(int sizeInBytes) {
		writeBufferSize = Math.max(1024, sizeInBytes);
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
	public CacheOptions fromConfig(final Config rc) {
		setTimeout(Timeout.getTimeout(rc, "cache", "dht", "timeout", getTimeout()));
		setWriteBufferSize(rc.getInt("cache", "dht", "writeBufferSize", getWriteBufferSize()));
		return this;
	}
}
