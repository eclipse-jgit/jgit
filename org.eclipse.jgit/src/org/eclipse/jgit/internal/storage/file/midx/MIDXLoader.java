package org.eclipse.jgit.internal.storage.file.midx;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jgit.internal.storage.file.midx.MIDXConstants.*;

public class MIDXLoader {
    private final static Logger LOG = LoggerFactory
            .getLogger(MIDXLoader.class);

    public static MIDX read(InputStream fd)
            throws MIDXFormatException, IOException {
        byte[] hdr = new byte[12];
        IO.readFully(fd, hdr, 0, hdr.length);

        int magic = NB.decodeInt32(hdr, 0);

        if (magic != MIDX_MAGIC) {
            throw new MIDXFormatException(
                    JGitText.get().notAMIDX);
        }

        // Check MIDX version
        int v = hdr[4];
        if (v != 1) {
            throw new MIDXFormatException(MessageFormat.format(
                    JGitText.get().unsupportedMIDXVersion,
                    v));
        }

        // Read the object Id version (1 byte)
        // 1 => SHA-1
        // 2 => SHA-256
        // TODO: If the hash type does not match the repository's hash algorithm,
        //    the multi-pack-index file should be ignored with a warning
        //    presented to the user.
        int commitIdVersion = hdr[5];
        if (commitIdVersion != 1) {
            throw new MIDXFormatException(
                    JGitText.get().incorrectOBJECT_ID_LENGTH);
        }

        // Read the number of "chunkOffsets" (1 byte)
        int numberOfChunks = hdr[6];

        // Read the number of multi-pack-index files (1 byte)
        // This value is currently always zero.
        int numberOfMultiPackIndexFiles = hdr[7];

        // Number of packfiles (4 bytes)
        //TODO not used at the moment. Need to find out how to use it.
        int numberOfPackFiles = NB.decodeInt32(hdr, 7);

        byte[] lookupBuffer = new byte[CHUNK_LOOKUP_WIDTH
                * (numberOfChunks + 1)];


        IO.readFully(fd, lookupBuffer, 0, lookupBuffer.length);

        List<ChunkSegment> chunks = new ArrayList<>(numberOfChunks + 1);
        for (int i = 0; i <= numberOfChunks; i++) {
            // chunks[numberOfChunks] is just a marker, in order to record the
            // length of the last chunk.
            int id = NB.decodeInt32(lookupBuffer, i * 12);
            long offset = NB.decodeInt64(lookupBuffer, i * 12 + 4);
            chunks.add(new ChunkSegment(id, offset));
        }

        MIDXBuilder builder = MIDXBuilder.builder();
        for (int i = 0; i < numberOfChunks; i++) {
            long chunkOffset = chunks.get(i).offset;
            int chunkId = chunks.get(i).id;
            long len = chunks.get(i + 1).offset - chunkOffset;

            if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
                throw new MIDXFormatException(
                        JGitText.get().multiPackFileIsTooLargeForJgit);
            }

            byte buffer[] = new byte[(int) len];
            IO.readFully(fd, buffer, 0, buffer.length);

            switch (chunkId) {
                case MIDX_ID_OID_FANOUT:
                    builder.addOidFanout(buffer);
                    break;
                case MIDX_ID_OID_LOOKUP:
                    builder.addOidLookUp(buffer);
                    break;
                case MIDX_PACKFILE_NAMES:
                    builder.addPackFileNames(buffer);
                    break;
                case MIDX_BITMAPPED_PACKFILES:
                    builder.addBitmappedPackfiles(buffer);
                    break;
                case MIDX_OBJECT_OFFSETS:
                    builder.addObjectOffsets(buffer);
                    break;
                default:
                    LOG.warn(MessageFormat.format(
                            JGitText.get().midxChunkUnknown,
                            Integer.toHexString(chunkId)));
            }
        }
        return builder.build();
    }

    private static class ChunkSegment {
        final int id;

        final long offset;

        private ChunkSegment(int id, long offset) {
            this.id = id;
            this.offset = offset;
        }
    }
}
