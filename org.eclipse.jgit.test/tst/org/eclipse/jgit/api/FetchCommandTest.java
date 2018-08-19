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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
	public void setupRemoteRepository() throws Exception {
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
	public void testFetch() throws Exception {

		// create some refs via commits and tag
		RevCommit commit = remoteGit.commit().setMessage("initial commit").call();
		Ref tagRef = remoteGit.tag().setName("tag").call();

		git.fetch().setRemote("test")
				.setRefSpecs("refs/heads/master:refs/heads/x").call();

		assertEquals(commit.getId(),
				db.resolve(commit.getId().getName() + "^{commit}"));
		assertEquals(tagRef.getObjectId(),
				db.resolve(tagRef.getObjectId().getName()));
	}

	@Test
	public void fetchAddsBranches() throws Exception {
		final String branch1 = "b1";
		final String branch2 = "b2";
		final String remoteBranch1 = "test/" + branch1;
		final String remoteBranch2 = "test/" + branch2;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef1 = remoteGit.branchCreate().setName(branch1).call();
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef2 = remoteGit.branchCreate().setName(branch2).call();

		String spec = "refs/heads/*:refs/remotes/test/*";
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));
	}

	@Test
	public void fetchDoesntDeleteBranches() throws Exception {
		final String branch1 = "b1";
		final String branch2 = "b2";
		final String remoteBranch1 = "test/" + branch1;
		final String remoteBranch2 = "test/" + branch2;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef1 = remoteGit.branchCreate().setName(branch1).call();
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef2 = remoteGit.branchCreate().setName(branch2).call();

		String spec = "refs/heads/*:refs/remotes/test/*";
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));

		remoteGit.branchDelete().setBranchNames(branch1).call();
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));
	}

	@Test
	public void fetchUpdatesBranches() throws Exception {
		final String branch1 = "b1";
		final String branch2 = "b2";
		final String remoteBranch1 = "test/" + branch1;
		final String remoteBranch2 = "test/" + branch2;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef1 = remoteGit.branchCreate().setName(branch1).call();
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef2 = remoteGit.branchCreate().setName(branch2).call();

		String spec = "refs/heads/*:refs/remotes/test/*";
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));

		remoteGit.commit().setMessage("commit").call();
		branchRef2 = remoteGit.branchCreate().setName(branch2).setForce(true).call();
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));
	}

	@Test
	public void fetchPrunesBranches() throws Exception {
		final String branch1 = "b1";
		final String branch2 = "b2";
		final String remoteBranch1 = "test/" + branch1;
		final String remoteBranch2 = "test/" + branch2;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef1 = remoteGit.branchCreate().setName(branch1).call();
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef2 = remoteGit.branchCreate().setName(branch2).call();

		String spec = "refs/heads/*:refs/remotes/test/*";
		git.fetch().setRemote("test").setRefSpecs(spec).call();
		assertEquals(branchRef1.getObjectId(), db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));

		remoteGit.branchDelete().setBranchNames(branch1).call();
		git.fetch().setRemote("test").setRefSpecs(spec)
				.setRemoveDeletedRefs(true).call();
		assertNull(db.resolve(remoteBranch1));
		assertEquals(branchRef2.getObjectId(), db.resolve(remoteBranch2));
	}

	@Test
	public void fetchShouldAutoFollowTag() throws Exception {
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef = remoteGit.tag().setName("foo").call();

		git.fetch().setRemote("test")
				.setRefSpecs("refs/heads/*:refs/remotes/origin/*")
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();

		assertEquals(tagRef.getObjectId(), db.resolve("foo"));
	}

	@Test
	public void fetchShouldAutoFollowTagForFetchedObjects() throws Exception {
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef = remoteGit.tag().setName("foo").call();
		remoteGit.commit().setMessage("commit2").call();
		git.fetch().setRemote("test")
				.setRefSpecs("refs/heads/*:refs/remotes/origin/*")
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();
		assertEquals(tagRef.getObjectId(), db.resolve("foo"));
	}

	@Test
	public void fetchShouldNotFetchTagsFromOtherBranches() throws Exception {
		remoteGit.commit().setMessage("commit").call();
		remoteGit.checkout().setName("other").setCreateBranch(true).call();
		remoteGit.commit().setMessage("commit2").call();
		remoteGit.tag().setName("foo").call();
		git.fetch().setRemote("test")
				.setRefSpecs("refs/heads/master:refs/remotes/origin/master")
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();
		assertNull(db.resolve("foo"));
	}

	@Test
	public void fetchWithUpdatedTagShouldNotTryToUpdateLocal() throws Exception {
		final String tagName = "foo";
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef = remoteGit.tag().setName(tagName).call();
		ObjectId originalId = tagRef.getObjectId();

		String spec = "refs/heads/*:refs/remotes/origin/*";
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

		String spec = "refs/heads/*:refs/remotes/origin/*";
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

	@Test
	public void fetchAddRefsWithDuplicateRefspec() throws Exception {
		final String branchName = "branch";
		final String remoteBranchName = "test/" + branchName;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef = remoteGit.branchCreate().setName(branchName).call();

		final String spec1 = "+refs/heads/*:refs/remotes/test/*";
		final String spec2 = "refs/heads/*:refs/remotes/test/*";
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addFetchRefSpec(new RefSpec(spec1));
		remoteConfig.addFetchRefSpec(new RefSpec(spec2));
		remoteConfig.update(config);

		git.fetch().setRemote("test").setRefSpecs(spec1).call();
		assertEquals(branchRef.getObjectId(), db.resolve(remoteBranchName));
	}

	@Test
	public void fetchPruneRefsWithDuplicateRefspec()
			throws Exception {
		final String branchName = "branch";
		final String remoteBranchName = "test/" + branchName;
		remoteGit.commit().setMessage("commit").call();
		Ref branchRef = remoteGit.branchCreate().setName(branchName).call();

		final String spec1 = "+refs/heads/*:refs/remotes/test/*";
		final String spec2 = "refs/heads/*:refs/remotes/test/*";
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addFetchRefSpec(new RefSpec(spec1));
		remoteConfig.addFetchRefSpec(new RefSpec(spec2));
		remoteConfig.update(config);

		git.fetch().setRemote("test").setRefSpecs(spec1).call();
		assertEquals(branchRef.getObjectId(), db.resolve(remoteBranchName));

		remoteGit.branchDelete().setBranchNames(branchName).call();
		git.fetch().setRemote("test").setRefSpecs(spec1)
				.setRemoveDeletedRefs(true).call();
		assertNull(db.resolve(remoteBranchName));
	}

	@Test
	public void fetchUpdateRefsWithDuplicateRefspec() throws Exception {
		final String tagName = "foo";
		remoteGit.commit().setMessage("commit").call();
		Ref tagRef1 = remoteGit.tag().setName(tagName).call();
		List<RefSpec> refSpecs = new ArrayList<>();
		refSpecs.add(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
		refSpecs.add(new RefSpec("+refs/tags/*:refs/tags/*"));
		// Updating tags via the RefSpecs and setting TagOpt.FETCH_TAGS (or
		// AUTO_FOLLOW) will result internally in *two* updates for the same
		// ref.
		git.fetch().setRemote("test").setRefSpecs(refSpecs)
				.setTagOpt(TagOpt.AUTO_FOLLOW).call();
		assertEquals(tagRef1.getObjectId(), db.resolve(tagName));

		remoteGit.commit().setMessage("commit 2").call();
		Ref tagRef2 = remoteGit.tag().setName(tagName).setForceUpdate(true)
				.call();
		FetchResult result = git.fetch().setRemote("test").setRefSpecs(refSpecs)
				.setTagOpt(TagOpt.FETCH_TAGS).call();
		assertEquals(2, result.getTrackingRefUpdates().size());
		TrackingRefUpdate update = result
				.getTrackingRefUpdate(Constants.R_TAGS + tagName);
		assertEquals(RefUpdate.Result.FORCED, update.getResult());
		assertEquals(tagRef2.getObjectId(), db.resolve(tagName));
	}
}
