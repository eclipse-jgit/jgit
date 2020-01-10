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
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.niofs.fs.attribute.VersionAttributes;
import org.eclipse.jgit.niofs.fs.attribute.VersionRecord;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.Clone;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.commands.GetTreeFromRef;
import org.eclipse.jgit.niofs.internal.op.commands.ListDiffs;
import org.eclipse.jgit.niofs.internal.op.commands.ListRefs;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.niofs.internal.op.model.PathType.DIRECTORY;
import static org.eclipse.jgit.niofs.internal.op.model.PathType.FILE;
import static org.eclipse.jgit.niofs.internal.op.model.PathType.NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JGitUtilTest extends AbstractTestInfra {

	@Test
	public void testNewRepo() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		assertThat(git).isNotNull();

		assertThat(new ListRefs(git.getRepository()).execute().size()).isEqualTo(0);

		new Commit(git, "master", "name", "name@example.com", "commit", null, null, false, new HashMap<String, File>() {
			{
				put("file.txt", tempFile("temp"));
			}
		}).execute();

		assertThat(new ListRefs(git.getRepository()).execute().size()).isEqualTo(1);
	}

	@Test
	public void testClone() throws IOException, InvalidRemoteException {
		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git origin = new CreateRepository(gitFolder).execute().get();

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

		final File gitClonedFolder = new File(parentFolder, "myclone.git");

		final Git git = new Clone(gitClonedFolder, origin.getRepository().getDirectory().toString(), false, null,
				CredentialsProvider.getDefault(), null, null).execute().get();

		assertThat(git).isNotNull();

		assertThat(new ListRefs(git.getRepository()).execute()).hasSize(2);

		assertThat(new ListRefs(git.getRepository()).execute().get(0).getName()).isEqualTo("refs/heads/master");
		assertThat(new ListRefs(git.getRepository()).execute().get(1).getName()).isEqualTo("refs/heads/user_branch");
	}

	@Test
	public void testPathResolve() throws IOException, InvalidRemoteException {
		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git origin = new CreateRepository(gitFolder).execute().get();

		new Commit(origin, "user_branch", "name", "name@example.com", "commit!", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(origin, "user_branch", "name", "name@example.com", "commit!", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file3.txt", tempFile("temp2222"));
					}
				}).execute();

		final File gitClonedFolder = new File(parentFolder, "myclone.git");

		final Git git = new Clone(gitClonedFolder, origin.getRepository().getDirectory().toString(), false, null,
				CredentialsProvider.getDefault(), null, null).execute().get();

		assertThat(git.getPathInfo("user_branch", "pathx/").getPathType()).isEqualTo(NOT_FOUND);
		assertThat(git.getPathInfo("user_branch", "path/to/file2.txt").getPathType()).isEqualTo(FILE);
		assertThat(git.getPathInfo("user_branch", "path/to").getPathType()).isEqualTo(DIRECTORY);
	}

	@Test
	public void testAmend() throws IOException, InvalidRemoteException {
		final File parentFolder = createTempDirectory();
		System.out.println("COOL!:" + parentFolder.toString());
		final File gitFolder = new File(parentFolder, "myxxxtest.git");

		final Git origin = new CreateRepository(gitFolder).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "commit!", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("tempwdf sdf asdf asd2222"));
					}
				}).execute();
		new Commit(origin, "master", "name", "name@example.com", "commit!", null, null, true,
				new HashMap<String, File>() {
					{
						put("path/to/file3.txt", tempFile("temp2x d dasdf asdf 222"));
					}
				}).execute();

		final File gitClonedFolder = new File(parentFolder, "myclone.git");

		final Git git = new Clone(gitClonedFolder, origin.getRepository().getDirectory().toString(), false, null,
				CredentialsProvider.getDefault(), null, null).execute().get();

		assertThat(git.getPathInfo("master", "pathx/").getPathType()).isEqualTo(NOT_FOUND);
		assertThat(git.getPathInfo("master", "path/to/file2.txt").getPathType()).isEqualTo(FILE);
		assertThat(git.getPathInfo("master", "path/to").getPathType()).isEqualTo(DIRECTORY);
	}

	@Test
	public void testBuildVersionAttributes() throws Exception {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "commit 1", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("who"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "commit 2", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("you"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "commit 3", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("gonna"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "commit 4", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("call?"));
					}
				}).execute();

		JGitFileSystem jGitFileSystem = mock(JGitFileSystem.class);
		when(jGitFileSystem.getGit()).thenReturn(git);

		final JGitPathImpl path = mock(JGitPathImpl.class);
		when(path.getFileSystem()).thenReturn(jGitFileSystem);
		when(path.getRefTree()).thenReturn("master");
		when(path.getPath()).thenReturn("path/to/file2.txt");

		final VersionAttributes versionAttributes = new JGitVersionAttributeViewImpl(path).readAttributes();

		List<VersionRecord> records = versionAttributes.history().records();
		assertEquals("commit 1", records.get(0).comment());
		assertEquals("commit 2", records.get(1).comment());
		assertEquals("commit 3", records.get(2).comment());
		assertEquals("commit 4", records.get(3).comment());
	}

	@Test
	public void testDiffForFileCreatedInEmptyRepositoryOrBranch() throws Exception {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		final ObjectId oldHead = new GetTreeFromRef(git, "master").execute();

		new Commit(git, "master", "name", "name@example.com", "commit 1", null, null, false,
				new HashMap<String, File>() {
					{
						put("path/to/file.txt", tempFile("who"));
					}
				}).execute();

		final ObjectId newHead = new GetTreeFromRef(git, "master").execute();

		List<DiffEntry> diff = new ListDiffs(git, oldHead, newHead).execute();
		assertNotNull(diff);
		assertFalse(diff.isEmpty());
		assertEquals(ChangeType.ADD, diff.get(0).getChangeType());
		assertEquals("path/to/file.txt", diff.get(0).getNewPath());
	}
}
