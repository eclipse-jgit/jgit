/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoteConfigTest {
	private Config config;

	@BeforeEach
	public void setUp() throws Exception {
		config = new Config();
	}

	private void readConfig(String dat) throws ConfigInvalidException {
		config = new Config();
		config.fromText(dat);
	}

	private void checkConfig(String exp) {
		assertEquals(exp, config.toText());
	}

	@Test
	void testSimple() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		final List<URIish> allURIs = rc.getURIs();
		RefSpec spec;

		assertEquals("spearce", rc.getName());
		assertNotNull(allURIs);
		assertNotNull(rc.getFetchRefSpecs());
		assertNotNull(rc.getPushRefSpecs());
		assertNotNull(rc.getTagOpt());
		assertEquals(0, rc.getTimeout());
		assertSame(TagOpt.AUTO_FOLLOW, rc.getTagOpt());

		assertEquals(1, allURIs.size());
		assertEquals("http://www.spearce.org/egit.git", allURIs.get(0)
				.toString());

		assertEquals(1, rc.getFetchRefSpecs().size());
		spec = rc.getFetchRefSpecs().get(0);
		assertTrue(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/heads/*", spec.getSource());
		assertEquals("refs/remotes/spearce/*", spec.getDestination());

		assertEquals(0, rc.getPushRefSpecs().size());
	}

	@Test
	void testSimpleNoTags() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n"
				+ "tagopt = --no-tags\n");
		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		assertSame(TagOpt.NO_TAGS, rc.getTagOpt());
	}

	@Test
	void testSimpleAlwaysTags() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n"
				+ "tagopt = --tags\n");
		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		assertSame(TagOpt.FETCH_TAGS, rc.getTagOpt());
	}

	@Test
	void testMirror() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/heads/*\n"
				+ "fetch = refs/tags/*:refs/tags/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		final List<URIish> allURIs = rc.getURIs();
		RefSpec spec;

		assertEquals("spearce", rc.getName());
		assertNotNull(allURIs);
		assertNotNull(rc.getFetchRefSpecs());
		assertNotNull(rc.getPushRefSpecs());

		assertEquals(1, allURIs.size());
		assertEquals("http://www.spearce.org/egit.git", allURIs.get(0)
				.toString());

		assertEquals(2, rc.getFetchRefSpecs().size());

		spec = rc.getFetchRefSpecs().get(0);
		assertTrue(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/heads/*", spec.getSource());
		assertEquals("refs/heads/*", spec.getDestination());

		spec = rc.getFetchRefSpecs().get(1);
		assertFalse(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/tags/*", spec.getSource());
		assertEquals("refs/tags/*", spec.getDestination());

		assertEquals(0, rc.getPushRefSpecs().size());
	}

	@Test
	void testBackup() throws Exception {
		readConfig("[remote \"backup\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "url = user@repo.or.cz:/srv/git/egit.git\n"
				+ "push = +refs/heads/*:refs/heads/*\n"
				+ "push = refs/tags/*:refs/tags/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "backup");
		final List<URIish> allURIs = rc.getURIs();
		RefSpec spec;

		assertEquals("backup", rc.getName());
		assertNotNull(allURIs);
		assertNotNull(rc.getFetchRefSpecs());
		assertNotNull(rc.getPushRefSpecs());

		assertEquals(2, allURIs.size());
		assertEquals("http://www.spearce.org/egit.git", allURIs.get(0)
				.toString());
		assertEquals("user@repo.or.cz:/srv/git/egit.git", allURIs.get(1)
				.toString());

		assertEquals(0, rc.getFetchRefSpecs().size());

		assertEquals(2, rc.getPushRefSpecs().size());
		spec = rc.getPushRefSpecs().get(0);
		assertTrue(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/heads/*", spec.getSource());
		assertEquals("refs/heads/*", spec.getDestination());

		spec = rc.getPushRefSpecs().get(1);
		assertFalse(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/tags/*", spec.getSource());
		assertEquals("refs/tags/*", spec.getDestination());
	}

	@Test
	void testUploadPack() throws Exception {
		readConfig("[remote \"example\"]\n"
				+ "url = user@example.com:egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/example/*\n"
				+ "uploadpack = /path/to/git/git-upload-pack\n"
				+ "receivepack = /path/to/git/git-receive-pack\n");

		final RemoteConfig rc = new RemoteConfig(config, "example");
		final List<URIish> allURIs = rc.getURIs();
		RefSpec spec;

		assertEquals("example", rc.getName());
		assertNotNull(allURIs);
		assertNotNull(rc.getFetchRefSpecs());
		assertNotNull(rc.getPushRefSpecs());

		assertEquals(1, allURIs.size());
		assertEquals("user@example.com:egit.git", allURIs.get(0).toString());

		assertEquals(1, rc.getFetchRefSpecs().size());
		spec = rc.getFetchRefSpecs().get(0);
		assertTrue(spec.isForceUpdate());
		assertTrue(spec.isWildcard());
		assertEquals("refs/heads/*", spec.getSource());
		assertEquals("refs/remotes/example/*", spec.getDestination());

		assertEquals(0, rc.getPushRefSpecs().size());

		assertEquals("/path/to/git/git-upload-pack", rc.getUploadPack());
		assertEquals("/path/to/git/git-receive-pack", rc.getReceivePack());
	}

	@Test
	void testUnknown() throws Exception {
		readConfig("");

		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertEquals(0, rc.getURIs().size());
		assertEquals(0, rc.getFetchRefSpecs().size());
		assertEquals(0, rc.getPushRefSpecs().size());
		assertEquals("git-upload-pack", rc.getUploadPack());
		assertEquals("git-receive-pack", rc.getReceivePack());
	}

	@Test
	void testAddURI() throws Exception {
		readConfig("");

		final URIish uri = new URIish("/some/dir");
		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertEquals(0, rc.getURIs().size());

		assertTrue(rc.addURI(uri));
		assertEquals(1, rc.getURIs().size());
		assertSame(uri, rc.getURIs().get(0));

		assertFalse(rc.addURI(new URIish(uri.toString())));
		assertEquals(1, rc.getURIs().size());
	}

	@Test
	void testRemoveFirstURI() throws Exception {
		readConfig("");

		final URIish a = new URIish("/some/dir");
		final URIish b = new URIish("/another/dir");
		final URIish c = new URIish("/more/dirs");
		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertTrue(rc.addURI(a));
		assertTrue(rc.addURI(b));
		assertTrue(rc.addURI(c));

		assertEquals(3, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));
		assertSame(b, rc.getURIs().get(1));
		assertSame(c, rc.getURIs().get(2));

		assertTrue(rc.removeURI(a));
		assertEquals(2, rc.getURIs().size());
		assertSame(b, rc.getURIs().get(0));
		assertSame(c, rc.getURIs().get(1));
	}

	@Test
	void testRemoveMiddleURI() throws Exception {
		readConfig("");

		final URIish a = new URIish("/some/dir");
		final URIish b = new URIish("/another/dir");
		final URIish c = new URIish("/more/dirs");
		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertTrue(rc.addURI(a));
		assertTrue(rc.addURI(b));
		assertTrue(rc.addURI(c));

		assertEquals(3, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));
		assertSame(b, rc.getURIs().get(1));
		assertSame(c, rc.getURIs().get(2));

		assertTrue(rc.removeURI(b));
		assertEquals(2, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));
		assertSame(c, rc.getURIs().get(1));
	}

	@Test
	void testRemoveLastURI() throws Exception {
		readConfig("");

		final URIish a = new URIish("/some/dir");
		final URIish b = new URIish("/another/dir");
		final URIish c = new URIish("/more/dirs");
		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertTrue(rc.addURI(a));
		assertTrue(rc.addURI(b));
		assertTrue(rc.addURI(c));

		assertEquals(3, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));
		assertSame(b, rc.getURIs().get(1));
		assertSame(c, rc.getURIs().get(2));

		assertTrue(rc.removeURI(c));
		assertEquals(2, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));
		assertSame(b, rc.getURIs().get(1));
	}

	@Test
	void testRemoveOnlyURI() throws Exception {
		readConfig("");

		final URIish a = new URIish("/some/dir");
		final RemoteConfig rc = new RemoteConfig(config, "backup");
		assertTrue(rc.addURI(a));

		assertEquals(1, rc.getURIs().size());
		assertSame(a, rc.getURIs().get(0));

		assertTrue(rc.removeURI(a));
		assertEquals(0, rc.getURIs().size());
	}

	@Test
	void testCreateOrigin() throws Exception {
		final RemoteConfig rc = new RemoteConfig(config, "origin");
		rc.addURI(new URIish("/some/dir"));
		rc.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
				+ rc.getName() + "/*"));
		rc.update(config);
		checkConfig("[remote \"origin\"]\n" + "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/origin/*\n");
	}

	@Test
	void testSaveAddURI() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		rc.addURI(new URIish("/some/dir"));
		assertEquals(2, rc.getURIs().size());
		rc.update(config);
		checkConfig("[remote \"spearce\"]\n"
				+ "\turl = http://www.spearce.org/egit.git\n"
				+ "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/spearce/*\n");
	}

	@Test
	void testSaveRemoveLastURI() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "url = /some/dir\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		assertEquals(2, rc.getURIs().size());
		rc.removeURI(new URIish("/some/dir"));
		assertEquals(1, rc.getURIs().size());
		rc.update(config);
		checkConfig("[remote \"spearce\"]\n"
				+ "\turl = http://www.spearce.org/egit.git\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/spearce/*\n");
	}

	@Test
	void testSaveRemoveFirstURI() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "url = /some/dir\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n");

		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		assertEquals(2, rc.getURIs().size());
		rc.removeURI(new URIish("http://www.spearce.org/egit.git"));
		assertEquals(1, rc.getURIs().size());
		rc.update(config);
		checkConfig("[remote \"spearce\"]\n" + "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/spearce/*\n");
	}

	@Test
	void testSaveNoTags() throws Exception {
		final RemoteConfig rc = new RemoteConfig(config, "origin");
		rc.addURI(new URIish("/some/dir"));
		rc.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
				+ rc.getName() + "/*"));
		rc.setTagOpt(TagOpt.NO_TAGS);
		rc.update(config);
		checkConfig("[remote \"origin\"]\n" + "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "\ttagopt = --no-tags\n");
	}

	@Test
	void testSaveAllTags() throws Exception {
		final RemoteConfig rc = new RemoteConfig(config, "origin");
		rc.addURI(new URIish("/some/dir"));
		rc.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
				+ rc.getName() + "/*"));
		rc.setTagOpt(TagOpt.FETCH_TAGS);
		rc.update(config);
		checkConfig("[remote \"origin\"]\n" + "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "\ttagopt = --tags\n");
	}

	@Test
	void testSimpleTimeout() throws Exception {
		readConfig("[remote \"spearce\"]\n"
				+ "url = http://www.spearce.org/egit.git\n"
				+ "fetch = +refs/heads/*:refs/remotes/spearce/*\n"
				+ "timeout = 12\n");
		final RemoteConfig rc = new RemoteConfig(config, "spearce");
		assertEquals(12, rc.getTimeout());
	}

	@Test
	void testSaveTimeout() throws Exception {
		final RemoteConfig rc = new RemoteConfig(config, "origin");
		rc.addURI(new URIish("/some/dir"));
		rc.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
				+ rc.getName() + "/*"));
		rc.setTimeout(60);
		rc.update(config);
		checkConfig("[remote \"origin\"]\n" + "\turl = /some/dir\n"
				+ "\tfetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "\ttimeout = 60\n");
	}

	@Test
	void noInsteadOf() throws Exception {
		config.setString("remote", "origin", "url", "short:project.git");
		config.setString("url", "https://server/repos/", "name", "short:");
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getURIs().isEmpty());
		assertEquals("short:project.git", rc.getURIs().get(0).toASCIIString());
	}

	@Test
	void singleInsteadOf() throws Exception {
		config.setString("remote", "origin", "url", "short:project.git");
		config.setString("url", "https://server/repos/", "insteadOf", "short:");
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getURIs().isEmpty());
		assertEquals("https://server/repos/project.git", rc.getURIs().get(0)
				.toASCIIString());
	}

	@Test
	void multipleInsteadOf() throws Exception {
		config.setString("remote", "origin", "url", "prefixproject.git");
		config.setStringList("url", "https://server/repos/", "insteadOf",
				Arrays.asList("pre", "prefix", "pref", "perf"));
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getURIs().isEmpty());
		assertEquals("https://server/repos/project.git", rc.getURIs().get(0)
				.toASCIIString());
	}

	@Test
	void noPushInsteadOf() throws Exception {
		config.setString("remote", "origin", "pushurl", "short:project.git");
		config.setString("url", "https://server/repos/", "name", "short:");
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getPushURIs().isEmpty());
		assertEquals("short:project.git", rc.getPushURIs().get(0)
				.toASCIIString());
	}

	@Test
	void pushInsteadOfNotAppliedToPushUri() throws Exception {
		config.setString("remote", "origin", "pushurl", "short:project.git");
		config.setString("url", "https://server/repos/", "pushInsteadOf",
				"short:");
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getPushURIs().isEmpty());
		assertEquals("short:project.git",
				rc.getPushURIs().get(0).toASCIIString());
	}

	@Test
	void pushInsteadOfAppliedToUri() throws Exception {
		config.setString("remote", "origin", "url", "short:project.git");
		config.setString("url", "https://server/repos/", "pushInsteadOf",
				"short:");
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getPushURIs().isEmpty());
		assertEquals("https://server/repos/project.git",
				rc.getPushURIs().get(0).toASCIIString());
	}

	@Test
	void multiplePushInsteadOf() throws Exception {
		config.setString("remote", "origin", "url", "prefixproject.git");
		config.setStringList("url", "https://server/repos/", "pushInsteadOf",
				Arrays.asList("pre", "prefix", "pref", "perf"));
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getPushURIs().isEmpty());
		assertEquals("https://server/repos/project.git", rc.getPushURIs()
				.get(0).toASCIIString());
	}

	@Test
	void pushInsteadOfNoPushUrl() throws Exception {
		config.setString("remote", "origin", "url",
				"http://git.eclipse.org/gitroot/jgit/jgit");
		config.setStringList("url", "ssh://someone@git.eclipse.org:29418/",
				"pushInsteadOf",
				Collections.singletonList("http://git.eclipse.org/gitroot/"));
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertFalse(rc.getPushURIs().isEmpty());
		assertEquals("ssh://someone@git.eclipse.org:29418/jgit/jgit",
				rc.getPushURIs().get(0).toASCIIString());
	}
}
