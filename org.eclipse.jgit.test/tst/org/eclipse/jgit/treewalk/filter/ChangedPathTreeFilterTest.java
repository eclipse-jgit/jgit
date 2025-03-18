/*
 * Copyright (C) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk.filter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.commitgraph.ChangedPathFilter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter.MutableBoolean;
import org.junit.Test;

public class ChangedPathTreeFilterTest {

    @Test
    public void shouldTreeWalk_no_usingCpf() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        MutableBoolean cfpUsed = new MutableBoolean();

        boolean result = f.shouldTreeWalk(FakeRevCommit.withCpfFor("c"), null,
                cfpUsed);

        assertFalse(result);
        assertTrue(cfpUsed.get());
    }

    @Test
    public void shouldTreeWalk_yes_usingCpf() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        MutableBoolean cfpUsed = new MutableBoolean();

        boolean result = f.shouldTreeWalk(FakeRevCommit.withCpfFor("a/b"), null,
                cfpUsed);

        assertTrue(result);
        assertTrue(cfpUsed.get());
    }

    @Test
    public void shouldTreeWalk_yes_noCpf() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        MutableBoolean cfpUsed = new MutableBoolean();

        boolean result = f.shouldTreeWalk(FakeRevCommit.noCpf(), null,
                cfpUsed);

        assertTrue(result);
        assertFalse(cfpUsed.get());
    }

    @Test
    public void shouldTreeWalk_no_usingCpf_noReport() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        boolean result = f.shouldTreeWalk(FakeRevCommit.withCpfFor("c"), null,
                null);

        assertFalse(result);
    }

    @Test
    public void shouldTreeWalk_yes_usingCpf_noReport() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        boolean result = f.shouldTreeWalk(FakeRevCommit.withCpfFor("a/b"), null,
                null);
        assertTrue(result);
    }

    @Test
    public void shouldTreeWalk_yes_noCpf_noReport() {
        ChangedPathTreeFilter f = ChangedPathTreeFilter.create("a/b");
        boolean result = f.shouldTreeWalk(FakeRevCommit.noCpf(), null,
                null);

        assertTrue(result);
    }

    private static class FakeRevCommit extends RevCommit {

        static RevCommit withCpfFor(String... paths) {
            return new FakeRevCommit(
                    ChangedPathFilter.fromPaths(Arrays.stream(paths)
                            .map(str -> ByteBuffer.wrap(str.getBytes(UTF_8)))
                            .collect(Collectors.toSet())));
        }

        static RevCommit noCpf() {
            return new FakeRevCommit(null);
        }

        private final ChangedPathFilter cpf;

        /**
         * Create a new commit reference.
         *
         * @param cpf
         *            changedPathFilter
         */
        protected FakeRevCommit(ChangedPathFilter cpf) {
            super(ObjectId.zeroId());
            this.cpf = cpf;
        }

        @Override
        public ChangedPathFilter getChangedPathFilter(RevWalk rw) {
            return cpf;
        }
    }
}