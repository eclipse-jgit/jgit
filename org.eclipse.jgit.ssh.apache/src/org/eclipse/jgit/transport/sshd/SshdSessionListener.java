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

import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Listener notified of read-only lifecycle, identification, key-exchange
 * negotiation, disconnect, and failure events for SSH sessions created by
 * {@link SshdSessionFactory}. Implementations should be quick and non-blocking
 * because callbacks are invoked synchronously on SSH I/O or connection threads.
 *
 * @since 8.0
 */
public interface SshdSessionListener {

	/**
	 * Key-exchange proposal categories defined by RFC 4253 section 7.1.
	 */
	enum KexOption {
		/** Key-exchange algorithms ({@code kex_algorithms}). */
		KEX_ALGORITHMS,
		/** Server host key algorithms ({@code server_host_key_algorithms}). */
		SERVER_HOST_KEY_ALGORITHMS,
		/**
		 * Client-to-server ciphers
		 * ({@code encryption_algorithms_client_to_server}).
		 */
		CLIENT_TO_SERVER_CIPHERS,
		/**
		 * Server-to-client ciphers
		 * ({@code encryption_algorithms_server_to_client}).
		 */
		SERVER_TO_CLIENT_CIPHERS,
		/** Client-to-server MACs ({@code mac_algorithms_client_to_server}). */
		CLIENT_TO_SERVER_MACS,
		/** Server-to-client MACs ({@code mac_algorithms_server_to_client}). */
		SERVER_TO_CLIENT_MACS,
		/**
		 * Client-to-server compression
		 * ({@code compression_algorithms_client_to_server}).
		 */
		CLIENT_TO_SERVER_COMPRESSION,
		/**
		 * Server-to-client compression
		 * ({@code compression_algorithms_server_to_client}).
		 */
		SERVER_TO_CLIENT_COMPRESSION,
		/**
		 * Client-to-server languages ({@code languages_client_to_server}).
		 */
		CLIENT_TO_SERVER_LANGUAGES,
		/**
		 * Server-to-client languages ({@code languages_server_to_client}).
		 */
		SERVER_TO_CLIENT_LANGUAGES
	}

	/**
	 * SSH session lifecycle states.
	 */
	enum State {
		/** Low-level transport connection established. */
		ESTABLISHED,
		/** Session object created. */
		CREATED,
		/** Key-exchange negotiation initialized. */
		KEX_COMPLETED,
		/** Session keys established ({@code SSH_MSG_NEWKEYS} exchanged). */
		KEY_ESTABLISHED,
		/** Session authentication succeeded. */
		AUTHENTICATED,
		/** Session closed. */
		CLOSED
	}

	/**
	 * Invoked when an SSH session reaches a lifecycle {@link State}.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param state
	 *            reached lifecycle state
	 */
	default void onStateChanged(@NonNull SshdSessionContext context,
			@NonNull State state) {
		// Default no-op
	}

	/**
	 * Invoked when the client is about to send its SSH identification string.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param version
	 *            resolved SSH identification version string
	 * @param extraLines
	 *            unmodifiable list of extra lines preceding the identification
	 */
	default void onPeerIdentificationSend(@NonNull SshdSessionContext context,
			@NonNull String version, @NonNull List<String> extraLines) {
		// Default no-op
	}

	/**
	 * Invoked when a line preceding the peer's SSH identification banner is
	 * read.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param line
	 *            identification data read so far
	 * @param extraLines
	 *            unmodifiable list of previous lines before this one
	 */
	default void onPeerIdentificationLine(@NonNull SshdSessionContext context,
			@NonNull String line, @NonNull List<String> extraLines) {
		// Default no-op
	}

	/**
	 * Invoked when the peer's SSH identification version string is received.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param version
	 *            received SSH identification version string
	 * @param extraLines
	 *            unmodifiable list of extra lines preceding the identification
	 */
	default void onPeerIdentificationReceived(
			@NonNull SshdSessionContext context, @NonNull String version,
			@NonNull List<String> extraLines) {
		// Default no-op
	}

	/**
	 * Invoked when the local SSH key-exchange proposal is created, exposed as
	 * a read-only snapshot.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param proposal
	 *            unmodifiable map of key-exchange options to be sent
	 */
	default void onNegotiationOptionsCreated(
			@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> proposal) {
		// Default no-op
	}

	/**
	 * Invoked at the start of SSH key-exchange option negotiation.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param clientProposal
	 *            unmodifiable map of algorithms offered by the client
	 * @param serverProposal
	 *            unmodifiable map of algorithms offered by the server
	 */
	default void onNegotiationStart(@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> clientProposal,
			@NonNull Map<KexOption, String> serverProposal) {
		// Default no-op
	}

	/**
	 * Invoked at the end of SSH key-exchange option negotiation, on both
	 * success and failure.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param clientProposal
	 *            unmodifiable map of algorithms offered by the client
	 * @param serverProposal
	 *            unmodifiable map of algorithms offered by the server
	 * @param negotiatedOptions
	 *            unmodifiable map of negotiated algorithms so far
	 * @param error
	 *            failure cause if negotiation failed, or {@code null} on
	 *            success
	 */
	default void onNegotiationEnd(@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> clientProposal,
			@NonNull Map<KexOption, String> serverProposal,
			@NonNull Map<KexOption, String> negotiatedOptions,
			@Nullable Throwable error) {
		// Default no-op
	}

	/**
	 * Invoked when an {@code SSH_MSG_DISCONNECT} message is sent or received.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param reasonCode
	 *            RFC 4253 disconnect reason code
	 * @param message
	 *            disconnect description (possibly empty)
	 * @param language
	 *            language tag (possibly empty)
	 * @param initiator
	 *            {@code true} if sent locally, {@code false} if received from
	 *            the remote peer
	 */
	default void onDisconnected(@NonNull SshdSessionContext context,
			int reasonCode, @NonNull String message, @NonNull String language,
			boolean initiator) {
		// Default no-op
	}

	/**
	 * Invoked when an SSH connection or session fails with an exception.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param cause
	 *            the failure cause
	 */
	default void onSessionFailed(@NonNull SshdSessionContext context,
			@NonNull Throwable cause) {
		// Default no-op
	}
}
