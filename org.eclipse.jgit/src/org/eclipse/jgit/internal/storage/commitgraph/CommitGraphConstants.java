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

	static final int CHUNK_GENERATION_DATA = 0x47444132; /* "GDA2" */

	static final int CHUNK_GENERATION_DATA_OVERFLOW = 0x47444f32; /* "GDO2" */

	static final int CHUNK_ID_EXTRA_EDGE_LIST = 0x45444745; /* "EDGE" */

	/**
	 * First 4 bytes describe the chunk id. Value 0 is a terminating label.
	 * Other 8 bytes provide the byte-offset in current file for chunk to start.
	 */
	static final int CHUNK_LOOKUP_WIDTH = 12;

	/**
	 * First 8 bytes are for the positions of the first two parents of the ith
	 * commit. The next 8 bytes store the generation number of the commit and
	 * the commit time in seconds since EPOCH.
	 */
	static final int COMMIT_DATA_WIDTH = 16;

	/** Mask to make the last edgeValue into position */
	static final int GRAPH_EDGE_LAST_MASK = 0x7fffffff;

	/** EdgeValue &amp; GRAPH_LAST_EDGE != 0 means it is the last edgeValue */
	static final int GRAPH_LAST_EDGE = 0x80000000;

	/** EdgeValue == GRAPH_NO_PARENT means it has no parents */
	static final int GRAPH_NO_PARENT = 0x70000000;

	/**
	 * EdgeValue &amp; GRAPH_EXTRA_EDGES_NEEDED != 0 means its other parents are
	 * in Chunk Extra Edge List
	 */
	static final int GRAPH_EXTRA_EDGES_NEEDED = 0x80000000;

	/**
	 * The version of commit graph generated
	 */
	static final int COMMIT_GRAPH_VERSION_GENERATED = 1;

	/**
	 * The version of Hash function used in Commit Graph 1 == SHA1, 2 == SHA256
	 */
	static final int OID_HASH_VERSION = 1;

	/**
	 * Fan out table size of Commit Graph Only first two bytes of OID (0x00 -
	 * 0xff) are considered
	 */
	static final int GRAPH_FANOUT_SIZE = 4 * 256;

	/**
	 * Generation Number V1, i.e. Topological order Stored within 30 bits
	 */
	static final int GENERATION_NUMBER_V1_MAX = 0x3FFFFFFF;

	/**
	 * Generation Number V2, i.e. Corrected commit date offset Default stored
	 * within 31 bits
	 */
	static final int GENERATION_DATA_MAX_OFFSET = 0x7FFFFFFF;

	/**
	 * The most significant bit of Generation Number V2 Signaling the present of
	 * overflow
	 */
	static final int GENERATION_DATA_OVERFLOW_BIT = 0x80000000;

	/**
	 * The size of generation data within generation data chunk 4 bytes
	 */
	static final int GENERATION_DATA_WIDTH = 4;

	/**
	 * The size of generation data overflow within generation data overflow
	 * chunk 8 bytes
	 */
	static final int GENERATION_DATA_OVERFLOW_WIDTH = 8;
}
