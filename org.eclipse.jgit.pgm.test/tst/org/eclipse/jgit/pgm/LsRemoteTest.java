/*
 * Copyright (C) 2014, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Before;
import org.junit.Test;

public class LsRemoteTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
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
		final List<String> result = CLIGitCommand.execute(
				"git ls-remote " + shellQuote(db.getDirectory()), db);
		assertArrayEquals(new String[] {
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	HEAD",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/master",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/test",
				"efc02078d83a5226986ae917323acec7e1e8b7cb	refs/tags/tag1",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag1^{}",
				"4e4b837e0fd4ba83c003678b03592dc1509a4115	refs/tags/tag2",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag2^{}",
				"489384bf8ace47522fe32093d2ceb85b65a6cbb1	refs/tags/tag3",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag3^{}",
				"" }, result.toArray());
	}

	@Test
	public void testLsRemoteHeads() throws Exception {
		final List<String> result = CLIGitCommand.execute(
				"git ls-remote --heads "
						+ shellQuote(db.getDirectory()),
				db);
		assertArrayEquals(new String[] {
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/master",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/test",
				"" }, result.toArray());
	}

	@Test
	public void testLsRemoteTags() throws Exception {
		final List<String> result = CLIGitCommand.execute(
				"git ls-remote --tags " + shellQuote(db.getDirectory()), db);
		assertArrayEquals(new String[] {
				"efc02078d83a5226986ae917323acec7e1e8b7cb	refs/tags/tag1",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag1^{}",
				"4e4b837e0fd4ba83c003678b03592dc1509a4115	refs/tags/tag2",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag2^{}",
				"489384bf8ace47522fe32093d2ceb85b65a6cbb1	refs/tags/tag3",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag3^{}",
				"" }, result.toArray());
	}

	@Test
	public void testLsRemoteHeadsTags() throws Exception {
		final List<String> result = CLIGitCommand.execute(
				"git ls-remote --heads --tags " + shellQuote(db.getDirectory()),
				db);
		assertArrayEquals(new String[] {
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/master",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/heads/test",
				"efc02078d83a5226986ae917323acec7e1e8b7cb	refs/tags/tag1",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag1^{}",
				"4e4b837e0fd4ba83c003678b03592dc1509a4115	refs/tags/tag2",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag2^{}",
				"489384bf8ace47522fe32093d2ceb85b65a6cbb1	refs/tags/tag3",
				"d0b1ef2b3dea02bb2ca824445c04e6def012c32c	refs/tags/tag3^{}",
				"" }, result.toArray());
	}

}
