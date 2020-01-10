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
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_NIO_DIR;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_NIO_DIR_NAME;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.REPOSITORIES_CONTAINER_DIR;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewProviderDefineDirTest extends BaseTest {

	private String dirPathName;
	private File tempDir;

	public NewProviderDefineDirTest(final String dirPathName) {
		this.dirPathName = dirPathName;
	}

	@Parameters(name = "{index}: dir name: {0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { { REPOSITORIES_CONTAINER_DIR }, { ".tempgit" } });
	}

	@Override
	public Map<String, String> getGitPreferences() {
		try {
			tempDir = util.createTempDirectory();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		Map<String, String> gitPrefs = super.getGitPreferences();
		gitPrefs.put(GIT_NIO_DIR, tempDir.toString());
		if (!REPOSITORIES_CONTAINER_DIR.equals(dirPathName)) {
			gitPrefs.put(GIT_NIO_DIR_NAME, dirPathName);
		}
		return gitPrefs;
	}

	@Test
	public void testUsingProvidedPath() throws IOException {
		final URI newRepo = URI.create("git://repo-name");

		JGitFileSystemProxy fileSystem = (JGitFileSystemProxy) provider.newFileSystem(newRepo, EMPTY_ENV);

		// no infra created due to lazy loading nature of our FS
		String[] names = tempDir.list();

		assertThat(names).isEmpty();

		String[] repos = new File(tempDir, dirPathName).list();

		assertThat(repos).isNull();

		// FS created
		fileSystem.getRealJGitFileSystem();

		names = tempDir.list();

		assertThat(names).contains(dirPathName);

		repos = new File(tempDir, dirPathName).list();

		assertThat(repos).contains("repo-name.git");
	}
}