/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.eclipse.jgit.annotations.NonNull;

/**
 * A {@link HostConfigEntry} that provides access to the multi-valued keys as
 * lists of strings. The super class treats them as single strings containing
 * comma-separated lists.
 */
public class JGitHostConfigEntry extends HostConfigEntry {

	private Map<String, List<String>> multiValuedOptions;

	/**
	 * Sets the multi-valued options.
	 *
	 * @param options
	 *            to set, may be {@code null} to set an empty map
	 */
	public void setMultiValuedOptions(Map<String, List<String>> options) {
		multiValuedOptions = options;
	}

	/**
	 * Retrieves all multi-valued options.
	 *
	 * @return an unmodifiable map
	 */
	@NonNull
	public Map<String, List<String>> getMultiValuedOptions() {
		Map<String, List<String>> options = multiValuedOptions;
		if (options == null) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(options);
	}

}
