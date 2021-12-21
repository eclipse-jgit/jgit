/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

/**
 * Constants relating to commit-graph.
 */
class CommitGraphConstants {

	static final int COMMIT_GRAPH_MAGIC = 0x43475048; /* "CGPH" */

	static final int CHUNK_ID_OID_FANOUT = 0x4f494446; /* "OIDF" */

	static final int CHUNK_ID_OID_LOOKUP = 0x4f49444c; /* "OIDL" */

	static final int CHUNK_ID_COMMIT_DATA = 0x43444154; /* "CDAT" */

	static final int CHUNK_ID_EXTRA_EDGE_LIST = 0x45444745; /* "EDGE" */

	static final int CHUNK_ID_BLOOM_INDEXES = 0x42494458; /* "BIDX" */

	static final int CHUNK_ID_BLOOM_DATA = 0x42444154; /* "BDAT" */

	/**
	 * First 4 bytes describe the chunk id. Value 0 is a terminating label.
	 * Other 8 bytes provide the byte-offset in current file for chunk to start.
	 */
	static final int GRAPH_CHUNK_LOOKUP_WIDTH = 12;

	/**
	 * First 8 bytes are for the positions of the first two parents of the ith
	 * commit. The next 8 bytes store the generation number of the commit and
	 * the commit time in seconds since EPOCH.
	 */
	static final int COMMIT_DATA_EXTRA_LENGTH = 16;

	/** Mask to make the last edgeValue into position */
	static final int GRAPH_EDGE_LAST_MASK = 0x7fffffff;

	/** EdgeValue & GRAPH_LAST_EDGE != 0 means it is the last edgeValue */
	static final int GRAPH_LAST_EDGE = 0x80000000;

	/** EdgeValue == GRAPH_NO_PARENT means it has no parents */
	static final int GRAPH_NO_PARENT = 0x70000000;

	/**
	 * EdgeValue & GRAPH_EXTRA_EDGES_NEEDED != 0 means its other parents are in
	 * Chunk Extra Edge List
	 */
	static final int GRAPH_EXTRA_EDGES_NEEDED = 0x80000000;

	static final int BLOOM_KEY_NUM_HASHES = 7;

	static final int BLOOM_BITS_PER_ENTRY = 10;

	static final int BLOOM_MAX_CHANGED_PATHS = 512;
}
