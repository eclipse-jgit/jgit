/*
 * Copyright (C) 2011, 2013 Google Inc., and others.
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

import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;

import java.util.Arrays;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.storage.pack.PackStatistics;

/**
 * Description of a DFS stored pack/index file.
 * <p>
 * Implementors may extend this class and add additional data members.
 * <p>
 * Instances of this class are cached with the DfsPackFile, and should not be
 * modified once initialized and presented to the JGit DFS library.
 */
public class DfsPackDescription implements Comparable<DfsPackDescription> {
	private final DfsRepositoryDescription repoDesc;
	private final String packName;
	private PackSource packSource;
	private long lastModified;
	private long[] sizeMap;
	private int[] blockSizeMap;
	private long objectCount;
	private long deltaCount;
	private long minUpdateIndex;
	private long maxUpdateIndex;

	private PackStatistics packStats;
	private ReftableWriter.Stats refStats;
	private int extensions;
	private int indexVersion;
	private long estimatedPackSize;

	/**
	 * Initialize a description by pack name and repository.
	 * <p>
	 * The corresponding index file is assumed to exist. If this is not true
	 * implementors must extend the class and override
	 * {@link #getFileName(PackExt)}.
	 * <p>
	 * Callers should also try to fill in other fields if they are reasonably
	 * free to access at the time this instance is being initialized.
	 *
	 * @param name
	 *            name of the pack file. Must end with ".pack".
	 * @param repoDesc
	 *            description of the repo containing the pack file.
	 * @param packSource
	 *            the source of the pack.
	 */
	public DfsPackDescription(DfsRepositoryDescription repoDesc, String name,
			@NonNull PackSource packSource) {
		this.repoDesc = repoDesc;
		int dot = name.lastIndexOf('.');
		this.packName = (dot < 0) ? name : name.substring(0, dot);
		this.packSource = packSource;

		int extCnt = PackExt.values().length;
		sizeMap = new long[extCnt];
		blockSizeMap = new int[extCnt];
	}

	/**
	 * Get description of the repository.
	 *
	 * @return description of the repository.
	 */
	public DfsRepositoryDescription getRepositoryDescription() {
		return repoDesc;
	}

	/**
	 * Adds the pack file extension to the known list.
	 *
	 * @param ext
	 *            the file extension
	 */
	public void addFileExt(PackExt ext) {
		extensions |= ext.getBit();
	}

	/**
	 * Whether the pack file extension is known to exist.
	 *
	 * @param ext
	 *            the file extension
	 * @return whether the pack file extension is known to exist.
	 */
	public boolean hasFileExt(PackExt ext) {
		return (extensions & ext.getBit()) != 0;
	}

	/**
	 * Get file name
	 *
	 * @param ext
	 *            the file extension
	 * @return name of the file.
	 */
	public String getFileName(PackExt ext) {
		return packName + '.' + ext.getExtension();
	}

	/**
	 * Get cache key for use by the block cache.
	 *
	 * @param ext
	 *            the file extension.
	 * @return cache key for use by the block cache.
	 */
	public DfsStreamKey getStreamKey(PackExt ext) {
		return DfsStreamKey.of(getRepositoryDescription(), getFileName(ext),
				ext);
	}

	/**
	 * Get the source of the pack.
	 *
	 * @return the source of the pack.
	 */
	@NonNull
	public PackSource getPackSource() {
		return packSource;
	}

	/**
	 * Set the source of the pack.
	 *
	 * @param source
	 *            the source of the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setPackSource(@NonNull PackSource source) {
		packSource = source;
		return this;
	}

	/**
	 * Get time the pack was created, in milliseconds.
	 *
	 * @return time the pack was created, in milliseconds.
	 */
	public long getLastModified() {
		return lastModified;
	}

	/**
	 * Set time the pack was created, in milliseconds.
	 *
	 * @param timeMillis
	 *            time the pack was created, in milliseconds. 0 if not known.
	 * @return {@code this}
	 */
	public DfsPackDescription setLastModified(long timeMillis) {
		lastModified = timeMillis;
		return this;
	}

	/**
	 * Get minUpdateIndex for the reftable, if present.
	 *
	 * @return minUpdateIndex for the reftable, if present.
	 */
	public long getMinUpdateIndex() {
		return minUpdateIndex;
	}

	/**
	 * Set minUpdateIndex for the reftable.
	 *
	 * @param min
	 *            minUpdateIndex for the reftable.
	 * @return {@code this}
	 */
	public DfsPackDescription setMinUpdateIndex(long min) {
		minUpdateIndex = min;
		return this;
	}

	/**
	 * Get maxUpdateIndex for the reftable, if present.
	 *
	 * @return maxUpdateIndex for the reftable, if present.
	 */
	public long getMaxUpdateIndex() {
		return maxUpdateIndex;
	}

	/**
	 * Set maxUpdateIndex for the reftable.
	 *
	 * @param max
	 *            maxUpdateIndex for the reftable.
	 * @return {@code this}
	 */
	public DfsPackDescription setMaxUpdateIndex(long max) {
		maxUpdateIndex = max;
		return this;
	}

	/**
	 * Set size of the file in bytes.
	 *
	 * @param ext
	 *            the file extension.
	 * @param bytes
	 *            size of the file in bytes. If 0 the file is not known and will
	 *            be determined on first read.
	 * @return {@code this}
	 */
	public DfsPackDescription setFileSize(PackExt ext, long bytes) {
		int i = ext.getPosition();
		if (i >= sizeMap.length) {
			sizeMap = Arrays.copyOf(sizeMap, i + 1);
		}
		sizeMap[i] = Math.max(0, bytes);
		return this;
	}

	/**
	 * Get size of the file, in bytes.
	 *
	 * @param ext
	 *            the file extension.
	 * @return size of the file, in bytes. If 0 the file size is not yet known.
	 */
	public long getFileSize(PackExt ext) {
		int i = ext.getPosition();
		return i < sizeMap.length ? sizeMap[i] : 0;
	}

	/**
	 * Get blockSize of the file, in bytes.
	 *
	 * @param ext
	 *            the file extension.
	 * @return blockSize of the file, in bytes. If 0 the blockSize size is not
	 *         yet known and may be discovered when opening the file.
	 */
	public int getBlockSize(PackExt ext) {
		int i = ext.getPosition();
		return i < blockSizeMap.length ? blockSizeMap[i] : 0;
	}

	/**
	 * Set blockSize of the file, in bytes.
	 *
	 * @param ext
	 *            the file extension.
	 * @param blockSize
	 *            blockSize of the file, in bytes. If 0 the blockSize is not
	 *            known and will be determined on first read.
	 * @return {@code this}
	 */
	public DfsPackDescription setBlockSize(PackExt ext, int blockSize) {
		int i = ext.getPosition();
		if (i >= blockSizeMap.length) {
			blockSizeMap = Arrays.copyOf(blockSizeMap, i + 1);
		}
		blockSizeMap[i] = Math.max(0, blockSize);
		return this;
	}

	/**
	 * Set estimated size of the .pack file in bytes.
	 *
	 * @param estimatedPackSize
	 *            estimated size of the .pack file in bytes. If 0 the pack file
	 *            size is unknown.
	 * @return {@code this}
	 */
	public DfsPackDescription setEstimatedPackSize(long estimatedPackSize) {
		this.estimatedPackSize = Math.max(0, estimatedPackSize);
		return this;
	}

	/**
	 * Get estimated size of the .pack file in bytes.
	 *
	 * @return estimated size of the .pack file in bytes. If 0 the pack file
	 *         size is unknown.
	 */
	public long getEstimatedPackSize() {
		return estimatedPackSize;
	}

	/**
	 * Get number of objects in the pack.
	 *
	 * @return number of objects in the pack.
	 */
	public long getObjectCount() {
		return objectCount;
	}

	/**
	 * Set number of objects in the pack.
	 *
	 * @param cnt
	 *            number of objects in the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setObjectCount(long cnt) {
		objectCount = Math.max(0, cnt);
		return this;
	}

	/**
	 * Get number of delta compressed objects in the pack.
	 *
	 * @return number of delta compressed objects in the pack.
	 */
	public long getDeltaCount() {
		return deltaCount;
	}

	/**
	 * Set number of delta compressed objects in the pack.
	 *
	 * @param cnt
	 *            number of delta compressed objects in the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setDeltaCount(long cnt) {
		deltaCount = Math.max(0, cnt);
		return this;
	}

	/**
	 * Get statistics from PackWriter, if the pack was built with it.
	 *
	 * @return statistics from PackWriter, if the pack was built with it.
	 *         Generally this is only available for packs created by
	 *         DfsGarbageCollector or DfsPackCompactor, and only when the pack
	 *         is being committed to the repository.
	 */
	public PackStatistics getPackStats() {
		return packStats;
	}

	DfsPackDescription setPackStats(PackStatistics stats) {
		this.packStats = stats;
		setFileSize(PACK, stats.getTotalBytes());
		setObjectCount(stats.getTotalObjects());
		setDeltaCount(stats.getTotalDeltas());
		return this;
	}

	/**
	 * Get stats from the sibling reftable, if created.
	 *
	 * @return stats from the sibling reftable, if created.
	 */
	public ReftableWriter.Stats getReftableStats() {
		return refStats;
	}

	void setReftableStats(ReftableWriter.Stats stats) {
		this.refStats = stats;
		setMinUpdateIndex(stats.minUpdateIndex());
		setMaxUpdateIndex(stats.maxUpdateIndex());
		setFileSize(REFTABLE, stats.totalBytes());
		setBlockSize(REFTABLE, stats.refBlockSize());
	}

	/**
	 * Discard the pack statistics, if it was populated.
	 *
	 * @return {@code this}
	 */
	public DfsPackDescription clearPackStats() {
		packStats = null;
		refStats = null;
		return this;
	}

	/**
	 * Get the version of the index file written.
	 *
	 * @return the version of the index file written.
	 */
	public int getIndexVersion() {
		return indexVersion;
	}

	/**
	 * Set the version of the index file written.
	 *
	 * @param version
	 *            the version of the index file written.
	 * @return {@code this}
	 */
	public DfsPackDescription setIndexVersion(int version) {
		indexVersion = version;
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return packName.hashCode();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object b) {
		if (b instanceof DfsPackDescription) {
			DfsPackDescription desc = (DfsPackDescription) b;
			return packName.equals(desc.packName) &&
					getRepositoryDescription().equals(desc.getRepositoryDescription());
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Sort packs according to the optimal lookup ordering.
	 * <p>
	 * This method tries to position packs in the order readers should examine
	 * them when looking for objects by SHA-1. The default tries to sort packs
	 * with more recent modification dates before older packs, and packs with
	 * fewer objects before packs with more objects.
	 */
	@Override
	public int compareTo(DfsPackDescription b) {
		// Cluster by PackSource, pushing UNREACHABLE_GARBAGE to the end.
		PackSource as = getPackSource();
		PackSource bs = b.getPackSource();
		int cmp = as.category - bs.category;
		if (cmp != 0) {
			return cmp;
		}

		// Tie break GC type packs by smallest first. There should be at most
		// one of each source, but when multiple exist concurrent GCs may have
		// run. Preferring the smaller file selects higher quality delta
		// compression, placing less demand on the DfsBlockCache.
		if (as == bs && isGC(as)) {
			cmp = Long.signum(getFileSize(PACK) - b.getFileSize(PACK));
			if (cmp != 0) {
				return cmp;
			}
		}

		// Newer packs should sort first.
		cmp = Long.signum(b.getLastModified() - getLastModified());
		if (cmp != 0)
			return cmp;

		// Break ties on smaller index. Readers may get lucky and find
		// the object they care about in the smaller index. This also pushes
		// big historical packs to the end of the list, due to more objects.
		return Long.signum(getObjectCount() - b.getObjectCount());
	}

	static boolean isGC(PackSource s) {
		switch (s) {
		case GC:
		case GC_REST:
		case GC_TXN:
			return true;
		default:
			return false;
		}
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getFileName(PackExt.PACK);
	}
}
