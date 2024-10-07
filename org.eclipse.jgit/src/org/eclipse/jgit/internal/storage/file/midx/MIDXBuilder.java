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

import org.eclipse.jgit.internal.JGitText;

import java.text.MessageFormat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.file.midx.MIDXConstants.*;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

/**
 * Builder for {@link MIDX}.
 */
public class MIDXBuilder {

    private final int hashLength;

    private long packFilesCnt;

    private byte[] oidFanout;

    private byte[] oidLookup;

    private String[] packfileNames;

    private byte[] bitmappedPackfiles;

    private byte[] objectOffsets;

    // Optional
    private byte[] objectLargeOffsets;

    // Optional
    private byte[] bitmapPackOrder;


    private MIDXBuilder(int hashLength) {
        this.hashLength = hashLength;
    }

    /**
     * Create builder
     *
     * @return A builder of {@link MIDX}.
     */
    static MIDXBuilder builder() {
        return new MIDXBuilder(OBJECT_ID_LENGTH);
    }

    MIDXBuilder packfilesCnt(long packFilesCnt) {
        this.packFilesCnt = packFilesCnt;
        return this;
    }

    MIDXBuilder addOidFanout(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(oidFanout, MIDX_ID_OID_FANOUT);
        oidFanout = buffer;
        return this;
    }

    MIDXBuilder addOidLookUp(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(oidLookup, MIDX_ID_OID_LOOKUP);
        oidLookup = buffer;
        return this;
    }

    MIDXBuilder addPackFileNames(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(packfileNames, MIDX_PACKFILE_NAMES);
        packfileNames = new String(buffer, UTF_8).split("\u0000");
        return this;
    }

    MIDXBuilder addBitmappedPackfiles(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(bitmappedPackfiles, MIDX_BITMAPPED_PACKFILES);
        bitmappedPackfiles = buffer;
        return this;
    }

    MIDXBuilder addObjectOffsets(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(objectOffsets, MIDX_OBJECT_OFFSETS);
        objectOffsets = buffer;
        return this;
    }

    MIDXBuilder addObjectLargeOffsets(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(objectLargeOffsets, MIDX_OBJECT_LARGE_OFFSETS);
        objectLargeOffsets = buffer;
        return this;
    }

    MIDXBuilder addBitmapPackOrder(byte[] buffer)
            throws MIDXFormatException {
        assertChunkNotSeenYet(bitmapPackOrder, MIDX_BITMAP_PACK_ORDER);
        bitmapPackOrder = buffer;
        return this;
    }

    MIDX build() throws MIDXFormatException {
        assertChunkNotNull(oidFanout, MIDX_ID_OID_FANOUT);
        assertChunkNotNull(oidLookup, MIDX_ID_OID_LOOKUP);
        assertChunkNotNull(packfileNames, MIDX_PACKFILE_NAMES);
        assertChunkNotNull(objectOffsets, MIDX_OBJECT_OFFSETS);

        MIDXIndex index = new MIDXIndex(hashLength, oidFanout,
                oidLookup, packFilesCnt);

        return new MIDXV1(index, packfileNames, bitmappedPackfiles, new ObjectOffsets(hashLength, objectOffsets));
    }

    private void assertChunkNotNull(Object object, int chunkId)
            throws MIDXFormatException {
        if (object == null) {
            throw new MIDXFormatException(
                    MessageFormat.format(JGitText.get().midxChunkNeeded,
                            Integer.toHexString(chunkId)));
        }
    }

    private void assertChunkNotSeenYet(Object object, int chunkId)
            throws MIDXFormatException {
        if (object != null) {
            throw new MIDXFormatException(MessageFormat.format(
                    JGitText.get().midxChunkRepeated,
                    Integer.toHexString(chunkId)));
        }
    }
}
