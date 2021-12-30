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

		/** Total number of cache hits for pack indexes. */
		long idxCacheHit;

		/** Total number of cache hits for reverse indexes. */
		long ridxCacheHit;

		/** Total number of cache hits for bitmap indexes. */
		long bitmapCacheHit;

		/** Total number of cache hits for object size indexes. */
		long objSizeCacheHit;

		/** Total number of complete pack indexes read into memory. */
		long readIdx;

		/** Total number of complete bitmap indexes read into memory. */
		long readBitmap;

		/** Total number of reverse indexes added into memory. */
		long readReverseIdx;

		/** Total number of object size indexes added into memory. */
		long readObjSizeIdx;

		/** Total number of bytes read from pack indexes. */
		long readIdxBytes;

		/** Total microseconds spent reading pack indexes. */
		long readIdxMicros;

		/** Total microseconds spent creating reverse indexes. */
		long readReverseIdxMicros;

		/** Total number of bytes read from bitmap indexes. */
		long readBitmapIdxBytes;

		/** Total microseconds spent reading bitmap indexes. */
		long readBitmapIdxMicros;

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
	 * Get total number of pack index cache hits.
	 *
	 * @return total number of pack index cache hits.
	 */
	public long getPackIndexCacheHits() {
		return stats.idxCacheHit;
	}

	/**
	 * Get total number of reverse index cache hits.
	 *
	 * @return total number of reverse index cache hits.
	 */
	public long getReverseIndexCacheHits() {
		return stats.ridxCacheHit;
	}

	/**
	 * Get total number of bitmap index cache hits.
	 *
	 * @return total number of bitmap index cache hits.
	 */
	public long getBitmapIndexCacheHits() {
		return stats.bitmapCacheHit;
	}

	/**
	 * Get total number of object size index cache hits.
	 *
	 * @return total number of objrect size index cache hits.
	 */
	public long getObjectSizeIndexCacheHits() {
		return stats.objSizeCacheHit;
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
	 * Get total number of times the reverse index was computed.
	 *
	 * @return total number of reverse index was computed.
	 */
	public long getReadReverseIndexCount() {
		return stats.readReverseIdx;
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
	 * Get total number of complete object size indexes read into memory.
	 *
	 * @return total number of complete object size indexes read into memory.
	 */
	public long getReadObjectSizeIndexCount() {
		return stats.readObjSizeIdx;
	}

	/**
	 * Get total number of bytes read from pack indexes.
	 *
	 * @return total number of bytes read from pack indexes.
	 */
	public long getReadIndexBytes() {
		return stats.readIdxBytes;
	}

	/**
	 * Get total microseconds spent reading pack indexes.
	 *
	 * @return total microseconds spent reading pack indexes.
	 */
	public long getReadIndexMicros() {
		return stats.readIdxMicros;
	}

	/**
	 * Get total microseconds spent creating reverse indexes.
	 *
	 * @return total microseconds spent creating reverse indexes.
	 */
	public long getReadReverseIndexMicros() {
		return stats.readReverseIdxMicros;
	}

	/**
	 * Get total number of bytes read from bitmap indexes.
	 *
	 * @return total number of bytes read from bitmap indexes.
	 */
	public long getReadBitmapIndexBytes() {
		return stats.readBitmapIdxBytes;
	}

	/**
	 * Get total microseconds spent reading bitmap indexes.
	 *
	 * @return total microseconds spent reading bitmap indexes.
	 */
	public long getReadBitmapIndexMicros() {
		return stats.readBitmapIdxMicros;
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
