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

import org.eclipse.jgit.internal.storage.file.WindowCache;

/**
 * Cache statistics for {@link WindowCache}.
 *
 * @since 4.11
 *
 */
public class WindowCacheStats implements WindowCacheStatsMXBean {
	/**
	 * @return the number of open files.
	 * @deprecated use {@link #getStats()} instead
	 */
	@Deprecated
	public static int getOpenFiles() {
		return (int) WindowCache.getInstance().getStats().getOpenFileCount();
	}

	/**
	 * @return the number of open bytes.
	 * @deprecated use {@link #getStats()} instead
	 */
	@Deprecated
	public static long getOpenBytes() {
		return WindowCache.getInstance().getStats().getOpenByteCount();
	}

	/**
	 * @return cache statistics for the WindowCache
	 * @since 5.1.13
	 */
	public static WindowCacheStatsMXBean getStats() {
		return WindowCache.getInstance().getStats();
	}

	private final long hitCount;
	private final long missCount;
	private final long loadSuccessCount;
	private final long loadFailureCount;
	private final long totalLoadTime;
	private final long evictionCount;
	private final long openFileCount;
	private final long openByteCount;

	/**
	 * Construct window cache statistics
	 *
	 * @param hitCount
	 * @param missCount
	 * @param loadSuccessCount
	 * @param loadFailureCount
	 * @param totalLoadTime
	 * @param evictionCount
	 * @param openFileCount
	 * @param openByteCount
	 * @since 5.1.13
	 */
	public WindowCacheStats(long hitCount, long missCount,
			long loadSuccessCount, long loadFailureCount, long totalLoadTime,
			long evictionCount, long openFileCount, long openByteCount) {
		this.hitCount = hitCount;
		this.missCount = missCount;
		this.loadSuccessCount = loadSuccessCount;
		this.loadFailureCount = loadFailureCount;
		this.totalLoadTime = totalLoadTime;
		this.evictionCount = evictionCount;
		this.openFileCount = openFileCount;
		this.openByteCount = openByteCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getHitCount() {
		return hitCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getMissCount() {
		return missCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getLoadSuccessCount() {
		return loadSuccessCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getLoadFailureCount() {
		return loadFailureCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getEvictionCount() {
		return evictionCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getTotalLoadTime() {
		return totalLoadTime;
	}

	/** {@inheritDoc} */
	@Override
	public long getOpenFileCount() {
		return openFileCount;
	}

	/** {@inheritDoc} */
	@Override
	public long getOpenByteCount() {
		return openByteCount;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ (int) (evictionCount ^ (evictionCount >>> 32));
		result = prime * result + (int) (hitCount ^ (hitCount >>> 32));
		result = prime * result
				+ (int) (loadFailureCount ^ (loadFailureCount >>> 32));
		result = prime * result
				+ (int) (loadSuccessCount ^ (loadSuccessCount >>> 32));
		result = prime * result + (int) (missCount ^ (missCount >>> 32));
		result = prime * result
				+ (int) (openByteCount ^ (openByteCount >>> 32));
		result = prime * result
				+ (int) (openFileCount ^ (openFileCount >>> 32));
		result = prime * result
				+ (int) (totalLoadTime ^ (totalLoadTime >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WindowCacheStats other = (WindowCacheStats) obj;
		if (evictionCount != other.evictionCount) {
			return false;
		}
		if (hitCount != other.hitCount) {
			return false;
		}
		if (loadFailureCount != other.loadFailureCount) {
			return false;
		}
		if (loadSuccessCount != other.loadSuccessCount) {
			return false;
		}
		if (missCount != other.missCount) {
			return false;
		}
		if (openByteCount != other.openByteCount) {
			return false;
		}
		if (openFileCount != other.openFileCount) {
			return false;
		}
		if (totalLoadTime != other.totalLoadTime) {
			return false;
		}
		return true;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "WindowCacheStats [hitCount=" + hitCount + ", missCount="
				+ missCount + ", loadSuccessCount=" + loadSuccessCount
				+ ", loadFailureCount=" + loadFailureCount + ", totalLoadTime="
				+ totalLoadTime + ", evictionCount=" + evictionCount
				+ ", openFileCount=" + openFileCount + ", openByteCount="
				+ openByteCount + "]";
	}

	@Override
	public void resetCounters() {
		WindowCache.getInstance().resetStats();
	}
}
