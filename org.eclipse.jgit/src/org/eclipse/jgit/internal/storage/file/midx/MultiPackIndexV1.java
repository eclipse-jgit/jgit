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


import org.eclipse.jgit.lib.AnyObjectId;

import java.util.Arrays;

/**
 * Support for the MultiPackIndex v1 format.
 *
 * @see MultiPackIndex
 */
public class MultiPackIndexV1 implements MultiPackIndex {


    private final MultiPackIndexIndex idx;

    private final String[] packfileNames;

    private final byte[] bitmappedPackfiles;

    private final ObjectOffsets objectOffsets;


    MultiPackIndexV1(MultiPackIndexIndex index, String[] packfileNames, byte[] bitmappedPackfiles, ObjectOffsets objectOffsets) {
        this.bitmappedPackfiles = bitmappedPackfiles;
        this.idx = index;
        this.objectOffsets = objectOffsets;
        this.packfileNames = packfileNames;
    }

    @Override
    public String toString() {
        return "MultiPackIndexV1 {" +
                "idx=" + idx +
                ", packfileNames=" + Arrays.toString(packfileNames) +
                ", bitmappedPackfiles=" + byteArrayToString(bitmappedPackfiles) +
                ", objectOffsets=" + objectOffsets +
                '}';
    }

    private String byteArrayToString(byte[] array) {
        return array == null ? "null" : new String(array);
    }

    @Override
    public String[] getPackFileNames() {
        return packfileNames;
    }

    @Override
    public long getPackFilesCount() {
        return idx.getPackFilesCnt();
    }

    @Override
    public String getPackFileName(int packIntId) {
        return getPackFileNames()[packIntId];
    }

    @Override
    public ObjectOffset getObjectOffset(AnyObjectId objectId) {
        int position = idx.findMultiPackIndexPosition(objectId);
        return objectOffsets.getObjectOffset(position);
    }
}
