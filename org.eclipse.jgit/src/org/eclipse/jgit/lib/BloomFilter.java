/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A BloomFilter represents a data segment to use when testing hash values of
 * {@link org.eclipse.jgit.lib.BloomFilter.Key}.
 */
public interface BloomFilter {

    /**
     * Whether the bloom filter contains the key.
     *
     * @param key
     *            the key
     * @return true if the bloom filter contains the key.
     */
    boolean contains(Key key);

    /**
     * A key represents the k hash values.
     *
     * The hash values can be precomputed and stored in a key for re-use when
     * testing against a {@link org.eclipse.jgit.lib.BloomFilter}.
     */
    class Key {
        private final int[] hashes;

        /**
         * Initialize a key.
         *
         * @param hashes
         *            the hashes to be used for testing against a bloom filter;
         *            Must not be null.
         */
        public Key(@NonNull int[] hashes) {
            this.hashes = hashes;
        }

        /**
         * Get the hashes to be used for testing against the bloom filter.
         *
         * @return the hashes.
         */
        public int[] getHashes() {
            return hashes;
        }
    }
}