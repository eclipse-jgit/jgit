/*
 * Copyright (C) 2026 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.net.SocketAddress;
import java.util.Objects;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.URIish;

/**
 * Read-only snapshot of an SSH session's identity, endpoints, protocol
 * versions, and state at the time a {@link SshdSessionListener} event was
 * emitted.
 *
 * @since 8.0
 */
public final class SshdSessionContext {

	private final @NonNull URIish uri;

	private final @Nullable SocketAddress localAddress;

	private final @Nullable SocketAddress remoteAddress;

	private final @Nullable String username;

	private final @Nullable String clientVersion;

	private final @Nullable String serverVersion;

	@Nullable
	private final byte[] sessionId;

	private final boolean authenticated;

	private final boolean open;

	/**
	 * Creates a read-only SSH session context snapshot.
	 *
	 * @param uri
	 *            target Git remote URI
	 * @param localAddress
	 *            local socket address, or {@code null}
	 * @param remoteAddress
	 *            peer socket address, or {@code null}
	 * @param username
	 *            SSH username, or {@code null}
	 * @param clientVersion
	 *            client SSH identification string, or {@code null}
	 * @param serverVersion
	 *            server SSH identification string, or {@code null}
	 * @param sessionId
	 *            established SSH session identifier, or {@code null}
	 * @param authenticated
	 *            whether authentication has completed
	 * @param open
	 *            whether the underlying session is open
	 */
	public SshdSessionContext(@NonNull URIish uri,
			@Nullable SocketAddress localAddress,
			@Nullable SocketAddress remoteAddress, @Nullable String username,
			@Nullable String clientVersion, @Nullable String serverVersion,
			@Nullable byte[] sessionId, boolean authenticated, boolean open) {
		this.uri = Objects.requireNonNull(uri);
		this.localAddress = localAddress;
		this.remoteAddress = remoteAddress;
		this.username = username;
		this.clientVersion = clientVersion;
		this.serverVersion = serverVersion;
		this.sessionId = sessionId != null ? sessionId.clone() : null;
		this.authenticated = authenticated;
		this.open = open;
	}

	/**
	 * Returns the target Git remote URI associated with the session.
	 *
	 * @return the {@link URIish}
	 */
	public @NonNull URIish getUri() {
		return uri;
	}

	/**
	 * Returns the local socket endpoint for this connection, or {@code null}
	 * if not yet resolved.
	 *
	 * @return the local {@link SocketAddress}, or {@code null}
	 */
	public @Nullable SocketAddress getLocalAddress() {
		return localAddress;
	}

	/**
	 * Returns the peer socket endpoint for this connection, or {@code null} if
	 * not yet resolved. For {@code ProxyJump} configurations this identifies
	 * the specific hop or target connection.
	 *
	 * @return the peer {@link SocketAddress}, or {@code null}
	 */
	public @Nullable SocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	/**
	 * Returns the SSH username associated with this session, or {@code null}
	 * if not yet known.
	 *
	 * @return the username, or {@code null}
	 */
	public @Nullable String getUsername() {
		return username;
	}

	/**
	 * Returns the client SSH identification string (for example
	 * {@code SSH-2.0-...}), or {@code null} if not yet exchanged.
	 *
	 * @return the client version string, or {@code null}
	 */
	public @Nullable String getClientVersion() {
		return clientVersion;
	}

	/**
	 * Returns the server SSH identification string (for example
	 * {@code SSH-2.0-...}), or {@code null} if not yet exchanged.
	 *
	 * @return the server version string, or {@code null}
	 */
	public @Nullable String getServerVersion() {
		return serverVersion;
	}

	/**
	 * Returns a copy of the established SSH session identifier, or
	 * {@code null} if key exchange has not established one yet.
	 *
	 * @return a clone of the session identifier, or {@code null}
	 */
	@Nullable
	public byte[] getSessionId() {
		return sessionId != null ? sessionId.clone() : null;
	}

	/**
	 * Returns whether the session has completed authentication.
	 *
	 * @return {@code true} if authenticated
	 */
	public boolean isAuthenticated() {
		return authenticated;
	}

	/**
	 * Returns whether the underlying session was open when this snapshot was
	 * taken.
	 *
	 * @return {@code true} if open
	 */
	public boolean isOpen() {
		return open;
	}
}
