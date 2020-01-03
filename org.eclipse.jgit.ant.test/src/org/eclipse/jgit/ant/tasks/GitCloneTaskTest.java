/*
 * Copyright (C) 2011, Ketan Padegaonkar <KetanPadegaonkar@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ant.tasks;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class GitCloneTaskTest extends LocalDiskRepositoryTestCase {

	private GitCloneTask task;
	private Project project;
	private File dest;

	@Before
	public void before() throws IOException {
		dest = createTempFile();
		FS.getFileStoreAttributes(dest.toPath().getParent());
		project = new Project();
		project.init();
		enableLogging();
		project.addTaskDefinition("git-clone", GitCloneTask.class);
		task = (GitCloneTask) project.createTask("git-clone");
		task.setDest(dest);
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnNoUrl() throws Exception {
		task.execute();
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnEmptyUrl() throws Exception {
		task.setUri("");
		task.execute();
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnBadUrl() throws Exception {
		task.setUri("foo://bar");
		task.execute();
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnBadSourceURL() throws Exception {
		task.setUri("http://localhost:9090/does-not-exist.git");
		task.execute();
	}

	@Test
	public void shouldCloneAValidGitRepository() throws Exception {
		Repository repo = createBareRepository();
		File directory = repo.getDirectory();
		task.setUri("file://" + directory.getAbsolutePath());
		task.execute();

		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(dest, ".git"), FS.DETECTED));
	}

	@Test
	public void shouldCreateABareCloneOfAValidGitRepository() throws Exception {
		Repository repo = createBareRepository();
		File directory = repo.getDirectory();
		task.setUri("file://" + directory.getAbsolutePath());
		task.setBare(true);
		task.execute();

		assertTrue(RepositoryCache.FileKey.isGitRepository(dest, FS.DETECTED));
	}

	private void enableLogging() {
		DefaultLogger logger = new DefaultLogger();
		logger.setOutputPrintStream(System.out);
		logger.setErrorPrintStream(System.err);
		logger.setMessageOutputLevel(Project.MSG_INFO);
		project.addBuildListener(logger);
	}

}
