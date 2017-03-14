/*
 * Copyright (C) 2017, Palantir Technologies Inc.
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

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;

import java.text.MessageFormat;

import static org.eclipse.jgit.lib.ConfigConstants.*;

/** Configuration parameters for {@link DefaultDfsBlockCache}. */
public class DfsBlockCaffeineCacheConfig {
    /** 1024 (number of bytes in one kibibyte/kilobyte) */
    private static final int KB = 1024;

    /** 1024 {@link #KB} (number of bytes in one mebibyte/megabyte) */
    private static final int MB = 1024 * KB;

    private long cacheMaximumSize;
    private int blockSize;
    private double streamRatio;

    /** Create a default configuration. */
    public DfsBlockCaffeineCacheConfig() {
        setCacheMaximumSize(32 * MB);
        setBlockSize(64 * KB);
        setStreamRatio(0.30);
    }

    /**
     * @return maximum number bytes of heap memory to dedicate to caching pack
     *         file data. <b>Default is 32 MB.</b>
     */
    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    /**
     * @param newCacheMaximumSize
     *            maximum number bytes of heap memory to dedicate to caching
     *            pack file data.
     * @return {@code this}
     */
    public DfsBlockCaffeineCacheConfig setCacheMaximumSize(final long newCacheMaximumSize) {
        cacheMaximumSize = cacheMaximumSize;
        return this;
    }

    /**
     * @return size in bytes of a single window mapped or read in from the pack
     *         file. <b>Default is 64 KB.</b>
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * @param newSize
     *            size in bytes of a single window read in from the pack file.
     *            The value must be a power of 2.
     * @return {@code this}
     */
    public DfsBlockCaffeineCacheConfig setBlockSize(final int newSize) {
        int size = Math.max(512, newSize);
        if ((size & (size - 1)) != 0) {
            throw new IllegalArgumentException(
                    JGitText.get().blockSizeNotPowerOf2);
        }
        blockSize = size;
        return this;
    }

    /**
     * @return highest percentage of {@link #getCacheMaximumSize()} a single pack can
     *         occupy while being copied by the pack reuse strategy. <b>Default
     *         is 0.30, or 30%</b>.
     */
    public double getStreamRatio() {
        return streamRatio;
    }

    /**
     * @param ratio
     *            percentage of cache to occupy with a copied pack.
     * @return {@code this}
     */
    public DfsBlockCaffeineCacheConfig setStreamRatio(double ratio) {
        streamRatio = Math.max(0, Math.min(ratio, 1.0));
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
    public DfsBlockCaffeineCacheConfig fromConfig(final Config rc) {
        setCacheMaximumSize(rc.getLong(
                CONFIG_CORE_SECTION,
                CONFIG_DFS_SECTION,
                CONFIG_KEY_BLOCK_LIMIT,
                getCacheMaximumSize()));

        setBlockSize(rc.getInt(
                CONFIG_CORE_SECTION,
                CONFIG_DFS_SECTION,
                CONFIG_KEY_BLOCK_SIZE,
                getBlockSize()));

        String v = rc.getString(
                CONFIG_CORE_SECTION,
                CONFIG_DFS_SECTION,
                CONFIG_KEY_STREAM_RATIO);
        if (v != null) {
            try {
                setStreamRatio(Double.parseDouble(v));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(MessageFormat.format(
                        JGitText.get().enumValueNotSupported3,
                        CONFIG_CORE_SECTION,
                        CONFIG_DFS_SECTION,
                        CONFIG_KEY_STREAM_RATIO, v));
            }
        }
        return this;
    }

}
