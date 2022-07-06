/*
 * Copyright (C) 2013, Chris Aniszczyk <zx@twitter.com> and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class FetchTest extends CLIRepositoryTestCase {
	private Git git;

	private Git remoteGit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();

		Repository remoteRepository = createWorkRepository();
		addRepoToClose(remoteRepository);
		remoteGit = new Git(remoteRepository);

		// setup the first repository to fetch from the second repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(remoteRepository.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		remoteGit.commit().setMessage("initial commit").call();
		remoteGit.tag().setName("tag").call();
		remoteGit.checkout().setName("other").setCreateBranch(true).call();
		remoteGit.commit().setMessage("commit2").call();
		remoteGit.tag().setName("foo").call();
	}

	@Test
	public void testFetchDefault() throws Exception {
		String[] result = execute("git fetch test refs/heads/master:refs/remotes/origin/master");
		assertEquals(" * [new branch]      master     -> origin/master",
				result[1]);
		assertEquals(" * [new tag]         tag        -> tag", result[2]);
	}

	@Test
	public void testFetchForceUpdate() throws Exception {
		String[] result = execute(
				"git fetch test refs/heads/master:refs/remotes/origin/master");
		assertEquals(" * [new branch]      master     -> origin/master",
				result[1]);
		assertEquals(" * [new tag]         tag        -> tag", result[2]);
		remoteGit.commit().setAmend(true).setMessage("amended").call();
		result = execute(
				"git fetch -f test refs/heads/master:refs/remotes/origin/master");
		assertEquals("", result[0]);
	}

	@Test
	public void testFetchNoTags() throws Exception {
		String[] result = execute("git fetch --no-tags test refs/heads/master:refs/remotes/origin/master");
		assertEquals(" * [new branch]      master     -> origin/master",
				result[1]);
		assertEquals("", result[2]);
	}

	@Test
	public void testFetchAllTags() throws Exception {
		String[] result = execute("git fetch --tags test refs/heads/master:refs/remotes/origin/master");
		assertEquals(" * [new branch]      master     -> origin/master",
				result[1]);
		assertEquals(" * [new tag]         foo        -> foo", result[2]);
		assertEquals(" * [new tag]         tag        -> tag", result[3]);
	}
}
