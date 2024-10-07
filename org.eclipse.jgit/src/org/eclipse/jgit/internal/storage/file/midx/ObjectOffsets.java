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

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.util.NB;

/**
 * Represent the collection of {@link MIDX.ObjectOffset}.
 */
public class ObjectOffsets {
    private final byte[] data;
    private final int hashLength;

    /**
     * Initialize the ObjectOffsets.
     *
     * @param hashLength   length of object hash.
     * @param objectOffset content of ObjectOffset Chunk.
     */
    public ObjectOffsets(int hashLength, @NonNull byte[] objectOffset) {
        this.hashLength = hashLength;
        this.data = objectOffset;
    }

    /**
     * Get the metadata of a commit。
     *
     * @param position the position in the multi-pack-index of the object.
     * @return the ObjectOffset.
     */
    public MIDX.ObjectOffset getObjectOffset(int position) {
        //TODO check this one
        int pos = hashLength * position;

        final int packIntId = NB.decodeInt32(data, pos);
        final int offset = NB.decodeInt32(data, pos + 4);
        return new ObjectOffsetImpl(Integer.toString(packIntId), offset);
    }

    private static class ObjectOffsetImpl implements MIDX.ObjectOffset {

        private final String packName;
        private final long offset;

        public ObjectOffsetImpl(String packName, long offset) {
            this.packName = packName;
            this.offset = offset;
        }

        @Override
        public String getPackName() {
            return packName;
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }
}
