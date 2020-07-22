/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class RepoCommandSymlinkTest extends RepositoryTestCase {
	@Before
	public void beforeMethod() {
		// If this assumption fails the tests are skipped. When running on a
		// filesystem not supporting symlinks I don't want this tests
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
	}

	private Repository defaultRepo;

	private String rootUri;
	private String defaultUri;

	@Override
	public void setUp() throws Exception {
		super.setUp();

		defaultRepo = createWorkRepository();
		try (Git git = new Git(defaultRepo)) {
			JGitTestUtil.writeTrashFile(defaultRepo, "hello.txt", "hello world");
			git.add().addFilepattern("hello.txt").call();
			git.commit().setMessage("Initial commit").call();
			addRepoToClose(defaultRepo);
		}

		defaultUri = defaultRepo.getDirectory().toURI().toString();
		int root = defaultUri.lastIndexOf("/",
				defaultUri.lastIndexOf("/.git") - 1)
				+ 1;
		rootUri = defaultUri.substring(0, root)
				+ "manifest";
		defaultUri = defaultUri.substring(root);
	}

	@Test
	public void testLinkFileBare() throws Exception {
		try (
				Repository remoteRepo = createBareRepository();
				Repository tempRepo = createWorkRepository()) {
			StringBuilder xmlContent = new StringBuilder();
			xmlContent
					.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
					.append("<manifest>")
					.append("<remote name=\"remote1\" fetch=\".\" />")
					.append("<default revision=\"master\" remote=\"remote1\" />")
					.append("<project path=\"foo\" name=\"").append(defaultUri)
					.append("\" revision=\"master\" >")
					.append("<linkfile src=\"hello.txt\" dest=\"LinkedHello\" />")
					.append("<linkfile src=\"hello.txt\" dest=\"foo/LinkedHello\" />")
					.append("<linkfile src=\"hello.txt\" dest=\"subdir/LinkedHello\" />")
					.append("</project>")
					.append("<project path=\"bar/baz\" name=\"")
					.append(defaultUri).append("\" revision=\"master\" >")
					.append("<linkfile src=\"hello.txt\" dest=\"bar/foo/LinkedHello\" />")
					.append("</project>").append("</manifest>");
			JGitTestUtil.writeTrashFile(tempRepo, "manifest.xml",
					xmlContent.toString());
			RepoCommand command = new RepoCommand(remoteRepo);
			command.setPath(
					tempRepo.getWorkTree().getAbsolutePath() + "/manifest.xml")
					.setURI(rootUri).call();
			// Clone it
			File directory = createTempDirectory("testCopyFileBare");
			try (Repository localRepo = Git.cloneRepository()
					.setDirectory(directory)
					.setURI(remoteRepo.getDirectory().toURI().toString()).call()
					.getRepository()) {

				// The LinkedHello symlink should exist.
				File linkedhello = new File(localRepo.getWorkTree(),
						"LinkedHello");
				assertTrue("The LinkedHello file should exist",
						localRepo.getFS().exists(linkedhello));
				assertTrue("The LinkedHello file should be a symlink",
						localRepo.getFS().isSymLink(linkedhello));
				assertEquals("foo/hello.txt",
						localRepo.getFS().readSymLink(linkedhello));

				// The foo/LinkedHello file should be skipped.
				File linkedfoohello = new File(localRepo.getWorkTree(),
						"foo/LinkedHello");
				assertFalse("The foo/LinkedHello file should be skipped",
						localRepo.getFS().exists(linkedfoohello));

				// The subdir/LinkedHello file should use a relative ../
				File linkedsubdirhello = new File(localRepo.getWorkTree(),
						"subdir/LinkedHello");
				assertTrue("The subdir/LinkedHello file should exist",
						localRepo.getFS().exists(linkedsubdirhello));
				assertTrue("The subdir/LinkedHello file should be a symlink",
						localRepo.getFS().isSymLink(linkedsubdirhello));
				assertEquals("../foo/hello.txt",
						localRepo.getFS().readSymLink(linkedsubdirhello));

				// The bar/foo/LinkedHello file should use a single relative ../
				File linkedbarfoohello = new File(localRepo.getWorkTree(),
						"bar/foo/LinkedHello");
				assertTrue("The bar/foo/LinkedHello file should exist",
						localRepo.getFS().exists(linkedbarfoohello));
				assertTrue("The bar/foo/LinkedHello file should be a symlink",
						localRepo.getFS().isSymLink(linkedbarfoohello));
				assertEquals("../baz/hello.txt",
						localRepo.getFS().readSymLink(linkedbarfoohello));
			}
		}
	}
}
