/*
 * Copyright (C) 2010, 2013 Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class FetchCommandTest extends RepositoryTestCase {

	private Git git;
	private Git remoteGit;

	@Before
	public void setupRemoteRepository() throws IOException, URISyntaxException {
		git = new Git(db);

		// create other repository
		Repository remoteRepository = createWorkRepository();
		remoteGit = new Git(remoteRepository);

		// setup the first repository to fetch from the second repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(remoteRepository.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();
	}

	@Test
	public void testFetch() throws JGitInternalException, IOException,
			GitAPIException {

		// create some refs via commits and tag
		RevCommit commit = remoteGit.commit().setMessage("initial commit").call();
		Ref tagRef = remoteGit.tag().setName("tag").call();

		RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
		git.fetch().setRemote("test").setRefSpecs(spec)
				.call();

		assertEquals(commit.getId(),
				db.resolve(commit.getId().getName() + "^{commit}"));
		assertEquals(tagRef.getObjectId(),
				db.resolve(tagRef.getObjectId().getName()));
	}

	@Test
	public void fetchShouldAutoFollowTag() throws Exception {
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef = remoteGit.tag().setName("foo").call();

		RefSpec spec = new RefSpec("refs/heads/*:refs/remotes/origin/*");
		git.fetch().setRemote("test").setRefSpecs(spec)
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();

		assertEquals(tagRef.getObjectId(), db.resolve("foo"));
	}

	@Test
	public void fetchWithUpdatedTagShouldNotTryToUpdateLocal() throws Exception {
		final String tagName = "foo";
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef = remoteGit.tag().setName(tagName).call();
		ObjectId originalId = tagRef.getObjectId();

		RefSpec spec = new RefSpec("refs/heads/*:refs/remotes/origin/*");
		git.fetch().setRemote("test").setRefSpecs(spec)
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();
		assertEquals(originalId, db.resolve(tagName));

		remoteGit.commit().setMessage("commit 2").call();
		remoteGit.tag().setName(tagName).setForceUpdate(true).call();

		FetchResult result = git.fetch().setRemote("test").setRefSpecs(spec)
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();

		Collection<TrackingRefUpdate> refUpdates = result
				.getTrackingRefUpdates();
		assertEquals(1, refUpdates.size());
		TrackingRefUpdate update = refUpdates.iterator().next();
		assertEquals("refs/heads/master", update.getRemoteName());

		assertEquals(originalId, db.resolve(tagName));
	}

	@Test
	public void fetchWithExplicitTagsShouldUpdateLocal() throws Exception {
		final String tagName = "foo";
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef1 = remoteGit.tag().setName(tagName).call();

		RefSpec spec = new RefSpec("refs/heads/*:refs/remotes/origin/*");
		git.fetch().setRemote("test").setRefSpecs(spec)
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();
		assertEquals(tagRef1.getObjectId(), db.resolve(tagName));

		remoteGit.commit().setMessage("commit 2").call();
		Ref tagRef2 = remoteGit.tag().setName(tagName).setForceUpdate(true)
				.call();

		FetchResult result = git.fetch().setRemote("test").setRefSpecs(spec)
				.setTagOpt(TagOpt.FETCH_TAGS).call();
		TrackingRefUpdate update = result.getTrackingRefUpdate(Constants.R_TAGS
				+ tagName);
		assertEquals(RefUpdate.Result.FORCED, update.getResult());
		assertEquals(tagRef2.getObjectId(), db.resolve(tagName));
	}
}
