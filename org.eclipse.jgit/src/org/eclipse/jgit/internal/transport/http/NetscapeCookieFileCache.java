/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.http;

import java.nio.file.Path;

import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.util.LRUMap;

/**
 * A cache of all known cookie files ({@link NetscapeCookieFile}). May contain
 * at most {@code n} entries, where the least-recently used one is evicted as
 * soon as more entries are added. The maximum number of entries (={@code n})
 * can be set via the git config key {@code http.cookieFileCacheLimit}. By
 * default it is set to 10.
 * <p>
 * The cache is global, i.e. it is shared among all consumers within the same
 * Java process.
 *
 * @see NetscapeCookieFile
 *
 */
public class NetscapeCookieFileCache {

	private final LRUMap<Path, NetscapeCookieFile> cookieFileMap;

	private static NetscapeCookieFileCache instance;

	private NetscapeCookieFileCache(HttpConfig config) {
		cookieFileMap = new LRUMap<>(config.getCookieFileCacheLimit(),
				config.getCookieFileCacheLimit());
	}

	/**
	 * @param config
	 *            the config which defines the limit for this cache
	 * @return the singleton instance of the cookie file cache. If the cache has
	 *         already been created the given config is ignored (even if it
	 *         differs from the config, with which the cache has originally been
	 *         created)
	 */
	public static NetscapeCookieFileCache getInstance(HttpConfig config) {
		if (instance == null) {
			return new NetscapeCookieFileCache(config);
		}
		return instance;
	}

	/**
	 * @param path
	 *            the path of the cookie file to retrieve
	 * @return the cache entry belonging to the requested file
	 */
	public NetscapeCookieFile getEntry(Path path) {
		if (!cookieFileMap.containsKey(path)) {
			synchronized (NetscapeCookieFileCache.class) {
				if (!cookieFileMap.containsKey(path)) {
					cookieFileMap.put(path, new NetscapeCookieFile(path));
				}
			}
		}
		return cookieFileMap.get(path);
	}

}
