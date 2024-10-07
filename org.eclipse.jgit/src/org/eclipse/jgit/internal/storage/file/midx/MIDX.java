package org.eclipse.jgit.internal.storage.file.midx;

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
    String[] getPackfileNames();

    /**
     * Obtain the number of packfiles in the MIDX.
     *
     * @return number number of packfiles in the MIDX.
     */
    long getPackfilesCnt();
}
