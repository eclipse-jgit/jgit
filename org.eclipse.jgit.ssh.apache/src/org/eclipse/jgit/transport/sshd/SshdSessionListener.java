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
 * negotiation, disconnect, and exception events for SSH sessions created by
 * {@link SshdSessionFactory}. Mirrors Apache MINA sshd's
 * {@code SessionListener} callbacks using JGit and JDK types only.
 * Implementations should be quick and non-blocking because callbacks are
 * invoked synchronously on SSH I/O or connection threads.
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
	 * SSH session milestone events reported via
	 * {@link #sessionEvent(SshdSessionContext, Event)}.
	 */
	enum Event {
		/** Session keys established ({@code SSH_MSG_NEWKEYS} exchanged). */
		KEY_ESTABLISHED,
		/** Session authentication succeeded. */
		AUTHENTICATED,
		/** Key-exchange negotiation proposals matched and KEX initialized. */
		KEX_COMPLETED
	}

	/**
	 * Invoked when an initial low-level session connection is established.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 */
	default void sessionEstablished(@NonNull SshdSessionContext context) {
		// Default no-op
	}

	/**
	 * Invoked when a new SSH session is created.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 */
	default void sessionCreated(@NonNull SshdSessionContext context) {
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
	default void sessionPeerIdentificationSend(
			@NonNull SshdSessionContext context, @NonNull String version,
			@NonNull List<String> extraLines) {
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
	default void sessionPeerIdentificationLine(
			@NonNull SshdSessionContext context, @NonNull String line,
			@NonNull List<String> extraLines) {
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
	default void sessionPeerIdentificationReceived(
			@NonNull SshdSessionContext context, @NonNull String version,
			@NonNull List<String> extraLines) {
		// Default no-op
	}

	/**
	 * Invoked when the local SSH key-exchange proposal is created, exposed as a
	 * read-only snapshot.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param proposal
	 *            unmodifiable map of key-exchange options to be sent
	 */
	default void sessionNegotiationOptionsCreated(
			@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> proposal) {
		// Default no-op
	}

	/**
	 * Invoked at the start of SSH key-exchange negotiation.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param clientProposal
	 *            unmodifiable map of client-proposed options
	 * @param serverProposal
	 *            unmodifiable map of server-proposed options
	 */
	default void sessionNegotiationStart(@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> clientProposal,
			@NonNull Map<KexOption, String> serverProposal) {
		// Default no-op
	}

	/**
	 * Invoked at the end of SSH key-exchange negotiation.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param clientProposal
	 *            unmodifiable map of client-proposed options
	 * @param serverProposal
	 *            unmodifiable map of server-proposed options
	 * @param negotiatedOptions
	 *            unmodifiable map of negotiated options (may be partial if
	 *            {@code reason} is non-{@code null})
	 * @param reason
	 *            failure cause if negotiation failed, or {@code null} on
	 *            success
	 */
	default void sessionNegotiationEnd(@NonNull SshdSessionContext context,
			@NonNull Map<KexOption, String> clientProposal,
			@NonNull Map<KexOption, String> serverProposal,
			@NonNull Map<KexOption, String> negotiatedOptions,
			@Nullable Throwable reason) {
		// Default no-op
	}

	/**
	 * Invoked when an SSH session milestone {@link Event} occurs.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param event
	 *            triggered session milestone event
	 */
	default void sessionEvent(@NonNull SshdSessionContext context,
			@NonNull Event event) {
		// Default no-op
	}

	/**
	 * Invoked when an exception is caught on the SSH session.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param t
	 *            caught exception
	 */
	default void sessionException(@NonNull SshdSessionContext context,
			@NonNull Throwable t) {
		// Default no-op
	}

	/**
	 * Invoked when an {@code SSH_MSG_DISCONNECT} packet is sent or received.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 * @param reason
	 *            RFC 4253 disconnect reason code
	 * @param msg
	 *            disconnect description (may be empty)
	 * @param language
	 *            language tag (may be empty)
	 * @param initiator
	 *            {@code true} if the local side sent the disconnect
	 */
	default void sessionDisconnect(@NonNull SshdSessionContext context,
			int reason, @NonNull String msg, @NonNull String language,
			boolean initiator) {
		// Default no-op
	}

	/**
	 * Invoked when the SSH session is closed.
	 *
	 * @param context
	 *            read-only snapshot of the SSH session
	 */
	default void sessionClosed(@NonNull SshdSessionContext context) {
		// Default no-op
	}
}
