/*
 * Copyright (C) 2011, 2013 Chris Aniszczyk <caniszczyk@gmail.com> and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Test;

public class LsRemoteCommandTest extends RepositoryTestCase {

	private Git git;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// tags
		git.tag().setName("tag1").call();
		git.tag().setName("tag2").call();
		git.tag().setName("tag3").call();
	}

	@Test
	public void testLsRemote() throws Exception {
		File directory = createTempDirectory("testRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setCloneAllBranches(true);
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());


		LsRemoteCommand lsRemoteCommand = git2.lsRemote();
		Collection<Ref> refs = lsRemoteCommand.call();
		assertNotNull(refs);
		assertEquals(6, refs.size());
	}

	@Test
	public void testLsRemoteWithTags() throws Exception {
		File directory = createTempDirectory("testRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setCloneAllBranches(true);
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());

		LsRemoteCommand lsRemoteCommand = git2.lsRemote();
		lsRemoteCommand.setTags(true);
		Collection<Ref> refs = lsRemoteCommand.call();
		assertNotNull(refs);
		assertEquals(3, refs.size());
	}

	@Test
	public void testLsRemoteWithHeads() throws Exception {
		File directory = createTempDirectory("testRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setCloneAllBranches(true);
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());

		LsRemoteCommand lsRemoteCommand = git2.lsRemote();
		lsRemoteCommand.setHeads(true);
		Collection<Ref> refs = lsRemoteCommand.call();
		assertNotNull(refs);
		assertEquals(2, refs.size());
	}

	@Test
	public void testLsRemoteWithoutLocalRepository() throws Exception {
		String uri = fileUri();
		Collection<Ref> refs = Git.lsRemoteRepository().setRemote(uri).setHeads(true).call();
		assertNotNull(refs);
		assertEquals(2, refs.size());
	}

	private String fileUri() {
		return "file://" + git.getRepository().getWorkTree().getAbsolutePath();
	}

}
