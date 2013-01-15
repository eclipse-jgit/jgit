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

package org.eclipse.jgit.storage.dfs;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.storage.pack.PackConstants;
import org.eclipse.jgit.storage.pack.PackWriter;

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

	private Map<String, Long> sizeMap;

	private long objectCount;

	private long deltaCount;

	private Set<ObjectId> tips;

	private PackWriter.Statistics stats;

	/**
	 * Initialize a description by pack name and repository.
	 * <p>
	 * The corresponding index file is assumed to exist. If this is not true
	 * implementors must extend the class and override
	 * {@link #getFileName(String)}.
	 * <p>
	 * Callers should also try to fill in other fields if they are reasonably
	 * free to access at the time this instance is being initialized.
	 *
	 * @param name
	 *            name of the pack file. Must end with ".pack".
	 * @param repoDesc
	 *            description of the repo containing the pack file.
	 */
	public DfsPackDescription(DfsRepositoryDescription repoDesc, String name) {
		this.repoDesc = repoDesc;
		int dot = name.lastIndexOf('.');
		this.packName = (dot < 0) ? name : name.substring(0, dot);
		this.sizeMap = new HashMap<String, Long>();
	}

	/** @return description of the repository. */
	public DfsRepositoryDescription getRepositoryDescription() {
		return repoDesc;
	}

	/**
	 * @param ext
	 *            the file extension
	 * @return name of the file.
	 * */
	public String getFileName(String ext) {
		return packName + '.' + ext;
	}

	/** @return the source of the pack. */
	public PackSource getPackSource() {
		return packSource;
	}

	/**
	 * @param source
	 *            the source of the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setPackSource(PackSource source) {
		packSource = source;
		return this;
	}

	/** @return time the pack was created, in milliseconds. */
	public long getLastModified() {
		return lastModified;
	}

	/**
	 * @param timeMillis
	 *            time the pack was created, in milliseconds. 0 if not known.
	 * @return {@code this}
	 */
	public DfsPackDescription setLastModified(long timeMillis) {
		lastModified = timeMillis;
		return this;
	}

	/**
	 * @param ext
	 *            the file extension.
	 * @param bytes
	 *            size of the file in bytes. If 0 the file is not known and will
	 *            be determined on first read.
	 * @return {@code this}
	 */
	public DfsPackDescription setFileSize(String ext, long bytes) {
		sizeMap.put(ext, Long.valueOf(Math.max(0, bytes)));
		return this;
	}

	/**
	 * @param ext
	 *            the file extension.
	 * @return size of the file, in bytes. If 0 the file size is not yet known.
	 */
	public long getFileSize(String ext) {
		Long size = sizeMap.get(ext);
		return (size == null) ? 0 : size.longValue();
	}

	/**
	 * @return size of the reverse index, in bytes.
	 */
	public int getReverseIndexSize() {
		return (int) Math.min(objectCount * 8, Integer.MAX_VALUE);
	}

	/** @return number of objects in the pack. */
	public long getObjectCount() {
		return objectCount;
	}

	/**
	 * @param cnt
	 *            number of objects in the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setObjectCount(long cnt) {
		objectCount = Math.max(0, cnt);
		return this;
	}

	/** @return number of delta compressed objects in the pack. */
	public long getDeltaCount() {
		return deltaCount;
	}

	/**
	 * @param cnt
	 *            number of delta compressed objects in the pack.
	 * @return {@code this}
	 */
	public DfsPackDescription setDeltaCount(long cnt) {
		deltaCount = Math.max(0, cnt);
		return this;
	}

	/** @return the tips that created this pack, if known. */
	public Set<ObjectId> getTips() {
		return tips;
	}

	/**
	 * @param tips
	 *            the tips of the pack, null if it has no known tips.
	 * @return {@code this}
	 */
	public DfsPackDescription setTips(Set<ObjectId> tips) {
		this.tips = tips;
		return this;
	}

	/**
	 * @return statistics from PackWriter, if the pack was built with it.
	 *         Generally this is only available for packs created by
	 *         DfsGarbageCollector or DfsPackCompactor, and only when the pack
	 *         is being committed to the repository.
	 */
	public PackWriter.Statistics getPackStats() {
		return stats;
	}

	DfsPackDescription setPackStats(PackWriter.Statistics stats) {
		this.stats = stats;
		return this;
	}

	/**
	 * Discard the pack statistics, if it was populated.
	 *
	 * @return {@code this}
	 */
	public DfsPackDescription clearPackStats() {
		stats = null;
		return this;
	}

	@Override
	public int hashCode() {
		return packName.hashCode();
	}

	@Override
	public boolean equals(Object b) {
		if (b instanceof DfsPackDescription) {
			DfsPackDescription desc = (DfsPackDescription) b;
			return packName.equals(desc.packName) && getRepositoryDescription()
					.equals(desc.getRepositoryDescription());
		}
		return false;
	}

	/**
	 * Sort packs according to the optimal lookup ordering.
	 * <p>
	 * This method tries to position packs in the order readers should examine
	 * them when looking for objects by SHA-1. The default tries to sort packs
	 * with more recent modification dates before older packs, and packs with
	 * fewer objects before packs with more objects.
	 *
	 * @param b
	 *            the other pack.
	 */
	public int compareTo(DfsPackDescription b) {
		// Newer packs should sort first.
		int cmp = Long.signum(b.getLastModified() - getLastModified());
		if (cmp != 0)
			return cmp;

		// Break ties on smaller index. Readers may get lucky and find
		// the object they care about in the smaller index. This also pushes
		// big historical packs to the end of the list, due to more objects.
		return Long.signum(getObjectCount() - b.getObjectCount());
	}

	@Override
	public String toString() {
		return getFileName(PackConstants.PACK_EXT);
	}
}
