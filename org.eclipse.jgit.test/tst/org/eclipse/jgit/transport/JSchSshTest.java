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
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.ssh.SshTestBase;
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
