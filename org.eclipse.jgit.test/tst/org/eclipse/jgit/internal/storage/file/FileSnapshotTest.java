/*
 * Copyright (C) 2010, Robin Rosenberg
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class FileSnapshotTest {

	private Path trash;

	@Before
	public void setUp() throws Exception {
		trash = Files.createTempDirectory("tmp_");
	}

	@Before
	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash.toFile(),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	private static void waitNextTick(Path f) throws IOException {
		Instant initialLastModified = FS.DETECTED.lastModifiedInstant(f);
		do {
			FS.DETECTED.setLastModified(f, Instant.now());
		} while (FS.DETECTED.lastModifiedInstant(f)
				.equals(initialLastModified));
	}

	/**
	 * Change data and time stamp.
	 *
	 * @throws Exception
	 */
	@Test
	public void testActuallyIsModifiedTrivial() throws Exception {
		Path f1 = createFile("simple");
		waitNextTick(f1);
		FileSnapshot save = FileSnapshot.save(f1.toFile());
		append(f1, (byte) 'x');
		waitNextTick(f1);
		assertTrue(save.isModified(f1.toFile()));
	}

	/**
	 * Create a file, but don't wait long enough for the difference between file
	 * system clock and system clock to be significant. Assume the file may have
	 * been modified. It may have been, but the clock alone cannot determine
	 * this
	 *
	 * @throws Exception
	 */
	@Test
	public void testNewFileWithWait() throws Exception {
		Path f1 = createFile("newfile");
		waitNextTick(f1);
		FileSnapshot save = FileSnapshot.save(f1.toFile());
		Thread.sleep(1500);
		assertTrue(save.isModified(f1.toFile()));
	}

	/**
	 * Same as {@link #testNewFileWithWait()} but do not wait at all
	 *
	 * @throws Exception
	 */
	@Test
	public void testNewFileNoWait() throws Exception {
		Path f1 = createFile("newfile");
		FileSnapshot save = FileSnapshot.save(f1.toFile());
		assertTrue(save.isModified(f1.toFile()));
	}

	/**
	 * Simulate packfile replacement in same file which may occur if set of
	 * objects in the pack is the same but pack config was different. On Posix
	 * filesystems this should change the inode (filekey in java.nio
	 * terminology).
	 *
	 * @throws Exception
	 */
	@Test
	public void testSimulatePackfileReplacement() throws Exception {
		Assume.assumeFalse(SystemReader.getInstance().isWindows());
		Path f1 = createFile("file"); // inode y
		Path f2 = createFile("fool"); // Guarantees new inode x
		// wait on f2 since this method resets lastModified of the file
		// and leaves lastModified of f1 untouched
		waitNextTick(f2);
		waitNextTick(f2);
		FileTime timestamp = Files.getLastModifiedTime(f1);
		FileSnapshot save = FileSnapshot.save(f1.toFile());
		Files.move(f2, f1, // Now "file" is inode x
				StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		Files.setLastModifiedTime(f1, timestamp);
		assertTrue(save.isModified(f1.toFile()));
		assertTrue("unexpected change of fileKey", save.wasFileKeyChanged());
		assertFalse("unexpected size change", save.wasSizeChanged());
		assertFalse("unexpected lastModified change",
				save.wasLastModifiedChanged());
		assertFalse("lastModified was unexpectedly racily clean",
				save.wasLastModifiedRacilyClean());
	}

	/**
	 * Append a character to a file to change its size and set original
	 * lastModified
	 *
	 * @throws Exception
	 */
	@Test
	public void testFileSizeChanged() throws Exception {
		Path f = createFile("file");
		FileTime timestamp = Files.getLastModifiedTime(f);
		FileSnapshot save = FileSnapshot.save(f.toFile());
		append(f, (byte) 'x');
		Files.setLastModifiedTime(f, timestamp);
		assertTrue(save.isModified(f.toFile()));
		assertTrue(save.wasSizeChanged());
	}

	@Test
	public void fileSnapshotEquals() throws Exception {
		// 0 sized FileSnapshot.
		FileSnapshot fs1 = FileSnapshot.MISSING_FILE;
		// UNKNOWN_SIZE FileSnapshot.
		FileSnapshot fs2 = FileSnapshot.save(fs1.lastModifiedInstant());

		assertTrue(fs1.equals(fs2));
		assertTrue(fs2.equals(fs1));
	}

	private Path createFile(String string) throws IOException {
		Files.createDirectories(trash);
		return Files.createTempFile(trash, string, "tdat");
	}

	private static void append(Path f, byte b) throws IOException {
		try (OutputStream os = Files.newOutputStream(f,
				StandardOpenOption.APPEND)) {
			os.write(b);
		}
	}

}
