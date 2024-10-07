/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

import java.io.IOException;

/**
 * Thrown when a MIDX file's format is different from we expected
 */
public class MIDXFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Construct an exception.
     *
     * @param why description of the type of error.
     */
    MIDXFormatException(String why) {
        super(why);
    }
}
