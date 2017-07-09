/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_BLOCK_SIZE;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/** Configuration used by a reftable writer when constructing the stream. */
public class ReftableConfig {
	private int refBlockSize = 4 << 10;
	private int logBlockSize;
	private int restartInterval;
	private int maxIndexLevels;
	private boolean alignBlocks = true;
	private boolean indexObjects = true;

	/** Create a default configuration. */
	public ReftableConfig() {
	}

	/**
	 * Create a configuration honoring the repository's settings.
	 *
	 * @param db
	 *            the repository to read settings from. The repository is not
	 *            retained by the new configuration, instead its settings are
	 *            copied during the constructor.
	 */
	public ReftableConfig(Repository db) {
		fromConfig(db.getConfig());
	}

	/**
	 * Create a configuration honoring settings in a {@link Config}.
	 *
	 * @param cfg
	 *            the source to read settings from. The source is not retained
	 *            by the new configuration, instead its settings are copied
	 *            during the constructor.
	 */
	public ReftableConfig(Config cfg) {
		fromConfig(cfg);
	}

	/**
	 * Copy an existing configuration to a new instance.
	 *
	 * @param cfg
	 *            the source configuration to copy from.
	 */
	public ReftableConfig(ReftableConfig cfg) {
		this.refBlockSize = cfg.refBlockSize;
		this.logBlockSize = cfg.logBlockSize;
		this.restartInterval = cfg.restartInterval;
		this.maxIndexLevels = cfg.maxIndexLevels;
		this.alignBlocks = cfg.alignBlocks;
		this.indexObjects = cfg.indexObjects;
	}

	/** @return desired output block size for references, in bytes */
	public int getRefBlockSize() {
		return refBlockSize;
	}

	/**
	 * @param szBytes
	 *            desired output block size for references, in bytes.
	 */
	public void setRefBlockSize(int szBytes) {
		if (szBytes > MAX_BLOCK_SIZE) {
			throw new IllegalArgumentException();
		}
		refBlockSize = Math.max(0, szBytes);
	}

	/**
	 * @return desired output block size for log entries, in bytes. If 0 the
	 *         writer will default to {@code 2 * getRefBlockSize()}.
	 */
	public int getLogBlockSize() {
		return logBlockSize;
	}

	/**
	 * @param szBytes
	 *            desired output block size for log entries, in bytes. If 0 will
	 *            default to {@code 2 * getRefBlockSize()}.
	 */
	public void setLogBlockSize(int szBytes) {
		if (szBytes > MAX_BLOCK_SIZE) {
			throw new IllegalArgumentException();
		}
		logBlockSize = Math.max(0, szBytes);
	}

	/** @return number of references between binary search markers. */
	public int getRestartInterval() {
		return restartInterval;
	}

	/**
	 * @param interval
	 *            number of references between binary search markers. If
	 *            {@code interval} is 0 (default), the writer will select a
	 *            default value based on the block size.
	 */
	public void setRestartInterval(int interval) {
		restartInterval = Math.max(0, interval);
	}

	/** @return maximum depth of the index; 0 for unlimited. */
	public int getMaxIndexLevels() {
		return maxIndexLevels;
	}

	/**
	 * @param levels
	 *            maximum number of levels to use in indexes. Lower levels of
	 *            the index respect {@link #getRefBlockSize()}, and the highest
	 *            level may exceed that if the number of levels is limited.
	 */
	public void setMaxIndexLevels(int levels) {
		maxIndexLevels = Math.max(0, levels);
	}

	/** @return {@code true} if the writer should align blocks. */
	public boolean isAlignBlocks() {
		return alignBlocks;
	}

	/**
	 * @param align
	 *            if {@code true} blocks are written aligned to multiples of
	 *            {@link #getRefBlockSize()}. May increase file size due to NUL
	 *            padding bytes added between blocks. Default is {@code true}.
	 */
	public void setAlignBlocks(boolean align) {
		alignBlocks = align;
	}

	/** @return {@code true} if the writer should index object to ref. */
	public boolean isIndexObjects() {
		return indexObjects;
	}

	/**
	 * @param index
	 *            if {@code true} the reftable may include additional storage to
	 *            efficiently map from {@code ObjectId} to reference names. By
	 *            default, {@code true}.
	 */
	public void setIndexObjects(boolean index) {
		indexObjects = index;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 *
	 * If a property's corresponding variable is not defined in the supplied
	 * configuration, then it is left unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 */
	public void fromConfig(Config rc) {
		refBlockSize = rc.getInt("reftable", "blockSize", refBlockSize); //$NON-NLS-1$ //$NON-NLS-2$
		logBlockSize = rc.getInt("reftable", "logBlockSize", logBlockSize); //$NON-NLS-1$ //$NON-NLS-2$
		restartInterval = rc.getInt("reftable", "restartInterval", restartInterval); //$NON-NLS-1$ //$NON-NLS-2$
		maxIndexLevels = rc.getInt("reftable", "indexLevels", maxIndexLevels); //$NON-NLS-1$ //$NON-NLS-2$
		alignBlocks = rc.getBoolean("reftable", "alignBlocks", alignBlocks); //$NON-NLS-1$ //$NON-NLS-2$
		indexObjects = rc.getBoolean("reftable", "indexObjects", indexObjects); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
