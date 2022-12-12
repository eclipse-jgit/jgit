package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.pack.PackExt.COMMIT_GRAPH;

/**
 * A commit graph stored in
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsBlockCache}.
 */
public class DfsCommitGraph extends BlockBasedFile {
	/**
	 * Construct a reader for an existing commit graph.
	 *
	 * @param desc description of the commit graph within the DFS.
	 */
	public DfsCommitGraph(DfsPackDescription desc) {
		this(DfsBlockCache.getInstance(), desc);
	}

	/**
	 * Construct a reader for an existing commit graph.
	 *
	 * @param cache cache that will store the commit graph data.
	 * @param desc  description of the commit grpah within the DFS.
	 */
	public DfsCommitGraph(DfsBlockCache cache, DfsPackDescription desc) {
		super(cache, desc, COMMIT_GRAPH);

		int bs = desc.getBlockSize(COMMIT_GRAPH);
		if (bs > 0) {
			setBlockSize(bs);
		}

		long sz = desc.getFileSize(COMMIT_GRAPH);
		length = sz > 0 ? sz : -1;
	}

	/**
	 * Get description that was originally used to configure this file.
	 *
	 * @return description that was originally used to configure this file.
	 */
	public DfsPackDescription getPackDescription() {
		return desc;
	}

	// TODO given a DfsReader, return a CommitGraphReader using CacheSource
}
