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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.hooks.PostCommitHook;
import org.eclipse.jgit.hooks.PreCommitHook;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.commands.Clone;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.commands.Fork;
import org.eclipse.jgit.niofs.internal.op.commands.ListRefs;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitForkTest extends BaseTest {

	private static final String TARGET_GIT = "target/target", SOURCE_GIT = "source/source";
	private static Logger logger = LoggerFactory.getLogger(JGitForkTest.class);

	@Test
	public void testToForkSuccess() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "user_branch", "name", "name@example.com", "commit!", null, null, false,
				new HashMap<String, File>() {
					{
						put("file2.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(origin, "master", "name", "name@example.com", "commit", null, null, false,
				new HashMap<String, File>() {
					{
						put("file.txt", tempFile("temp"));
					}
				}).execute();
		new Commit(origin, "master", "name", "name@example.com", "commit", null, null, false,
				new HashMap<String, File>() {
					{
						put("file3.txt", tempFile("temp3"));
					}
				}).execute();

		new Fork(parentFolder, SOURCE_GIT, TARGET_GIT, null, CredentialsProvider.getDefault(), null, null).execute();

		final File gitCloned = new File(parentFolder, TARGET_GIT + ".git");
		final Git cloned = Git.createRepository(gitCloned);

		assertThat(cloned).isNotNull();

		assertThat(new ListRefs(cloned.getRepository()).execute()).hasSize(2);

		assertThat(new ListRefs(cloned.getRepository()).execute().get(0).getName()).isEqualTo("refs/heads/master");
		assertThat(new ListRefs(cloned.getRepository()).execute().get(1).getName()).isEqualTo("refs/heads/user_branch");

		final String remotePath = new File(((GitImpl) cloned)._remoteList().call().get(0).getURIs().get(0).getPath())
				.getAbsolutePath();
		assertThat(remotePath).isEqualTo(new File(gitSource.getPath()).getAbsolutePath());
	}

	@Test(expected = GitException.class)
	public void testToForkAlreadyExists() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "commit", null, null, false,
				new HashMap<String, File>() {
					{
						put("file.txt", tempFile("temp"));
					}
				}).execute();

		final File gitTarget = new File(parentFolder, TARGET_GIT + ".git");
		final Git originTarget = new CreateRepository(gitTarget).execute().get();

		new Commit(originTarget, "master", "name", "name@example.com", "commit", null, null, false,
				new HashMap<String, File>() {
					{
						put("file.txt", tempFile("temp"));
					}
				}).execute();

		new Fork(parentFolder, SOURCE_GIT, TARGET_GIT, null, CredentialsProvider.getDefault(), null, null).execute();
	}

	@Test
	public void testToForkWrongSource() throws IOException {
		final File parentFolder = util.createTempDirectory();

		try {
			new Fork(parentFolder, SOURCE_GIT, TARGET_GIT, null, CredentialsProvider.getDefault(), null, null)
					.execute();
			fail("If got here is because it could for the repository");
		} catch (Clone.CloneException e) {
			assertThat(e).isNotNull();
			logger.info(e.getMessage(), e);
		}
	}

	@Test
	public void testForkRepository() throws GitAPIException, IOException {

		String SOURCE = "testforkA/source";
		String TARGET = "testforkB/target";

		final Map<String, ?> env = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_INIT, "true");
			}
		};

		String sourcePath = "git://" + SOURCE;
		final URI sourceUri = URI.create(sourcePath);
		provider.newFileSystem(sourceUri, env);

		final Map<String, ?> forkEnv = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME, SOURCE);
			}
		};
		String forkPath = "git://" + TARGET;
		final URI forkUri = URI.create(forkPath);
		final JGitFileSystem fs = (JGitFileSystem) provider.newFileSystem(forkUri, forkEnv);

		assertThat(((GitImpl) fs.getGit())._remoteList().call().get(0).getURIs().get(0).toString())
				.isEqualTo(new File(provider.getGitRepoContainerDir(), SOURCE + ".git").toPath().toUri().toString());
	}

	@Test(expected = FileSystemAlreadyExistsException.class)
	public void testForkRepositoryThatAlreadyExists() throws IOException {

		String SOURCE = "testforkA/source";
		String TARGET = "testforkB/target";

		final Map<String, ?> env = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_INIT, "true");
			}
		};

		String sourcePath = "git://" + SOURCE;
		final URI sourceUri = URI.create(sourcePath);
		provider.newFileSystem(sourceUri, env);

		final Map<String, ?> forkEnv = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME, SOURCE);
			}
		};

		String forkPath = "git://" + TARGET;
		final URI forkUri = URI.create(forkPath);
		provider.newFileSystem(forkUri, forkEnv);
		provider.newFileSystem(forkUri, forkEnv);
	}

	@Test
	public void testForkWithoutHookDirShouldNotBeUpdatedAfterGitHookDirAdded() throws IOException, GitAPIException {

		final File hooksDir = util.createTempDirectory();

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");

		writeMockHook(hooksDir, PostCommitHook.NAME);
		writeMockHook(hooksDir, PreCommitHook.NAME);

		final Git repo = new CreateRepository(gitSource, null).execute().get();
		final Git existentRepoWithHookDirDefined = new CreateRepository(gitSource, hooksDir).execute().get();

		File[] hooks = new File(existentRepoWithHookDirDefined.getRepository().getDirectory(), "hooks").listFiles();
		assertThat(hooks).isEmpty();
	}

	@Test
	public void testForkWithHookDir() throws IOException, GitAPIException {
		final File hooksDir = util.createTempDirectory();

		writeMockHook(hooksDir, PostCommitHook.NAME);
		writeMockHook(hooksDir, PreCommitHook.NAME);

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource, hooksDir).execute().get();

		new Commit(origin, "user_branch", "name", "name@example.com", "commit!", null, null, false,
				new HashMap<String, File>() {
					{
						put("file2.txt", tempFile("temp2222"));
					}
				}).execute();

		final Git cloned = new Fork(parentFolder, SOURCE_GIT, TARGET_GIT, null, CredentialsProvider.getDefault(), null,
				hooksDir).execute();

		assertThat(cloned).isNotNull();

		assertThat(new ListRefs(cloned.getRepository()).execute()).hasSize(1);

		assertThat(new ListRefs(cloned.getRepository()).execute().get(0).getName()).isEqualTo("refs/heads/user_branch");

		final String remotePath = new File(((GitImpl) cloned)._remoteList().call().get(0).getURIs().get(0).getPath())
				.getAbsolutePath();
		assertThat(remotePath).isEqualTo(new File(gitSource.getPath()).getAbsolutePath());

		boolean foundPreCommitHook = false;
		boolean foundPostCommitHook = false;
		File[] hooks = new File(cloned.getRepository().getDirectory(), "hooks").listFiles();
		assertThat(hooks).isNotEmpty().isNotNull();
		assertThat(hooks.length).isEqualTo(2);
		for (File hook : hooks) {
			if (hook.getName().equals(PreCommitHook.NAME)) {
				foundPreCommitHook = hook.canExecute();
			} else if (hook.getName().equals(PostCommitHook.NAME)) {
				foundPostCommitHook = hook.canExecute();
			}
		}
		assertThat(foundPreCommitHook).isTrue();
		assertThat(foundPostCommitHook).isTrue();
	}

	@Test
	public void testForkMultipleBranches() throws Exception {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");

		final Git origin = new CreateRepository(gitSource, null).execute().get();

		commit(origin, "master", "first", content("dir1/file.txt", "foo"), content("dir2/file2.txt", "bar"),
				content("file3.txt", "moogah"));

		branch(origin, "master", "dev");
		commit(origin, "dev", "second", content("dir1/file.txt", "foo1"), content("file3.txt", "bar1"));

		branch(origin, "master", "ignored");
		commit(origin, "ignored", "third", content("dir1/file.txt", "foo2"));

		final Git cloned = new Fork(parentFolder, SOURCE_GIT, TARGET_GIT, asList("master", "dev"),
				CredentialsProvider.getDefault(), null, null).execute();

		assertThat(cloned).isNotNull();
		final Set<String> clonedRefs = listRefs(cloned).stream().map(ref -> ref.getName()).collect(toSet());
		assertThat(clonedRefs).hasSize(2);
		assertThat(clonedRefs).containsExactly("refs/heads/master", "refs/heads/dev");
	}
}
