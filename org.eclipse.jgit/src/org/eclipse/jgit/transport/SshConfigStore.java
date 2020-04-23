/*
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.NonNull;

/**
 * An abstraction for a SSH config storage, like the OpenSSH ~/.ssh/config file.
 *
 * @since 5.8
 */
public interface SshConfigStore {

	/**
	 * Locate the configuration for a specific host request.
	 *
	 * @param hostName
	 *            to look up
	 * @param port
	 *            the user supplied; <= 0 if none
	 * @param userName
	 *            the user supplied, may be {@code null} or empty if none given
	 * @return the configuration for the requested name.
	 */
	@NonNull
	HostConfig lookup(@NonNull String hostName, int port, String userName);

	/**
	 * A host entry from the ssh config. Any merging of global values and of
	 * several matching host entries, %-substitutions, and ~ replacement have
	 * all been done.
	 */
	interface HostConfig {

		/**
		 * Retrieves the value of a single-valued key, or the first if the key
		 * has multiple values. Keys are case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the value of
		 * @return the value, or {@code null} if none
		 */
		String getValue(String key);

		/**
		 * Retrieves the values of a multi- or list-valued key. Keys are
		 * case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the values of
		 * @return a possibly empty list of values
		 */
		List<String> getValues(String key);

		/**
		 * Retrieves an unmodifiable map of all single-valued options, with
		 * case-insensitive lookup by keys.
		 *
		 * @return all single-valued options
		 */
		@NonNull
		Map<String, String> getOptions();

		/**
		 * Retrieves an unmodifiable map of all multi- or list-valued options,
		 * with case-insensitive lookup by keys.
		 *
		 * @return all multi-valued options
		 */
		@NonNull
		Map<String, List<String>> getMultiValuedOptions();

	}

	/**
	 * An empty {@link HostConfig}.
	 */
	static final HostConfig EMPTY_CONFIG = new HostConfig() {

		@Override
		public String getValue(String key) {
			return null;
		}

		@Override
		public List<String> getValues(String key) {
			return Collections.emptyList();
		}

		@Override
		public Map<String, String> getOptions() {
			return Collections.emptyMap();
		}

		@Override
		public Map<String, List<String>> getMultiValuedOptions() {
			return Collections.emptyMap();
		}

	};
}
