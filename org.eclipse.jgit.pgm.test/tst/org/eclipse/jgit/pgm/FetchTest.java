/*
 * Copyright (C) 2013, Chris Aniszczyk <zx@twitter.com> and others.
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
