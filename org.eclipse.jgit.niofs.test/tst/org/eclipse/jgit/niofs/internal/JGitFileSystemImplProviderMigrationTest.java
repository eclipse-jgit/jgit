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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class JGitFileSystemImplProviderMigrationTest extends BaseTest {

	@Test
	public void testCreateANewDirectoryWithMigrationEnv() throws IOException {

		final Map<String, ?> envMigrate = new HashMap<String, Object>() {
			{
				put("init", Boolean.TRUE);
				put("migrate-from", URI.create("git://old"));
			}
		};

		String newPath = "git://test/old";
		final URI newUri = URI.create(newPath);
		provider.newFileSystem(newUri, envMigrate);

		provider.getFileSystem(newUri);
		assertThat(new File(provider.getGitRepoContainerDir(), "test/old" + ".git")).exists();
		assertThat(provider.getFileSystem(newUri)).isNotNull();
	}
}
