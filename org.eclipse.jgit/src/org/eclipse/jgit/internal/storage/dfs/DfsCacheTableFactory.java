package org.eclipse.jgit.internal.storage.dfs;

@FunctionalInterface
interface DfsCacheTableFactory {
	DfsBlockCacheTable create(DfsBlockCacheConfig config);
}
