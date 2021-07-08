/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.internal.storage.dfs.DfsReader;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.pack.PackNotFoundException;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

public class LocalCachedPackTest {
    InMemoryRepository db;

    @Before
    public void setUp() {
        db = new InMemoryRepository(new DfsRepositoryDescription("test"));
    }

    @Test
    public void testPackNotFoundExceptionThrownWhenStaleFileHandle() throws Exception {
        Pack mockedPack = Mockito.mock(Pack.class);
        doThrow(new IOException("Stale file handle"))
            .when(mockedPack)
            .copyPackAsIs(any(), any());

        try (DfsReader ctx = db.getObjectDatabase().newReader();
             PackWriter pw = new PackWriter(ctx);
             ByteArrayOutputStream os = new ByteArrayOutputStream();
             PackOutputStream out = new PackOutputStream(
                     NullProgressMonitor.INSTANCE, os, pw)) {

            LocalCachedPack localCachedPack =
                    new LocalCachedPack(Collections.singletonList(mockedPack));
            assertThrows(
                PackNotFoundException.class,
                () -> localCachedPack.copyAsIs(out, new WindowCursor(null))
            );
        }
    }

    @Test
    public void testIOExceptionThrownWhenGenericError() throws Exception {
        Pack mockedPack = Mockito.mock(Pack.class);
        doThrow(new IOException("Any IO Exception"))
            .when(mockedPack)
            .copyPackAsIs(any(), any());

        try (DfsReader ctx = db.getObjectDatabase().newReader();
             PackWriter pw = new PackWriter(ctx);
             ByteArrayOutputStream os = new ByteArrayOutputStream();
             PackOutputStream out = new PackOutputStream(
                     NullProgressMonitor.INSTANCE, os, pw)) {

            LocalCachedPack localCachedPack =
                    new LocalCachedPack(Collections.singletonList(mockedPack));
            assertThrows(
                IOException.class,
                () -> localCachedPack.copyAsIs(out, new WindowCursor(null))
            );
        }

    }

    @Test
    public void testNoExceptionsAreThrown() throws Exception {
        Pack mockedPack = Mockito.mock(Pack.class);
        doNothing()
            .when(mockedPack)
            .copyPackAsIs(any(), any());

        try (DfsReader ctx = db.getObjectDatabase().newReader();
             PackWriter pw = new PackWriter(ctx);
             ByteArrayOutputStream os = new ByteArrayOutputStream();
             PackOutputStream out = new PackOutputStream(
                     NullProgressMonitor.INSTANCE, os, pw)) {

            LocalCachedPack localCachedPack =
                    new LocalCachedPack(Collections.singletonList(mockedPack));
            localCachedPack.copyAsIs(out, new WindowCursor(null));
        }
    }
}