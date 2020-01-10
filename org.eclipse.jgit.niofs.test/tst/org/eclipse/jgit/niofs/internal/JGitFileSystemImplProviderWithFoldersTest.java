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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.niofs.fs.FileSystemState;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class JGitFileSystemImplProviderWithFoldersTest extends BaseTest {

	@Test
	public void testNewFileSystemWithSubfolder() throws IOException {
		final URI newRepo = URI.create("git://test/sub-repo-name");
		final FileSystem fs = provider.newFileSystem(newRepo, EMPTY_ENV);

		assertThat(fs).isNotNull();

		final DirectoryStream<Path> stream = provider.newDirectoryStream(provider.getPath(newRepo), null);
		assertThat(stream).isEmpty();
	}

	@Test
	public void testCreateFileIntoRepositoryWithFolder() throws IOException, GitAPIException {

		final Map<String, ?> env = new HashMap<String, Object>() {
			{
				put("init", Boolean.TRUE);
			}
		};

		String oldPath = "git://test/old";
		final URI oldUri = URI.create(oldPath);
		final JGitFileSystem fs = (JGitFileSystem) provider.newFileSystem(oldUri, env);

		final Path path = provider.getPath(URI.create("git://master@test/old/some/path/myfile.txt"));
		provider.setAttribute(path, FileSystemState.FILE_SYSTEM_STATE_ATTR, FileSystemState.BATCH);
		final OutputStream outStream = provider.newOutputStream(path);
		assertThat(outStream).isNotNull();
		outStream.write(("my cool content").getBytes());
		outStream.close();

		assertThat(new File(provider.getGitRepoContainerDir(), "test/old" + ".git")).exists();

		int commitsCount = 0;
		for (RevCommit com : ((GitImpl) fs.getGit())._log().all().call()) {
			commitsCount++;
		}
	}

	@Test
	public void testExtractPathWithAuthority() throws IOException {

		provider.newFileSystem(URI.create("git://test/repo"), new HashMap<String, Object>() {
			{
				put("init", Boolean.TRUE);
			}
		});

		String path = "git://master@test/repo/readme.md";
		final URI uri = URI.create(path);
		final String extracted = provider.extractPath(uri);
		assertThat(extracted).isEqualTo("/readme.md");
	}

	@Test
	public void testComplexExtractPath() throws IOException {

		final URI newRepo = URI.create("git://test/repo");
		final FileSystem fs = provider.newFileSystem(newRepo, EMPTY_ENV);

		String path = "git://origin/master@test/repo/readme.md";
		final URI uri = URI.create(path);
		final String extracted = provider.extractPath(uri);
		assertThat(extracted).isEqualTo("/readme.md");
	}

	@Test
	public void testExtractComplexRepoName() throws IOException {
		provider.newFileSystem(URI.create("git://test/repo"), new HashMap<String, Object>() {
			{
				put("init", Boolean.TRUE);
			}
		});

		String path = "git://origin/master@test/repo/readme.md";
		final URI uri = URI.create(path);
		final String extracted = provider.extractFSNameWithPath(uri);
		assertThat(extracted).isEqualTo("test/repo/readme.md");
	}

	@Test
	public void testExtractSimpleRepoName() {
		String path = "git://master@test/repo/readme.md";
		final URI uri = URI.create(path);
		final String extracted = provider.extractFSNameWithPath(uri);
		assertThat(extracted).isEqualTo("test/repo/readme.md");
	}

	@Test
	public void testExtractVerySimpleRepoName() {
		String path = "git://test/repo/readme.md";
		final URI uri = URI.create(path);
		final String extracted = provider.extractFSNameWithPath(uri);
		assertThat(extracted).isEqualTo("test/repo/readme.md");
	}
}
