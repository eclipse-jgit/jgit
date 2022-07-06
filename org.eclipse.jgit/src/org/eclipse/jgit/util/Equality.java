/*
 * Copyright (C) 2022, Fabio Ponciroli <ponch78@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

/**
 * Equality utilities.
 *
 * @since: 6.2
 */
public class Equality {

    /**
     * Compare by reference
     *
     * @param a
     *            First object to compare
     * @param b
     *            Second object to compare
     * @return {@code true} if the objects are identical, {@code false}
     *         otherwise
     *
     * @since 6.2
     */
    @SuppressWarnings("ReferenceEquality")
    public static <T> boolean isSameInstance(T a, T b) {
        return a == b;
    }
}