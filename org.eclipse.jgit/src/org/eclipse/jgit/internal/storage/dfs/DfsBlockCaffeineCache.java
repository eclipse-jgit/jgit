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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This implementation of DfsBlockCache uses a CaffeineCache object to store objects that a {@link DfsPackFile} may
 * want to cache, which includes indices and blocks. We also maintained two other maps to cache
 * DfsPackDescription -> DfsPackFile and DfsPackKey -> DfsPackDescription for performance and memory management.
 * The retained size of these three objects is constrained by the CacheMaximumSize in the configuration.
 * <p>
 * When a packFile is first created, we put it into {@code packFileMap} and
 * the reverse index {@code reversePackDescriptionIndex}. The packKey is put into {@code packKeysWithNoCacheEntries}.
 * A placeholder cache entry is added to the caffeine cache.
 * When a packFile tries to load an object through the cache, we either get it from the cache or load it.
 * The concurrency correctness is guaranteed by Caffeine Cache's atomic read/write.
 * We also increase the cachedSize for that packFile.
 * When a packFile tries to put a cache entry, we add it to the cache if there is nothing for that key and position.
 * If it's a block object, we increase the cachedSize for that packFile.
 * The concurrency correctness is guaranteed by Caffeine Cache's atomic read/write.
 * <p>
 * When a cache object gets evicted from the cache:
 * (1) If it's a block object, we decrease the cachedSize for that packFile. When it becomes zero,
 * we cleanup the entries for the file in {@code packFileMap} and {@code reversePackDescriptionIndex}.
 * (2) If it's an index object, we cleanup the entries for the file in {@code packFileMap}
 * and {@code reversePackDescriptionIndex}.
 * In these two cases, it is possible for the Cache to still contain entries related to the packFile. But since the
 * packFile is not in {@code packFileMap} anymore, for the same packDescription a new packFile will always be created.
 * Other objects will not try to access cache entries with the old packKey and they will be evicted when the
 * maximum size of the cache is reached.
 * (3) If it's a placeholder object, we check whether it's still in {@code packKeysWithNoCacheEntries}. If so, no other
 * cache entry for this packKey has been added and it will be clean-uped from {@code packFileMap}
 * and {@code reversePackDescriptionIndex}; otherwise nothing happens. This is to eliminate the edge case where
 * a lot of packFiles are created but no cache entries are added, so Caffeine cache never triggers its removal listener
 * to clear {@code packFileMap} and {@code reversePackDescriptionIndex}.
 * The concurrency correctness of the removal listener is based on the atomic property of caffeine cache.
 */
public class DfsBlockCaffeineCache extends DfsBlockCache {

    private static final int EMPTY_STRING_SIZE = 8;

    /**
     * The estimated retained bytes is estimated based on the fields each object contains
     * plus the key/value reference itself.
     *
     * DfsPackKey: {
     *   int hash;                4 bytes
     *   AtomicLong cachedSize;   28 bytes
     * }
     * long position;             8 bytes
     * int size;                  4 bytes
     * key, value reference       8 * 2 bytes
     */
    private static final int ESTIMATED_EMPTY_ENTRY_SIZE = 60;

    /**
     * The actual retained size of an index file is much larger than the size
     * set in {@link DfsPackFile}'s put method (object_count * 28).
     *
     * The estimated retained size for an PackIndexV2 object is:
     * PackIndexV2 {
     *  long IS_O64;         8 bytes
     *  int FANOUT;          4 bytes
     *  int[] NO_INTS;       4 bytes
     *  byte[] NO_BYTES;     4 bytes
     *  long objectCnt;      8 bytes
     *  long[] fanoutTable;  2048 bytes
     *  int[][] names;       object_count * 160 bytes
     *  byte[][] offset32;   object_count * 160 bytes
     *  byte[][] crc32;      object_count * 160 bytes
     *  byte[] offset64;     object_count * 16 bytes
     * }
     *
     * For each index, we can roughly get the retained size from the size given by DfsPackFile by:
     * retained_size = 18 * ref.size + 2076
     */
    private static final int ESTIMATED_INDEX_SIZE_MULTIPLIER = 18;
    private static final int ESTIMATED_INDEX_SIZE_EXTRA_BYTES = 2076;

    /**
     * The position used for placeholder entries that uses to map sure {@code packFileMap} gets cleared eventually
     * even if no other cache entry has been put into the cache.
     */
    private static final int PLACEHOLDER_POSITION = -100;

    /** Pack files smaller than this size can be copied through the cache. */
    private final long maxStreamThroughCache;

    /**
     * Suggested block size to read from pack files in bytes.
     * <p>
     * If a pack file does not have a native block size, this size will be used.
     * <p>
     * If a pack file has a native size, a whole multiple of the native size
     * will be used until it matches this size.
     * <p>
     * The value for blockSize must be a power of 2 and no less than 512.
     */
    private final int blockSize;

    /** Map of pack files, indexed by description. */
    private final Map<DfsPackDescription, DfsPackFile> packFileMap;

    /**
     * A set of packKeys that exists in {@code packFileMap} but does not have an entry (besides the placeholder)
     * put in the cache.
     */
    private final Set<DfsPackKey> packKeysWithNoCacheEntries;

    /** Reverse index from DfsPackKey to the DfsPackDescription. */
    private final Map<DfsPackKey, DfsPackDescription> reversePackDescriptionIndex;

    /** Cache of Dfs blocks and Indices. */
    private final Cache<DfsPackKeyWithPosition, Ref> dfsBlockAndIndicesCache;

    public static void reconfigure(final DfsBlockCaffeineCacheConfig cacheConfig) {
        DfsBlockCache.setInstance(new DfsBlockCaffeineCache(cacheConfig));
    }

    private DfsBlockCaffeineCache(DfsBlockCaffeineCacheConfig cacheConfig) {
        maxStreamThroughCache = (long) (cacheConfig.getCacheMaximumSize() * cacheConfig.getStreamRatio());

        blockSize = cacheConfig.getBlockSize();

        packFileMap = new ConcurrentHashMap<>(16, 0.75f, 1);
        packKeysWithNoCacheEntries = new HashSet<>(16, 0.75f);
        reversePackDescriptionIndex = new ConcurrentHashMap<>(16, 0.75f, 1);

        /**
         * The extra retained size by packFileMap and reversePackDescriptionIndex is less than
         * 1KB * number of pack files + 128 bytes * number of objects, which is less than half of the estimated caffeine
         * cache size. So here we set the weigher to be 2/3 of CacheMaximumSize to make sure the total memory used
         * does not exceed this limit.
         */
        long maximumSizeForCaffeinceCache = (cacheConfig.getCacheMaximumSize() / 3) * 2;
        dfsBlockAndIndicesCache = Caffeine.newBuilder()
                .removalListener(this::cleanUpIndices)
                .maximumWeight(maximumSizeForCaffeinceCache)
                .weigher((DfsPackKeyWithPosition keyWithPosition, Ref ref) ->
                        ref == null ? ESTIMATED_EMPTY_ENTRY_SIZE : ESTIMATED_EMPTY_ENTRY_SIZE + ref.getRetainedSize())
                .build();
    }

    private void cleanUpIndices(DfsPackKeyWithPosition keyWithPosition, Ref ref, RemovalCause cause) {
        if (keyWithPosition != null && ref != null) {
            DfsPackKey key = keyWithPosition.getDfsPackKey();
            long position = keyWithPosition.getPosition();

            if (position == PLACEHOLDER_POSITION) {
                // if it's a placeholder and there is no other entries put into the cache for the packFile
                // remove the packFile from our index maps
                if (key != null && packKeysWithNoCacheEntries.contains(key)) {
                    cleanUpIndicesIfExists(key);
                }
            } else if (position < 0) {
                // if it's an index, remove the packFile from our index maps
                cleanUpIndicesIfExists(key);
            } else {
                // if it's not an index, decrease the cached size
                // remove the whole packFile if cachedSize is below 0
                if (key.cachedSize().addAndGet(-ref.getSize()) <= 0) {
                    cleanUpIndicesIfExists(key);
                }
            }
        }
    }

    private void cleanUpIndicesIfExists(DfsPackKey key) {
        if (key != null) {
            packKeysWithNoCacheEntries.remove(key);
            DfsPackDescription description = reversePackDescriptionIndex.remove(key);
            if (description != null) {
                packFileMap.remove(description);
            }
        }
    }

    public boolean shouldCopyThroughCache(long length) {
        return length <= maxStreamThroughCache;
    }

    public DfsPackFile getOrCreate(DfsPackDescription description, DfsPackKey key) {
        return packFileMap.compute(description, (DfsPackDescription desc, DfsPackFile packFile) -> {
            if (packFile != null && !packFile.invalid()) {
                return packFile;
            }
            DfsPackKey newPackKey = key != null ? key : new DfsPackKey();
            reversePackDescriptionIndex.put(newPackKey, desc);
            packKeysWithNoCacheEntries.add(newPackKey);
            dfsBlockAndIndicesCache.put(
                    new DfsPackKeyWithPosition(key, PLACEHOLDER_POSITION),
                    new Ref(key, PLACEHOLDER_POSITION, EMPTY_STRING_SIZE, ""));
            return new DfsPackFile(this, desc, newPackKey);
        });
    }

    public int getBlockSize() {
        return blockSize;
    }

    public DfsBlock getOrLoad(DfsPackFile pack, long position, DfsReader dfsReader) throws IOException {
        long alignedPosition = pack.alignToBlock(position);
        DfsPackKey key = pack.key();

        if (key == null) {
            return null;
        }

        Ref<DfsBlock> loadedBlockRef = dfsBlockAndIndicesCache.get(
                new DfsPackKeyWithPosition(key, alignedPosition), keyWithPosition -> {
                    packKeysWithNoCacheEntries.remove(key);
                    try {
                        DfsBlock loadedBlock = pack.readOneBlock(keyWithPosition.getPosition(), dfsReader);
                        key.cachedSize().addAndGet(loadedBlock.size());
                        return new Ref(keyWithPosition.getDfsPackKey(), keyWithPosition.getPosition(),
                                loadedBlock.size(), loadedBlock);
                    } catch (IOException e) {
                        return null;
                    }
                });

        if (loadedBlockRef != null) {
            DfsBlock loadedBlock = loadedBlockRef.get();
            if (loadedBlock != null && loadedBlock.contains(key, alignedPosition)) {
                return loadedBlock;
            }
        }

        // the current block is not valid, remove it and attempt `getOrLoad` again
        dfsBlockAndIndicesCache.invalidate(new DfsPackKeyWithPosition(key, alignedPosition));
        return getOrLoad(pack, position, dfsReader);
    }

    public void put(DfsBlock block) {
        put(block.packKey(), block.getStart(), block.size(), block);
    }

    public <T> Ref<T> put(DfsPackKey key, long position, int size, T value) {
        return dfsBlockAndIndicesCache.get(new DfsPackKeyWithPosition(key, position), keyWithPosition -> {
            packKeysWithNoCacheEntries.remove(key);
            // if it's not an index, increase the cached size
            if (keyWithPosition.position >= 0) {
                keyWithPosition.getDfsPackKey().cachedSize().getAndAdd(size);
            }
            return new Ref(keyWithPosition.getDfsPackKey(), keyWithPosition.getPosition(), size, value);
        });
    }

    public boolean contains(DfsPackKey key, long position) {
        return get(key, position) != null;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DfsPackKey key, long position) {
        Ref<T> blockCache = dfsBlockAndIndicesCache.getIfPresent(new DfsPackKeyWithPosition(key, position));
        return blockCache != null ? blockCache.get() : null;
    }

    public void remove(DfsPackFile pack) {
        if (pack != null) {
            DfsPackKey key = pack.key();
            key.cachedSize().set(0);
            cleanUpIndicesIfExists(key);
        }
    }

    public void cleanUp() {
        packFileMap.clear();
        packKeysWithNoCacheEntries.clear();
        reversePackDescriptionIndex.clear();
        dfsBlockAndIndicesCache.invalidateAll();
    }

    private static final class DfsPackKeyWithPosition {
        private final DfsPackKey dfsPackKey;
        private final long position;

        DfsPackKeyWithPosition(DfsPackKey dfsPackKey, long position) {
            this.dfsPackKey = dfsPackKey;
            this.position = position;
        }

        public DfsPackKey getDfsPackKey() {
            return dfsPackKey;
        }

        public long getPosition() {
            return position;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DfsPackKeyWithPosition)) {
                return false;
            }
            DfsPackKeyWithPosition that = (DfsPackKeyWithPosition) other;
            return Objects.equals(this.getDfsPackKey(), that.getDfsPackKey())
                    && this.getPosition() == that.getPosition();
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getDfsPackKey(), this.getPosition());
        }

    }

    public static final class Ref<T> extends DfsBlockCache.Ref<T> {
        private final DfsPackKey key;
        private final long position;
        private final int size;
        private volatile T value;

        public Ref(DfsPackKey key, long position, int size, T value) {
            this.key = key;
            this.position = position;
            this.size = size;
            this.value = value;
        }

        public T get() {
            return value;
        }

        public boolean has() {
            return value != null;
        }

        public int getSize() {
            return size;
        }

        public int getRetainedSize() {
            // if the ref is an index
            if (position < 0 && position != PLACEHOLDER_POSITION) {
                return ESTIMATED_INDEX_SIZE_MULTIPLIER * size + ESTIMATED_INDEX_SIZE_EXTRA_BYTES;
            }
            return size;
        }
    }
}
