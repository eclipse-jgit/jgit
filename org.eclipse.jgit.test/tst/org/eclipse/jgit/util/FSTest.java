/*
 * Copyright (C) 2012-2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.time.Instant.EPOCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.RepositoryCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FSTest {
	private File trash;

	@BeforeEach
	public void setUp() throws Exception {
		SystemReader.setInstance(new MockSystemReader());
		trash = File.createTempFile("tmp_", "");
		trash.delete();
		assertTrue(trash.mkdir(), "mkdir " + trash);
	}

	@AfterEach
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	/**
	 * The old File methods traverse symbolic links and look at the targets.
	 * With symbolic links we usually want to modify/look at the link. For some
	 * reason the executable attribute seems to always look at the target, but
	 * for the other attributes like lastModified, hidden and exists we must
	 * differ between the link and the target.
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	void testSymlinkAttributes() throws IOException, InterruptedException {
		Assumptions.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = FS.DETECTED;
		File link = new File(trash, "a");
		File target = new File(trash, "b");
		fs.createSymLink(link, "b");
		assertTrue(fs.exists(link));
		String targetName = fs.readSymLink(link);
		assertEquals("b", targetName);
		assertTrue(fs.lastModifiedInstant(link).compareTo(EPOCH) > 0);
		assertTrue(fs.exists(link));
		assertFalse(fs.canExecute(link));
		// The length of a symbolic link is a length of the target file path.
		assertEquals(1, fs.length(link));
		assertFalse(fs.exists(target));
		assertFalse(fs.isFile(target));
		assertFalse(fs.isDirectory(target));
		assertFalse(fs.canExecute(target));

		RepositoryTestCase.fsTick(link);
		// Now create the link target
		FileUtils.createNewFile(target);
		assertTrue(fs.exists(link));
		assertTrue(fs.lastModifiedInstant(link).compareTo(EPOCH) > 0);
		assertTrue(fs.lastModifiedInstant(target)
				.compareTo(fs.lastModifiedInstant(link)) > 0);
		assertFalse(fs.canExecute(link));
		fs.setExecute(target, true);
		assertFalse(fs.canExecute(link));
		assumeTrue(fs.supportsExecute());
		assertTrue(fs.canExecute(target));
	}

	@Test
	void testUnicodeFilePath() throws IOException {
		Assumptions.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = FS.DETECTED;
		File link = new File(trash, "ä");
		File target = new File(trash, "å");

		try {
			// Check if the runtime can support Unicode file paths.
			link.toPath();
			target.toPath();
		} catch (InvalidPathException e) {
			// When executing a test with LANG environment variable set to non
			// UTF-8 encoding, it seems that JRE cannot handle Unicode file
			// paths. This happens when this test is executed in Bazel as it
			// unsets LANG
			// (https://docs.bazel.build/versions/master/test-encyclopedia.html#initial-conditions).
			// Skip the test if the runtime cannot handle Unicode characters.
			assumeTrue(false);
		}

		fs.createSymLink(link, "å");
		assertTrue(fs.exists(link));
		assertEquals("å", fs.readSymLink(link));
	}

	@Test
	void testExecutableAttributes() throws Exception {
		FS fs = FS.DETECTED.newInstance();
		// If this assumption fails the test is halted and ignored.
		assumeTrue(fs instanceof FS_POSIX);
		((FS_POSIX) fs).setUmask(0022);

		File f = new File(trash, "bla");
		assertTrue(f.createNewFile());
		assertFalse(fs.canExecute(f));

		Set<PosixFilePermission> permissions = readPermissions(f);
		assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE));
		assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE));
		assertFalse(permissions.contains(PosixFilePermission.OWNER_EXECUTE));

		fs.setExecute(f, true);

		permissions = readPermissions(f);
		assertTrue(permissions.contains(PosixFilePermission.OWNER_EXECUTE),
				"'owner' execute permission not set");
		assertTrue(permissions.contains(PosixFilePermission.GROUP_EXECUTE),
				"'group' execute permission not set");
		assertTrue(permissions.contains(PosixFilePermission.OTHERS_EXECUTE),
				"'others' execute permission not set");

		((FS_POSIX) fs).setUmask(0033);
		fs.setExecute(f, false);
		assertFalse(fs.canExecute(f));
		fs.setExecute(f, true);

		permissions = readPermissions(f);
		assertTrue(permissions.contains(PosixFilePermission.OWNER_EXECUTE),
				"'owner' execute permission not set");
		assertFalse(permissions.contains(PosixFilePermission.GROUP_EXECUTE),
				"'group' execute permission set");
		assertFalse(permissions.contains(PosixFilePermission.OTHERS_EXECUTE),
				"'others' execute permission set");
	}

	private Set<PosixFilePermission> readPermissions(File f) throws IOException {
		return Files
				.getFileAttributeView(f.toPath(), PosixFileAttributeView.class)
				.readAttributes().permissions();
	}

	@Test
	void testReadPipePosixCommandFailure() {
		assertThrows(CommandFailedException.class, () -> {
			FS fs = FS.DETECTED.newInstance();
			assumeTrue(fs instanceof FS_POSIX);

			FS.readPipe(fs.userHome(),
					new String[]{"/bin/sh", "-c", "exit 1"},
					SystemReader.getInstance().getDefaultCharset().name());
		});
	}

	@Test
	void testReadPipeCommandStartFailure() {
		assertThrows(CommandFailedException.class, () -> {
			FS fs = FS.DETECTED.newInstance();

			FS.readPipe(fs.userHome(),
					new String[]{"this-command-does-not-exist"},
					SystemReader.getInstance().getDefaultCharset().name());
		});
	}

	@Test
	void testFsTimestampResolution() throws Exception {
		DateTimeFormatter formatter = DateTimeFormatter
				.ofPattern("uuuu-MMM-dd HH:mm:ss.nnnnnnnnn", Locale.ENGLISH)
				.withZone(ZoneId.systemDefault());
		Path dir = Files.createTempDirectory("probe-filesystem");
		Duration resolution = FS.getFileStoreAttributes(dir)
				.getFsTimestampResolution();
		long resolutionNs = resolution.toNanos();
		assertTrue(resolutionNs > 0);
		for (int i = 0; i < 10; i++) {
			Path f = null;
			try {
				f = dir.resolve("testTimestampResolution" + i);
				Files.createFile(f);
				FileUtils.touch(f);
				FileTime t1 = Files.getLastModifiedTime(f);
				TimeUnit.NANOSECONDS.sleep(resolutionNs);
				FileUtils.touch(f);
				FileTime t2 = Files.getLastModifiedTime(f);
				assertTrue(t2.compareTo(t1) > 0, String.format(
						"expected t2=%s to be larger than t1=%s\nsince file timestamp resolution was measured to be %,d ns",
						formatter.format(t2.toInstant()),
						formatter.format(t1.toInstant()),
						Long.valueOf(resolutionNs)));
			} finally {
				if (f != null) {
					Files.delete(f);
				}
			}
		}
	}

	// bug 548682
	@Test
	void testRepoCacheRelativePathUnbornRepo() {
		assertFalse(RepositoryCache.FileKey
				.isGitRepository(new File("repo.git"), FS.DETECTED));
	}
}
