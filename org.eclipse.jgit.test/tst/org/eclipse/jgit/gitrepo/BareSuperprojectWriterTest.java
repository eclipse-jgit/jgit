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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.gitrepo.BareSuperprojectWriter.BareWriterConfig;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
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
					BareWriterConfig.getDefault());

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

	private String readContents(Repository repo, RevCommit commit,
			String path) throws Exception {
		String idStr = commit.getId().name() + ":" + path;
		ObjectId modId = repo.resolve(idStr);
		try (ObjectReader reader = repo.newObjectReader()) {
			return new String(
					reader.open(modId).getCachedBytes(Integer.MAX_VALUE));

		}
	}
}
