/*
 * Copyright (C) 2023, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/** Empty {@link CommitGraph} with no results. */
class EmptyCommitGraph implements CommitGraph {

    /** {@inheritDoc} */
    @Override
    public int findGraphPosition(AnyObjectId commit) {
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public CommitData getCommitData(int graphPos) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public ObjectId getObjectId(int graphPos) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public long getCommitCnt() {
        return 0;
    }
}