/*
 * Copyright (C) 2015 Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.indexdiff;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

/**
 * MacOS-only test for dealing with symlinks in IndexDiff. Unzips a bare test
 * repository prepared with git 2.2.1 for MacOS on MacOS 10.7.5 containing some
 * symlinks. Then clones that repository using cgit, examines a symlink, changes
 * it, examines it again to verify it's been changed, and finally calculates an
 * IndexDiff.
 */
public class IndexDiffWithSymlinkTest extends LocalDiskRepositoryTestCase {

	private static final String FILEREPO = "filerepo";

	private static final String TESTFOLDER = "testfolder";

	private static final String TESTTARGET = "äéü.txt";

	private static final String TESTLINK = "aeu.txt";

	private static final byte[] NFC = // "äéü.txt" in NFC
	{ -61, -92, -61, -87, -61, -68, 46, 116, 120, 116 };

	private static final byte[] NFD = // "äéü.txt" in NFD
	{ 97, -52, -120, 101, -52, -127, 117, -52, -120, 46, 116, 120, 116 };

	private File testRepoDir;

	@Override
	@Before
	public void setUp() throws Exception {
		assumeTrue(SystemReader.getInstance().isMacOS()
				&& FS.DETECTED.supportsSymlinks());
		super.setUp();
		File testDir = createTempDirectory("IndeDiffWithSymlink");
		InputStream in = this.getClass().getClassLoader().getResourceAsStream(
				this.getClass().getPackage().getName().replace('.', '/') + '/'
						+ FILEREPO + Constants.DOT_GIT_EXT + ".zip");
		assertNotNull("Test repo zip not found", in);
		try {
			unzip(in, testDir);
		} finally {
			in.close();
		}
		// Let's not clone with JGit just to be sure that JGit link creation
		// does not taint this test.
		cloneWithCGit(testDir,
				new File(testDir, FILEREPO + Constants.DOT_GIT_EXT));
		testRepoDir = new File(new File(testDir, FILEREPO), Constants.DOT_GIT);
	}

	private void unzip(InputStream stream, File directory) throws IOException {
		try (InputStream buffered = new BufferedInputStream(stream);
				ZipInputStream zip = new ZipInputStream(buffered)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				File file = new File(directory, entry.getName());
				if (entry.isDirectory()) {
					file.mkdirs();
				} else {
					file.getParentFile().mkdirs();
					try (OutputStream out = new FileOutputStream(file)) {
						copy(zip, out);
					}
				}
			}
		}
	}

	private void copy(InputStream from, OutputStream to) throws IOException {
		byte[] buffer = new byte[4096];
		int n;
		while ((n = from.read(buffer)) > 0) {
			to.write(buffer, 0, n);
		}
	}

	private void cloneWithCGit(File directory, File repo) throws Exception {
		String[] cmd = { "git", "clone", repo.getAbsolutePath() };
		Process process = Runtime.getRuntime().exec(cmd, null, directory);
		readStream(process.getInputStream());
		String stdErr = readStream(process.getErrorStream());
		process.waitFor();
		int exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new IOException("cgit returned " + exitCode + '\n' + stdErr);
		}
	}

	private String readStream(InputStream stream) throws IOException {
		try (BufferedReader stdOut = new BufferedReader(
				new InputStreamReader(stream))) {
			StringBuilder out = new StringBuilder();
			String line;
			while ((line = stdOut.readLine()) != null) {
				out.append(line);
			}
			return out.toString();
		}
	}

	@Test
	public void testSymlinkWithEncodingDifference() throws Exception {
		try (Repository testRepo = FileRepositoryBuilder.create(testRepoDir)) {
			File workingTree = testRepo.getWorkTree();
			File symLink = new File(new File(workingTree, TESTFOLDER),
					TESTLINK);
			// Read the symlink as it was created by the clone through cgit
			Path linkTarget = Files.readSymbolicLink(symLink.toPath());
			assertEquals("Unexpected link target", TESTTARGET,
					linkTarget.toString());
			byte[] raw = rawPath(linkTarget);
			assertNotNull("Cannot get raw path", raw);
			assertArrayEquals("Expected an NFC link target", NFC, raw);
			// Now re-create that symlink through Java
			assertTrue("Could not delete symlink", symLink.delete());
			Files.createSymbolicLink(symLink.toPath(), Paths.get(TESTTARGET));
			// Read it again
			linkTarget = Files.readSymbolicLink(symLink.toPath());
			assertEquals("Unexpected link target", TESTTARGET,
					linkTarget.toString());
			raw = rawPath(linkTarget);
			assertNotNull("Cannot get raw path", raw);
			assertArrayEquals("Expected an NFD link target", NFD, raw);
			// Do the indexdiff
			WorkingTreeIterator iterator = new FileTreeIterator(testRepo);
			IndexDiff diff = new IndexDiff(testRepo, Constants.HEAD, iterator);
			diff.setFilter(PathFilterGroup.createFromStrings(
					Collections.singleton(TESTFOLDER + '/' + TESTLINK)));
			diff.diff();
			// We're testing that this does NOT throw "EOFException: Short read
			// of block." The diff will not report any modified files -- the
			// link modification is not visible to JGit, which always works with
			// the Java internal NFC encoding. CGit does report the link as an
			// unstaged modification here, though.
		}
	}

	private byte[] rawPath(Path p) {
		try {
			Method method = p.getClass().getDeclaredMethod("asByteArray");
			if (method != null) {
				method.setAccessible(true);
				return (byte[]) method.invoke(p);
			}
		} catch (NoSuchMethodException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
			// Ignore and fall through.
		}
		return null;
	}
}
