/*
 * Copyright (C) 2008, 2021 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.transport.ssh;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.SshConfigStore.HostConfig;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class OpenSshConfigFileTest extends RepositoryTestCase {

	private File home;

	private File configFile;

	private OpenSshConfigFile osc;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		home = new File(trash, "home");
		FileUtils.mkdir(home);

		configFile = new File(new File(home, ".ssh"), Constants.CONFIG);
		FileUtils.mkdir(configFile.getParentFile());

		mockSystemReader.setProperty(Constants.OS_USER_NAME_KEY, "jex_junit");
		mockSystemReader.setProperty("TST_VAR", "TEST");
		osc = new OpenSshConfigFile(home, configFile, "jex_junit");
	}

	private void config(String data) throws IOException {
		FS fs = FS.DETECTED;
		long resolution = FS.getFileStoreAttributes(configFile.toPath())
				.getFsTimestampResolution().toNanos();
		Instant lastMtime = fs.lastModifiedInstant(configFile);
		do {
			try (final OutputStreamWriter fw = new OutputStreamWriter(
					new FileOutputStream(configFile), UTF_8)) {
				fw.write(data);
				TimeUnit.NANOSECONDS.sleep(resolution);
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		} while (lastMtime.equals(fs.lastModifiedInstant(configFile)));
	}

	private HostConfig lookup(String hostname) {
		return osc.lookupDefault(hostname, 0, null);
	}

	private void assertHost(String expected, HostConfig h) {
		assertEquals(expected, h.getValue(SshConstants.HOST_NAME));
	}

	private void assertUser(String expected, HostConfig h) {
		assertEquals(expected, h.getValue(SshConstants.USER));
	}

	private void assertPort(int expected, HostConfig h) {
		assertEquals(expected,
				OpenSshConfigFile.positive(h.getValue(SshConstants.PORT)));
	}

	private void assertIdentity(File expected, HostConfig h) {
		String actual = h.getValue(SshConstants.IDENTITY_FILE);
		if (expected == null) {
			assertNull(actual);
		} else {
			assertEquals(expected, new File(actual));
		}
	}

	private void assertAttempts(int expected, HostConfig h) {
		assertEquals(expected, OpenSshConfigFile
				.positive(h.getValue(SshConstants.CONNECTION_ATTEMPTS)));
	}

	@Test
	public void testNoConfig() {
		final HostConfig h = lookup("repo.or.cz");
		assertNotNull(h);
		assertHost("repo.or.cz", h);
		assertUser("jex_junit", h);
		assertPort(22, h);
		assertAttempts(1, h);
		assertIdentity(null, h);
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
		assertNotNull(lookup("first"));
		assertHost("first.tld", lookup("first"));
		assertNotNull(lookup("second"));
		assertHost("second.tld", lookup("second"));
		assertNotNull(lookup("third"));
		assertHost("third.tld", lookup("third"));
		assertNotNull(lookup("fourth"));
		assertHost("fourth.tld", lookup("fourth"));
		assertNotNull(lookup("last"));
		assertHost("last.tld", lookup("last"));
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
		assertHost("good.tld", lookup("good"));
		assertUser("gooduser", lookup("good"));
		assertPort(6007, lookup("good"));
		assertPort(2222, lookup("multiple"));
		assertPort(2222, lookup("quoted"));
		assertPort(2222, lookup("and"));
		assertPort(2222, lookup("unquoted"));
		assertPort(2222, lookup("hosts"));
		assertHost(" spaced\ttld ", lookup("spaced"));
		assertHost("bad.tld", lookup("bad"));
	}

	@Test
	public void testAdvancedParsing() throws Exception {
		// Escaped quotes, and line comments
		config("Host foo\n"
				+ " HostName=\"foo\\\"d.tld\"\n"
				+ " User= someone#foo\n"
				+ "Host bar\n"
				+ " User ' some one#two' # Comment\n"
				+ " GlobalKnownHostsFile '/a folder/with spaces/hosts' '/other/more hosts' # Comment\n"
				+ "Host foobar\n"
				+ " User a\\ u\\ thor\n"
				+ "Host backslash\n"
				+ " User some\\one\\\\\\ foo\n"
				+ "Host backslash_before_quote\n"
				+ " User \\\"someone#\"el#se\" #Comment\n"
				+ "Host backslash_in_quote\n"
				+ " User 'some\\one\\\\\\ foo'\n");
		assertHost("foo\"d.tld", lookup("foo"));
		assertUser("someone#foo", lookup("foo"));
		HostConfig c = lookup("bar");
		assertUser(" some one#two", c);
		assertArrayEquals(
				new Object[] { "/a folder/with spaces/hosts",
						"/other/more hosts" },
				c.getValues("GlobalKnownHostsFile").toArray());
		assertUser("a u thor", lookup("foobar"));
		assertUser("some\\one\\ foo", lookup("backslash"));
		assertUser("\"someone#el#se", lookup("backslash_before_quote"));
		assertUser("some\\one\\\\ foo", lookup("backslash_in_quote"));
	}

	@Test
	public void testCaseInsensitiveKeyLookup() throws Exception {
		config("Host orcz\n" + "Port 29418\n"
				+ "\tHostName repo.or.cz\nStrictHostKeyChecking yes\n");
		final HostConfig c = lookup("orcz");
		String exactCase = c.getValue("StrictHostKeyChecking");
		assertEquals("yes", exactCase);
		assertEquals(exactCase, c.getValue("stricthostkeychecking"));
		assertEquals(exactCase, c.getValue("STRICTHOSTKEYCHECKING"));
		assertEquals(exactCase, c.getValue("sTrIcThostKEYcheckING"));
		assertNull(c.getValue("sTrIcThostKEYcheckIN"));
	}

	@Test
	public void testAlias_DoesNotMatch() throws Exception {
		config("Host orcz\n" + "Port 29418\n"
				+ "\tHostName repo.or.cz\n");
		final HostConfig h = lookup("repo.or.cz");
		assertNotNull(h);
		assertHost("repo.or.cz", h);
		assertUser("jex_junit", h);
		assertPort(22, h);
		assertIdentity(null, h);
		final HostConfig h2 = lookup("orcz");
		assertHost("repo.or.cz", h);
		assertUser("jex_junit", h);
		assertPort(29418, h2);
		assertIdentity(null, h);
	}

	@Test
	public void testAlias_OptionsSet() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertHost("repo.or.cz", h);
		assertUser("jex", h);
		assertPort(2222, h);
		assertIdentity(new File(home, ".ssh/id_jex"), h);
	}

	@Test
	public void testAlias_OptionsKeywordCaseInsensitive() throws Exception {
		config("hOsT orcz\n" + "\thOsTnAmE repo.or.cz\n" + "\tPORT 2222\n"
				+ "\tuser jex\n" + "\tidentityfile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertHost("repo.or.cz", h);
		assertUser("jex", h);
		assertPort(2222, h);
		assertIdentity(new File(home, ".ssh/id_jex"), h);
	}

	@Test
	public void testAlias_OptionsInherit() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tHostName not.a.host.example.com\n" + "\tPort 2222\n"
				+ "\tUser jex\n" + "\tIdentityFile .ssh/id_jex\n"
				+ "\tForwardX11 no\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertHost("repo.or.cz", h);
		assertUser("jex", h);
		assertPort(2222, h);
		assertIdentity(new File(home, ".ssh/id_jex"), h);
	}

	@Test
	public void testAlias_PreferredAuthenticationsDefault() throws Exception {
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertNull(h.getValue(SshConstants.PREFERRED_AUTHENTICATIONS));
	}

	@Test
	public void testAlias_PreferredAuthentications() throws Exception {
		config("Host orcz\n" + "\tPreferredAuthentications publickey\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey",
				h.getValue(SshConstants.PREFERRED_AUTHENTICATIONS));
	}

	@Test
	public void testAlias_InheritPreferredAuthentications() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tPreferredAuthentications 'publickey, hostbased'\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertEquals("publickey,hostbased",
				h.getValue(SshConstants.PREFERRED_AUTHENTICATIONS));
	}

	@Test
	public void testAlias_BatchModeDefault() throws Exception {
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertNull(h.getValue(SshConstants.BATCH_MODE));
	}

	@Test
	public void testAlias_BatchModeYes() throws Exception {
		config("Host orcz\n" + "\tBatchMode yes\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertTrue(OpenSshConfigFile.flag(h.getValue(SshConstants.BATCH_MODE)));
	}

	@Test
	public void testAlias_InheritBatchMode() throws Exception {
		config("Host orcz\n" + "\tHostName repo.or.cz\n" + "\n" + "Host *\n"
				+ "\tBatchMode yes\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertTrue(OpenSshConfigFile.flag(h.getValue(SshConstants.BATCH_MODE)));
	}

	@Test
	public void testAlias_ConnectionAttemptsDefault() throws Exception {
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(1, h);
	}

	@Test
	public void testAlias_ConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts 5\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(5, h);
	}

	@Test
	public void testAlias_invalidConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts -1\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(1, h);
	}

	@Test
	public void testAlias_badConnectionAttempts() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts xxx\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(1, h);
	}

	@Test
	public void testDefaultBlock() throws Exception {
		config("ConnectionAttempts 5\n\nHost orcz\nConnectionAttempts 3\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(5, h);
	}

	@Test
	public void testHostCaseInsensitive() throws Exception {
		config("hOsT orcz\nConnectionAttempts 3\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(3, h);
	}

	@Test
	public void testListValueSingle() throws Exception {
		config("Host orcz\nUserKnownHostsFile /foo/bar\n");
		final HostConfig c = lookup("orcz");
		assertNotNull(c);
		assertEquals("/foo/bar", c.getValue("UserKnownHostsFile"));
	}

	@Test
	public void testListValueMultiple() throws Exception {
		// Tilde expansion occurs within the parser
		config("Host orcz\nUserKnownHostsFile \"~/foo/ba z\" /foo/bar \n");
		final HostConfig c = lookup("orcz");
		assertNotNull(c);
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar" },
				c.getValues("UserKnownHostsFile").toArray());
	}

	@Test
	public void testRepeatedLookupsWithModification() throws Exception {
		config("Host orcz\n" + "\tConnectionAttempts -1\n");
		final HostConfig h1 = lookup("orcz");
		assertNotNull(h1);
		assertAttempts(1, h1);
		config("Host orcz\n" + "\tConnectionAttempts 5\n");
		final HostConfig h2 = lookup("orcz");
		assertNotNull(h2);
		assertNotSame(h1, h2);
		assertAttempts(5, h2);
		assertAttempts(1, h1);
		assertNotSame(h1, h2);
	}

	@Test
	public void testIdentityFile() throws Exception {
		config("Host orcz\nIdentityFile \"~/foo/ba z\"\nIdentityFile /foo/bar");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		// Does tilde replacement
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar" },
				h.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testMultiIdentityFile() throws Exception {
		config("IdentityFile \"~/foo/ba z\"\nHost orcz\nIdentityFile /foo/bar\nHOST *\nIdentityFile /foo/baz");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertArrayEquals(new Object[] { new File(home, "foo/ba z").getPath(),
				"/foo/bar", "/foo/baz" },
				h.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testNegatedPattern() throws Exception {
		config("Host repo.or.cz\nIdentityFile ~/foo/bar\nHOST !*.or.cz\nIdentityFile /foo/baz");
		final HostConfig h = lookup("repo.or.cz");
		assertNotNull(h);
		assertIdentity(new File(home, "foo/bar"), h);
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath() },
				h.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testPattern() throws Exception {
		config("Host repo.or.cz\nIdentityFile ~/foo/bar\nHOST *.or.cz\nIdentityFile /foo/baz");
		final HostConfig h = lookup("repo.or.cz");
		assertNotNull(h);
		assertIdentity(new File(home, "foo/bar"), h);
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath(),
				"/foo/baz" },
				h.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testMultiHost() throws Exception {
		config("Host orcz *.or.cz\nIdentityFile ~/foo/bar\nHOST *.or.cz\nIdentityFile /foo/baz");
		final HostConfig h1 = lookup("repo.or.cz");
		assertNotNull(h1);
		assertIdentity(new File(home, "foo/bar"), h1);
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath(),
				"/foo/baz" },
				h1.getValues(SshConstants.IDENTITY_FILE).toArray());
		final HostConfig h2 = lookup("orcz");
		assertNotNull(h2);
		assertIdentity(new File(home, "foo/bar"), h2);
		assertArrayEquals(new Object[] { new File(home, "foo/bar").getPath() },
				h2.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testEqualsSign() throws Exception {
		config("Host=orcz\n\tConnectionAttempts = 5\n\tUser=\t  foobar\t\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertAttempts(5, h);
		assertUser("foobar", h);
	}

	@Test
	public void testMissingArgument() throws Exception {
		config("Host=orcz\n\tSendEnv\nIdentityFile\t\nForwardX11\n\tUser=\t  foobar\t\n");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertUser("foobar", h);
		assertEquals("[]", h.getValues("SendEnv").toString());
		assertIdentity(null, h);
		assertNull(h.getValue("ForwardX11"));
	}

	@Test
	public void testHomeDirUserReplacement() throws Exception {
		config("Host=orcz\n\tIdentityFile %d/.ssh/%u_id_dsa");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertIdentity(new File(new File(home, ".ssh"), "jex_junit_id_dsa"), h);
	}

	@Test
	public void testHostnameReplacement() throws Exception {
		config("Host=orcz\nHost *.*\n\tHostname %h\nHost *\n\tHostname %h.example.org");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertHost("orcz.example.org", h);
	}

	@Test
	public void testRemoteUserReplacement() throws Exception {
		config("Host=orcz\n\tUser foo\n" + "Host *.*\n\tHostname %h\n"
				+ "Host *\n\tHostname %h.ex%%20ample.org\n\tIdentityFile ~/.ssh/%h_%r_id_dsa");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertIdentity(
				new File(new File(home, ".ssh"),
						"orcz.ex%20ample.org_foo_id_dsa"),
				h);
	}

	@Test
	public void testLocalhostFQDNReplacement() throws Exception {
		String localhost = SystemReader.getInstance().getHostname();
		config("Host=orcz\n\tIdentityFile ~/.ssh/%l_id_dsa");
		final HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertIdentity(
				new File(new File(home, ".ssh"), localhost + "_id_dsa"),
				h);
	}

	@Test
	public void testPubKeyAcceptedAlgorithms() throws Exception {
		config("Host=orcz\n\tPubkeyAcceptedAlgorithms ^ssh-rsa");
		HostConfig h = lookup("orcz");
		assertEquals("^ssh-rsa",
				h.getValue(SshConstants.PUBKEY_ACCEPTED_ALGORITHMS));
		assertEquals("^ssh-rsa", h.getValue("PubkeyAcceptedKeyTypes"));
	}

	@Test
	public void testPubKeyAcceptedKeyTypes() throws Exception {
		config("Host=orcz\n\tPubkeyAcceptedKeyTypes ^ssh-rsa");
		HostConfig h = lookup("orcz");
		assertEquals("^ssh-rsa",
				h.getValue(SshConstants.PUBKEY_ACCEPTED_ALGORITHMS));
		assertEquals("^ssh-rsa", h.getValue("PubkeyAcceptedKeyTypes"));
	}

	@Test
	public void testEolComments() throws Exception {
		config("#Comment\nHost=orcz #Comment\n\tPubkeyAcceptedAlgorithms ^ssh-rsa # Comment\n#Comment");
		HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertEquals("^ssh-rsa",
				h.getValue(SshConstants.PUBKEY_ACCEPTED_ALGORITHMS));
	}

	@Test
	public void testEnVarSubstitution() throws Exception {
		config("Host orcz\nIdentityFile /tmp/${TST_VAR}\n"
				+ "CertificateFile /tmp/${}/foo\nUser ${TST_VAR}\nIdentityAgent /tmp/${TST_VAR/bar");
		HostConfig h = lookup("orcz");
		assertNotNull(h);
		assertEquals("/tmp/TEST",
				h.getValue(SshConstants.IDENTITY_FILE));
		// No variable name
		assertEquals("/tmp/${}/foo", h.getValue(SshConstants.CERTIFICATE_FILE));
		// User doesn't get env var substitution:
		assertUser("${TST_VAR}", h);
		// Unterminated:
		assertEquals("/tmp/${TST_VAR/bar",
				h.getValue(SshConstants.IDENTITY_AGENT));
	}

	@Test
	public void testIdentityAgentNone() throws Exception {
		config("Host orcz\nIdentityAgent none\n");
		HostConfig h = lookup("orcz");
		assertEquals(SshConstants.NONE,
				h.getValue(SshConstants.IDENTITY_AGENT));
	}

	@Test
	public void testNegativeMatch() throws Exception {
		config("Host foo.bar !foobar.baz *.baz\n" + "Port 29418\n");
		HostConfig h = lookup("foo.bar");
		assertNotNull(h);
		assertPort(29418, h);
		h = lookup("foobar.baz");
		assertNotNull(h);
		assertPort(22, h);
		h = lookup("foo.baz");
		assertNotNull(h);
		assertPort(29418, h);
	}

	@Test
	public void testNegativeMatch2() throws Exception {
		// Negative match after the positive match.
		config("Host foo.bar *.baz !foobar.baz\n" + "Port 29418\n");
		HostConfig h = lookup("foo.bar");
		assertNotNull(h);
		assertPort(29418, h);
		h = lookup("foobar.baz");
		assertNotNull(h);
		assertPort(22, h);
		h = lookup("foo.baz");
		assertNotNull(h);
		assertPort(29418, h);
	}

	@Test
	public void testNoMatch() throws Exception {
		config("Host !host1 !host2\n" + "Port 29418\n");
		HostConfig h = lookup("host1");
		assertNotNull(h);
		assertPort(22, h);
		h = lookup("host2");
		assertNotNull(h);
		assertPort(22, h);
		h = lookup("host3");
		assertNotNull(h);
		assertPort(22, h);
	}

	@Test
	public void testMultipleMatch() throws Exception {
		config("Host foo.bar\nPort 29418\nIdentityFile /foo\n\n"
				+ "Host *.bar\nPort 22\nIdentityFile /bar\n"
				+ "Host foo.bar\nPort 47\nIdentityFile /baz\n");
		HostConfig h = lookup("foo.bar");
		assertNotNull(h);
		assertPort(29418, h);
		assertArrayEquals(new Object[] { "/foo", "/bar", "/baz" },
				h.getValues(SshConstants.IDENTITY_FILE).toArray());
	}

	@Test
	public void testWhitespace() throws Exception {
		config("Host foo \tbar   baz\nPort 29418\n");
		HostConfig h = lookup("foo");
		assertNotNull(h);
		assertPort(29418, h);
		h = lookup("bar");
		assertNotNull(h);
		assertPort(29418, h);
		h = lookup("baz");
		assertNotNull(h);
		assertPort(29418, h);
		h = lookup("\tbar");
		assertNotNull(h);
		assertPort(22, h);
	}
}
