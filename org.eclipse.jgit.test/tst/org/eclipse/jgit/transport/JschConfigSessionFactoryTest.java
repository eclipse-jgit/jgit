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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Test;

import com.jcraft.jsch.Session;

/**
 * Tests for correctly interpreting ssh config values when Jsch sessions are
 * used.
 */
public class JschConfigSessionFactoryTest {

	File tmpConfigFile;

	OpenSshConfig tmpConfig;

	DefaultSshSessionFactory factory = new DefaultSshSessionFactory();

	@After
	public void removeTmpConfig() {
		if (tmpConfigFile == null) {
			return;
		}
		if (tmpConfigFile.exists() && !tmpConfigFile.delete()) {
			tmpConfigFile.deleteOnExit();
		}
		tmpConfigFile = null;
	}

	@Test
	public void testNoConfigEntry() throws Exception {
		tmpConfigFile = File.createTempFile("jsch", "test");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://egit/egit/egit");
		assertEquals("egit", session.getHost());
		// No user in URI, none in ssh config: default is OS user name
		assertEquals(System.getProperty("user.name"), session.getUserName());
		assertEquals(22, session.getPort());
	}

	@Test
	public void testAlias() throws Exception {
		tmpConfigFile = createConfig("Host egit", "Hostname git.eclipse.org",
				"User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://egit/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("foo", session.getUserName());
		assertEquals(29418, session.getPort());
	}

	@Test
	public void testAliasWithUser() throws Exception {
		tmpConfigFile = createConfig("Host egit", "Hostname git.eclipse.org",
				"User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://bar@egit/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(29418, session.getPort());
	}

	@Test
	public void testAliasWithPort() throws Exception {
		tmpConfigFile = createConfig("Host egit", "Hostname git.eclipse.org",
				"User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://bar@egit:22/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(22, session.getPort());
	}

	@Test
	public void testAliasIdentical() throws Exception {
		tmpConfigFile = createConfig("Host git.eclipse.org",
				"Hostname git.eclipse.org", "User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://git.eclipse.org/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("foo", session.getUserName());
		assertEquals(29418, session.getPort());
	}

	@Test
	public void testAliasIdenticalWithUser() throws Exception {
		tmpConfigFile = createConfig("Host git.eclipse.org",
				"Hostname git.eclipse.org", "User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://bar@git.eclipse.org/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(29418, session.getPort());
	}

	@Test
	public void testAliasIdenticalWithPort() throws Exception {
		tmpConfigFile = createConfig("Host git.eclipse.org",
				"Hostname git.eclipse.org", "User foo", "Port 29418");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession(
				"ssh://bar@git.eclipse.org:300/egit/egit");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(300, session.getPort());
	}

	@Test
	public void testConnectTimout() throws Exception {
		tmpConfigFile = createConfig("Host git.eclipse.org",
				"Hostname git.eclipse.org", "User foo", "Port 29418",
				"ConnectTimeout 10");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://git.eclipse.org/something");
		assertEquals("git.eclipse.org", session.getHost());
		assertEquals("foo", session.getUserName());
		assertEquals(29418, session.getPort());
		assertEquals(TimeUnit.SECONDS.toMillis(10), session.getTimeout());
	}

	@Test
	public void testAliasCaseDifferenceUpcase() throws Exception {
		tmpConfigFile = createConfig("Host Bitbucket.org",
				"Hostname bitbucket.org", "User foo", "Port 29418",
				"ConnectTimeout 10", //
				"Host bitbucket.org", "Hostname bitbucket.org", "User bar",
				"Port 22", "ConnectTimeout 5");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://Bitbucket.org/something");
		assertEquals("bitbucket.org", session.getHost());
		assertEquals("foo", session.getUserName());
		assertEquals(29418, session.getPort());
		assertEquals(TimeUnit.SECONDS.toMillis(10), session.getTimeout());
	}

	@Test
	public void testAliasCaseDifferenceLowcase() throws Exception {
		tmpConfigFile = createConfig("Host Bitbucket.org",
				"Hostname bitbucket.org", "User foo", "Port 29418",
				"ConnectTimeout 10", //
				"Host bitbucket.org", "Hostname bitbucket.org", "User bar",
				"Port 22", "ConnectTimeout 5");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://bitbucket.org/something");
		assertEquals("bitbucket.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(22, session.getPort());
		assertEquals(TimeUnit.SECONDS.toMillis(5), session.getTimeout());
	}

	@Test
	public void testAliasCaseDifferenceUpcaseInverted() throws Exception {
		tmpConfigFile = createConfig("Host bitbucket.org",
				"Hostname bitbucket.org", "User bar", "Port 22",
				"ConnectTimeout 5", //
				"Host Bitbucket.org", "Hostname bitbucket.org", "User foo",
				"Port 29418", "ConnectTimeout 10");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://Bitbucket.org/something");
		assertEquals("bitbucket.org", session.getHost());
		assertEquals("foo", session.getUserName());
		assertEquals(29418, session.getPort());
		assertEquals(TimeUnit.SECONDS.toMillis(10), session.getTimeout());
	}

	@Test
	public void testAliasCaseDifferenceLowcaseInverted() throws Exception {
		tmpConfigFile = createConfig("Host bitbucket.org",
				"Hostname bitbucket.org", "User bar", "Port 22",
				"ConnectTimeout 5", //
				"Host Bitbucket.org", "Hostname bitbucket.org", "User foo",
				"Port 29418", "ConnectTimeout 10");
		tmpConfig = new OpenSshConfig(tmpConfigFile.getParentFile(),
				tmpConfigFile);
		factory.setConfig(tmpConfig);
		Session session = createSession("ssh://bitbucket.org/something");
		assertEquals("bitbucket.org", session.getHost());
		assertEquals("bar", session.getUserName());
		assertEquals(22, session.getPort());
		assertEquals(TimeUnit.SECONDS.toMillis(5), session.getTimeout());
	}

	private File createConfig(String... lines) throws Exception {
		File f = File.createTempFile("jsch", "test");
		Files.write(f.toPath(), Arrays.asList(lines));
		return f;
	}

	private Session createSession(String uriText) throws Exception {
		// For this test to make sense, these few lines must correspond to the
		// code in JschConfigSessionFactory.getSession(). Because of
		// side-effects we cannot encapsulate that there properly and so we have
		// to duplicate this bit here. We also can't test getSession() itself
		// since it would try to actually connect to a server.
		URIish uri = new URIish(uriText);
		String host = uri.getHost();
		String user = uri.getUser();
		String password = uri.getPass();
		int port = uri.getPort();
		OpenSshConfig.Host hostConfig = tmpConfig.lookup(host);
		if (port <= 0) {
			port = hostConfig.getPort();
		}
		if (user == null) {
			user = hostConfig.getUser();
		}
		return factory.createSession(null, FS.DETECTED, user, password, host,
				port, hostConfig);
	}
}
