/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
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

	@Before
	public void setup() {
		SystemReader.setInstance(new MockSystemReader());
	}

	@After
	public void removeTmpConfig() {
		SystemReader.setInstance(null);
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
		assertEquals(SystemReader.getInstance().getProperty("user.name"),
				session.getUserName());
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
