/*
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com>
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
package org.eclipse.jgit.storage.file;

import javax.management.MXBean;

import org.eclipse.jgit.internal.storage.file.WindowCache;

/**
 * Cache statistics for {@link WindowCache}.
 *
 * @since 4.11
 */
@MXBean
public interface WindowCacheStats {
	/**
	 * @return the number of open files.
	 * @deprecated use {@link #getOpenFileCount()} instead
	 */
	@Deprecated
	public static int getOpenFiles() {
		return (int) WindowCache.getInstance().getStats().getOpenFileCount();
	}

	/**
	 * @return the number of open bytes.
	 * @deprecated use {@link #getOpenByteCount()} instead
	 */
	@Deprecated
	public static long getOpenBytes() {
		return WindowCache.getInstance().getStats().getOpenByteCount();
	}

	/**
	 * @return cache statistics for the WindowCache
	 * @since 5.1.13
	 */
	public static WindowCacheStats getStats() {
		return WindowCache.getInstance().getStats();
	}

	/**
	 * Number of cache hits
	 *
	 * @return number of cache hits
	 */
	long getHitCount();

	/**
	 * Ratio of cache requests which were hits defined as
	 * {@code hitCount / requestCount}, or {@code 1.0} when
	 * {@code requestCount == 0}. Note that {@code hitRate + missRate =~ 1.0}.
	 *
	 * @return the ratio of cache requests which were hits
	 */
	default double getHitRatio() {
		long requestCount = getRequestCount();
		return (requestCount == 0) ? 1.0
				: (double) getHitCount() / requestCount;
	}

	/**
	 * Number of cache misses.
	 *
	 * @return number of cash misses
	 */
	long getMissCount();

	/**
	 * Ratio of cache requests which were misses defined as
	 * {@code missCount / requestCount}, or {@code 0.0} when
	 * {@code requestCount == 0}. Note that {@code hitRate + missRate =~ 1.0}.
	 * Cache misses include all requests which weren't cache hits, including
	 * requests which resulted in either successful or failed loading attempts.
	 *
	 * @return the ratio of cache requests which were misses
	 */
	default double getMissRatio() {
		long requestCount = getRequestCount();
		return (requestCount == 0) ? 0.0
				: (double) getMissCount() / requestCount;
	}

	/**
	 * Number of successful loads
	 *
	 * @return number of successful loads
	 */
	long getLoadSuccessCount();

	/**
	 * Number of failed loads
	 *
	 * @return number of failed loads
	 */
	long getLoadFailureCount();

	/**
	 * Ratio of cache load attempts which threw exceptions. This is defined as
	 * {@code loadFailureCount / (loadSuccessCount + loadFailureCount)}, or
	 * {@code 0.0} when {@code loadSuccessCount + loadFailureCount == 0}.
	 *
	 * @return the ratio of cache loading attempts which threw exceptions
	 */
	default double getLoadFailureRatio() {
		long loadFailureCount = getLoadFailureCount();
		long totalLoadCount = getLoadSuccessCount() + loadFailureCount;
		return (totalLoadCount == 0) ? 0.0
				: (double) loadFailureCount / totalLoadCount;
	}

	/**
	 * Total number of times that the cache attempted to load new values. This
	 * includes both successful load operations, as well as failed loads. This
	 * is defined as {@code loadSuccessCount + loadFailureCount}.
	 *
	 * @return the {@code loadSuccessCount + loadFailureCount}
	 */
	default long getLoadCount() {
		return getLoadSuccessCount() + getLoadFailureCount();
	}

	/**
	 * Number of cache evictions
	 *
	 * @return number of evictions
	 */
	long getEvictionCount();

	/**
	 * Ratio of cache evictions. This is defined as
	 * {@code evictionCount / requestCount}, or {@code 0.0} when
	 * {@code requestCount == 0}.
	 *
	 * @return the ratio of cache loading attempts which threw exceptions
	 */
	default double getEvictionRatio() {
		long evictionCount = getEvictionCount();
		long requestCount = getRequestCount();
		return (requestCount == 0) ? 0.0
				: (double) evictionCount / requestCount;
	}

	/**
	 * Number of times the cache returned either a cached or uncached value.
	 * This is defined as {@code hitCount + missCount}.
	 *
	 * @return the {@code hitCount + missCount}
	 */
	default long getRequestCount() {
		return getHitCount() + getMissCount();
	}

	/**
	 * Average time in nanoseconds for loading new values. This is
	 * {@code totalLoadTime / (loadSuccessCount + loadFailureCount)}.
	 *
	 * @return the average time spent loading new values
	 */
	default double getAverageLoadTime() {
		long totalLoadCount = getLoadSuccessCount() + getLoadFailureCount();
		return (totalLoadCount == 0) ? 0.0
				: (double) getTotalLoadTime() / totalLoadCount;
	}

	/**
	 * Total time in nanoseconds the cache spent loading new values.
	 *
	 * @return the total number of nanoseconds the cache has spent loading new
	 *         values
	 */
	long getTotalLoadTime();

	/**
	 * Number of pack files kept open by the cache
	 *
	 * @return number of files kept open by cache
	 */
	long getOpenFileCount();

	/**
	 * Number of bytes cached
	 *
	 * @return number of bytes cached
	 */
	long getOpenByteCount();

	/**
	 * Reset counters. Does not reset open bytes and open files counters.
	 */
	void resetCounters();
}
