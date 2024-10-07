/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
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

public class ObjectOffsets {
    private final byte[] data;
    private final int hashLength;

    public ObjectOffsets(int hashLength, @NonNull byte[] objectOffset) {
        this.hashLength = hashLength;
        this.data = objectOffset;
    }

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
