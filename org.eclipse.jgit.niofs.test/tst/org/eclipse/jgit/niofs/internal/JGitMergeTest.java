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
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateBranch;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.commands.GetTreeFromRef;
import org.eclipse.jgit.niofs.internal.op.commands.ListDiffs;
import org.eclipse.jgit.niofs.internal.op.commands.Merge;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.junit.Test;

public class JGitMergeTest extends BaseTest {

	private static final String SOURCE_GIT = "source/source";

	@Test
	public void testMergeFastForwardSuccessful() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file2.txt", tempFile("temp2"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-2", null, null, false,
				new HashMap<String, File>() {
					{
						put("file3.txt", tempFile("temp3"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-3", null, null, false,
				new HashMap<String, File>() {
					{
						put("file4.txt", tempFile("temp4"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-4", null, null, false,
				new HashMap<String, File>() {
					{
						put("file5.txt", tempFile("temp5"));
					}
				}).execute();

		new Merge(origin, "develop", "master").execute();

		final List<DiffEntry> result = new ListDiffs(origin, new GetTreeFromRef(origin, "master").execute(),
				new GetTreeFromRef(origin, "develop").execute()).execute();

		assertThat(result).isEmpty();
	}

	@Test
	public void testMergeNonFastForwardSuccessful() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file2.txt", tempFile("temp2"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-2", null, null, false,
				new HashMap<String, File>() {
					{
						put("file3.txt", tempFile("temp3"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-3", null, null, false,
				new HashMap<String, File>() {
					{
						put("file4.txt", tempFile("temp4"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-4", null, null, false,
				new HashMap<String, File>() {
					{
						put("file5.txt", tempFile("temp5"));
					}
				}).execute();

		new Merge(origin, "develop", "master", true).execute();

		final List<DiffEntry> result = new ListDiffs(origin, new GetTreeFromRef(origin, "master").execute(),
				new GetTreeFromRef(origin, "develop").execute()).execute();

		assertThat(result).isEmpty();
	}

	@Test
	public void testMergeNoDiff() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1"));
					}
				}).execute();

		List<String> commitIds = new Merge(origin, "develop", "master").execute();

		assertThat(commitIds).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParametersNotNull() throws IOException {

		new Merge(null, "develop", "master").execute();
	}

	@Test(expected = GitException.class)
	public void testTryToMergeNonexistentBranch() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file2.txt", tempFile("temp2"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-2", null, null, false,
				new HashMap<String, File>() {
					{
						put("file3.txt", tempFile("temp3"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-3", null, null, false,
				new HashMap<String, File>() {
					{
						put("file4.txt", tempFile("temp4"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-4", null, null, false,
				new HashMap<String, File>() {
					{
						put("file5.txt", tempFile("temp5"));
					}
				}).execute();

		new Merge(origin, "develop", "nonexistent").execute();
	}

	@Test(expected = GitException.class)
	public void testMergeBinaryInformationButHasConflicts() throws IOException {

		final byte[] contentA = loadImage("images/drools.png");
		final byte[] contentB = loadImage("images/jbpm.png");
		final byte[] contentC = loadImage("images/opta.png");

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.jpg", tempFile(contentA));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.jpg", tempFile(contentB));
					}
				}).execute();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.jpg", tempFile(contentC));
					}
				}).execute();

		new Merge(origin, "develop", "master").execute();

		final List<DiffEntry> result = new ListDiffs(origin, new GetTreeFromRef(origin, "master").execute(),
				new GetTreeFromRef(origin, "develop").execute()).execute();

		assertThat(result).isEmpty();
	}

	@Test
	public void testMergeBinaryInformationSuccessful() throws IOException {

		final byte[] contentA = loadImage("images/drools.png");
		final byte[] contentB = loadImage("images/jbpm.png");

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, SOURCE_GIT + ".git");
		final Git origin = new CreateRepository(gitSource).execute().get();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.jpg", tempFile(contentA));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.jpg", tempFile(contentB));
					}
				}).execute();

		new Merge(origin, "develop", "master").execute();

		final List<DiffEntry> result = new ListDiffs(origin, new GetTreeFromRef(origin, "master").execute(),
				new GetTreeFromRef(origin, "develop").execute()).execute();

		assertThat(result).isEmpty();
	}
}
