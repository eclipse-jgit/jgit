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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Provider of URIs corresponding to cached packs. For use with the
 * "packfile-uris" feature.
 * @since 5.5
 */
public interface CachedPackUriProvider {

	/**
	 * Get pack info
	 *
	 * @param pack
	 *            the cached pack for which to check if a corresponding URI
	 *            exists
	 * @param protocolsSupported
	 *            the protocols that the client has declared support for; if a
	 *            URI is returned, it must be of one of these protocols
	 * @throws IOException
	 *             implementations may throw this
	 * @return if a URI corresponds to the cached pack, an object containing the
	 *         URI and some other information; null otherwise
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
		 *            trailer checksum of the exact pack bytes at the URI, as hex;
		 *            must change when pack contents change, never a logical name
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
		 * Validate metadata before including it in a protocol response.
		 *
		 * @param protocolsSupported
		 *            schemes accepted by the client
		 * @throws IOException
		 *             hash, URI, size or scheme is invalid
		 * @since 8.0
		 */
		public void validate(Collection<String> protocolsSupported)
				throws IOException {
			try {
				URI parsed = new URI(uri);
				if (!ObjectId.isId(hash) || size < -1 || !parsed.isAbsolute()
						|| protocolsSupported.stream().noneMatch(
							protocol -> protocol.equalsIgnoreCase(parsed.getScheme()))) {
					throw new IOException("Invalid packfile URI metadata"); //$NON-NLS-1$
				}
			} catch (URISyntaxException | NullPointerException e) {
				throw new IOException("Invalid packfile URI metadata", e); //$NON-NLS-1$
			}
		}

		/**
		 * Get the hash of the packfile
		 *
		 * @return the hash of the packfile as a hexadecimal string
		 */
		public String getHash() {
			return hash;
		}

		/**
		 * Get the URI corresponding to the packfile
		 *
		 * @return the URI corresponding to the packfile
		 */
		public String getUri() {
			return uri;
		}

		/**
		 * Get the size of the packfile in bytes
		 *
		 * @return the size of the packfile in bytes (-1 if unknown)
		 */
		public long getSize() {
			return size;
		}
	}
}
