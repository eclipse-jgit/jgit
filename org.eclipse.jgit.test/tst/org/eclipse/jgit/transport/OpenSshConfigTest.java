/*
 * Copyright (C) 2008, Google Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class OpenSshConfigTest extends RepositoryTestCase {

	private static File homeDir;

	private static File etcDir;

	private static File gitDir;

	private static File otherDir;

	private FS_Mock fsMock;

	private OpenSshConfig userConfig;

	private OpenSshConfig systemConfig;

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("user.name", "jex_junit");
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();

		homeDir = new File(trash, "home");
		FileUtils.mkdir(homeDir);
		etcDir = new File(trash, "etc");
		FileUtils.mkdir(etcDir);
		gitDir = new File(trash, "git");
		FileUtils.mkdir(gitDir);
		otherDir = new File(trash, "other");
		FileUtils.mkdir(otherDir);

		fsMock = new FS_Mock();
		userConfig = OpenSshConfig.get(fsMock);
		systemConfig = OpenSshConfig.get(fsMock, true);
	}

	private void writeConfigFile(final File configFile, final String data)
			throws IOException {
		final OutputStreamWriter fw = new OutputStreamWriter(
				new FileOutputStream(configFile), "UTF-8");
		fw.write(data);
		fw.close();
	}

	private void userConfig(final String data) throws IOException {
		final File userConfigDir = new File(homeDir, ".ssh");
		FileUtils.mkdir(userConfigDir);
		final File userConfigFile = new File(userConfigDir, "config");
		writeConfigFile(userConfigFile, data);
	}

	private void systemConfig(final File systemConfigDir, final String data)
			throws IOException {
		final File parentDir = systemConfigDir.getParentFile();
		if (!parentDir.exists())
			FileUtils.mkdir(parentDir);
		if (!systemConfigDir.exists())
			FileUtils.mkdir(systemConfigDir);
		final File systemConfigFile = new File(systemConfigDir, "ssh_config");
		writeConfigFile(systemConfigFile, data);
	}

	private void systemConfig(final String data) throws IOException {
		systemConfig(new File(etcDir, "ssh"), data);
	}

	@Test
	public void testNoConfig() {
		final Host h = userConfig.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex_junit", h.getUser());
		assertEquals(22, h.getPort());
		assertNull(h.getIdentityFile());
	}

	@Test
	public void testSeparatorParsing() throws Exception {
		userConfig("Host\tfirst\n" +
				"\tHostName\tfirst.tld\n" +
				"\n" +
				"Host second\n" +
				" HostName\tsecond.tld\n" +
				"Host=third\n" +
				"HostName=third.tld\n\n\n" +
				"\t Host = fourth\n\n\n" +
				" \t HostName\t=fourth.tld\n" +
				"Host\t =     last\n" +
				"HostName  \t    last.tld");
		assertNotNull(userConfig.lookup("first"));
		assertEquals("first.tld", userConfig.lookup("first").getHostName());
		assertNotNull(userConfig.lookup("second"));
		assertEquals("second.tld", userConfig.lookup("second").getHostName());
		assertNotNull(userConfig.lookup("third"));
		assertEquals("third.tld", userConfig.lookup("third").getHostName());
		assertNotNull(userConfig.lookup("fourth"));
		assertEquals("fourth.tld", userConfig.lookup("fourth").getHostName());
		assertNotNull(userConfig.lookup("last"));
		assertEquals("last.tld", userConfig.lookup("last").getHostName());
	}

	@Test
	public void testQuoteParsing() throws Exception {
		userConfig("Host \"good\"\n" +
				" HostName=\"good.tld\"\n" +
				" Port=\"6007\"\n" +
				" User=\"gooduser\"\n" +
				"Host multiple unquoted and \"quoted\" \"hosts\"\n" +
				" Port=\"2222\"\n" +
				"Host \"spaced\"\n" +
				"# Bad host name, but testing preservation of spaces\n" +
				" HostName=\" spaced\ttld \"\n" +
				"# Misbalanced quotes\n" +
				"Host \"bad\"\n" +
				"# OpenSSH doesn't allow this but ...\n" +
				" HostName=bad.tld\"\n");
		assertEquals("good.tld", userConfig.lookup("good").getHostName());
		assertEquals("gooduser", userConfig.lookup("good").getUser());
		assertEquals(6007, userConfig.lookup("good").getPort());
		assertEquals(2222, userConfig.lookup("multiple").getPort());
		assertEquals(2222, userConfig.lookup("quoted").getPort());
		assertEquals(2222, userConfig.lookup("and").getPort());
		assertEquals(2222, userConfig.lookup("unquoted").getPort());
		assertEquals(2222, userConfig.lookup("hosts").getPort());
		assertEquals(" spaced\ttld ", userConfig.lookup("spaced").getHostName());
		assertEquals("bad.tld\"", userConfig.lookup("bad").getHostName());
	}

	@Test
	public void testAlias_DoesNotMatch() throws Exception {
		userConfig("Host orcz\n" + "\tHostName repo.or.cz\n");
		final Host h = userConfig.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex_junit", h.getUser());
		assertEquals(22, h.getPort());
		assertNull(h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsSet() throws Exception {
		userConfig("Host orcz\n" + "\tHostName repo.or.cz\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(homeDir, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsKeywordCaseInsensitive() throws Exception {
		userConfig("hOsT orcz\n" + "\thOsTnAmE repo.or.cz\n" + "\tPORT 2222\n"
				+ "\tuser jex\n" + "\tidentityfile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(homeDir, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsInherit() throws Exception {
		userConfig("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tHostName not.a.host.example.com\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(homeDir, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_PreferredAuthenticationsDefault() throws Exception {
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertNull(h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_PreferredAuthentications() throws Exception {
		userConfig("Host orcz\n" + "\tPreferredAuthentications publickey\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey", h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_InheritPreferredAuthentications() throws Exception {
		userConfig("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tPreferredAuthentications publickey, hostbased\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey,hostbased", h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_BatchModeDefault() throws Exception {
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertFalse(h.isBatchMode());
	}

	@Test
	public void testAlias_BatchModeYes() throws Exception {
		userConfig("Host orcz\n" + "\tBatchMode yes\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertTrue(h.isBatchMode());
	}

	@Test
	public void testAlias_InheritBatchMode() throws Exception {
		userConfig("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tBatchMode yes\n");
		final Host h = userConfig.lookup("orcz");
		assertNotNull(h);
		assertTrue(h.isBatchMode());
	}

	@Test
	public void testHostFromSystemConfigViaSystemProperty() throws Exception {
		systemConfig(otherDir, "Host host\n" + "\tHostName example.org\n");
		mockSystemReader.setProperty("jgit.ssh.sysconfdir",
				otherDir.getAbsolutePath());
		// re-create system config after writing the config file and setting the
		// system property
		systemConfig = OpenSshConfig.get(fsMock, true);

		Host h = systemConfig.lookup("host");
		assertEquals("example.org", h.getHostName());
	}

	@Test
	public void testHostFromSystemConfigViaGitPrefix() throws Exception {
		systemConfig(new File(gitDir, "etc/ssh"), "Host host\n"
				+ "\tHostName example.org\n");
		// re-create system config after writing the config file
		systemConfig = OpenSshConfig.get(fsMock, true);

		Host h = systemConfig.lookup("host");
		assertEquals("example.org", h.getHostName());
	}

	@Test
	public void testHostFromSystemConfig() throws Exception {
		systemConfig("Host host\n" + "\tHostName example.org\n");

		Host h = systemConfig.lookup("host");
		assertEquals("example.org", h.getHostName());
	}

	@Test
	public void testHostFromSystemConfigOverriddenByUserConfig()
			throws Exception {
		userConfig("Host host\n" + "\tHostName test.org\n");
		systemConfig("Host host\n" + "\tHostName example.org\n");

		Host h = systemConfig.lookup("host");
		assertEquals("test.org", h.getHostName());
	}

	@Test
	public void testHostFromSystemConfigComplementedByUserConfig()
			throws Exception {
		userConfig("Host host\n" + "\tUser someone");
		systemConfig("Host host\n" + "\tHostName example.org");

		Host h = systemConfig.lookup("host");
		assertEquals("example.org", h.getHostName());
		assertEquals("someone", h.getUser());
	}

	/**
	 * FS mock returning the user's home directory and resolving
	 * /etc/ssh/ssh_config.
	 */
	private static class FS_Mock extends FS {

		@Override
		public File userHome() {
			return homeDir;
		}

		@Override
		public File gitPrefix() {
			return gitDir;
		}

		@Override
		public File resolve(final File dir, final String name) {
			if (dir == null && name.equals("/etc"))
				return etcDir;
			if (dir == gitDir && name.equals("etc"))
				return new File(dir, name);
			if (dir == null
					&& name.equalsIgnoreCase(otherDir.getAbsolutePath()))
				return otherDir;

			fail();
			return null;
		}

		@Override
		public FS newInstance() {
			return null;
		}

		@Override
		public boolean supportsExecute() {
			return false;
		}

		@Override
		public boolean canExecute(File f) {
			return false;
		}

		@Override
		public boolean setExecute(File f, boolean canExec) {
			return false;
		}

		@Override
		public boolean retryFailedLockFileCommit() {
			return false;
		}

		@Override
		protected File discoverGitPrefix() {
			return null;
		}

		@Override
		public ProcessBuilder runInShell(String cmd, String[] args) {
			return null;
		}
	}
}
