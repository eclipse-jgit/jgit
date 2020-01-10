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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.eclipse.jgit.niofs.fs.attribute.VersionAttributes;
import org.eclipse.jgit.niofs.fs.options.CherryPickCopyOption;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class JGitFileSystemImplProviderCpMvTest extends AbstractTestInfra {

	@Test
	public void testCopyBranches() throws IOException {
		final URI newRepo = URI.create("git://copybranch-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@copybranch-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		{
			final Path path2 = provider
					.getPath(URI.create("git://user_branch@copybranch-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@copybranch-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		final Path source = provider.getPath(URI.create("git://user_branch@copybranch-test-repo"));
		final Path target = provider.getPath(URI.create("git://other_branch@copybranch-test-repo"));

		provider.copy(source, target);

		final DirectoryStream<Path> stream = provider
				.newDirectoryStream(provider.getPath(URI.create("git://other_branch@copybranch-test-repo/")), null);

		assertThat(stream).isNotNull().hasSize(2);

		assertThatThrownBy(() -> provider.copy(source, target)).isInstanceOf(FileAlreadyExistsException.class);

		final Path notExists = provider.getPath(URI.create("git://xxx_user_branch@copybranch-test-repo"));
		final Path notExists2 = provider.getPath(URI.create("git://xxx_other_branch@copybranch-test-repo"));

		assertThatThrownBy(() -> provider.copy(notExists, notExists2)).isInstanceOf(NoSuchFileException.class);
	}

	@Test
	public void testCopyFiles() throws IOException {
		final URI newRepo = URI.create("git://copyasset-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@copyasset-test-repo/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}
		final Path path2 = provider.getPath(URI.create("git://user_branch@copyasset-test-repo/other/path/myfile2.txt"));
		{
			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		final Path path3 = provider.getPath(URI.create("git://user_branch@copyasset-test-repo/myfile3.txt"));
		{
			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		final Path target = provider.getPath(URI.create("git://user_branch@copyasset-test-repo/myfile1.txt"));

		provider.copy(path, target);

		final DirectoryStream<Path> stream = provider
				.newDirectoryStream(provider.getPath(URI.create("git://user_branch@copyasset-test-repo/")), null);

		for (Path path1 : stream) {
			System.out.println("content: " + path1.toUri());
		}

		assertThat(stream).isNotNull().hasSize(3);
	}

	@Test
	public void testCopyDir() throws IOException {
		final URI newRepo = URI.create("git://copydir-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@copydir-test-repo/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}
		final Path path2 = provider.getPath(URI.create("git://user_branch@copydir-test-repo/path/myfile2.txt"));
		{
			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		final Path path3 = provider.getPath(URI.create("git://user_branch@copydir-test-repo/path/myfile3.txt"));
		{
			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).hasSize(3);
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/some/place/here/"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).hasSize(2);
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/soXme/place/here"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).hasSize(2);
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/other_here/"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).hasSize(1);
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/not_exists"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/xxxxxxxxother_here/"));

			assertThatThrownBy(() -> provider.copy(source, target)).isInstanceOf(NoSuchFileException.class);
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@copydir-test-repo/"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo/other_here/"));

			assertThatThrownBy(() -> provider.copy(source, target)).isInstanceOf(FileAlreadyExistsException.class);
		}
	}

	@Test
	public void testCopyFilesAcrossRepositories() throws IOException {
		final URI newRepo1 = URI.create("git://copyasset-test-repo1");
		provider.newFileSystem(newRepo1, EMPTY_ENV);

		final URI newRepo2 = URI.create("git://copyasset-test-repo2");
		provider.newFileSystem(newRepo2, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@copyasset-test-repo1/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		final Path target = provider.getPath(URI.create("git://master@copyasset-test-repo2/myfile1.txt"));

		provider.copy(path, target);

		final DirectoryStream<Path> stream = provider
				.newDirectoryStream(provider.getPath(URI.create("git://master@copyasset-test-repo2/")), null);

		for (Path path1 : stream) {
			System.out.println("content: " + path1.toUri());
		}

		assertThat(stream).isNotNull().hasSize(1);
	}

	@Test
	public void testCopyDirAcrossRepositories() throws IOException {
		final URI newRepo1 = URI.create("git://copydir-test-repo1");
		provider.newFileSystem(newRepo1, EMPTY_ENV);

		final URI newRepo2 = URI.create("git://copydir-test-repo2");
		provider.newFileSystem(newRepo2, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@copydir-test-repo1/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}
		final Path path2 = provider.getPath(URI.create("git://master@copydir-test-repo2/path/myfile2.txt"));
		{
			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		final Path path3 = provider.getPath(URI.create("git://master@copydir-test-repo2/path/myfile3.txt"));
		{
			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		{
			final Path source = provider.getPath(URI.create("git://master@copydir-test-repo2/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo1/"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).isNotNull().hasSize(3);
		}

		{
			final Path source = provider.getPath(URI.create("git://master@copydir-test-repo2/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo1/some/place/here/"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).isNotNull().hasSize(2);
		}

		{
			final Path source = provider.getPath(URI.create("git://master@copydir-test-repo2/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo1/soXme/place/here"));

			provider.copy(source, target);

			final DirectoryStream<Path> stream = provider.newDirectoryStream(target, null);

			assertThat(stream).isNotNull().hasSize(2);
		}

		{
			final Path source = provider.getPath(URI.create("git://master@copydir-test-repo1/not_exists"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo2/xxxxxxxxother_here/"));

			assertThatThrownBy(() -> provider.copy(source, target)).isInstanceOf(NoSuchFileException.class);
		}

		{
			final Path source = provider.getPath(URI.create("git://master@copydir-test-repo2/path"));
			final Path target = provider.getPath(URI.create("git://master@copydir-test-repo1/some/place/here/"));

			assertThatThrownBy(() -> provider.copy(source, target)).isInstanceOf(FileAlreadyExistsException.class);
		}
	}

	@Test
	public void testMoveBranches() throws IOException {
		final URI newRepo = URI.create("git://movebranch-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@movebranch-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		{
			final Path path2 = provider
					.getPath(URI.create("git://user_branch@movebranch-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@movebranch-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		final Path source = provider.getPath(URI.create("git://user_branch@movebranch-test-repo/"));
		final Path target = provider.getPath(URI.create("git://master@movebranch-test-repo/"));

		assertThatThrownBy(() -> provider.move(source, target)).isInstanceOf(FileAlreadyExistsException.class);

		final Path source2 = provider.getPath(URI.create("git://user_branch@movebranch-test-repo/"));
		final Path target2 = provider.getPath(URI.create("git://xxxxddddkh@movebranch-test-repo/"));

		try {
			provider.move(source2, target2);
		} catch (final Exception e) {
			fail("should not throw");
		}
	}

	@Test
	public void testMoveFiles() throws IOException {
		final URI newRepo = URI.create("git://moveasset-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@moveasset-test-repo/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}
		final Path path2 = provider.getPath(URI.create("git://user_branch@moveasset-test-repo/other/path/myfile2.txt"));
		{
			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		final Path path3 = provider.getPath(URI.create("git://user_branch@moveasset-test-repo/myfile3.txt"));
		{
			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		final Path target = provider.getPath(URI.create("git://user_branch@moveasset-test-repo/myfile1.txt"));

		try {
			provider.move(path, target);
		} catch (final Exception e) {
			fail("should move file", e);
		}
	}

	@Test
	public void testMoveDir() throws IOException {
		final URI newRepo = URI.create("git://movedir-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://master@movedir-test-repo/myfile1.txt"));
		{
			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}
		final Path path2 = provider.getPath(URI.create("git://user_branch@movedir-test-repo/path/myfile2.txt"));
		{
			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		final Path path3 = provider.getPath(URI.create("git://user_branch@movedir-test-repo/path/myfile3.txt"));
		{
			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		{
			final Path source = provider.getPath(URI.create("git://user_branch@movedir-test-repo/path"));
			final Path target = provider.getPath(URI.create("git://master@movedir-test-repo/"));

			try {
				provider.move(source, target);
			} catch (IOException e) {
				assertThat(e).isInstanceOf(DirectoryNotEmptyException.class);
			}
		}
	}

	@Test
	public void testCherryPick() throws IOException {
		final URI newRepo = URI.create("git://cherrypick-test-repo");
		provider.newFileSystem(newRepo, EMPTY_ENV);

		{
			final Path path = provider.getPath(URI.create("git://master@cherrypick-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write("my cool content".getBytes());
			outStream.close();
		}

		{
			final Path path2 = provider
					.getPath(URI.create("git://user_branch@cherrypick-test-repo/other/path/myfile2.txt"));

			final OutputStream outStream2 = provider.newOutputStream(path2);
			outStream2.write("my cool content".getBytes());
			outStream2.close();
		}
		{
			final Path path3 = provider.getPath(URI.create("git://user_branch@cherrypick-test-repo/myfile3.txt"));

			final OutputStream outStream3 = provider.newOutputStream(path3);
			outStream3.write("my cool content".getBytes());
			outStream3.close();
		}

		String commit2CherryPick;
		String cherryPickContent = "my 2nd cool content";
		{
			final Path path = provider.getPath(URI.create("git://master@cherrypick-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(cherryPickContent.getBytes());
			outStream.close();

			final VersionAttributes versionAttributes = provider.readAttributes(path, VersionAttributes.class);

			assertThat(versionAttributes.history().records()).isNotNull().hasSize(2);
			commit2CherryPick = versionAttributes.history().records()
					.get(versionAttributes.history().records().size() - 1).id();

			final OutputStream outStream2 = provider.newOutputStream(path);
			outStream2.write("my 3rd cool content".getBytes());
			outStream2.close();
		}

		final Path source = provider.getPath(URI.create("git://user_branch@cherrypick-test-repo"));
		final Path target = provider.getPath(URI.create("git://other_branch@cherrypick-test-repo"));

		provider.copy(source, target);

		String commit2CherryPick2;
		String cherryPickContent2 = "my 4tn cool content";
		{
			final Path path = provider.getPath(URI.create("git://master@cherrypick-test-repo/myfile1.txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			outStream.write(cherryPickContent2.getBytes());
			outStream.close();

			final VersionAttributes versionAttributes = provider.readAttributes(path, VersionAttributes.class);

			commit2CherryPick2 = versionAttributes.history().records()
					.get(versionAttributes.history().records().size() - 1).id();
		}

		final Path target2 = provider.getPath(URI.create("git://other_branch2@cherrypick-test-repo"));
		provider.copy(source, target2);

		{
			provider.copy(source, target, new CherryPickCopyOption(commit2CherryPick));

			String result = convertStreamToString(provider.newInputStream(
					provider.getPath(URI.create("git://other_branch@cherrypick-test-repo/myfile1.txt"))));
			assertThat(result).isEqualTo(cherryPickContent);
		}

		{
			provider.copy(source, target2, new CherryPickCopyOption(commit2CherryPick, commit2CherryPick2));

			final String result = convertStreamToString(provider.newInputStream(
					provider.getPath(URI.create("git://other_branch2@cherrypick-test-repo/myfile1.txt"))));
			assertThat(result).isEqualTo(cherryPickContent2);
		}
	}

	private static String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}
}
