/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.RefDatabase;

import java.io.IOException;

/**
 * Snapshot of a traditional file system based {@link RefDatabase}.
 * <p>
 * This can be used in a request scope to avoid re-reading packed-refs
 * more than once in a single request.
 */
public class SnapshotRefDirectory extends RefDirectory {
    private volatile boolean isPackedRefsSnapshot;

    /**
     * Create a snapshot of file system based {@link RefDatabase}.
     *
     * @param refDb
     *            a reference to the ref database
     */
    public SnapshotRefDirectory(RefDirectory refDb) {
        super(refDb.parent);
        looseRefs.set(refDb.looseRefs.get());
        packedRefs.set(refDb.packedRefs.get());
        isPackedRefsSnapshot = false;
    }

    @Override
    PackedRefList getPackedRefs() throws IOException {
        PackedRefList refs = packedRefs.get();
        if (!isPackedRefsSnapshot) {
            refs = super.getPackedRefs();
            isPackedRefsSnapshot = true;
        }
        return refs;
    }
}
