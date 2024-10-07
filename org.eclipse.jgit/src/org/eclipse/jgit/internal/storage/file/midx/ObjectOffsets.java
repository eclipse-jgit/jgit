/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.util.NB;

import static org.eclipse.jgit.internal.storage.file.midx.MIDXConstants.OBJECT_OFFSETS_DATA_WIDTH;

/**
 * Represent the collection of {@link MIDX.ObjectOffset}.
 */
public class ObjectOffsets {
    private final byte[] data;

    /**
     * Initialize the ObjectOffsets.
     *
     * @param objectOffset content of ObjectOffset Chunk.
     */
    public ObjectOffsets(@NonNull byte[] objectOffset) {
        this.data = objectOffset;
    }

    /**
     * Get the metadata of a commit。
     *
     * @param position the position in the multi-pack-index of the object.
     * @return the ObjectOffset.
     */
    public MIDX.ObjectOffset getObjectOffset(int position) {
        int pos = position * OBJECT_OFFSETS_DATA_WIDTH;
        final int packIntId = NB.decodeInt32(data, pos);
        final int offset = NB.decodeInt32(data, pos + 4);
        return new ObjectOffsetImpl(packIntId, offset);
    }

    private static class ObjectOffsetImpl implements MIDX.ObjectOffset {

        private final int packIntId;
        private final long offset;

        public ObjectOffsetImpl(int packIntId, long offset) {
            this.packIntId = packIntId;
            this.offset = offset;
        }

        @Override
        public int getPackIntId() {
            return packIntId;
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }
}
