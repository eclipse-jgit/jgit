package org.eclipse.jgit.internal.storage.file.midx;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.util.NB;

/**
 * The index which are used by the MIDX to:
 * <ul>
 * <li>Get the object in MIDX by using a specific position.</li>
 * <li>Get the position of a specific object in MIDX.</li>
 * </ul>
 */
public class MIDXIndex {

    private static final int FANOUT = 256;

    private final int hashLength;

    private final int[] fanoutTable;

    private final byte[] oidLookup;

    private final long objectInfoCnt;

    private final long packFilesCnt;

    /**
     * Initialize the MIDXIndex.
     *
     * @param hashLength   length of object hash.
     * @param oidFanout    content of OID Fanout Chunk.
     * @param oidLookup    content of OID Lookup Chunk.
     * @param packFilesCnt number of packfiles.
     * @throws MIDXFormatException MIDX file's format is different from we expected.
     */
    MIDXIndex(int hashLength, @NonNull byte[] oidFanout,
              @NonNull byte[] oidLookup, long packFilesCnt) throws MIDXFormatException {
        this.hashLength = hashLength;
        this.oidLookup = oidLookup;
        this.packFilesCnt = packFilesCnt;

        int[] table = new int[FANOUT];
        long uint32;
        for (int k = 0; k < table.length; k++) {
            uint32 = NB.decodeUInt32(oidFanout, k * 4);
            if (uint32 > Integer.MAX_VALUE) {
                throw new MIDXFormatException(
                        JGitText.get().multiPackFileIsTooLargeForJgit);
            }
            table[k] = (int) uint32;
        }
        this.fanoutTable = table;
        this.objectInfoCnt = table[FANOUT - 1];
    }

    /**
     * Find the position in the MIDX file of the specified id.
     *
     * @param id the id for which the midx position will be found.
     * @return the MIDX position or -1 if the object was not found.
     */
    int findMIDXPosition(AnyObjectId id) {
        int levelOne = id.getFirstByte();
        int high = fanoutTable[levelOne];
        int low = 0;
        if (levelOne > 0) {
            low = fanoutTable[levelOne - 1];
        }
        while (low < high) {
            int mid = (low + high) >>> 1;
            int pos = objIdOffset(mid);
            int cmp = id.compareTo(oidLookup, pos);
            if (cmp < 0) {
                high = mid;
            } else if (cmp == 0) {
                return mid;
            } else {
                low = mid + 1;
            }
        }
        return -1;
    }

    long getPackFilesCnt() {
        return packFilesCnt;
    }

    private int objIdOffset(int pos) {
        return hashLength * pos;
    }
}
