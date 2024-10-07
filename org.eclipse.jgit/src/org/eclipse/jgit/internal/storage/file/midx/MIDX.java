/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

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

    /**
     * Ge the packfile name given the pack int Id.
     *
     * @param packIntId pack integer Id.
     * @return packfile name.
     */
    String getPackFileName(int packIntId);

    /**
     * Obtain the ObjectOffset in the MIDX.
     *
     * @param objectId objectId to read.
     * @return ObjectOffset from the MIDX.
     */
    ObjectOffset getObjectOffset(AnyObjectId objectId);

    /**
     * Object offset in data chunk.
     */
    interface ObjectOffset {
        int getPackIntId();

        long getOffset();
    }
}
