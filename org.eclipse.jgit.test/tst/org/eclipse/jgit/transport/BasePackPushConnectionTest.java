/*
 * Copyright (c) 2022 Darius Jokilehto <darius.jokilehto+os@gmail.com>
 * Copyright (c) 2022 Antonio Barone <syntonyze@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class BasePackPushConnectionTest {
    @Test
    public void testNoRepositoryPropagatesNoRemoteRepositoryException() {
        NoRemoteRepositoryException rootException = new NoRemoteRepositoryException(new URIish(), "not found");
        IOException suppressedException = new IOException("not read");

        TransportException result =
                new FailingBasePackPushConnection(rootException).noRepository(suppressedException);

        assertEquals(rootException, result);
        assertEquals(1, result.getSuppressed().length);
        assertEquals(suppressedException, result.getSuppressed()[0]);
    }

    private static class FailingBasePackPushConnection extends BasePackPushConnection {
        FailingBasePackPushConnection(TransportException transportException) {
            super(new TransportLocal(new URIish(), new java.io.File("")) {
                @Override
                public FetchConnection openFetch() throws TransportException {
                    throw transportException;
                }
            });
        }
    }
}
