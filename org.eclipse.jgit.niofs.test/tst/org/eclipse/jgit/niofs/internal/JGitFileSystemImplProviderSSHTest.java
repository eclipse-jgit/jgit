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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.niofs.internal.security.AuthenticationService;
import org.eclipse.jgit.niofs.internal.security.User;
import org.apache.sshd.server.SshServer;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHookExecutionContext;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooks;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_IDLE_TIMEOUT;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class JGitFileSystemImplProviderSSHTest extends AbstractTestInfra {

	private int gitSSHPort;

	@Override
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();

		gitPrefs.put(GIT_SSH_ENABLED, "true");
		gitSSHPort = findFreePort();
		gitPrefs.put(GIT_SSH_PORT, String.valueOf(gitSSHPort));
		gitPrefs.put(GIT_SSH_IDLE_TIMEOUT, "10001");

		return gitPrefs;
	}

	@Test
	public void testSSHPostReceiveHook() throws IOException {
		FileSystemHooks.FileSystemHook hook = spy(new FileSystemHooks.FileSystemHook() {
			@Override
			public void execute(FileSystemHookExecutionContext context) {
				assertEquals("repo", context.getFsName());
			}
		});

		Assume.assumeFalse("UF-511", System.getProperty("java.vendor").equals("IBM Corporation"));
		// Setup Authorization/Authentication
		provider.setJAASAuthenticator(new AuthenticationService() {
			private User user;

			@Override
			public User login(String s, String s1) {
				user = new User() {
					@Override
					public String getIdentifier() {
						return s;
					}
				};
				return user;
			}

			@Override
			public boolean isLoggedIn() {
				return user != null;
			}

			@Override
			public void logout() {
				user = null;
			}

			@Override
			public User getUser() {
				return user;
			}
		});
		provider.setAuthorizer((fs, fileSystemUser) -> true);

		CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider("admin", ""));
		assertEquals("10001", provider.getGitSSHService().getProperties().get(SshServer.IDLE_TIMEOUT));

		// Setup origin
		final URI originRepo = URI.create("git://repo");
		final JGitFileSystem origin = (JGitFileSystem) provider.newFileSystem(originRepo,
				new HashMap<String, Object>() {
					{
						put(FileSystemHooks.ExternalUpdate.name(), hook);
					}
				});

		// Write a file to origin that we won't amend in the clone
		new Commit(origin.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("file-name.txt", tempFile("temp1"));
					}
				}).execute();

		// Setup clone
		JGitFileSystem clone;
		clone = (JGitFileSystem) provider.newFileSystem(URI.create("git://repo-clone"), new HashMap<String, Object>() {
			{
				put("init", "true");
				put("origin", "ssh://admin@localhost:" + gitSSHPort + "/repo");
			}
		});

		assertNotNull(clone);

		// Push clone back to origin
		provider.getFileSystem(URI.create("git://repo-clone?push=ssh://admin@localhost:" + gitSSHPort + "/repo"));

		ArgumentCaptor<FileSystemHookExecutionContext> captor = ArgumentCaptor
				.forClass(FileSystemHookExecutionContext.class);

		verify(hook).execute(captor.capture());

		Assertions.assertThat(captor.getValue()).isNotNull().hasFieldOrPropertyWithValue("fsName", "repo");
	}
}
