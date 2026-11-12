/*
 * Copyright (C) 2026, Son Luong Ngoc and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.pack.CachedPackUriProvider.PackInfo;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Optional remote distribution of completed generated packs. Unlike
 * CachedPackUriProvider, this maps exact fetch-output keys, not repository packs.
 * Implementations own storage, publication policy, retention and access control.
 * They must be thread-safe and enforce bounded I/O timeouts. Lookups run on the
 * request thread and may use its authorization context. Background publication
 * must use independently owned resources, without retaining the request's
 * repository reader, authentication context or output stream.
 *
 * @since 8.0
 */
public interface GeneratedPackUriProvider {
	/**
	 * Look up a successfully published pack, returning a fresh authorized URI.
	 * Called on the request thread after fetch validation. An unavailable pack,
	 * exception or unusable URI falls back to local generation or cache replay.
	 * Once a URI is sent, client download failure cannot fall back in this fetch.
	 *
	 * @param key
	 *            opaque versioned request, repository, scope and offload identity
	 * @param protocolsSupported
	 *            URI schemes accepted by this client
	 * @return committed metadata, or null on a miss
	 * @throws IOException
	 *             lookup failed; treated as a miss
	 */
	@Nullable
	PackInfo getInfo(String key, Collection<String> protocolsSupported)
			throws IOException;

	/**
	 * Optionally publish a completed self-contained pack, then atomically expose
	 * its metadata through getInfo. Invoked on a bounded background executor,
	 * outside cache locks, once per admitted entry. May decline publication based
	 * on size or other policy. Failures do not affect completed fetches.
	 *
	 * The same request key may be regenerated with different bytes after local
	 * expiry, repacking or concurrent generation on another server. Store pack
	 * objects immutably by checksum, then atomically update the key's metadata.
	 * Never overwrite bytes at an issued URL. Retain previously published objects
	 * while their URLs remain valid; local eviction does not revoke those URLs.
	 * Use the caller's versioned cache scope and remote retention/invalidation
	 * policy to retire outputs after permission or repository-history changes.
	 *
	 * @param key
	 *            the same exact key used by getInfo
	 * @param pack
	 *            self-contained bytes; valid only during the callback, closed by
	 *            the cache; must not be retained or closed by the implementation
	 * @param size
	 *            byte length
	 * @param checksum
	 *            trailer checksum to advertise, also native index-pack's hash
	 * @throws IOException
	 *             publication failed; not retried for this local entry
	 */
	void publish(String key, InputStream pack, long size, ObjectId checksum)
			throws IOException;
}
