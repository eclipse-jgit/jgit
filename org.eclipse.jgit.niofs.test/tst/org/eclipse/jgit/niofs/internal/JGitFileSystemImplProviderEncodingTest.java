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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_PORT;

public class JGitFileSystemImplProviderEncodingTest extends AbstractTestInfra {

	private int gitDaemonPort;

	@Override
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();
		gitPrefs.put(GIT_DAEMON_ENABLED, "true");
		// use different port for every test -> easy to run tests in parallel
		gitDaemonPort = findFreePort();
		gitPrefs.put(GIT_DAEMON_PORT, String.valueOf(gitDaemonPort));
		return gitPrefs;
	}

	@Test
	public void test() throws IOException {
		final URI originRepo = URI.create("git://encoding-origin-name");

		final JGitFileSystem origin = (JGitFileSystem) provider.newFileSystem(originRepo, Collections.emptyMap());

		new Commit(origin.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("file-name.txt", tempFile("temp1"));
					}
				}).execute();

		new Commit(origin.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("file+name.txt", tempFile("temp2"));
					}
				}).execute();

		new Commit(origin.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("file name.txt", tempFile("temp3"));
					}
				}).execute();

		final URI newRepo = URI.create("git://my-encoding-repo-name");

		final Map<String, Object> env = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME,
						"git://localhost:" + gitDaemonPort + "/encoding-origin-name");
			}
		};

		final FileSystem fs = provider.newFileSystem(newRepo, env);

		assertThat(fs).isNotNull();

		fs.getPath("file+name.txt").toUri();

		provider.getPath(fs.getPath("file+name.txt").toUri());

		URI uri = fs.getPath("file+name.txt").toUri();
		Path path = provider.getPath(uri);
		Path path1 = fs.getPath("file+name.txt");
		assertThat(path).isEqualTo(path1);

		assertThat(provider.getPath(fs.getPath("file name.txt").toUri())).isEqualTo(fs.getPath("file name.txt"));

		assertThat(fs.getPath("file.txt").toUri());

		assertThat(provider.getPath(fs.getPath("file.txt").toUri())).isEqualTo(fs.getPath("file.txt"));
	}
}
