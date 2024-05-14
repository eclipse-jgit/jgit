/*
 * Copyright (C) 2025, Fabio Ponciroli <ponch78@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class PackDirectoryTest extends RepositoryTestCase {

    @Test
    public void testRapidPackIndexIsPopulated() throws Exception {
        ObjectDirectory dir = db.getObjectDatabase();
        commitFile("file.txt", "content", "master");
        GC gc = new GC(db);
        gc.gc().get();

        File packDirectory = dir.getPackDirectory();
        PackDirectory pd = new PackDirectory(new Config(), packDirectory);
        // Call getPacks to make sure packList is populated
        Collection<Pack> packs = pd.getPacks();
        assertEquals(1, packs.size());

        PackDirectory.PackList pList = pd.getPackList().get();
        assertTrue(pList.getRapidPackIndex().isEmpty());

        pList.preloadRapidPackIndex();

        long objectCount = packs.stream().toList().get(0).getObjectCount();

        assertEquals(objectCount, pList.getRapidPackIndex().size());
    }
}
