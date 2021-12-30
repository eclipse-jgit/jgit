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

import org.eclipse.jgit.lib.AnyObjectId;

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

		/** Total number of cache hits for commit graphs. */
		long commitGraphCacheHit;

		/** Total number of cache hits for object size indexes. */
		long objectSizeIndexCacheHit;

		/** Total number of complete pack indexes read into memory. */
		long readIdx;

		/** Total number of complete bitmap indexes read into memory. */
		long readBitmap;

		/** Total number of reverse indexes added into memory. */
		long readReverseIdx;

		/** Total number of complete commit graphs read into memory. */
		long readCommitGraph;

		/** Total number of object size indexes added into memory. */
		long readObjectSizeIndex;

		/** Total number of bytes read from pack indexes. */
		long readIdxBytes;

		/** Total number of bytes read from commit graphs. */
		long readCommitGraphBytes;

		/** Total numer of bytes read from object size index */
		long readObjectSizeIndexBytes;

		/** Total microseconds spent reading pack indexes. */
		long readIdxMicros;

		/** Total microseconds spent creating reverse indexes. */
		long readReverseIdxMicros;

		/** Total microseconds spent creating commit graphs. */
		long readCommitGraphMicros;

		/** Total microseconds spent creating object size indexes */
		long readObjectSizeIndexMicros;

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

		/** Count of queries for the size of an object via #isNotLargerThan */
		long isNotLargerThanCallCount;

		/** Object was below threshold in the object size index */
		long objectSizeIndexMiss;

		/** Object size found in the object size index */
		long objectSizeIndexHit;

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
	 * Get total number of commit graph cache hits.
	 *
	 * @return total number of commit graph cache hits.
	 */
	public long getCommitGraphCacheHits() {
		return stats.commitGraphCacheHit;
	}

	/**
	 * Get total number of object size index cache hits.
	 *
	 * @return total number of object size index cache hits.
	 */
	public long getObjectSizeIndexCacheHits() {
		return stats.objectSizeIndexCacheHit;
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
	 * Get total number of times the commit graph read into memory.
	 *
	 * @return total number of commit graph read into memory.
	 */
	public long getReadCommitGraphCount() {
		return stats.readCommitGraph;
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
		return stats.readObjectSizeIndex;
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
	 * Get total number of bytes read from commit graphs.
	 *
	 * @return total number of bytes read from commit graphs.
	 */
	public long getCommitGraphBytes() {
		return stats.readCommitGraphBytes;
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
	 * Get total microseconds spent reading commit graphs.
	 *
	 * @return total microseconds spent reading commit graphs.
	 */
	public long getReadCommitGraphMicros() {
		return stats.readCommitGraphMicros;
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

	/**
	 * Get count of invocations to
	 * {@link DfsReader#isNotLargerThan(AnyObjectId, int, long)}
	 * <p>
	 * Each call could use the object-size index or not.
	 *
	 * @return how many times the size of an object was checked with
	 *         {@link DfsReader#isNotLargerThan(AnyObjectId, int, long)}
	 */
	public long getIsNotLargerThanCallCount() {
		return stats.isNotLargerThanCallCount;
	}

	/**
	 * Get number of times the size of a blob was found in the object size
	 * index.
	 * <p>
	 * This counts only queries for blobs on packs with object size index.
	 *
	 * @return count of object size index hits
	 */
	public long getObjectSizeIndexHits() {
		return stats.objectSizeIndexHit;
	}

	/**
	 * Get number of times the size of an object was not found in the object
	 * size index. This usually means it was below the threshold.
	 * <p>
	 * This counts only queries for blobs on packs with object size index.
	 *
	 * @return count of object size index misses.
	 */
	public long getObjectSizeIndexMisses() {
		return stats.objectSizeIndexMiss;
	}
}
