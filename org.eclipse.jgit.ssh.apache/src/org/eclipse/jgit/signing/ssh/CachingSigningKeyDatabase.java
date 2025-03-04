/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing.ssh;

/**
 * A {@link SigningKeyDatabase} that caches data.
 * <p>
 * A signing key database may be used to check keys frequently; it may thus need
 * to cache some data and it may need to cache data per repository. If an
 * implementation does cache data, it is responsible itself for refreshing that
 * cache at appropriate times. Clients can control the cache size somewhat via
 * {@link #setCacheSize(int)}, although the meaning of the cache size (i.e., its
 * unit) is left undefined here.
 * </p>
 *
 * @since 7.1
 */
public interface CachingSigningKeyDatabase extends SigningKeyDatabase {

	/**
	 * Retrieves the current cache size.
	 *
	 * @return the cache size, or -1 if this database has no cache.
	 */
	int getCacheSize();

	/**
	 * Sets the cache size to use.
	 *
	 * @param size
	 *            the cache size, ignored if this database does not have a
	 *            cache.
	 * @throws IllegalArgumentException
	 *             if {@code size < 0}
	 */
	void setCacheSize(int size);

	/**
	 * Discards any cached data. A no-op if the database has no cache.
	 */
	void clearCache();
}
