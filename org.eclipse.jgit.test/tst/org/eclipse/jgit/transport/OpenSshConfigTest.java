/*
 * Copyright (C) 2008, 2017 Google Inc.
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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

import com.jcraft.jsch.ConfigRepository;

public class OpenSshConfigTest extends RepositoryTestCase {
	private File home;

	private File configFile;

	private OpenSshConfig osc;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		home = new File(trash, "home");
		FileUtils.mkdir(home);

		configFile = new File(new File(home, ".ssh"), Constants.CONFIG);
		FileUtils.mkdir(configFile.getParentFile());

		mockSystemReader.setProperty(Constants.OS_USER_NAME_KEY, "jex_junit");
		osc = new OpenSshConfig(home, configFile);
	}

	private void config(final String data) throws IOException {
		long lastMtime = configFile.lastModified();
		do {
			try (final OutputStreamWriter fw = new OutputStreamWriter(
					new FileOutputStream(configFile), CHARSET)) {
				fw.write(data);
			}
		} while (lastMtime == configFile.lastModified());
	}

	@Test
	public void testNoConfig() {
		final Host h = osc.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex_junit", h.getUser());
		assertEquals(22, h.getPort());
		assertEquals(1, h.getConnectionAttempts());
		assertNull(h.getIdentityFile());
	}

	@Test
	public void testSeparatorParsing() throws Exception {
		config("Host\tfirst\n" +
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
		assertNotNull(osc.lookup("first"));
		assertEquals("first.tld", osc.lookup("first").getHostName());
		assertNotNull(osc.lookup("second"));
		assertEquals("second.tld", osc.lookup("second").getHostName());
		assertNotNull(osc.lookup("third"));
		assertEquals("third.tld", osc.lookup("third").getHostName());
		assertNotNull(osc.lookup("fourth"));
		assertEquals("fourth.tld", osc.lookup("fourth").getHostName());
		assertNotNull(osc.lookup("last"));
		assertEquals("last.tld", osc.lookup("last").getHostName());
	}

	@Test
	public void testQuoteParsing() throws Exception {
		config("Host \"good\"\n" +
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
		assertEquals("good.tld", osc.lookup("good").getHostName());
		assertEquals("gooduser", osc.lookup("good").getUser());
		assertEquals(6007, osc.lookup("good").getPort());
		assertEquals(2222, osc.lookup("multiple").getPort());
		assertEquals(2222, osc.lookup("quoted").getPort());
		assertEquals(2222, osc.lookup("and").getPort());
		assertEquals(2222, osc.lookup("unquoted").getPort());
		assertEquals(2222, osc.lookup("hosts").getPort());
		assertEquals(" spaced\ttld ", osc.lookup("spaced").getHostName());
		assertEquals("bad.tld\"", osc.lookup("bad").getHostName());
	}

	@Test
	public void testAlias_DoesNotMatch() throws Exception {
		config("Host orcz\n" + "Port 29418\n" + "\tHostName repo.or.cz\n");
		final Host h = osc.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex_junit", h.getUser());
		assertEquals(22, h.getPort());
		assertNull(h.getIdentityFile());
		final Host h2 = osc.lookup("orcz");
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex_junit", h.getUser());
		assertEquals(29418, h2.getPort());
		assertNull(h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsSet() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(home, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsKeywordCaseInsensitive() throws Exception {
		config("hOsT orcz\n" + "\thOsTnAmE repo.or.cz\n" + "\tPORT 2222\n"
				+ "\tuser jex\n" + "\tidentityfile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(home, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_OptionsInherit() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tHostName not.a.host.example.com\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("repo.or.cz", h.getHostName());
		assertEquals("jex", h.getUser());
		assertEquals(2222, h.getPort());
		assertEquals(new File(home, ".ssh/id_jex"), h.getIdentityFile());
	}

	@Test
	public void testAlias_PreferredAuthenticationsDefault() throws Exception {
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertNull(h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_PreferredAuthentications() throws Exception {
		config("Host orcz\n" + "\tPreferredAuthentications publickey\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey", h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_InheritPreferredAuthentications() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tPreferredAuthentications publickey, hostbased\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey,hostbased", h.getPreferredAuthentications());
	}

	@Test
	public void testAlias_BatchModeDefault() throws Exception {
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertFalse(h.isBatchMode());
	}

	@Test
	public void testAlias_BatchModeYes() throws Exception {
		config("Host orcz\n" + "\tBatchMode yes\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertTrue(h.isBatchMode());
	}

	@Test
	public void testAlias_InheritBatchMode() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tBatchMode yes\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertTrue(h.isBatchMode());
	}

	@Test
	public void testAlias_ConnectionAttemptsDefault() throws Exception {
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(1, h.getConnectionAttempts());
	}

	@Test
	public void testAlias_ConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts 5\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(5, h.getConnectionAttempts());
	}

	@Test
	public void testAlias_invalidConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts -1\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(1, h.getConnectionAttempts());
	}

	@Test
	public void testAlias_badConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts xxx\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(1, h.getConnectionAttempts());
	}

	@Test
	public void testDefaultBlock() throws Exception {
		config("ConnectionAttempts 5\n\nHost orcz\nConnectionAttempts 3\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(5, h.getConnectionAttempts());
	}

	@Test
	public void testHostCaseInsensitive() throws Exception {
		config("hOsT orcz\nConnectionAttempts 3\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(3, h.getConnectionAttempts());
	}

	@Test
	public void testListValueSingle() throws Exception {
		config("Host orcz\nUserKnownHostsFile /foo/bar\n");
		final ConfigRepository.Config c = osc.getConfig("orcz");
		assertNotNull(c);
		assertEquals("/foo/bar", c.getValue("UserKnownHostsFile"));
	}

	@Test
	public void testListValueMultiple() throws Exception {
		// Tilde expansion occurs within the parser
		config("Host orcz\nUserKnownHostsFile \"~/foo/ba z\" /foo/bar \n");
		final ConfigRepository.Config c = osc.getConfig("orcz");
		assertNotNull(c);
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar" },
				c.getValues("UserKnownHostsFile"));
	}

	@Test
	public void testRepeatedLookups() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts 5\n");
		final Host h1 = osc.lookup("orcz");
		final Host h2 = osc.lookup("orcz");
		assertNotNull(h1);
		assertSame(h1, h2);
		assertEquals(5, h1.getConnectionAttempts());
		assertEquals(h1.getConnectionAttempts(), h2.getConnectionAttempts());
		final ConfigRepository.Config c = osc.getConfig("orcz");
		assertNotNull(c);
		assertSame(c, h1.getConfig());
		assertSame(c, h2.getConfig());
	}

	@Test
	public void testRepeatedLookupsWithModification() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts -1\n");
		final Host h1 = osc.lookup("orcz");
		assertNotNull(h1);
		assertEquals(1, h1.getConnectionAttempts());
		config("Host orcz\n" + "\tConnectionAttempts 5\n");
		final Host h2 = osc.lookup("orcz");
		assertNotNull(h2);
		assertNotSame(h1, h2);
		assertEquals(5, h2.getConnectionAttempts());
		assertEquals(1, h1.getConnectionAttempts());
		assertNotSame(h1.getConfig(), h2.getConfig());
	}

	@Test
	public void testIdentityFile() throws Exception {
		config("Host orcz\nIdentityFile \"~/foo/ba z\"\nIdentityFile /foo/bar");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		File f = h.getIdentityFile();
		assertNotNull(f);
		// Host does tilde replacement
		assertEquals(new File(home, "foo/ba z"), f);
		final ConfigRepository.Config c = h.getConfig();
		// Config does tilde replacement, too
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar" },
				c.getValues("IdentityFile"));
	}

	@Test
	public void testMultiIdentityFile() throws Exception {
		config("IdentityFile \"~/foo/ba z\"\nHost orcz\nIdentityFile /foo/bar\nHOST *\nIdentityFile /foo/baz");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		File f = h.getIdentityFile();
		assertNotNull(f);
		// Host does tilde replacement
		assertEquals(new File(home, "foo/ba z"), f);
		final ConfigRepository.Config c = h.getConfig();
		// Config does tilde replacement, too
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar", "/foo/baz" },
				c.getValues("IdentityFile"));
	}

	@Test
	public void testNegatedPattern() throws Exception {
		config("Host repo.or.cz\nIdentityFile ~/foo/bar\nHOST !*.or.cz\nIdentityFile /foo/baz");
		final Host h = osc.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals(new File(home, "foo/bar"), h.getIdentityFile());
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath() },
				h.getConfig().getValues("IdentityFile"));
	}

	@Test
	public void testPattern() throws Exception {
		config("Host repo.or.cz\nIdentityFile ~/foo/bar\nHOST *.or.cz\nIdentityFile /foo/baz");
		final Host h = osc.lookup("repo.or.cz");
		assertNotNull(h);
		assertEquals(new File(home, "foo/bar"), h.getIdentityFile());
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath(),
				"/foo/baz" },
				h.getConfig().getValues("IdentityFile"));
	}

	@Test
	public void testMultiHost() throws Exception {
		config("Host orcz *.or.cz\nIdentityFile ~/foo/bar\nHOST *.or.cz\nIdentityFile /foo/baz");
		final Host h1 = osc.lookup("repo.or.cz");
		assertNotNull(h1);
		assertEquals(new File(home, "foo/bar"), h1.getIdentityFile());
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath(),
				"/foo/baz" },
				h1.getConfig().getValues("IdentityFile"));
		final Host h2 = osc.lookup("orcz");
		assertNotNull(h2);
		assertEquals(new File(home, "foo/bar"), h2.getIdentityFile());
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath() },
				h2.getConfig().getValues("IdentityFile"));
	}

	@Test
	public void testEqualsSign() throws Exception {
		config("Host=orcz\n\tConnectionAttempts = 5\n\tUser=\t  foobar\t\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(5, h.getConnectionAttempts());
		assertEquals("foobar", h.getUser());
	}

	@Test
	public void testMissingArgument() throws Exception {
		config("Host=orcz\n\tSendEnv\nIdentityFile\t\nForwardX11\n\tUser=\t  foobar\t\n");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("foobar", h.getUser());
		assertArrayEquals(new String[0], h.getConfig().getValues("SendEnv"));
		assertNull(h.getIdentityFile());
		assertNull(h.getConfig().getValue("ForwardX11"));
	}

	@Test
	public void testHomeDirUserReplacement() throws Exception {
		config("Host=orcz\n\tIdentityFile %d/.ssh/%u_id_dsa");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(new File(new File(home, ".ssh"), "jex_junit_id_dsa"),
				h.getIdentityFile());
	}

	@Test
	public void testHostnameReplacement() throws Exception {
		config("Host=orcz\nHost *.*\n\tHostname %h\nHost *\n\tHostname %h.example.org");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals("orcz.example.org", h.getHostName());
	}

	@Test
	public void testRemoteUserReplacement() throws Exception {
		config("Host=orcz\n\tUser foo\n" + "Host *.*\n\tHostname %h\n"
				+ "Host *\n\tHostname %h.ex%%20ample.org\n\tIdentityFile ~/.ssh/%h_%r_id_dsa");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(
				new File(new File(home, ".ssh"),
						"orcz.ex%20ample.org_foo_id_dsa"),
				h.getIdentityFile());
	}

	@Test
	public void testLocalhostFQDNReplacement() throws Exception {
		String localhost = SystemReader.getInstance().getHostname();
		config("Host=orcz\n\tIdentityFile ~/.ssh/%l_id_dsa");
		final Host h = osc.lookup("orcz");
		assertNotNull(h);
		assertEquals(
				new File(new File(home, ".ssh"), localhost + "_id_dsa"),
				h.getIdentityFile());
	}
}
