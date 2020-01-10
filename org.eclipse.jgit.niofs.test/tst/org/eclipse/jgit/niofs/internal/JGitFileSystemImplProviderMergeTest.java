/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.niofs.fs.options.MergeCopyOption;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.GetTreeFromRef;
import org.eclipse.jgit.niofs.internal.op.commands.ListDiffs;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.junit.Test;

public class JGitFileSystemImplProviderMergeTest extends BaseTest {

	@Test
	public void testMergeSuccessful() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path userBranch = provider.getPath(URI.create("git://user_branch@merge-test-repo"));

		provider.copy(master, userBranch);

		{
			final Path path2 = provider.getPath(URI.create("git://user_branch@merge-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		provider.copy(userBranch, master, new MergeCopyOption());

		final Git gitRepo = ((JGitFileSystem) master.getFileSystem()).getGit();

		final List<DiffEntry> result = new ListDiffs(gitRepo, new GetTreeFromRef(gitRepo, "master").execute(),
				new GetTreeFromRef(gitRepo, "user_branch").execute()).execute();

		assertThat(result.size()).isEqualTo(0);
	}

	@Test(expected = GitException.class)
	public void testMergeConflicts() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path userBranch = provider.getPath(URI.create("git://user_branch@merge-test-repo"));

		provider.copy(master, userBranch);

		{
			final Path path2 = provider.getPath(URI.create("git://user_branch@merge-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		{
			final Path path = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content changed".getBytes());
			outStream.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}
		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my very cool content".getBytes());
			outStream.close();
		}

		provider.copy(userBranch, master, new MergeCopyOption());
	}

	@Test
	public void testMergeBinarySuccessful() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(loadImage("images/drools.png"));
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path userBranch = provider.getPath(URI.create("git://user_branch@merge-test-repo"));

		provider.copy(master, userBranch);

		{
			final Path path2 = provider.getPath(URI.create("git://user_branch@merge-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write(loadImage("images/jbpm.png"));
			outStream2.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write(loadImage("images/opta.png"));
			outStream3.close();
		}

		provider.copy(userBranch, master, new MergeCopyOption());

		final Git gitRepo = ((JGitFileSystem) master.getFileSystem()).getGit();
		final List<DiffEntry> result = new ListDiffs(gitRepo, new GetTreeFromRef(gitRepo, "master").execute(),
				new GetTreeFromRef(gitRepo, "user_branch").execute()).execute();

		assertThat(result.size()).isEqualTo(0);
	}

	@Test(expected = GitException.class)
	public void testBinaryMergeConflicts() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(loadImage("images/drools.png"));
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path userBranch = provider.getPath(URI.create("git://user_branch@merge-test-repo"));

		provider.copy(master, userBranch);

		{
			final Path path2 = provider.getPath(URI.create("git://user_branch@merge-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream = provider.newOutputStream(path2);
			outStream.write(loadImage("images/jbpm.png"));
			outStream.close();
		}
		{
			final Path path = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(loadImage("images/jbpm.png"));
			outStream.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@merge-test-repo/myfile3.txt"));

			final OutputStream outStream = provider.newOutputStream(path3);
			outStream.write(loadImage("images/opta.png"));
			outStream.close();
		}
		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(loadImage(""));
			outStream.close();
		}

		provider.copy(userBranch, master, new MergeCopyOption());
	}

	@Test(expected = GitException.class)
	public void testTryToMergeNonexistentBranch() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path develop = provider.getPath(URI.create("git://develop@merge-test-repo"));

		provider.copy(develop, master, new MergeCopyOption());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMissingParemeter() throws IOException {
		final URI newRepo = URI.create("git://merge-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@merge-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		final Path master = provider.getPath(URI.create("git://master@merge-test-repo"));
		final Path develop = provider.getPath(URI.create("git://develop@merge-test-repo"));

		provider.copy(develop, null, new MergeCopyOption());
	}
}
