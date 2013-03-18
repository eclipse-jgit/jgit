/*
 * Copyright (C) 2011, Ketan Padegaonkar <KetanPadegaonkar@gmail.com>
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
package org.eclipse.jgit.ant.tasks;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
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
		project = new Project();
		project.init();
		enableLogging();
		project.addTaskDefinition("git-clone", GitCloneTask.class);
		task = (GitCloneTask) project.createTask("git-clone");
		dest = createTempFile();
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
		FileRepository repo = createBareRepository();
		File directory = repo.getDirectory();
		task.setUri("file://" + directory);
		task.execute();

		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(dest, ".git"), FS.DETECTED));
	}

	@Test
	public void shouldCreateABareCloneOfAValidGitRepository() throws Exception {
		FileRepository repo = createBareRepository();
		File directory = repo.getDirectory();
		task.setUri("file://" + directory);
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
