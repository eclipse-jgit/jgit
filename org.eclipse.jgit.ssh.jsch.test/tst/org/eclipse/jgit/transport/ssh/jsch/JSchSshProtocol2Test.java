/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

//TODO(ms): move to org.eclipse.jgit.ssh.jsch in 6.0
package org.eclipse.jgit.transport.ssh.jsch;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.junit.ssh.SshBasicTestBase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class JSchSshProtocol2Test extends SshBasicTestBase {

	private class TestSshSessionFactory extends JschConfigSessionFactory {

		@Override
		protected void configure(Host hc, Session session) {
			// Nothing
		}

		@Override
		public synchronized RemoteSession getSession(URIish uri,
				CredentialsProvider credentialsProvider, FS fs, int tms)
				throws TransportException {
			return super.getSession(uri, credentialsProvider, fs, tms);
		}

		@Override
		protected JSch createDefaultJSch(FS fs) throws JSchException {
			JSch defaultJSch = super.createDefaultJSch(fs);
			if (knownHosts.exists()) {
				defaultJSch.setKnownHosts(knownHosts.getAbsolutePath());
			}
			return defaultJSch;
		}
	}

	@Override
	protected SshSessionFactory createSessionFactory() {
		return new TestSshSessionFactory();
	}

	@Override
	protected void installConfig(String... config) {
		SshSessionFactory factory = getSessionFactory();
		assertTrue(factory instanceof JschConfigSessionFactory);
		JschConfigSessionFactory j = (JschConfigSessionFactory) factory;
		try {
			j.setConfig(createConfig(config));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private OpenSshConfig createConfig(String... content) throws IOException {
		File configFile = new File(sshDir, Constants.CONFIG);
		if (content != null) {
			Files.write(configFile.toPath(), Arrays.asList(content));
		}
		return new OpenSshConfig(getTemporaryDirectory(), configFile);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		StoredConfig config = db.getConfig();
		config.setInt("protocol", null, "version", 2);
		config.save();
	}
}
