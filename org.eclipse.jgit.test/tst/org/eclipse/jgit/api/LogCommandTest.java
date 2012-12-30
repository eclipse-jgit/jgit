/*
 * Copyright (C) 2011, GitHub Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class LogCommandTest extends RepositoryTestCase {

	@Test
	public void logAllCommits() throws Exception {
		List<RevCommit> commits = new ArrayList<RevCommit>();
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("initial commit").call());

		git.branchCreate().setName("branch1").call();
		Ref checkedOut = git.checkout().setName("branch1").call();
		assertEquals("refs/heads/branch1", checkedOut.getName());
		writeTrashFile("Test1.txt", "Hello world!");
		git.add().addFilepattern("Test1.txt").call();
		commits.add(git.commit().setMessage("branch1 commit").call());

		checkedOut = git.checkout().setName("master").call();
		assertEquals("refs/heads/master", checkedOut.getName());
		writeTrashFile("Test2.txt", "Hello world!!");
		git.add().addFilepattern("Test2.txt").call();
		commits.add(git.commit().setMessage("branch1 commit").call());

		Iterator<RevCommit> log = git.log().all().call().iterator();
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertFalse(log.hasNext());
	}

	private List<RevCommit> createCommits(Git git) throws Exception {
		List<RevCommit> commits = new ArrayList<RevCommit>();
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("commit#1").call());
		writeTrashFile("Test.txt", "Hello world!");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("commit#2").call());
		writeTrashFile("Test1.txt", "Hello world!!");
		git.add().addFilepattern("Test1.txt").call();
		commits.add(git.commit().setMessage("commit#3").call());
		return commits;
	}

	@Test
	public void logAllCommitsWithMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setMaxCount(2).call()
				.iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#3", commit.getShortMessage());
		assertTrue(log.hasNext());
		commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logPathWithMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().addPath("Test.txt").setMaxCount(1)
				.call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logPathWithSkip() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().addPath("Test.txt").setSkip(1)
				.call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#1", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logAllCommitsWithSkip() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setSkip(1).call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertTrue(log.hasNext());
		commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#1", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logAllCommitsWithSkipAndMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setSkip(1).setMaxCount(1).call()
				.iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}
}