/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.function.Function;

/**
 * A cache for {@link KeyPair}s.
 *
 * @since 5.2
 */
public interface KeyCache {

	/**
	 * Obtains a {@link KeyPair} from the cache. Implementations must be
	 * thread-safe.
	 *
	 * @param path
	 *            of the key
	 * @param loader
	 *            to load the key if it isn't present in the cache yet
	 * @return the {@link KeyPair}, or {@code null} if not present and could not
	 *         be loaded
	 */
	KeyPair get(Path path, Function<? super Path, ? extends KeyPair> loader);

	/**
	 * Removes all {@link KeyPair} from this cache and destroys their private
	 * keys. This cache instance must not be used anymore thereafter.
	 */
	void close();
}
