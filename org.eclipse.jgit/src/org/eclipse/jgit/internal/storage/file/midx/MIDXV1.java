package org.eclipse.jgit.internal.storage.file.midx;


import org.eclipse.jgit.lib.AnyObjectId;

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

    private final ObjectOffsets objectOffsets;


    MIDXV1(MIDXIndex index, String[] packfileNames, byte[] bitmappedPackfiles, ObjectOffsets objectOffsets) {
        this.bitmappedPackfiles = bitmappedPackfiles;
        this.idx = index;
        this.objectOffsets = objectOffsets;
        this.packfileNames = packfileNames;
    }

    @Override
    public String toString() {
        return "MIDXV1 {" +
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
    public ObjectOffset getObjectOffset(AnyObjectId objectId) {
        int position = idx.findMIDXPosition(objectId);
        return objectOffsets.getObjectOffset(position);
    }
}
