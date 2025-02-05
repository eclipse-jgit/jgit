package org.eclipse.jgit.lib;


import java.io.IOException;

/**
 * Utility for writing reflog entries.
 */
public interface ReflogWriter {
    /**
     * Write the given entry to the ref's log.
     *
     * @param refName
     *            a {@link java.lang.String} object.
     * @param entry
     *            a {@link org.eclipse.jgit.lib.ReflogEntry} object.
     * @return this writer
     * @throws java.io.IOException
     *             if an IO error occurred
     */
    ReflogWriter log(String refName, ReflogEntry entry)
            throws IOException;

    /**
     * Write the given entry information to the ref's log
     *
     * @param refName
     *            ref name
     * @param oldId
     *            old object id
     * @param newId
     *            new object id
     * @param ident
     *            a {@link org.eclipse.jgit.lib.PersonIdent}
     * @param message
     *            reflog message
     * @return this writer
     * @throws java.io.IOException
     *             if an IO error occurred
     */
    ReflogWriter log(String refName, ObjectId oldId,
                                     ObjectId newId, PersonIdent ident, String message) throws IOException;
}
