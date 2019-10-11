/*
 * Copyright (C) 2019, Google LLC.
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

		private final int size;

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
		public PackInfo(String hash, String uri, int size) {
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
