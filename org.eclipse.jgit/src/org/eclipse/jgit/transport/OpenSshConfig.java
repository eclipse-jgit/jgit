/*
 * Copyright (C) 2008, 2018, Google Inc.
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

import static org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.positive;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile;
import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.HostEntry;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.ConfigRepository;

/**
 * Fairly complete configuration parser for the OpenSSH ~/.ssh/config file.
 * <p>
 * JSch does have its own config file parser
 * {@link com.jcraft.jsch.OpenSSHConfig} since version 0.1.50, but it has a
 * number of problems:
 * <ul>
 * <li>it splits lines of the format "keyword = value" wrongly: you'd end up
 * with the value "= value".
 * <li>its "Host" keyword is not case insensitive.
 * <li>it doesn't handle quoted values.
 * <li>JSch's OpenSSHConfig doesn't monitor for config file changes.
 * </ul>
 * <p>
 * This parser makes the critical options available to
 * {@link org.eclipse.jgit.transport.SshSessionFactory} via
 * {@link org.eclipse.jgit.transport.OpenSshConfig.Host} objects returned by
 * {@link #lookup(String)}, and implements a fully conforming
 * {@link com.jcraft.jsch.ConfigRepository} providing
 * {@link com.jcraft.jsch.ConfigRepository.Config}s via
 * {@link #getConfig(String)}.
 * </p>
 *
 * @see OpenSshConfigFile
 */
public class OpenSshConfig implements ConfigRepository {

	/**
	 * Obtain the user's configuration data.
	 * <p>
	 * The configuration file is always returned to the caller, even if no file
	 * exists in the user's home directory at the time the call was made. Lookup
	 * requests are cached and are automatically updated if the user modifies
	 * the configuration file since the last time it was cached.
	 *
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @return a caching reader of the user's configuration file.
	 */
	public static OpenSshConfig get(FS fs) {
		File home = fs.userHome();
		if (home == null)
			home = new File(".").getAbsoluteFile(); //$NON-NLS-1$

		final File config = new File(new File(home, SshConstants.SSH_DIR),
				SshConstants.CONFIG);
		return new OpenSshConfig(home, config);
	}

	/** The base file. */
	private OpenSshConfigFile configFile;

	OpenSshConfig(File h, File cfg) {
		configFile = new OpenSshConfigFile(h, cfg,
				SshSessionFactory.getLocalUserName());
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
	public Host lookup(String hostName) {
		HostEntry entry = configFile.lookup(hostName, -1, null);
		return new Host(entry, hostName, configFile.getLocalUserName());
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
		String hostName;

		int port;

		File identityFile;

		String user;

		String preferredAuthentications;

		Boolean batchMode;

		String strictHostKeyChecking;

		int connectionAttempts;

		private HostEntry entry;

		private Config config;

		// See com.jcraft.jsch.OpenSSHConfig. Translates some command-line keys
		// to ssh-config keys.
		private static final Map<String, String> KEY_MAP = new TreeMap<>(
				String.CASE_INSENSITIVE_ORDER);

		static {
			KEY_MAP.put("kex", SshConstants.KEX_ALGORITHMS); //$NON-NLS-1$
			KEY_MAP.put("server_host_key", SshConstants.HOST_KEY_ALGORITHMS); //$NON-NLS-1$
			KEY_MAP.put("cipher.c2s", SshConstants.CIPHERS); //$NON-NLS-1$
			KEY_MAP.put("cipher.s2c", SshConstants.CIPHERS); //$NON-NLS-1$
			KEY_MAP.put("mac.c2s", SshConstants.MACS); //$NON-NLS-1$
			KEY_MAP.put("mac.s2c", SshConstants.MACS); //$NON-NLS-1$
			KEY_MAP.put("compression.s2c", SshConstants.COMPRESSION); //$NON-NLS-1$
			KEY_MAP.put("compression.c2s", SshConstants.COMPRESSION); //$NON-NLS-1$
			KEY_MAP.put("compression_level", "CompressionLevel"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("MaxAuthTries", //$NON-NLS-1$
					SshConstants.NUMBER_OF_PASSWORD_PROMPTS);
		}

		private static String mapKey(String key) {
			String k = KEY_MAP.get(key);
			return k != null ? k : key;
		}

		/**
		 * Creates a new uninitialized {@link Host}.
		 */
		public Host() {
			// For API backwards compatibility with pre-4.9 JGit
		}

		Host(HostEntry entry, String hostName, String localUserName) {
			this.entry = entry;
			complete(hostName, localUserName);
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

		/**
		 * @return the number of tries (one per second) to connect before
		 *         exiting. The argument must be an integer. This may be useful
		 *         in scripts if the connection sometimes fails. The default is
		 *         1.
		 * @since 3.4
		 */
		public int getConnectionAttempts() {
			return connectionAttempts;
		}


		private void complete(String initialHostName, String localUserName) {
			// Try to set values from the options.
			hostName = entry.getValue(SshConstants.HOST_NAME);
			user = entry.getValue(SshConstants.USER);
			port = positive(entry.getValue(SshConstants.PORT));
			connectionAttempts = positive(
					entry.getValue(SshConstants.CONNECTION_ATTEMPTS));
			strictHostKeyChecking = entry
					.getValue(SshConstants.STRICT_HOST_KEY_CHECKING);
			batchMode = Boolean.valueOf(OpenSshConfigFile
					.flag(entry.getValue(SshConstants.BATCH_MODE)));
			preferredAuthentications = entry
					.getValue(SshConstants.PREFERRED_AUTHENTICATIONS);
			// Fill in defaults if still not set
			if (hostName == null || hostName.isEmpty()) {
				hostName = initialHostName;
			}
			if (user == null || user.isEmpty()) {
				user = localUserName;
			}
			if (port <= 0) {
				port = SshConstants.SSH_DEFAULT_PORT;
			}
			if (connectionAttempts <= 0) {
				connectionAttempts = 1;
			}
			List<String> identityFiles = entry
					.getValues(SshConstants.IDENTITY_FILE);
			if (identityFiles != null && !identityFiles.isEmpty()) {
				identityFile = new File(identityFiles.get(0));
			}
		}

		Config getConfig() {
			if (config == null) {
				config = new Config() {

					@Override
					public String getHostname() {
						return Host.this.getHostName();
					}

					@Override
					public String getUser() {
						return Host.this.getUser();
					}

					@Override
					public int getPort() {
						return Host.this.getPort();
					}

					@Override
					public String getValue(String key) {
						// See com.jcraft.jsch.OpenSSHConfig.MyConfig.getValue()
						// for this special case.
						if (key.equals("compression.s2c") //$NON-NLS-1$
								|| key.equals("compression.c2s")) { //$NON-NLS-1$
							if (!OpenSshConfigFile.flag(
									Host.this.entry.getValue(mapKey(key)))) {
								return "none,zlib@openssh.com,zlib"; //$NON-NLS-1$
							}
							return "zlib@openssh.com,zlib,none"; //$NON-NLS-1$
						}
						return Host.this.entry.getValue(mapKey(key));
					}

					@Override
					public String[] getValues(String key) {
						List<String> values = Host.this.entry
								.getValues(mapKey(key));
						if (values == null) {
							return new String[0];
						}
						return values.toArray(new String[0]);
					}
				};
			}
			return config;
		}

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "Host [hostName=" + hostName + ", port=" + port
					+ ", identityFile=" + identityFile + ", user=" + user
					+ ", preferredAuthentications=" + preferredAuthentications
					+ ", batchMode=" + batchMode + ", strictHostKeyChecking="
					+ strictHostKeyChecking + ", connectionAttempts="
					+ connectionAttempts + ", entry=" + entry + "]";
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Retrieves the full {@link com.jcraft.jsch.ConfigRepository.Config Config}
	 * for the given host name. Should be called only by Jsch and tests.
	 *
	 * @since 4.9
	 */
	@Override
	public Config getConfig(String hostName) {
		Host host = lookup(hostName);
		return host.getConfig();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "OpenSshConfig [configFile=" + configFile + ']'; //$NON-NLS-1$
	}
}
