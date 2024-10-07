package org.eclipse.jgit.internal.storage.file.midx;

/**
 * Support for the MIDX v1 format.
 *
 * @see MIDX
 */
public class MIDXV1 implements MIDX {


    private final MIDXIndex idx;

    private byte[] packfileNames;

    private byte[] bitmappedPackfiles;

    private byte[] objectOffsets;

    // Optional
    private byte[] objectLargeOffsets;

    MIDXV1(MIDXIndex index, byte[] packfileNames, byte[] bitmappedPackfiles, byte[] objectOffsets) {
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
                ", packfileNames=" + byteArrayToString(packfileNames) +
                ", bitmappedPackfiles=" + byteArrayToString(bitmappedPackfiles) +
                ", objectOffsets=" + byteArrayToString(objectOffsets) +
                ", objectLargeOffsets=" + byteArrayToString(objectLargeOffsets) +
                '}';
    }

    private String byteArrayToString(byte[] array) {
        return array == null ? "null" : new String(array);
    }

}
