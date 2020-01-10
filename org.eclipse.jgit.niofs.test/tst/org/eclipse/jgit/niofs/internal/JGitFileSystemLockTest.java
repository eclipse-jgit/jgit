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
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JGitFileSystemLockTest {

	@Test
	public void thresholdMaxTest() {
		long lastAccessThreshold = Long.MAX_VALUE;
		JGitFileSystemLock lock = createLock(lastAccessThreshold);
		lock.registerAccess();
		assertTrue(lock.hasBeenInUse());
	}

	@Test
	public void thresholdMinTest() {
		long lastAccessThreshold = Long.MIN_VALUE;
		JGitFileSystemLock lock = createLock(lastAccessThreshold);
		lock.registerAccess();

		lock.lock.lock();
		assertTrue(lock.hasBeenInUse());
		lock.lock.unlock();
		assertFalse(lock.hasBeenInUse());
	}

	private JGitFileSystemLock createLock(long lastAccessThreshold) {
		Git gitMock = mock(Git.class);
		Repository repo = mock(Repository.class);
		File directory = mock(File.class);
		when(directory.isDirectory()).thenReturn(true);
		when(directory.toURI()).thenReturn(URI.create(""));
		when(repo.getDirectory()).thenReturn(directory);
		when(gitMock.getRepository()).thenReturn(repo);
		return new JGitFileSystemLock(gitMock, TimeUnit.MILLISECONDS, lastAccessThreshold) {

			@Override
			Path createLockInfra(URI uri) {
				return mock(Path.class);
			}
		};
	}
}