/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.jgit.junit.ssh.SshBasicTestBase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

public class ApacheSshProtocol2Test extends SshBasicTestBase {

	@Override
	protected SshSessionFactory createSessionFactory() {
		return new SshdSessionFactoryBuilder()
				// No proxies in tests
				.setProxyDataFactory(null)
				// No ssh-agent in tests
				.setConnectorFactory(null)
				// The home directory is mocked at this point!
				.setHomeDirectory(FS.DETECTED.userHome())
				.setSshDirectory(sshDir)
				.build(new JGitKeyCache());
	}

	@Override
	protected void installConfig(String... config) {
		File configFile = new File(sshDir, Constants.CONFIG);
		if (config != null) {
			try {
				Files.write(configFile.toPath(), Arrays.asList(config));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		@SuppressWarnings("restriction")
		StoredConfig config = db.getConfig();
		config.setInt("protocol", null, "version", 2);
		config.save();
	}
}
