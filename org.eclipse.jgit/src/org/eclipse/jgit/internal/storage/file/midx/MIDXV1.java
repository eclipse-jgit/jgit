package org.eclipse.jgit.internal.storage.file.midx;


public class MIDXV1 implements MIDX {


    private final MIDXIndex idx;

    private byte[] packfileNames;

    private byte[] bitmappedPackfiles;

    private byte[] objectOffsets;

    // Optional
    private byte[] objectLargeOffsets;

    // Optional
    private byte[] bitmapPackOrder;

    MIDXV1(MIDXIndex index, byte[] packfileNames, byte[] bitmappedPackfiles, byte[] objectOffsets) {
        this.idx = index;
    }
}
