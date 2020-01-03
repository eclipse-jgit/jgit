/*
 * Copyright (C) 2019 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * An interface for a database of known server keys, supporting finding all
 * known keys and also deciding whether a server key is to be accepted.
 * <p>
 * Connection addresses are given as strings of the format
 * {@code [hostName]:port} if using a non-standard port (i.e., not port 22),
 * otherwise just {@code hostname}.
 * </p>
 *
 * @since 5.5
 */
public interface ServerKeyDatabase {

	/**
	 * Retrieves all known host keys for the given addresses.
	 *
	 * @param connectAddress
	 *            IP address the session tried to connect to
	 * @param remoteAddress
	 *            IP address as reported for the remote end point
	 * @param config
	 *            giving access to potentially interesting configuration
	 *            settings
	 * @return the list of known keys for the given addresses
	 */
	@NonNull
	List<PublicKey> lookup(@NonNull String connectAddress,
			@NonNull InetSocketAddress remoteAddress,
			@NonNull Configuration config);

	/**
	 * Determines whether to accept a received server host key.
	 *
	 * @param connectAddress
	 *            IP address the session tried to connect to
	 * @param remoteAddress
	 *            IP address as reported for the remote end point
	 * @param serverKey
	 *            received from the remote end
	 * @param config
	 *            giving access to potentially interesting configuration
	 *            settings
	 * @param provider
	 *            for interacting with the user, if required; may be
	 *            {@code null}
	 * @return {@code true} if the serverKey is accepted, {@code false}
	 *         otherwise
	 */
	boolean accept(@NonNull String connectAddress,
			@NonNull InetSocketAddress remoteAddress,
			@NonNull PublicKey serverKey,
			@NonNull Configuration config, CredentialsProvider provider);

	/**
	 * A simple provider for ssh config settings related to host key checking.
	 * An instance is created by the JGit sshd framework and passed into
	 * {@link ServerKeyDatabase#lookup(String, InetSocketAddress, Configuration)}
	 * and
	 * {@link ServerKeyDatabase#accept(String, InetSocketAddress, PublicKey, Configuration, CredentialsProvider)}.
	 */
	interface Configuration {

		/**
		 * Retrieves the list of file names from the "UserKnownHostsFile" ssh
		 * config.
		 *
		 * @return the list as configured, with ~ already replaced
		 */
		List<String> getUserKnownHostsFiles();

		/**
		 * Retrieves the list of file names from the "GlobalKnownHostsFile" ssh
		 * config.
		 *
		 * @return the list as configured, with ~ already replaced
		 */
		List<String> getGlobalKnownHostsFiles();

		/**
		 * The possible values for the "StrictHostKeyChecking" ssh config.
		 */
		enum StrictHostKeyChecking {
			/**
			 * "ask"; default: ask the user whether to accept (and store) a new
			 * or mismatched key.
			 */
			ASK,
			/**
			 * "yes", "on": never accept new or mismatched keys.
			 */
			REQUIRE_MATCH,
			/**
			 * "no", "off": always accept new or mismatched keys.
			 */
			ACCEPT_ANY,
			/**
			 * "accept-new": accept new keys, but never accept modified keys.
			 */
			ACCEPT_NEW
		}

		/**
		 * Obtains the value of the "StrictHostKeyChecking" ssh config.
		 *
		 * @return the {@link StrictHostKeyChecking}
		 */
		@NonNull
		StrictHostKeyChecking getStrictHostKeyChecking();

		/**
		 * Obtains the value of the "HashKnownHosts" ssh config.
		 *
		 * @return {@code true} if new entries should be stored with hashed host
		 *         information, {@code false} otherwise
		 */
		boolean getHashKnownHosts();

		/**
		 * Obtains the user name used in the connection attempt.
		 *
		 * @return the user name
		 */
		@NonNull
		String getUsername();
	}
}
