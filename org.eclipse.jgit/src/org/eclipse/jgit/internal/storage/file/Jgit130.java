/*
 * Copyright (C) 2025, Antonio Barone <syntonyze@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;

/**
 * This little script allows to expose a concurrency issue during the auto-refresh phase.
 * It runs two threads.
 * First it triggers EXACT_REF1 thread, which attempts an exactRef
 * The jgit code was updated to wait for EXACT_REF1 at two points during the refresh
 *  1. Before checking reftableStack.isUpToDate(), so that a new commit can be pushed to cause
 *  reftableStack.isUpToDate() to return false and thus initiate the `refresh` cycle.
 *
 *  2. After reftableDatabase.clearCache(); is called. This allows two things:
 *      - A second thread EXACT_REF2 to repopulate the `mergedTables` cache.
 *      - A new commit to be pushed so that an existing refTable file is closed.
 */
class Jgit130 {

    public static void runExactRef(Git repo) {
        try {
            repo.getRepository().exactRef("refs/heads/master");
        } catch (IOException e) {
            System.out.println(Thread.currentThread().getName() + ": GOT EXCEPTION!");
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        try (Git repo = Git.open(new File("/tmp/gerrit-reftable-remote.git"))) {

            Thread t1 = new Thread(() -> runExactRef(repo));
            t1.setName("EXACT_REF1");

            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(25000);
                    runExactRef(repo);
                } catch (InterruptedException e) {
                    System.out.println("interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            });
            t2.setName("EXACT_REF2");

            t1.start();
            t2.start();

            t1.join();
            t2.join();

        } catch (IOException e) {
            System.out.println("Repo error: " + e.getCause());
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
