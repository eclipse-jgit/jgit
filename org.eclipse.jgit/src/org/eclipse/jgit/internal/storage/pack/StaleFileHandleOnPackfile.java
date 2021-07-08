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

import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.transport.UploadPackSendPackException;

import java.io.IOException;

/**
 * Indicates that a stale file handle error has been raised against a pack file
 *
 */
public class StaleFileHandleOnPackfile extends UploadPackSendPackException {
    private static final long serialVersionUID = 1L;

    private final Pack pack;

    /**
	 * <p>
	 * Constructor for StaleFileHandleOnPackfile.
	 * </p>
	 *
	 * @param why
	 *            root cause exception.
	 *
	 * @param missingPack
	 *            pack which file on disk has gone missing.
	 */
    public StaleFileHandleOnPackfile(Throwable why, Pack missingPack) {
        super(why);
        this.pack = missingPack;
    }

    /**
     * Get the pack which file on disk has gone missing.
     *
     * @return Pack object which file on disk has gone missing.
     */
    public Pack getPack() {
        return pack;
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
