/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
 *
 */
public class JGitHostConfigEntry extends HostConfigEntry {

	private Map<String, List<String>> multiValuedOptions;

	@Override
	public String getProperty(String name, String defaultValue) {
		// Upstream bug fix (SSHD-867): if there are _no_ properties at all, the
		// super implementation returns always null even if a default value is
		// given.
		//
		// See https://issues.apache.org/jira/projects/SSHD/issues/SSHD-867
		//
		// TODO: remove this override once we're based on sshd > 2.1.0
		Map<String, String> properties = getProperties();
		if (properties == null || properties.isEmpty()) {
			return defaultValue;
		}
		return super.getProperty(name, defaultValue);
	}

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
