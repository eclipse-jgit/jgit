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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.merge.MergeConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

/**
 * Test reading of git config
 */
public class ConfigTest {

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

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
		final LinkedList<String> values = new LinkedList<>();
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
		// first clear environment variables since they would override
		// configuration files
		mockSystemReader.clearProperties();
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

		// also git environment variables are defined
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_NAME_KEY,
				"git author name");
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_EMAIL_KEY,
				"author@email");
		localConfig.setString("user", null, "name", "local username");
		localConfig.setString("user", null, "email", "author@localemail");
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("git author name", authorName);
		assertEquals("author@email", authorEmail);
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorNameImplicit());
		assertFalse(localConfig.get(UserConfig.KEY).isAuthorEmailImplicit());
	}

	@Test
	public void testReadUserConfigWithInvalidCharactersStripped() {
		final MockSystemReader mockSystemReader = new MockSystemReader();
		final Config localConfig = new Config(mockSystemReader.openUserConfig(
				null, FS.DETECTED));

		localConfig.setString("user", null, "name", "foo<bar");
		localConfig.setString("user", null, "email", "baz>\nqux@example.com");

		UserConfig userConfig = localConfig.get(UserConfig.KEY);
		assertEquals("foobar", userConfig.getAuthorName());
		assertEquals("bazqux@example.com", userConfig.getAuthorEmail());
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
		MergeConfig mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF, mergeConfig.getFastForwardMode());
		c = parse("[branch \"side\"]\n\tmergeoptions = --ff-only\n");
		assertSame(FastForwardMode.FF_ONLY, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS,
				FastForwardMode.FF_ONLY));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF_ONLY, mergeConfig.getFastForwardMode());
		c = parse("[branch \"side\"]\n\tmergeoptions = --ff\n");
		assertSame(FastForwardMode.FF, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS, FastForwardMode.FF));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF, mergeConfig.getFastForwardMode());
		c = parse("[branch \"side\"]\n\tmergeoptions = --no-ff\n");
		assertSame(FastForwardMode.NO_FF, c.getEnum(
				ConfigConstants.CONFIG_BRANCH_SECTION, "side",
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS, FastForwardMode.NO_FF));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.NO_FF, mergeConfig.getFastForwardMode());
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
		MergeConfig mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF, mergeConfig.getFastForwardMode());
		c = parse("[merge]\n\tff = only\n");
		assertSame(FastForwardMode.Merge.ONLY, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.ONLY));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF_ONLY, mergeConfig.getFastForwardMode());
		c = parse("[merge]\n\tff = true\n");
		assertSame(FastForwardMode.Merge.TRUE, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.TRUE));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF, mergeConfig.getFastForwardMode());
		c = parse("[merge]\n\tff = false\n");
		assertSame(FastForwardMode.Merge.FALSE, c.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.FALSE));
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.NO_FF, mergeConfig.getFastForwardMode());
	}

	@Test
	public void testCombinedMergeOptions() throws ConfigInvalidException {
		Config c = new Config(null); // not set
		MergeConfig mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF, mergeConfig.getFastForwardMode());
		assertTrue(mergeConfig.isCommit());
		assertFalse(mergeConfig.isSquash());
		// branch..mergeoptions should win over merge.ff
		c = parse("[merge]\n\tff = false\n"
				+ "[branch \"side\"]\n\tmergeoptions = --ff-only\n");
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF_ONLY, mergeConfig.getFastForwardMode());
		assertTrue(mergeConfig.isCommit());
		assertFalse(mergeConfig.isSquash());
		// merge.ff used for ff setting if not set via mergeoptions
		c = parse("[merge]\n\tff = only\n"
				+ "[branch \"side\"]\n\tmergeoptions = --squash\n");
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF_ONLY, mergeConfig.getFastForwardMode());
		assertTrue(mergeConfig.isCommit());
		assertTrue(mergeConfig.isSquash());
		// mergeoptions wins if it has ff options amongst other options
		c = parse("[merge]\n\tff = false\n"
				+ "[branch \"side\"]\n\tmergeoptions = --ff-only --no-commit\n");
		mergeConfig = c.get(MergeConfig.getParser("side"));
		assertSame(FastForwardMode.FF_ONLY, mergeConfig.getFastForwardMode());
		assertFalse(mergeConfig.isCommit());
		assertFalse(mergeConfig.isSquash());
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

		assertTrue("Core section should contain \"repositoryformatversion\"",
				names.contains("repositoryformatversion"));

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
		assertTrue("Core section should contain \"logallrefupdates\"",
				names.contains("logallrefupdates"));

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
				+ "A=0\n";//
		String configString = "[a \"sub1\"]\n"//
				+ "z = true\n"//
				+ "[a \"sub2\"]\n"//
				+ "B=1\n";
		final Config c = parse(configString, parse(baseConfigString));
		Set<String> names = c.getNames("a", "sub1", true);
		assertEquals("Subsection size", 3, names.size());
		assertTrue("Subsection should contain \"x\"", names.contains("x"));
		assertTrue("Subsection should contain \"y\"", names.contains("y"));
		assertTrue("Subsection should contain \"z\"", names.contains("z"));
		names = c.getNames("a", "sub2", true);
		assertEquals("Subsection size", 2, names.size());
		assertTrue("Subsection should contain \"A\"", names.contains("A"));
		assertTrue("Subsection should contain \"a\"", names.contains("a"));
		assertTrue("Subsection should contain \"B\"", names.contains("B"));
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

	@Test
	public void testExplicitlySetEmptyString() throws Exception {
		Config c = new Config();
		c.setString("a", null, "x", "0");
		c.setString("a", null, "y", "");

		assertEquals("0", c.getString("a", null, "x"));
		assertEquals(0, c.getInt("a", null, "x", 1));

		assertEquals("", c.getString("a", null, "y"));
		assertArrayEquals(new String[]{""}, c.getStringList("a", null, "y"));
		try {
			c.getInt("a", null, "y", 1);
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid integer value: a.y=", e.getMessage());
		}

		assertNull(c.getString("a", null, "z"));
		assertArrayEquals(new String[]{}, c.getStringList("a", null, "z"));
	}

	@Test
	public void testParsedEmptyString() throws Exception {
		Config c = parse("[a]\n"
				+ "x = 0\n"
				+ "y =\n");

		assertEquals("0", c.getString("a", null, "x"));
		assertEquals(0, c.getInt("a", null, "x", 1));

		assertNull(c.getString("a", null, "y"));
		assertArrayEquals(new String[]{null}, c.getStringList("a", null, "y"));
		try {
			c.getInt("a", null, "y", 1);
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid integer value: a.y=", e.getMessage());
		}

		assertNull(c.getString("a", null, "z"));
		assertArrayEquals(new String[]{}, c.getStringList("a", null, "z"));
	}

	@Test
	public void testSetStringListWithEmptyValue() throws Exception {
		Config c = new Config();
		c.setStringList("a", null, "x", Arrays.asList(""));
		assertArrayEquals(new String[]{""}, c.getStringList("a", null, "x"));
	}

	@Test
	public void testEmptyValueAtEof() throws Exception {
		String text = "[a]\nx =";
		Config c = parse(text);
		assertNull(c.getString("a", null, "x"));
		assertArrayEquals(new String[]{null},
				c.getStringList("a", null, "x"));
		c = parse(text + "\n");
		assertNull(c.getString("a", null, "x"));
		assertArrayEquals(new String[]{null},
				c.getStringList("a", null, "x"));
	}

	@Test
	public void testReadMultipleValuesForName() throws ConfigInvalidException {
		Config c = parse("[foo]\nbar=false\nbar=true\n");
		assertTrue(c.getBoolean("foo", "bar", false));
	}

	@Test
	public void testIncludeInvalidName() throws ConfigInvalidException {
		expectedEx.expect(ConfigInvalidException.class);
		expectedEx.expectMessage(JGitText.get().invalidLineInConfigFile);
		parse("[include]\nbar\n");
	}

	@Test
	public void testIncludeNoValue() throws ConfigInvalidException {
		expectedEx.expect(ConfigInvalidException.class);
		expectedEx.expectMessage(JGitText.get().invalidLineInConfigFile);
		parse("[include]\npath\n");
	}

	@Test
	public void testIncludeEmptyValue() throws ConfigInvalidException {
		expectedEx.expect(ConfigInvalidException.class);
		expectedEx.expectMessage(JGitText.get().invalidLineInConfigFile);
		parse("[include]\npath=\n");
	}

	@Test
	public void testIncludeValuePathNotFound() throws ConfigInvalidException {
		// we do not expect an exception, included path not found are ignored
		String notFound = "/not/found";
		Config parsed = parse("[include]\npath=" + notFound + "\n");
		assertEquals(1, parsed.getSections().size());
		assertEquals(notFound, parsed.getString("include", null, "path"));
	}

	@Test
	public void testIncludeValuePathWithTilde() throws ConfigInvalidException {
		// we do not expect an exception, included path not supported are
		// ignored
		String notSupported = "~/someFile";
		Config parsed = parse("[include]\npath=" + notSupported + "\n");
		assertEquals(1, parsed.getSections().size());
		assertEquals(notSupported, parsed.getString("include", null, "path"));
	}

	@Test
	public void testIncludeValuePathRelative() throws ConfigInvalidException {
		// we do not expect an exception, included path not supported are
		// ignored
		String notSupported = "someRelativeFile";
		Config parsed = parse("[include]\npath=" + notSupported + "\n");
		assertEquals(1, parsed.getSections().size());
		assertEquals(notSupported, parsed.getString("include", null, "path"));
	}

	@Test
	public void testIncludeTooManyRecursions() throws IOException {
		File config = tmp.newFile("config");
		String include = "[include]\npath=" + config.toPath() + "\n";
		Files.write(config.toPath(), include.getBytes());
		FileBasedConfig fbConfig = new FileBasedConfig(null, config,
				FS.DETECTED);
		try {
			fbConfig.load();
			fail();
		} catch (ConfigInvalidException cie) {
			assertEquals(JGitText.get().tooManyIncludeRecursions,
					cie.getCause().getMessage());
		}
	}

	@Test
	public void testInclude() throws IOException, ConfigInvalidException {
		File config = tmp.newFile("config");
		File more = tmp.newFile("config.more");
		File other = tmp.newFile("config.other");

		String fooBar = "[foo]\nbar=true\n";
		String includeMore = "[include]\npath=" + more.toPath() + "\n";
		String includeOther = "path=" + other.toPath() + "\n";
		String fooPlus = fooBar + includeMore + includeOther;
		Files.write(config.toPath(), fooPlus.getBytes());

		String fooMore = "[foo]\nmore=bar\n";
		Files.write(more.toPath(), fooMore.getBytes());

		String otherMore = "[other]\nmore=bar\n";
		Files.write(other.toPath(), otherMore.getBytes());

		Config parsed = parse("[include]\npath=" + config.toPath() + "\n");
		assertTrue(parsed.getBoolean("foo", "bar", false));
		assertEquals("bar", parsed.getString("foo", null, "more"));
		assertEquals("bar", parsed.getString("other", null, "more"));
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

	@Test
	public void testTimeUnit() throws ConfigInvalidException {
		assertEquals(0, parseTime("0", MILLISECONDS));
		assertEquals(2, parseTime("2ms", MILLISECONDS));
		assertEquals(200, parseTime("200 milliseconds", MILLISECONDS));

		assertEquals(0, parseTime("0s", SECONDS));
		assertEquals(2, parseTime("2s", SECONDS));
		assertEquals(231, parseTime("231sec", SECONDS));
		assertEquals(1, parseTime("1second", SECONDS));
		assertEquals(300, parseTime("300 seconds", SECONDS));

		assertEquals(2, parseTime("2m", MINUTES));
		assertEquals(2, parseTime("2min", MINUTES));
		assertEquals(1, parseTime("1 minute", MINUTES));
		assertEquals(10, parseTime("10 minutes", MINUTES));

		assertEquals(5, parseTime("5h", HOURS));
		assertEquals(5, parseTime("5hr", HOURS));
		assertEquals(1, parseTime("1hour", HOURS));
		assertEquals(48, parseTime("48hours", HOURS));

		assertEquals(5, parseTime("5 h", HOURS));
		assertEquals(5, parseTime("5 hr", HOURS));
		assertEquals(1, parseTime("1 hour", HOURS));
		assertEquals(48, parseTime("48 hours", HOURS));
		assertEquals(48, parseTime("48 \t \r hours", HOURS));

		assertEquals(4, parseTime("4d", DAYS));
		assertEquals(1, parseTime("1day", DAYS));
		assertEquals(14, parseTime("14days", DAYS));

		assertEquals(7, parseTime("1w", DAYS));
		assertEquals(7, parseTime("1week", DAYS));
		assertEquals(14, parseTime("2w", DAYS));
		assertEquals(14, parseTime("2weeks", DAYS));

		assertEquals(30, parseTime("1mon", DAYS));
		assertEquals(30, parseTime("1month", DAYS));
		assertEquals(60, parseTime("2mon", DAYS));
		assertEquals(60, parseTime("2months", DAYS));

		assertEquals(365, parseTime("1y", DAYS));
		assertEquals(365, parseTime("1year", DAYS));
		assertEquals(365 * 2, parseTime("2years", DAYS));
	}

	private long parseTime(String value, TimeUnit unit)
			throws ConfigInvalidException {
		Config c = parse("[a]\na=" + value + "\n");
		return c.getTimeUnit("a", null, "a", 0, unit);
	}

	@Test
	public void testTimeUnitDefaultValue() throws ConfigInvalidException {
		// value not present
		assertEquals(20, parse("[a]\na=0\n").getTimeUnit("a", null, "b", 20,
				MILLISECONDS));
		// value is empty
		assertEquals(20, parse("[a]\na=\" \"\n").getTimeUnit("a", null, "a", 20,
				MILLISECONDS));

		// value is not numeric
		assertEquals(20, parse("[a]\na=test\n").getTimeUnit("a", null, "a", 20,
				MILLISECONDS));
	}

	@Test
	public void testTimeUnitInvalid() throws ConfigInvalidException {
		expectedEx.expect(IllegalArgumentException.class);
		expectedEx
				.expectMessage("Invalid time unit value: a.a=1 monttthhh");
		parseTime("1 monttthhh", DAYS);
	}

	@Test
	public void testTimeUnitInvalidWithSection() throws ConfigInvalidException {
		Config c = parse("[a \"b\"]\na=1 monttthhh\n");
		expectedEx.expect(IllegalArgumentException.class);
		expectedEx.expectMessage("Invalid time unit value: a.b.a=1 monttthhh");
		c.getTimeUnit("a", "b", "a", 0, DAYS);
	}

	@Test
	public void testTimeUnitNegative() throws ConfigInvalidException {
		expectedEx.expect(IllegalArgumentException.class);
		parseTime("-1", MILLISECONDS);
	}
}
