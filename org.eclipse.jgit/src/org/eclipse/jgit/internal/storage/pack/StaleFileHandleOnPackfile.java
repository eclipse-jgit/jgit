/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import org.eclipse.jgit.transport.TriggerRefreshPackListException;

import java.io.IOException;

/**
 * Indicates that a stale file handle error has been raised against a pack file
 *
 */
public class StaleFileHandleOnPackfile extends TriggerRefreshPackListException {
    private static final long serialVersionUID = 1L;

    /**
	 * <p>
	 * Constructor for StaleFileHandleOnPackfile.
	 * </p>
	 *
	 * @param why
	 *            root cause exception.
	 */
    public StaleFileHandleOnPackfile(Throwable why) {
        super(why);
    }

    /**
     * Get the original IOEXception thrown.
     *
     * @return original IOEXception thrown.
     */
    public IOException getOriginalException() {
        return (IOException) this.getCause();
    }
}
