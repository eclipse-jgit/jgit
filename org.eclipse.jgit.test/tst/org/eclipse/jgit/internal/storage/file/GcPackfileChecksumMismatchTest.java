/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import org.apache.log4j.*;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;

public class GcPackfileChecksumMismatchTest extends GcTestCase {

    static class ReadThread extends Thread {
        final ObjectDirectory objectsDirectory;
        final ObjectId objectId;
        private final long initialDelay;
        boolean failed = false;
        Logger log = Logger.getLogger(ReadThread.class);

        ReadThread(ObjectDirectory objectDirectory, ObjectId objectId, long initialDelay) {
            this.objectsDirectory = objectDirectory;
            this.objectId = objectId;
            this.initialDelay = initialDelay;
        }

        @Override
        public void run() {
            try {
//                Thread.sleep(initialDelay);
                log.info("Starting thread " + currentThread().getName());
                ObjectLoader objLoader = objectsDirectory.newReader().open(objectId);
                failed = objLoader.getSize() <= 0;
                log.info("Ending thread " + currentThread().getName() + " : " + (failed ? "FAILED" : "SUCCESS"));
            } catch (Exception e) {
                log.error(currentThread().getName() + "Failed", e);
                failed = true;
            }
        }

        public boolean isFailed() {
            return failed;
        }
    }

    @BeforeClass
    public static void setupWindowCache() {
        WindowCacheConfig cfg = new WindowCacheConfig();
        cfg.setPackedGitLimit(4096);
        cfg.setPackedGitWindowSize(4096);
        WindowCache.reconfigure(cfg);
    }

    @Test
    public void testPack2Commits() throws Exception {
        BranchBuilder bb = tr.branch("refs/heads/master");
        List<RevCommit> commits = new ArrayList<>();

        commits.add(bb.commit().add("A", "AAAAAAAAAAA").add("B", "BBBBBBBBBBBB").create());
        configureGc(gc, true);
        gc.gc();

        for (int i = 0; i < 1024; i++) {
            commits.add(bb.commit().add("A" + i, "AAAAAAAAAAA" + i).add("B" + i, "BBBBBBBBBBBB" + i).create());
        }

        ObjectId objId = commits.get(commits.size()-1).getId();

        configureGc(gc, false);
        gc.gc();

        FileRepository openRepo = (FileRepository) FileRepositoryBuilder.create(repo.getDirectory());
        ObjectDirectory objectsDirectory = openRepo.getObjectDatabase();
        try (ObjectReader reader = objectsDirectory.newReader()) {
            reader.open(commits.get(0).getId());
        }

        configureGc(gc, true);
        gc.gc();

        Thread.sleep(5000L);

        int currentReads = 3;
        List<ReadThread> readThreads = new ArrayList<>();
        for (int i = 0; i < currentReads; i++) {
            ReadThread e = new ReadThread(objectsDirectory, objId, i * 1500L);
            readThreads.add(e);
            e.start();
        }

        for (ReadThread t : readThreads) {
            t.join();
            Assert.assertFalse(t.isFailed());
        }
//		}
    }

    private PackConfig configureGc(GC myGc, boolean aggressive) {
        PackConfig pconfig = new PackConfig(repo);
        if (aggressive) {
            pconfig.setDeltaSearchWindowSize(250);
            pconfig.setMaxDeltaDepth(250);
            pconfig.setReuseObjects(false);
            pconfig.setCompressionLevel(9);
        } else {
            pconfig.setMaxDeltaDepth(1);
            pconfig.setCompressionLevel(0);
        }
        myGc.setPackConfig(pconfig);
        return pconfig;
    }
}
