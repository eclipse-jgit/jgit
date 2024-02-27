package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;

public interface DfsBlockCacheTable {
	/**
	 * Quickly check if the cache contains block 0 of the given stream.
	 * <p>
	 * This can be useful for sophisticated pre-read algorithms to quickly
	 * determine if a file is likely already in cache, especially small
	 * reftables which may be smaller than a typical DFS block size.
	 *
	 * @param key
	 *            the file to check.
	 * @return true if block 0 (the first block) is in the cache.
	 */
	boolean hasBlock0(DfsStreamKey key);

	/**
	 * Look up a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param file
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @param dfsReader
	 *            current thread's reader.
	 * @param fileChannel
	 *            supplier for channel to read {@code pack}.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	DfsBlock getOrLoad(BlockBasedFile file, long position, DfsReader dfsReader,
			DfsBlockCache.ReadableChannelSupplier fileChannel)
			throws IOException;

	/**
	 * Look up a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param position
	 *            the position in the key. The default should be 0.
	 * @param loader
	 *            the function to load the reference.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	<T> DfsBlockCache.Ref<T> getOrLoadRef(DfsStreamKey key, long position,
			DfsBlockCache.RefLoader<T> loader) throws IOException;

	void put(DfsBlock v);

	<T> DfsBlockCache.Ref<T> put(DfsStreamKey key, long pos, long size, T v);

	<T> DfsBlockCache.Ref<T> putRef(DfsStreamKey key, long size, T v);

	boolean contains(DfsStreamKey key, long position);

	<T> T get(DfsStreamKey key, long position);

	DfsBlockCacheStats getDfsBlockCacheStats();
}