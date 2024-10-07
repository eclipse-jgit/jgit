package org.eclipse.jgit.internal.storage.file.midx;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

public class MIDXIndex {

    private static final int FANOUT = 256;

    private final int hashLength;

    private final int[] fanoutTable;

    private final byte[] oidLookup;

    private final long commitCnt;

    /**
     * Initialize the GraphObjectIndex.
     *
     * @param hashLength
     *            length of object hash.
     * @param oidFanout
     *            content of OID Fanout Chunk.
     * @param oidLookup
     *            content of OID Lookup Chunk.
     * @throws MIDXFormatException
     *             MIDX file's format is different from we expected.
     */
    MIDXIndex(int hashLength, @NonNull byte[] oidFanout,
                     @NonNull byte[] oidLookup) throws MIDXFormatException {
        this.hashLength = hashLength;
        this.oidLookup = oidLookup;

        int[] table = new int[FANOUT];
        long uint32;
        for (int k = 0; k < table.length; k++) {
            uint32 = NB.decodeUInt32(oidFanout, k * 4);
            if (uint32 > Integer.MAX_VALUE) {
                throw new MIDXFormatException(
                        JGitText.get().commitGraphFileIsTooLargeForJgit);
            }
            table[k] = (int) uint32;
        }
        this.fanoutTable = table;
        this.commitCnt = table[FANOUT - 1];
    }

    /**
     * Find the position in the MIDX file of the specified id.
     *
     * @param id
     *            the id for which the midx position will be found.
     * @return the MIDX position or -1 if the object was not found.
     */
    int findGraphPosition(AnyObjectId id) {
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

    /**
     * Get the object at the midx position.
     *
     * @param midxPos
     *            the position in the midx of the object.
     * @return the ObjectId or null if it's not found.
     */
    ObjectId getObjectId(int midxPos) {
        if (midxPos < 0 || midxPos >= commitCnt) {
            return null;
        }
        return ObjectId.fromRaw(oidLookup, objIdOffset(midxPos));
    }

    long getCommitCnt() {
        return commitCnt;
    }

    private int objIdOffset(int pos) {
        return hashLength * pos;
    }
}
