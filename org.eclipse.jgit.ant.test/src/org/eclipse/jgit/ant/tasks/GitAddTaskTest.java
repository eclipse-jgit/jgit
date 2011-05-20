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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DirSet;
import org.apache.tools.ant.types.FileSet;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class GitAddTaskTest extends RepositoryTestCase {
	private GitAddTask task;
	private Project project;

	@Before
	public void before() {
		project = new Project();
		enableLogging();
		task = new GitAddTask();
		task.setProject(project);
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnNoGitRepo() throws Exception {
		task.execute();
	}

	@Test(expected = BuildException.class)
	public void shouldRaiseErrorOnBadGitRepo() throws Exception {
		task.setSrc(createTempFile());
		task.execute();
	}

	@Test
	public void shouldAddFilesInAGivenFilesetToTheIndex() throws Exception {
		task.setSrc(db.getWorkTree());

		createTmpFileInRepo("a.txt");
		createTmpFileInRepo("foo/a.txt");

		FileSet fileSet;
		fileSet = new FileSet();
		fileSet.setDir(db.getWorkTree());
		fileSet.setIncludes("a.txt");

		task.addFileset(fileSet);
		task.execute();

		assertEquals("[a.txt, mode:100644, content:content]",
				indexState(CONTENT));

		fileSet = new FileSet();
		fileSet.setDir(db.getWorkTree());
		fileSet.setIncludes("**/a.txt");

		task.addFileset(fileSet);
		task.execute();

		assertEquals(
				"[a.txt, mode:100644, content:content][foo/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	@Test
	public void shouldAddRootToIndex() throws Exception {
		task.setSrc(db.getWorkTree());

		createTmpFileInRepo("a.txt");
		createTmpFileInRepo("foo/a.txt");

		DirSet dirSet = new DirSet();
		dirSet.setDir(db.getWorkTree());

		task.addDirset(dirSet);
		task.execute();

		assertEquals(
				"[a.txt, mode:100644, content:content][foo/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	private void createTmpFileInRepo(String path) throws IOException {
		File file = new File(db.getWorkTree(), path);
		file.getParentFile().mkdirs();

		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
	}

	private void enableLogging() {
		DefaultLogger logger = new DefaultLogger();
		logger.setOutputPrintStream(System.out);
		logger.setErrorPrintStream(System.err);
		logger.setMessageOutputLevel(Project.MSG_INFO);
		project.addBuildListener(logger);
	}
}
