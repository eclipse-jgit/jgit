/*
 * Copyright (C) 2021, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.gitrepo.BareSuperprojectWriter.BareWriterConfig;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BareSuperprojectWriterTest extends RepositoryTestCase {

	private static final String SHA1_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	@Override
	public void setUp() throws Exception {
		super.setUp();
	}

	@Test
	public void write_setGitModulesContents() throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					"refs/heads/branch-x", "remote", "");
			repoProject.setUrl("http://example.com/a");

			RemoteReader mockRemoteReader = mock(RemoteReader.class);
			when(mockRemoteReader.sha1("http://example.com/a",
					"refs/heads/branch-x"))
							.thenReturn(ObjectId.fromString(SHA1_A));

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, mockRemoteReader,
					BareWriterConfig.getDefault(), List.of());

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, ".gitmodules");
			List<String> contentLines = Arrays
					.asList(contents.split("\n"));
			assertThat(contentLines.get(0),
					is("[submodule \"subprojectX\"]"));
			assertThat(contentLines.subList(1, contentLines.size()),
					containsInAnyOrder(is("\tbranch = refs/heads/branch-x"),
							is("\tpath = path/to"),
							is("\turl = http://example.com/a")));
		}
	}

	@Test
	public void write_setGitModulesContents_pinned() throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject pinWithUpstream = new RepoProject("pinWithUpstream",
					"path/x", "cbc0fae7e1911d27e1de37d364698dba4411c78b",
					"remote", "");
			pinWithUpstream.setUrl("http://example.com/a");
			pinWithUpstream.setUpstream("branchX");

			RepoProject pinWithoutUpstream = new RepoProject(
					"pinWithoutUpstream", "path/y",
					"cbc0fae7e1911d27e1de37d364698dba4411c78b", "remote", "");
			pinWithoutUpstream.setUrl("http://example.com/b");

			RemoteReader mockRemoteReader = mock(RemoteReader.class);

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, mockRemoteReader,
					BareWriterConfig.getDefault(), List.of());

			RevCommit commit = w
					.write(Arrays.asList(pinWithUpstream, pinWithoutUpstream));

			String contents = readContents(bareRepo, commit, ".gitmodules");
			Config cfg = new Config();
			cfg.fromText(contents);

			assertThat(cfg.getString("submodule", "pinWithUpstream", "path"),
					is("path/x"));
			assertThat(cfg.getString("submodule", "pinWithUpstream", "url"),
					is("http://example.com/a"));
			assertThat(cfg.getString("submodule", "pinWithUpstream", "ref"),
					is("branchX"));

			assertThat(cfg.getString("submodule", "pinWithoutUpstream", "path"),
					is("path/y"));
			assertThat(cfg.getString("submodule", "pinWithoutUpstream", "url"),
					is("http://example.com/b"));
			assertThat(cfg.getString("submodule", "pinWithoutUpstream", "ref"),
					nullValue());
		}
	}

	@Test
	public void write_setExtraContents() throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					"refs/heads/branch-x", "remote", "");
			repoProject.setUrl("http://example.com/a");

			RemoteReader mockRemoteReader = mock(RemoteReader.class);
			when(mockRemoteReader.sha1("http://example.com/a",
					"refs/heads/branch-x"))
							.thenReturn(ObjectId.fromString(SHA1_A));

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, mockRemoteReader,
					BareWriterConfig.getDefault(),
					List.of(new BareSuperprojectWriter.ExtraContent("x",
							"extra-content")));

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, "x");
			assertThat(contents, is("extra-content"));
		}
	}

	private String readContents(Repository repo, RevCommit commit,
			String path) throws Exception {
		String idStr = commit.getId().name() + ":" + path;
		ObjectId modId = repo.resolve(idStr);
		try (ObjectReader reader = repo.newObjectReader()) {
			return new String(
					reader.open(modId).getCachedBytes(Integer.MAX_VALUE),
					StandardCharsets.UTF_8);

		}
	}
}
