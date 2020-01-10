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

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

import org.junit.Test;

public class JGitFileSystemImplProviderUnsupportedOpTest extends BaseTest {

	@Test
	public void testNewFileSystemUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://someunsup-repo-name");

		final FileSystem fs = provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = JGitPathImpl.create((JGitFileSystem) fs, "", "repo2-name", false);

		assertThatThrownBy(() -> provider.newFileSystem(path, EMPTY_ENV))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void testNewFileChannelUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://newfcrepo-name");

		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://newfcrepo-name/file.txt"));

		final Set<? extends OpenOption> options = emptySet();
		assertThatThrownBy(() -> provider.newFileChannel(path, options))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void testNewAsynchronousFileChannelUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://newasyncrepo-name");

		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path path = provider.getPath(URI.create("git://newasyncrepo-name/file.txt"));

		final Set<? extends OpenOption> options = emptySet();
		assertThatThrownBy(() -> provider.newAsynchronousFileChannel(path, options, null))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void testCreateSymbolicLinkUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://symbolic-repo-name");

		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path link = provider.getPath(URI.create("git://symbolic-repo-name/link.lnk"));
		final Path path = provider.getPath(URI.create("git://symbolic-repo-name/file.txt"));

		assertThatThrownBy(() -> provider.createSymbolicLink(link, path))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void testCreateLinkUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://link-repo-name");

		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path link = provider.getPath(URI.create("git://link-repo-name/link.lnk"));
		final Path path = provider.getPath(URI.create("git://link-repo-name/file.txt"));

		assertThatThrownBy(() -> provider.createLink(link, path)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void testReadSymbolicLinkUnsupportedOp() throws IOException {
		final URI newRepo = URI.create("git://read-link-repo-name");

		provider.newFileSystem(newRepo, EMPTY_ENV);

		final Path link = provider.getPath(URI.create("git://read-link-repo-name/link.lnk"));

		assertThatThrownBy(() -> provider.readSymbolicLink(link)).isInstanceOf(UnsupportedOperationException.class);
	}
}
