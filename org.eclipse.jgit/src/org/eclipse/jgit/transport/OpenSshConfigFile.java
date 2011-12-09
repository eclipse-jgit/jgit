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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

class OpenSshConfigFile {

	private static final String ETC = "etc";

	private static final String SSH = "ssh";
	private static final String DOT_SSH = ".ssh";

	private static final String SSH_CONFIG = "ssh_config";
	private static final String CONFIG = "config";

	private static final String JGIT_SSH_SYSCONFDIR = "jgit.ssh.sysconfdir";

	static final OpenSshConfigFile IGNORED = new OpenSshConfigFile(null, null);

	static OpenSshConfigFile getUserConfigFile(final FS fs) {
		final File homeDir = getHomeDir(fs);
		final File userConfigDir = new File(homeDir, DOT_SSH);
		final File userConfigFile = new File(userConfigDir, CONFIG);
		return new OpenSshConfigFile(homeDir, userConfigFile);
	}

	static OpenSshConfigFile getSystemConfigFile(final FS fs) {
		final File homeDir = getHomeDir(fs);
		File systemConfigFile = getSystemConfigFileViaSystemProperty(fs);
		if (systemConfigFile == null) {
			systemConfigFile = getSystemConfigFileViaGitPrefix(fs);
			if (systemConfigFile == null)
				systemConfigFile = getDefaultSystemConfigFile(fs);
		}
		return new OpenSshConfigFile(homeDir, systemConfigFile);
	}

	private static File getHomeDir(final FS fs) {
		File homeDir = fs.userHome();
		if (homeDir == null)
			homeDir = new File(".").getAbsoluteFile();
		return homeDir;
	}

	private static File getSystemConfigFileViaSystemProperty(final FS fs) {
		final String sshSysConfDir = SystemReader.getInstance().getProperty(
				JGIT_SSH_SYSCONFDIR);
		if (sshSysConfDir != null) {
			final File systemConfigDir = fs.resolve(null, sshSysConfDir);
			final File systemConfigFile = new File(systemConfigDir, SSH_CONFIG);
			if (systemConfigFile.exists())
				return systemConfigFile;
		}
		return null;
	}

	private static File getSystemConfigFileViaGitPrefix(final FS fs) {
		final File gitPrefix = fs.gitPrefix();
		if (gitPrefix != null) {
			final File etcDir = fs.resolve(gitPrefix, ETC);
			final File systemConfigDir = new File(etcDir, SSH);
			final File systemConfigFile = new File(systemConfigDir, SSH_CONFIG);
			if (systemConfigFile.exists())
				return systemConfigFile;
		}
		return null;
	}

	private static File getDefaultSystemConfigFile(final FS fs) {
		final File etcDir = fs.resolve(null, "/" + ETC);
		final File systemConfigDir = new File(etcDir, SSH);
		final File systemConfigFile = new File(systemConfigDir, SSH_CONFIG);
		return systemConfigFile;
	}

	/** The user's home directory, as key files may be relative to here (~/). */
	private final File homeDir;

	/** The configuration file we read and monitor for updates. */
	private final File configFile;

	/** Modification time of {@link #configFile} when {@link #hosts} loaded. */
	private long lastModified;

	/** Cached entries read out of the configuration file. */
	private Map<String, Host> hosts;

	private OpenSshConfigFile(final File homeDir, final File configFile) {
		this.homeDir = homeDir;
		this.configFile = configFile;
		hosts = Collections.emptyMap();
	}

	boolean exists() {
		return configFile.exists();
	}

	synchronized boolean hasChanged() {
		return configFile != null && configFile.lastModified() != lastModified;
	}

	synchronized Map<String, Host> read() {
		if (configFile == null || !configFile.exists())
			return Collections.emptyMap();

		final long mtime = configFile.lastModified();
		if (mtime == lastModified)
			return hosts;

		try {
			final FileInputStream in = new FileInputStream(configFile);
			try {
				hosts = parse(in);
			} finally {
				in.close();
			}
		} catch (FileNotFoundException none) {
			hosts = Collections.emptyMap();
		} catch (IOException err) {
			hosts = Collections.emptyMap();
		}
		lastModified = mtime;

		return hosts;
	}

	private Map<String, Host> parse(final InputStream in) throws IOException {
		final Map<String, Host> m = new LinkedHashMap<String, Host>();
		final BufferedReader br = new BufferedReader(new InputStreamReader(in));
		final List<Host> current = new ArrayList<Host>(4);
		String line;

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#"))
				continue;

			final String[] parts = line.split("[ \t]*[= \t]", 2);
			final String keyword = parts[0].trim();
			final String argValue = parts[1].trim();

			if (StringUtils.equalsIgnoreCase("Host", keyword)) {
				current.clear();
				for (final String pattern : argValue.split("[ \t]")) {
					final String name = dequote(pattern);
					Host c = m.get(name);
					if (c == null) {
						c = new Host();
						m.put(name, c);
					}
					current.add(c);
				}
				continue;
			}

			if (current.isEmpty()) {
				// We received an option outside of a Host block. We
				// don't know who this should match against, so skip.
				//
				continue;
			}

			if (StringUtils.equalsIgnoreCase("HostName", keyword)) {
				for (final Host c : current)
					if (c.hostName == null)
						c.hostName = dequote(argValue);
			} else if (StringUtils.equalsIgnoreCase("User", keyword)) {
				for (final Host c : current)
					if (c.user == null)
						c.user = dequote(argValue);
			} else if (StringUtils.equalsIgnoreCase("Port", keyword)) {
				try {
					final int port = Integer.parseInt(dequote(argValue));
					for (final Host c : current)
						if (c.port == 0)
							c.port = port;
				} catch (NumberFormatException nfe) {
					// Bad port number. Don't set it.
				}
			} else if (StringUtils.equalsIgnoreCase("IdentityFile", keyword)) {
				for (final Host c : current)
					if (c.identityFile == null)
						c.identityFile = toFile(dequote(argValue));
			} else if (StringUtils.equalsIgnoreCase("PreferredAuthentications",
					keyword)) {
				for (final Host c : current)
					if (c.preferredAuthentications == null)
						c.preferredAuthentications = nows(dequote(argValue));
			} else if (StringUtils.equalsIgnoreCase("BatchMode", keyword)) {
				for (final Host c : current)
					if (c.batchMode == null)
						c.batchMode = yesno(dequote(argValue));
			} else if (StringUtils.equalsIgnoreCase("StrictHostKeyChecking",
					keyword)) {
				String value = dequote(argValue);
				for (final Host c : current)
					if (c.strictHostKeyChecking == null)
						c.strictHostKeyChecking = value;
			}
		}

		return m;
	}

	private File toFile(final String path) {
		if (path.startsWith("~/"))
			return new File(homeDir, path.substring(2));
		File ret = new File(path);
		if (ret.isAbsolute())
			return ret;
		return new File(homeDir, path);
	}

	private static String dequote(final String value) {
		if (value.startsWith("\"") && value.endsWith("\""))
			return value.substring(1, value.length() - 1);
		return value;
	}

	private static String nows(final String value) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isSpaceChar(value.charAt(i)))
				b.append(value.charAt(i));
		}
		return b.toString();
	}

	private static Boolean yesno(final String value) {
		if (StringUtils.equalsIgnoreCase("yes", value))
			return Boolean.TRUE;
		return Boolean.FALSE;
	}
}
