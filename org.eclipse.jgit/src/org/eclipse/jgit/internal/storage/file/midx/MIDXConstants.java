/*
 * Copyright (C) 2024, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

/**
 * Constants relating to MIDX.
 */
public class MIDXConstants {

    static final int MIDX_MAGIC = 0x4D494458; /* "MIDX" */

    static final int MIDX_ID_OID_FANOUT = 0x4f494446; /* "OIDF" */

    static final int MIDX_PACKFILE_NAMES = 0x504E414D; /* "PNAM" */

    static final int MIDX_BITMAPPED_PACKFILES = 0x42544D50; /* "BTMP" */

    static final int MIDX_ID_OID_LOOKUP = 0x4f49444c; /* "OIDL" */

    static final int MIDX_OBJECT_OFFSETS = 0x4F4F4646; /* "OOFF" */

    // Optional
    static final int MIDX_OBJECT_LARGE_OFFSETS = 0x4C4F4646; /* "LOFF" */

    // Optional
    static final int MIDX_BITMAP_PACK_ORDER = 0x52494458; /* "RIDX" */

    /**
     * First 4 bytes describe the chunk id. Value 0 is a terminating label.
     * Other 8 bytes provide the byte-offset in current file for chunk to start.
     */
    static final int CHUNK_LOOKUP_WIDTH = 12;

    /**
     * Stores a table of two 4-byte unsigned integers in network order.
     */
    static final int BITMAP_PACKFILES_DATA_WIDTH = 8;

    /**
     * Stores two 4-byte values for every object.
     */
    static final int OBJECT_OFFSETS_DATA_WIDTH = 8;
}
