/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

/**
 * IO statistics for a {@link org.eclipse.jgit.internal.storage.dfs.DfsReader}.
 */
public class DfsReaderIoStats {
	/** POJO to accumulate IO statistics. */
	public static class Accumulator {
		/** Number of times the reader explicitly called scanPacks. */
		long scanPacks;

		/** Total number of complete pack indexes read into memory. */
		long readIdx;

		/** Total number of complete bitmap indexes read into memory. */
		long readBitmap;

		/** Total number of bytes read from indexes. */
		long readIdxBytes;

		/** Total microseconds spent reading pack or bitmap indexes. */
		long readIdxMicros;

		/** Total number of block cache hits. */
		long blockCacheHit;

		/**
		 * Total number of discrete blocks actually read from pack file(s), that is,
		 * block cache misses.
		 */
		long readBlock;

		/**
		 * Total number of compressed bytes read during cache misses, as block sized
		 * units.
		 */
		long readBlockBytes;

		/** Total microseconds spent reading {@link #readBlock} blocks. */
		long readBlockMicros;

		/** Total number of bytes decompressed. */
		long inflatedBytes;

		/** Total microseconds spent inflating compressed bytes. */
		long inflationMicros;

		Accumulator() {
		}
	}

	private final Accumulator stats;

	DfsReaderIoStats(Accumulator stats) {
		this.stats = stats;
	}

	/**
	 * Get number of times the reader explicitly called scanPacks.
	 *
	 * @return number of times the reader explicitly called scanPacks.
	 */
	public long getScanPacks() {
		return stats.scanPacks;
	}

	/**
	 * Get total number of complete pack indexes read into memory.
	 *
	 * @return total number of complete pack indexes read into memory.
	 */
	public long getReadPackIndexCount() {
		return stats.readIdx;
	}

	/**
	 * Get total number of complete bitmap indexes read into memory.
	 *
	 * @return total number of complete bitmap indexes read into memory.
	 */
	public long getReadBitmapIndexCount() {
		return stats.readBitmap;
	}

	/**
	 * Get total number of bytes read from indexes.
	 *
	 * @return total number of bytes read from indexes.
	 */
	public long getReadIndexBytes() {
		return stats.readIdxBytes;
	}

	/**
	 * Get total microseconds spent reading pack or bitmap indexes.
	 *
	 * @return total microseconds spent reading pack or bitmap indexes.
	 */
	public long getReadIndexMicros() {
		return stats.readIdxMicros;
	}

	/**
	 * Get total number of block cache hits.
	 *
	 * @return total number of block cache hits.
	 */
	public long getBlockCacheHits() {
		return stats.blockCacheHit;
	}

	/**
	 * Get total number of discrete blocks actually read from pack file(s), that
	 * is, block cache misses.
	 *
	 * @return total number of discrete blocks read from pack file(s).
	 */
	public long getReadBlocksCount() {
		return stats.readBlock;
	}

	/**
	 * Get total number of compressed bytes read during cache misses, as block
	 * sized units.
	 *
	 * @return total number of compressed bytes read as block sized units.
	 */
	public long getReadBlocksBytes() {
		return stats.readBlockBytes;
	}

	/**
	 * Get total microseconds spent reading blocks during cache misses.
	 *
	 * @return total microseconds spent reading blocks.
	 */
	public long getReadBlocksMicros() {
		return stats.readBlockMicros;
	}

	/**
	 * Get total number of bytes decompressed.
	 *
	 * @return total number of bytes decompressed.
	 */
	public long getInflatedBytes() {
		return stats.inflatedBytes;
	}

	/**
	 * Get total microseconds spent inflating compressed bytes.
	 *
	 * @return total microseconds inflating compressed bytes.
	 */
	public long getInflationMicros() {
		return stats.inflationMicros;
	}
}
