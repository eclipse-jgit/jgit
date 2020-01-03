/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Provider of URIs corresponding to cached packs. For use with the
 * "packfile-uris" feature.
 * @since 5.5
 */
public interface CachedPackUriProvider {

	/**
	 * @param pack the cached pack for which to check if a corresponding URI
	 *	exists
	 * @param protocolsSupported the protocols that the client has declared
	 *	support for; if a URI is returned, it must be of one of these
	 *	protocols
	 * @throws IOException implementations may throw this
	 * @return if a URI corresponds to the cached pack, an object
	 *	containing the URI and some other information; null otherwise
	 * @since 5.5
	 */
	@Nullable
	PackInfo getInfo(CachedPack pack, Collection<String> protocolsSupported)
		throws IOException;

	/**
	 * Information about a packfile.
	 * @since 5.5
	 */
	public static class PackInfo {
		private final String hash;
		private final String uri;

		private final long size;

		/**
		 * Constructs an object containing information about a packfile.
		 *
		 * @param hash
		 *            the hash of the packfile as a hexadecimal string
		 * @param uri
		 *            the URI corresponding to the packfile
		 * @param size
		 *            the size of the packfile in bytes
		 */
		public PackInfo(String hash, String uri, long size) {
			this.hash = hash;
			this.uri = uri;
			this.size = size;
		}

		/**
		 * @return the hash of the packfile as a hexadecimal string
		 */
		public String getHash() {
			return hash;
		}

		/**
		 * @return the URI corresponding to the packfile
		 */
		public String getUri() {
			return uri;
		}

		/**
		 * @return the size of the packfile in bytes (-1 if unknown)
		 */
		public long getSize() {
			return size;
		}
	}
}
