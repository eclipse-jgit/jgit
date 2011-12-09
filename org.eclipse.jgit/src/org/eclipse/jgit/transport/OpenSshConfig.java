/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.util.FS;

/**
 * Simple parser for the OpenSSH configuration file.
 * <p>
 * Since JSch does not (currently) have the ability to parse an OpenSSH
 * configuration file this is a simple parser to read that file and make the
 * critical options available to {@link SshSessionFactory}.
 */
public class OpenSshConfig {
	/** IANA assigned port number for SSH. */
	private static final int SSH_PORT = 22;

	/**
	 * Obtain the user's configuration data.
	 * <p>
	 * A configuration data instance is always returned to the caller, even if
	 * no configuration file exists in the user's home directory at the time the
	 * call was made. Lookup requests are cached and are automatically updated
	 * if the user modifies the configuration file since the last time it was
	 * cached.
	 *
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return a caching reader of the configuration data.
	 */
	public static OpenSshConfig get(final FS fs) {
		final OpenSshConfigFile userConfigFile = OpenSshConfigFile
				.getUserConfigFile(fs);
		return new OpenSshConfig(userConfigFile);
	}

	/** The user's configuration file. */
	private final OpenSshConfigFile userConfigFile;

	/** The cached configuration data. */
	private volatile Map<String, Host> cache;

	private OpenSshConfig(final OpenSshConfigFile userConfigFile) {
		this.userConfigFile = userConfigFile;
		cache = Collections.emptyMap();
	}

	/**
	 * Locate the configuration for a specific host request.
	 *
	 * @param hostName
	 *            the name the user has supplied to the SSH tool. This may be a
	 *            real host name, or it may just be a "Host" block in the
	 *            configuration file.
	 * @return r configuration for the requested name. Never null.
	 */
	public Host lookup(final String hostName) {
		if (userConfigFile.hasChanged())
			updateCache();

		Host h = cache.get(hostName);
		if (h == null)
			h = new Host();
		if (h.patternsApplied)
			return h;

		for (final Map.Entry<String, Host> e : cache.entrySet()) {
			if (!isHostPattern(e.getKey()))
				continue;
			if (!isHostMatch(e.getKey(), hostName))
				continue;
			h.copyFrom(e.getValue());
		}

		if (h.hostName == null)
			h.hostName = hostName;
		if (h.user == null)
			h.user = OpenSshConfig.userName();
		if (h.port == 0)
			h.port = OpenSshConfig.SSH_PORT;
		h.patternsApplied = true;
		return h;
	}

	private void updateCache() {
		if (userConfigFile.exists())
			cache = userConfigFile.read();
		else
			cache = Collections.emptyMap();
	}

	private static boolean isHostPattern(final String s) {
		return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
	}

	private static boolean isHostMatch(final String pattern, final String name) {
		final FileNameMatcher fn;
		try {
			fn = new FileNameMatcher(pattern, null);
		} catch (InvalidPatternException e) {
			return false;
		}
		fn.append(name);
		return fn.isMatch();
	}

	private static String userName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			public String run() {
				return System.getProperty("user.name");
			}
		});
	}

	/**
	 * Configuration of one "Host" block in the configuration file.
	 * <p>
	 * If returned from {@link OpenSshConfig#lookup(String)} some or all of the
	 * properties may not be populated. The properties which are not populated
	 * should be defaulted by the caller.
	 * <p>
	 * When returned from {@link OpenSshConfig#lookup(String)} any wildcard
	 * entries which appear later in the configuration file will have been
	 * already merged into this block.
	 */
	public static class Host {
		boolean patternsApplied;

		String hostName;

		int port;

		File identityFile;

		String user;

		String preferredAuthentications;

		Boolean batchMode;

		String strictHostKeyChecking;

		void copyFrom(final Host src) {
			if (hostName == null)
				hostName = src.hostName;
			if (port == 0)
				port = src.port;
			if (identityFile == null)
				identityFile = src.identityFile;
			if (user == null)
				user = src.user;
			if (preferredAuthentications == null)
				preferredAuthentications = src.preferredAuthentications;
			if (batchMode == null)
				batchMode = src.batchMode;
			if (strictHostKeyChecking == null)
				strictHostKeyChecking = src.strictHostKeyChecking;
		}

		/**
		 * @return the value StrictHostKeyChecking property, the valid values
		 *         are "yes" (unknown hosts are not accepted), "no" (unknown
		 *         hosts are always accepted), and "ask" (user should be asked
		 *         before accepting the host)
		 */
		public String getStrictHostKeyChecking() {
			return strictHostKeyChecking;
		}

		/**
		 * @return the real IP address or host name to connect to; never null.
		 */
		public String getHostName() {
			return hostName;
		}

		/**
		 * @return the real port number to connect to; never 0.
		 */
		public int getPort() {
			return port;
		}

		/**
		 * @return path of the private key file to use for authentication; null
		 *         if the caller should use default authentication strategies.
		 */
		public File getIdentityFile() {
			return identityFile;
		}

		/**
		 * @return the real user name to connect as; never null.
		 */
		public String getUser() {
			return user;
		}

		/**
		 * @return the preferred authentication methods, separated by commas if
		 *         more than one authentication method is preferred.
		 */
		public String getPreferredAuthentications() {
			return preferredAuthentications;
		}

		/**
		 * @return true if batch (non-interactive) mode is preferred for this
		 *         host connection.
		 */
		public boolean isBatchMode() {
			return batchMode != null && batchMode.booleanValue();
		}
	}
}
