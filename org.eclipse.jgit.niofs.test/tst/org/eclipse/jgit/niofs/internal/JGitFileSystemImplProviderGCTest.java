/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Path;

import org.junit.Test;

public class JGitFileSystemImplProviderGCTest extends BaseTest {

	@Test
	public void testGC() throws IOException {
		final URI newRepo = URI.create("git://gc-repo-name");

		final JGitFileSystem fs = (JGitFileSystem) provider.newFileSystem(newRepo, EMPTY_ENV);

		assertThat(fs).isNotNull();

		final DirectoryStream<Path> stream = provider.newDirectoryStream(provider.getPath(newRepo), null);
		assertThat(stream).isNotNull().hasSize(0);

		try {
			provider.newFileSystem(newRepo, EMPTY_ENV);
			failBecauseExceptionWasNotThrown(FileSystemAlreadyExistsException.class);
		} catch (final Exception ex) {
		}

		for (int i = 0; i < 19; i++) {
			assertThat(fs.getNumberOfCommitsSinceLastGC()).isEqualTo(i);

			final Path path = provider.getPath(URI.create("git://gc-repo-name/path/to/myfile" + i + ".txt"));

			final OutputStream outStream = provider.newOutputStream(path);
			assertThat(outStream).isNotNull();
			outStream.write(("my cool" + i + " content").getBytes());
			outStream.close();
		}

		final Path path = provider.getPath(URI.create("git://gc-repo-name/path/to/myfile.txt"));

		final OutputStream outStream = provider.newOutputStream(path);
		assertThat(outStream).isNotNull();
		outStream.write("my cool content".getBytes());
		outStream.close();
		assertThat(fs.getNumberOfCommitsSinceLastGC()).isEqualTo(0);

		final OutputStream outStream2 = provider.newOutputStream(path);
		assertThat(outStream2).isNotNull();
		outStream2.write("my co dwf sdf ol content".getBytes());
		outStream2.close();
		assertThat(fs.getNumberOfCommitsSinceLastGC()).isEqualTo(1);
	}
}
