/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.internal.storage.file.Pack;

/**
 * A previously selected representation is no longer available.
 */
public class StoredPackRepresentationNotAvailableException extends Exception {
//TODO remove unused ObjectToPack in 5.0
    private static final long serialVersionUID = 1L;

    /**
     * Construct an error for an object.
     *
     * @param pack
     *            the object whose current representation is no longer present.
     * @param cause
     *            cause
     * @since 5.13
     */
    public StoredPackRepresentationNotAvailableException(Pack pack,
                                                         Throwable cause) {
        super(cause);
        // Do nothing.
    }
}
