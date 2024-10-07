package org.eclipse.jgit.internal.storage.file.midx;

import java.util.Arrays;

/**
 * Support for the MIDX v1 format.
 *
 * @see MIDX
 */
public class MIDXV1 implements MIDX {


    private final MIDXIndex idx;

    private final String[] packfileNames;

    private final byte[] bitmappedPackfiles;

    private final byte[] objectOffsets;

    // Optional
    private final byte[] objectLargeOffsets;

    MIDXV1(MIDXIndex index, String[] packfileNames, byte[] bitmappedPackfiles, byte[] objectOffsets) {
        this.bitmappedPackfiles = bitmappedPackfiles;
        this.idx = index;
        this.objectLargeOffsets = objectOffsets;
        this.objectOffsets = objectOffsets;
        this.packfileNames = packfileNames;
    }

    @Override
    public String toString() {
        return "MIDXV1 {" +
                "idx=" + idx +
                ", packfileNames=" + Arrays.toString(packfileNames) +
                ", bitmappedPackfiles=" + byteArrayToString(bitmappedPackfiles) +
                ", objectOffsets=" + byteArrayToString(objectOffsets) +
                ", objectLargeOffsets=" + byteArrayToString(objectLargeOffsets) +
                '}';
    }

    private String byteArrayToString(byte[] array) {
        return array == null ? "null" : new String(array);
    }

    @Override
    public String[] getPackfileNames() {
        return packfileNames;
    }

    @Override
    public long getPackfilesCnt() {
        return idx.getPackFilesCnt();
    }
}
