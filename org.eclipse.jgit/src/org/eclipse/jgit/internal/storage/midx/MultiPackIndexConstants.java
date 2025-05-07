/*
 * Copyright (C) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

class MultiPackIndexConstants {
	static final int MIDX_SIGNATURE = 0x4d494458; /* MIDX */

	static final byte MIDX_VERSION = 1;

	/**
	 * We infer the length of object IDs (OIDs) from this value:
	 * 
	 * <pre>
	 * 1 => SHA-1
	 * 2 => SHA-256
	 * </pre>
	 */
	static final byte OID_HASH_VERSION = 1;

	static final int MULTIPACK_INDEX_FANOUT_SIZE = 4 * 256;

	/**
	 * First 4 bytes describe the chunk id. Value 0 is a terminating label.
	 * Other 8 bytes provide the byte-offset in current file for chunk to start.
	 */
	static final int CHUNK_LOOKUP_WIDTH = 12;

	/** "PNAM" chunk */
	static final int MIDX_CHUNKID_PACKNAMES = 0x504e414d;

	/** "OIDF" chunk */
	static final int MIDX_CHUNKID_OIDFANOUT = 0x4f494446;

	/** "OIDL" chunk */
	static final int MIDX_CHUNKID_OIDLOOKUP = 0x4f49444c;

	/** "OOFF" chunk */
	static final int MIDX_CHUNKID_OBJECTOFFSETS = 0x4f4f4646;

	/** "LOFF" chunk */
	static final int MIDX_CHUNKID_LARGEOFFSETS = 0x4c4f4646;

	/** "RIDX" chunk */
	static final int MIDX_CHUNKID_REVINDEX = 0x52494458;

	/** "BTMP" chunk */
	static final int MIDX_CHUNKID_BITMAPPEDPACKS = 0x42544D50;

	private MultiPackIndexConstants() {
	}
}
