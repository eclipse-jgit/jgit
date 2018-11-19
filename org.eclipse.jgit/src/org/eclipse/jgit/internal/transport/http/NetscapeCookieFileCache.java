/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
		} else {
			return instance;
		}
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
