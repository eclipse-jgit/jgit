package org.eclipse.jgit.internal.storage.file.midx;

import org.eclipse.jgit.lib.AnyObjectId;

/**
 * The MIDX is a supplemental data structure that accelerates
 * objects retrieval.
 */
public interface MIDX {

    /**
     * Obtain the array of packfiles in the MIDX.
     *
     * @return array of packfiles in the MIDX.
     */
    String[] getPackFileNames();

    /**
     * Obtain the number of packfiles in the MIDX.
     *
     * @return number number of packfiles in the MIDX.
     */
    long getPackFilesCount();

    ObjectOffset getObjectOffset(AnyObjectId objectId);

    interface ObjectOffset {
        String getPackName();

        long getOffset();
    }
}
