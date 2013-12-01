/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Test;

/**
 * Test reading of git config
 */
public class ConfigTest {

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	public void test001_ReadBareKey() throws ConfigInvalidException {
		final Config c = parse("[foo]\nbar\n");
		assertTrue(c.getBoolean("foo", null, "bar", false));
		assertEquals("", c.getString("foo", null, "bar"));
	}

	@Test
	public void test002_ReadWithSubsection() throws ConfigInvalidException {
		final Config c = parse("[foo \"zip\"]\nbar\n[foo \"zap\"]\nbar=false\nn=3\n");
		assertTrue(c.getBoolean("foo", "zip", "bar", false));
		assertEquals("", c.getString("foo","zip", "bar"));
		assertFalse(c.getBoolean("foo", "zap", "bar", true));
		assertEquals("false", c.getString("foo", "zap", "bar"));
		assertEquals(3, c.getInt("foo", "zap", "n", 4));
		assertEquals(4, c.getInt("foo", "zap","m", 4));
	}

	@Test
	public void test003_PutRemote() {
		final Config c = new Config();
		c.setString("sec", "ext", "name", "value");
		c.setString("sec", "ext", "name2", "value2");
		final String expText = "[sec \"ext\"]\n\tname = value\n\tname2 = value2\n";
		assertEquals(expText, c.toText());
	}

	@Test
	public void test004_PutGetSimple() {
		Config c = new Config();
		c.setString("my", null, "somename", "false");
		assertEquals("false", c.getString("my", null, "somename"));
		assertEquals("[my]\n\tsomename = false\n", c.toText());
	}

	@Test
	public void test005_PutGetStringList() {
		Config c = new Config();
		final LinkedList<String> values = new LinkedList<String>();
		values.add("value1");
		values.add("value2");
		c.setStringList("my", null, "somename", values);

		final Object[] expArr = values.toArray();
		final String[] actArr = c.getStringList("my", null, "somename");
		assertArrayEquals(expArr, actArr);

		final String expText = "[my]\n\tsomename = value1\n\tsomename = value2\n";
		assertEquals(expText, c.toText());
	}

	@Test
	public void test006_readCaseInsensitive() throws ConfigInvalidException {
		final Config c = parse("[Foo]\nBar\n");
		assertTrue(c.getBoolean("foo", null, "bar", false));
		assertEquals("", c.getString("foo", null, "bar"));
	}

	@Test
	public void test007_readUserConfig() {
		final MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
		final String hostname = mockSystemReader.getHostname();
		final Config userGitConfig = mockSystemReader.openUserConfig(null,
				FS.DETECTED);
		final Config localConfig = new Config(userGitConfig);
		mockSystemReader.clearProperties();

		String authorName;
		String authorEmail;

		// no values defined nowhere
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals(Constants.UNKNOWN_USER_DEFAULT, authorName);
		assertEquals(Constants.UNKNOWN_USER_DEFAULT + "@" + hostname, authorEmail);
		assertTrue(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());
		assertTrue(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());

		// the system user name is defined
		mockSystemReader.setProperty(Constants.OS_USER_NAME_KEY, "os user name");
		localConfig.uncache(UserConfig.KEY);
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		assertEquals("os user name", authorName);
		assertTrue(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());

		if (hostname != null && hostname.length() != 0) {
			authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
			assertEquals("os user name@" + hostname, authorEmail);
		}
		assertTrue(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());

		// the git environment variables are defined
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_NAME_KEY, "git author name");
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_EMAIL_KEY, "author@email");
		localConfig.uncache(UserConfig.KEY);
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("git author name", authorName);
		assertEquals("author@email", authorEmail);
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());

		// the values are defined in the global configuration
		userGitConfig.setString("user", null, "name", "global username");
		userGitConfig.setString("user", null, "email", "author@globalemail");
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("global username", authorName);
		assertEquals("author@globalemail", authorEmail);
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());

		// the values are defined in the local configuration
		localConfig.setString("user", null, "name", "local username");
		localConfig.setString("user", null, "email", "author@localemail");
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("local username", authorName);
		assertEquals("author@localemail", authorEmail);
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());

		authorName = localConfig.get(UserConfig.KEY).getCommitterName();
		authorEmail = localConfig.get(UserConfig.KEY).getCommitterEmail();
		assertEquals("local username", authorName);
		assertEquals("author@localemail", authorEmail);
		assertFalse(localConfig.get(UserConfig.KEY).isCommitterNameImplicit());
		assertFalse(localConfig.get(UserConfig.KEY).isCommitterEmailImplicit());
	}

	@Test
	public void testReadBoolean_TrueFalse1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = true\nb = false\n");
		assertEquals("true", c.getString("s", null, "a"));
		assertEquals("false", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	@Test
	public void testReadBoolean_TrueFalse2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = TrUe\nb = fAlSe\n");
		assertEquals("TrUe", c.getString("s", null, "a"));
		assertEquals("fAlSe", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	@Test
	public void testReadBoolean_YesNo1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = yes\nb = no\n");
		assertEquals("yes", c.getString("s", null, "a"));
		assertEquals("no", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	@Test
	public void testReadBoolean_YesNo2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = yEs\nb = NO\n");
		assertEquals("yEs", c.getString("s", null, "a"));
		assertEquals("NO", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	@Test
	public void testReadBoolean_OnOff1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = on\nb = off\n");
		assertEquals("on", c.getString("s", null, "a"));
		assertEquals("off", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	@Test
	public void testReadBoolean_OnOff2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = ON\nb = OFF\n");
		assertEquals("ON", c.getString("s", null, "a"));
		assertEquals("OFF", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	static enum TestEnum {
		ONE_TWO;
	}

	@Test
	public void testGetEnum() throws ConfigInvalidException {
		Config c = parse("[s]\na = ON\nb = input\nc = true\nd = off\n");
		assertSame(CoreConfig.AutoCRLF.TRUE, c.getEnum("s", null, "a",
				CoreConfig.AutoCRLF.FALSE));

		assertSame(CoreConfig.AutoCRLF.INPUT, c.getEnum("s", null, "b",
				CoreConfig.AutoCRLF.FALSE));

		assertSame(CoreConfig.AutoCRLF.TRUE, c.getEnum("s", null, "c",
				CoreConfig.AutoCRLF.FALSE));

		assertSame(CoreConfig.AutoCRLF.FALSE, c.getEnum("s", null, "d",
				CoreConfig.AutoCRLF.TRUE));

		c = new Config();
		assertSame(CoreConfig.AutoCRLF.FALSE, c.getEnum("s", null, "d",
				CoreConfig.AutoCRLF.FALSE));

		c = parse("[s \"b\"]\n\tc = one two\n");
		assertSame(TestEnum.ONE_TWO, c.getEnum("s", "b", "c", TestEnum.ONE_TWO));

		c = parse("[s \"b\"]\n\tc = one-two\n");
		assertSame(TestEnum.ONE_TWO, c.getEnum("s", "b", "c", TestEnum.ONE_TWO));
	}

	@Test
	public void testGetInvalidEnum() throws ConfigInvalidException {
		Config c = parse("[a]\n\tb = invalid\n");
		try {
			c.getEnum("a", null, "b", TestEnum.ONE_TWO);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid value: a.b=invalid", e.getMessage());
		}

		c = parse("[a \"b\"]\n\tc = invalid\n");
		try {
			c.getEnum("a", "b", "c", TestEnum.ONE_TWO);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid value: a.b.c=invalid", e.getMessage());
		}
	}

	@Test
	public void testSetEnum() {
		final Config c = new Config();
		c.setEnum("s", "b", "c", TestEnum.ONE_TWO);
		assertEquals("[s \"b\"]\n\tc = one two\n", c.toText());
	}

	@Test
	public void testGetFastForwardMergeoptions() throws ConfigInvalidException {
		Config c = new Config(null); // not set
		assertSame(FastForwardMode.FF, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS, FastForwardMode.FF));
		c = parse("[branch \"side\"]\n\tmergeoptions = --ff-only\n");
		assertSame(FastForwardMode.FF_ONLY, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS,
				FastForwardMode.FF_ONLY));
		c = parse("[branch \"side\"]\n\tmergeoptions = --ff\n");
		assertSame(FastForwardMode.FF, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS, FastForwardMode.FF));
		c = parse("[branch \"side\"]\n\tmergeoptions = --no-ff\n");
		assertSame(FastForwardMode.NO_FF, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS, FastForwardMode.NO_FF));
	}

	@Test
	public void testSetFastForwardMergeoptions() {
		final Config c = new Config();
		c.setEnum("branch", "side", "mergeoptions", FastForwardMode.FF);
		assertEquals("[branch \"side\"]\n\tmergeoptions = --ff\n", c.toText());
		c.setEnum("branch", "side", "mergeoptions", FastForwardMode.FF_ONLY);
		assertEquals("[branch \"side\"]\n\tmergeoptions = --ff-only\n",
				c.toText());
		c.setEnum("branch", "side", "mergeoptions", FastForwardMode.NO_FF);
		assertEquals("[branch \"side\"]\n\tmergeoptions = --no-ff\n",
				c.toText());
	}

	@Test
	public void testGetFastForwardMerge() throws ConfigInvalidException {
		Config c = new Config(null); // not set
		assertSame(FastForwardMode.Merge.TRUE, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.TRUE));
		c = parse("[merge]\n\tff = only\n");
		assertSame(FastForwardMode.Merge.ONLY, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.ONLY));
		c = parse("[merge]\n\tff = true\n");
		assertSame(FastForwardMode.Merge.TRUE, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.TRUE));
		c = parse("[merge]\n\tff = false\n");
		assertSame(FastForwardMode.Merge.FALSE, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.FALSE));
	}

	@Test
	public void testSetFastForwardMerge() {
		final Config c = new Config();
		c.setEnum("merge", null, "ff",
				FastForwardMode.Merge.valueOf(FastForwardMode.FF));
		assertEquals("[merge]\n\tff = true\n", c.toText());
		c.setEnum("merge", null, "ff",
				FastForwardMode.Merge.valueOf(FastForwardMode.FF_ONLY));
		assertEquals("[merge]\n\tff = only\n", c.toText());
		c.setEnum("merge", null, "ff",
				FastForwardMode.Merge.valueOf(FastForwardMode.NO_FF));
		assertEquals("[merge]\n\tff = false\n", c.toText());
	}

	@Test
	public void testReadLong() throws ConfigInvalidException {
		assertReadLong(1L);
		assertReadLong(-1L);
		assertReadLong(Long.MIN_VALUE);
		assertReadLong(Long.MAX_VALUE);
		assertReadLong(4L * 1024 * 1024 * 1024, "4g");
		assertReadLong(3L * 1024 * 1024, "3 m");
		assertReadLong(8L * 1024, "8 k");

		try {
			assertReadLong(-1, "1.5g");
			fail("incorrectly accepted 1.5g");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid integer value: s.a=1.5g", e.getMessage());
		}
	}

	@Test
	public void testBooleanWithNoValue() throws ConfigInvalidException {
		Config c = parse("[my]\n\tempty\n");
		assertEquals("", c.getString("my", null, "empty"));
		assertEquals(1, c.getStringList("my", null, "empty").length);
		assertEquals("", c.getStringList("my", null, "empty")[0]);
		assertTrue(c.getBoolean("my", "empty", false));
		assertEquals("[my]\n\tempty\n", c.toText());
	}

	@Test
	public void testEmptyString() throws ConfigInvalidException {
		Config c = parse("[my]\n\tempty =\n");
		assertNull(c.getString("my", null, "empty"));

		String[] values = c.getStringList("my", null, "empty");
		assertNotNull(values);
		assertEquals(1, values.length);
		assertNull(values[0]);

		// always matches the default, because its non-boolean
		assertTrue(c.getBoolean("my", "empty", true));
		assertFalse(c.getBoolean("my", "empty", false));

		assertEquals("[my]\n\tempty =\n", c.toText());

		c = new Config();
		c.setStringList("my", null, "empty", Arrays.asList(values));
		assertEquals("[my]\n\tempty =\n", c.toText());
	}

	@Test
	public void testUnsetBranchSection() throws ConfigInvalidException {
		Config c = parse("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[branch \"remove\"]\n"
				+ "  merge = this.will.get.deleted\n"
				+ "  remote = origin-for-some-long-gone-place\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n");
		c.unsetSection("branch", "does.not.exist");
		c.unsetSection("branch", "remove");
		assertEquals("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n", c.toText());
	}

	@Test
	public void testUnsetSingleSection() throws ConfigInvalidException {
		Config c = parse("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[single]\n"
				+ "  merge = this.will.get.deleted\n"
				+ "  remote = origin-for-some-long-gone-place\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n");
		c.unsetSection("single", null);
		assertEquals("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n", c.toText());
	}

	@Test
	public void test008_readSectionNames() throws ConfigInvalidException {
		final Config c = parse("[a]\n [B]\n");
		Set<String> sections = c.getSections();
		assertTrue("Sections should contain \"a\"", sections.contains("a"));
		assertTrue("Sections should contain \"b\"", sections.contains("b"));
	}

	@Test
	public void test009_readNamesInSection() throws ConfigInvalidException {
		String configString = "[core]\n" + "repositoryFormatVersion = 0\n"
				+ "filemode = false\n" + "logAllRefUpdates = true\n";
		final Config c = parse(configString);
		Set<String> names = c.getNames("core");
		assertEquals("Core section size", 3, names.size());
		assertTrue("Core section should contain \"filemode\"", names
				.contains("filemode"));

		assertTrue("Core section should contain \"repositoryFormatVersion\"",
				names.contains("repositoryFormatVersion"));

		assertTrue("Core section should contain \"logallrefupdates\"",
				names.contains("logAllRefUpdates"));

		Iterator<String> itr = names.iterator();
		assertEquals("filemode", itr.next());
		assertEquals("logAllRefUpdates", itr.next());
		assertEquals("repositoryFormatVersion", itr.next());
		assertFalse(itr.hasNext());
	}

	@Test
	public void test_ReadNamesInSectionRecursive()
			throws ConfigInvalidException {
		String baseConfigString = "[core]\n" + "logAllRefUpdates = true\n";
		String configString = "[core]\n" + "repositoryFormatVersion = 0\n"
				+ "filemode = false\n";
		final Config c = parse(configString, parse(baseConfigString));
		Set<String> names = c.getNames("core", true);
		assertEquals("Core section size", 3, names.size());
		assertTrue("Core section should contain \"filemode\"",
				names.contains("filemode"));
		assertTrue("Core section should contain \"repositoryFormatVersion\"",
				names.contains("repositoryFormatVersion"));
		assertTrue("Core section should contain \"logAllRefUpdates\"",
				names.contains("logAllRefUpdates"));

		Iterator<String> itr = names.iterator();
		assertEquals("filemode", itr.next());
		assertEquals("repositoryFormatVersion", itr.next());
		assertEquals("logAllRefUpdates", itr.next());
		assertFalse(itr.hasNext());
	}

	@Test
	public void test010_readNamesInSubSection() throws ConfigInvalidException {
		String configString = "[a \"sub1\"]\n"//
				+ "x = 0\n" //
				+ "y = false\n"//
				+ "z = true\n"//
				+ "[a \"sub2\"]\n"//
				+ "a=0\n"//
				+ "b=1\n";
		final Config c = parse(configString);
		Set<String> names = c.getNames("a", "sub1");
		assertEquals("Subsection size", 3, names.size());
		assertTrue("Subsection should contain \"x\"", names.contains("x"));
		assertTrue("Subsection should contain \"y\"", names.contains("y"));
		assertTrue("Subsection should contain \"z\"", names.contains("z"));
		names = c.getNames("a", "sub2");
		assertEquals("Subsection size", 2, names.size());
		assertTrue("Subsection should contain \"a\"", names.contains("a"));
		assertTrue("Subsection should contain \"b\"", names.contains("b"));
	}

	@Test
	public void readNamesInSubSectionRecursive() throws ConfigInvalidException {
		String baseConfigString = "[a \"sub1\"]\n"//
				+ "x = 0\n" //
				+ "y = false\n"//
				+ "[a \"sub2\"]\n"//
				+ "a=0\n";//
		String configString = "[a \"sub1\"]\n"//
				+ "z = true\n"//
				+ "[a \"sub2\"]\n"//
				+ "b=1\n";
		final Config c = parse(configString, parse(baseConfigString));
		Set<String> names = c.getNames("a", "sub1", true);
		assertEquals("Subsection size", 3, names.size());
		assertTrue("Subsection should contain \"x\"", names.contains("x"));
		assertTrue("Subsection should contain \"y\"", names.contains("y"));
		assertTrue("Subsection should contain \"z\"", names.contains("z"));
		names = c.getNames("a", "sub2", true);
		assertEquals("Subsection size", 2, names.size());
		assertTrue("Subsection should contain \"a\"", names.contains("a"));
		assertTrue("Subsection should contain \"b\"", names.contains("b"));
	}

	@Test
	public void testQuotingForSubSectionNames() {
		String resultPattern = "[testsection \"{0}\"]\n\ttestname = testvalue\n";
		String result;

		Config config = new Config();
		config.setString("testsection", "testsubsection", "testname",
				"testvalue");

		result = MessageFormat.format(resultPattern, "testsubsection");
		assertEquals(result, config.toText());
		config.clear();

		config.setString("testsection", "#quotable", "testname", "testvalue");
		result = MessageFormat.format(resultPattern, "#quotable");
		assertEquals(result, config.toText());
		config.clear();

		config.setString("testsection", "with\"quote", "testname", "testvalue");
		result = MessageFormat.format(resultPattern, "with\\\"quote");
		assertEquals(result, config.toText());
	}

	@Test
	public void testNoFinalNewline() throws ConfigInvalidException {
		Config c = parse("[a]\n"
				+ "x = 0\n"
				+ "y = 1");
		assertEquals("0", c.getString("a", null, "x"));
		assertEquals("1", c.getString("a", null, "y"));
	}

	private static void assertReadLong(long exp) throws ConfigInvalidException {
		assertReadLong(exp, String.valueOf(exp));
	}

	private static void assertReadLong(long exp, String act)
			throws ConfigInvalidException {
		final Config c = parse("[s]\na = " + act + "\n");
		assertEquals(exp, c.getLong("s", null, "a", 0L));
	}

	private static Config parse(final String content)
			throws ConfigInvalidException {
		return parse(content, null);
	}

	private static Config parse(final String content, Config baseConfig)
			throws ConfigInvalidException {
		final Config c = new Config(baseConfig);
		c.fromText(content);
		return c;
	}
}
