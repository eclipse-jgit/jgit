/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

//TODO(ms): move to org.eclipse.jgit.ssh.jsch in 6.0
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.junit.ssh.SshTestBase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@RunWith(Theories.class)
public class JSchSshTest extends SshTestBase {

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

}
