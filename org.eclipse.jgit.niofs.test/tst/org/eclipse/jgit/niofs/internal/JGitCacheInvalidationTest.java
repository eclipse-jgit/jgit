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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.JGIT_CACHE_EVICT_THRESHOLD_DURATION;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.JGIT_CACHE_EVICT_THRESHOLD_TIME_UNIT;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.JGIT_CACHE_INSTANCES;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.niofs.internal.manager.JGitFileSystemsCache;
import org.eclipse.jgit.niofs.internal.manager.JGitFileSystemsManager;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.junit.Before;
import org.junit.Test;

public class JGitCacheInvalidationTest extends BaseTest {

	private JGitFileSystemsCache fsCache;
	private JGitFileSystemsManager fsManager;

	@Override
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPreferences = super.getGitPreferences();
		gitPreferences.put(JGIT_CACHE_EVICT_THRESHOLD_DURATION, "1");
		gitPreferences.put(JGIT_CACHE_EVICT_THRESHOLD_TIME_UNIT, TimeUnit.MILLISECONDS.name());
		gitPreferences.put(JGIT_CACHE_INSTANCES, "2");
		return gitPreferences;
	}

	@Before
	public void createGitFsProvider() throws IOException {
		fsManager = provider.getFsManager();
		fsCache = fsManager.getFsCache();
	}

	@Test
	public void testTwoInstancesForSameFS() throws IOException {
		String fs1Name = "dora";
		String fs2Name = "bento";
		String fs3Name = "bela";

		final JGitFileSystemProxy fs1 = (JGitFileSystemProxy) provider.newFileSystem(URI.create("git://" + fs1Name),
				EMPTY_ENV);
		final JGitFileSystemImpl realInstanceFs1 = (JGitFileSystemImpl) fs1.getRealJGitFileSystem();

		final FileSystem fs2 = provider.newFileSystem(URI.create("git://" + fs2Name), EMPTY_ENV);
		final FileSystem fs3 = provider.newFileSystem(URI.create("git://" + fs3Name), EMPTY_ENV);

		assertThat(fs1).isNotNull();
		assertThat(fs2).isNotNull();
		assertThat(fs3).isNotNull();

		// only proxies instances
		assertThat(fs1).isInstanceOf(JGitFileSystemProxy.class);
		assertThat(fs2).isInstanceOf(JGitFileSystemProxy.class);
		assertThat(fs3).isInstanceOf(JGitFileSystemProxy.class);

		// all the fs have suppliers registered
		assertThat(fsCache.getFileSystems()).contains(fs1.getName());
		assertThat(fsCache.getFileSystems()).contains(((JGitFileSystem) fs2).getName());
		assertThat(fsCache.getFileSystems()).contains(((JGitFileSystem) fs3).getName());

		// only the last two FS are memoized
		JGitFileSystemsCache.JGitFileSystemsCacheInfo cacheInfo = fsCache.getCacheInfo();

		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).contains(((JGitFileSystem) fs2).getName());
		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).contains(((JGitFileSystem) fs3).getName());

		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).doesNotContain(fs1.getName());

		// a hit on fs1 in order to put him on cache
		JGitFileSystemProxy anotherInstanceOfFs1Proxy = (JGitFileSystemProxy) fsManager.get(fs1Name);
		JGitFileSystemImpl anotherInstanceOfFs1 = (JGitFileSystemImpl) anotherInstanceOfFs1Proxy
				.getRealJGitFileSystem();

		// now fs2 are not memoized
		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).contains(fs1.getName());
		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).contains(((JGitFileSystem) fs3).getName());

		assertThat(cacheInfo.memoizedFileSystemsCacheKeys()).doesNotContain(((JGitFileSystem) fs2).getName());

		// asserting that fs1 and anotherInstanceOfFs1 are instances of the same fs
		assertThat(realInstanceFs1.getName()).isEqualToIgnoringCase(anotherInstanceOfFs1.getName());
		// they share the same lock
		assertThat(realInstanceFs1.getLock()).isEqualTo(anotherInstanceOfFs1.getLock());

		// now lets commit on both instances and read with other one
		new Commit(realInstanceFs1.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("realInstanceFs1File.txt", tempFile("dora"));
					}
				}).execute();

		InputStream stream = provider.newInputStream(anotherInstanceOfFs1.getPath("realInstanceFs1File.txt"));
		assertNotNull(stream);
		String content = new Scanner(stream).useDelimiter("\\A").next();
		assertEquals("dora", content);

		new Commit(anotherInstanceOfFs1.getGit(), "master", "user1", "user1@example.com", "commitx", null, null, false,
				new HashMap<String, File>() {
					{
						put("anotherInstanceOfFs1File.txt", tempFile("bento"));
					}
				}).execute();

		stream = provider.newInputStream(realInstanceFs1.getPath("anotherInstanceOfFs1File.txt"));
		assertNotNull(stream);
		content = new Scanner(stream).useDelimiter("\\A").next();
		assertEquals("bento", content);

		realInstanceFs1.lock();
		assertThat(realInstanceFs1.hasBeenInUse()).isTrue();
		assertThat(anotherInstanceOfFs1.hasBeenInUse()).isTrue();

		// Unlock the lock so that cleanup can finish on Windows
		realInstanceFs1.unlock();
	}
}
