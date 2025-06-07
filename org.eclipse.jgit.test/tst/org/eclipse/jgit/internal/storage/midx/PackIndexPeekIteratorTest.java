/*
 * Copyright (C) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.junit.Test;

public class PackIndexPeekIteratorTest {
    @Test
    public void next() {
        PackIndex index1 = indexOf(
                object("0000000000000000000000000000000000000001", 500),
                object("0000000000000000000000000000000000000003", 1500),
                object("0000000000000000000000000000000000000005", 3000));
        PackIndexMerger.PackIndexPeekIterator it = new PackIndexMerger.PackIndexPeekIterator(0, index1);
        assertEquals("0000000000000000000000000000000000000001", it.next().name());
        assertEquals("0000000000000000000000000000000000000003", it.next().name());
        assertEquals("0000000000000000000000000000000000000005", it.next().name());
        assertNull(it.next());
    }

    @Test
    public void peek_doesNotAdvance() {
        PackIndex index1 = indexOf(
                object("0000000000000000000000000000000000000001", 500),
                object("0000000000000000000000000000000000000003", 1500),
                object("0000000000000000000000000000000000000005", 3000));
        PackIndexMerger.PackIndexPeekIterator it = new PackIndexMerger.PackIndexPeekIterator(0, index1);
        it.next();
        assertEquals("0000000000000000000000000000000000000001", it.peek().name());
        assertEquals("0000000000000000000000000000000000000001", it.peek().name());
        it.next();
        assertEquals("0000000000000000000000000000000000000003", it.peek().name());
        assertEquals("0000000000000000000000000000000000000003", it.peek().name());
        it.next();
        assertEquals("0000000000000000000000000000000000000005", it.peek().name());
        assertEquals("0000000000000000000000000000000000000005", it.peek().name());
        it.next();
        assertNull(it.peek());
        assertNull(it.peek());
    }

    @Test
    public void empty() {
        PackIndex index1 = indexOf();
        PackIndexMerger.PackIndexPeekIterator it = new PackIndexMerger.PackIndexPeekIterator(0, index1);
        assertNull(it.next());
        assertNull(it.peek());
    }

    private static PackIndex indexOf(FakeIndexFactory.IndexObject... objs) {
        return FakeIndexFactory.indexOf(Arrays.asList(objs));
    }

    private static FakeIndexFactory.IndexObject object(String name, long offset) {
        return new FakeIndexFactory.IndexObject(name, offset);
    }
}