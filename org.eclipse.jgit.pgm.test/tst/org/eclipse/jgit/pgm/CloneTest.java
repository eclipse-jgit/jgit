/*
 * Copyright (C) 2014 Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class CloneTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testClone() throws Exception {
		createInitialCommit();

		File gitDir = db.getDirectory();
		String sourceURI = gitDir.toURI().toString();
		File target = createTempDirectory("target");
		String cmd = "git clone " + sourceURI + " "
				+ shellQuote(target.getPath());
		String[] result = execute(cmd);
		assertArrayEquals(new String[] {
				"Cloning into '" + target.getPath() + "'...",
						"", "" }, result);

		Git git2 = Git.open(target);
		List<Ref> branches = git2.branchList().call();
		assertEquals("expected 1 branch", 1, branches.size());
	}

	private RevCommit createInitialCommit() throws Exception {
		JGitTestUtil.writeTrashFile(db, "hello.txt", "world");
		git.add().addFilepattern("hello.txt").call();
		return git.commit().setMessage("Initial commit").call();
	}

	@Test
	public void testCloneEmpty() throws Exception {
		File gitDir = db.getDirectory();
		String sourceURI = gitDir.toURI().toString();
		File target = createTempDirectory("target");
		String cmd = "git clone " + sourceURI + " "
				+ shellQuote(target.getPath());
		String[] result = execute(cmd);
		assertArrayEquals(new String[] {
				"Cloning into '" + target.getPath() + "'...",
				"warning: You appear to have cloned an empty repository.", "",
				"" }, result);

		Git git2 = Git.open(target);
		List<Ref> branches = git2.branchList().call();
		assertEquals("expected 0 branch", 0, branches.size());
	}

	@Test
	public void testCloneIntoCurrentDir() throws Exception {
		createInitialCommit();
		File target = createTempDirectory("target");

		MockSystemReader sr = (MockSystemReader) SystemReader.getInstance();
		sr.setProperty(Constants.OS_USER_DIR, target.getAbsolutePath());

		File gitDir = db.getDirectory();
		String sourceURI = gitDir.toURI().toString();
		String name = new URIish(sourceURI).getHumanishName();
		String cmd = "git clone " + sourceURI;
		String[] result = execute(cmd);
		assertArrayEquals(new String[] {
				"Cloning into '" + new File(target, name).getName() + "'...",
				"", "" }, result);
		Git git2 = Git.open(new File(target, name));
		List<Ref> branches = git2.branchList().call();
		assertEquals("expected 1 branch", 1, branches.size());
	}

	@Test
	public void testCloneBare() throws Exception {
		createInitialCommit();

		File gitDir = db.getDirectory();
		String sourcePath = gitDir.getAbsolutePath();
		String targetPath = (new File(sourcePath)).getParentFile()
				.getParentFile().getAbsolutePath()
				+ File.separator + "target.git";
		String cmd = "git clone --bare " + shellQuote(sourcePath) + " "
				+ shellQuote(targetPath);
		String[] result = execute(cmd);
		assertArrayEquals(new String[] {
				"Cloning into '" + targetPath + "'...", "", "" }, result);
		Git git2 = Git.open(new File(targetPath));
		List<Ref> branches = git2.branchList().call();
		assertEquals("expected 1 branch", 1, branches.size());
		assertTrue("expected bare repository", git2.getRepository().isBare());
	}

	@Test
	public void testCloneMirror() throws Exception {
		ObjectId head = createInitialCommit();
		// create a non-standard ref
		RefUpdate ru = db.updateRef("refs/meta/foo/bar");
		ru.setNewObjectId(head);
		ru.update();

		File gitDir = db.getDirectory();
		String sourcePath = gitDir.getAbsolutePath();
		String targetPath = (new File(sourcePath)).getParentFile()
				.getParentFile().getAbsolutePath() + File.separator
				+ "target.git";
		String cmd = "git clone --mirror " + shellQuote(sourcePath) + " "
				+ shellQuote(targetPath);
		String[] result = execute(cmd);
		assertArrayEquals(
				new String[] { "Cloning into '" + targetPath + "'...", "", "" },
				result);
		Git git2 = Git.open(new File(targetPath));
		List<Ref> branches = git2.branchList().call();
		assertEquals("expected 1 branch", 1, branches.size());
		assertTrue("expected bare repository", git2.getRepository().isBare());
		StoredConfig config = git2.getRepository().getConfig();
		RemoteConfig rc = new RemoteConfig(config, "origin");
		assertTrue("expected mirror configuration", rc.isMirror());
		RefSpec fetchRefSpec = rc.getFetchRefSpecs().get(0);
		assertTrue("exected force udpate", fetchRefSpec.isForceUpdate());
		assertEquals("refs/*", fetchRefSpec.getSource());
		assertEquals("refs/*", fetchRefSpec.getDestination());
		assertNotNull(git2.getRepository().exactRef("refs/meta/foo/bar"));
	}
}
