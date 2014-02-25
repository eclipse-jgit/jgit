/*******************************************************************************
 * Copyright (C) 2014, Obeo
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
 *******************************************************************************/
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PathMatcherTest {
	protected File temporaryFolder;

	protected File[] testFiles;

	@Before
	public void setUp() throws IOException {
		temporaryFolder = createTempDirectory();

		// Create a few test files and folders
		testFiles = new File[8];
		testFiles[0] = createFile(temporaryFolder, "abc");
		testFiles[1] = createFile(temporaryFolder, "test/abc");
		testFiles[2] = createFile(temporaryFolder,
				"test/long/winded/folder/abc");
		testFiles[3] = createFile(temporaryFolder, "test/exec");
		testFiles[4] = createFile(temporaryFolder, "test/executive");
		testFiles[5] = createFile(temporaryFolder, "deaf");
		testFiles[6] = createFile(temporaryFolder, "test/def");
		testFiles[7] = createFile(temporaryFolder, "test/folder/def");
	}

	private static File createTempDirectory() throws IOException {
		// No Files.createTempDirectory(...) before Java 7, work around it.
		File temporaryPath = File.createTempFile("JGitPathMatcherTest_",
				Long.toString(System.currentTimeMillis()));
		temporaryPath.delete();
		File tempFolder = new File(temporaryPath.getPath() + "_Folder");
		tempFolder.mkdir();
		return tempFolder;
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.delete(temporaryFolder, FileUtils.RECURSIVE);
	}

	@Test
	public void testMatchAcrossFolders() {
		final String globAll = "**";
		final String globAllEndInC = "**c";
		final String globAllEndInCUnderFolderTest = "test/**/*c";

		final PathMatcher matcherAll = getPathMatcher(globAll);
		final PathMatcher matcherAllEndInC = getPathMatcher(globAllEndInC);
		final PathMatcher matcherAllEndInCUnderFolderTest = getPathMatcher(globAllEndInCUnderFolderTest);

		final Set<File> all = getAllMatchesIn(temporaryFolder, matcherAll);

		assertEquals(8, all.size());

		final Set<File> endingInC = getAllMatchesIn(temporaryFolder,
				matcherAllEndInC);

		assertEquals(4, endingInC.size());
		assertTrue(endingInC.contains(testFiles[0]));
		assertTrue(endingInC.contains(testFiles[1]));
		assertTrue(endingInC.contains(testFiles[2]));
		assertTrue(endingInC.contains(testFiles[3]));

		final Set<File> endingInCUnderFolderTest = getAllMatchesIn(
				temporaryFolder, matcherAllEndInCUnderFolderTest);
		assertEquals(1, endingInCUnderFolderTest.size());
		assertTrue(endingInCUnderFolderTest.contains(testFiles[2]));
	}

	@Test
	public void testMatchStarWildcard() {
		final String globEndInC = "*c";
		final String globContainsA = "*a*";

		final PathMatcher matcherEndInC = getPathMatcher(globEndInC);
		final PathMatcher matcherContainsA = getPathMatcher(globContainsA);

		final Set<File> endInC = getAllMatchesIn(temporaryFolder, matcherEndInC);

		assertEquals(1, endInC.size());
		assertTrue(endInC.contains(testFiles[0]));

		final Set<File> containsA = getAllMatchesIn(temporaryFolder,
				matcherContainsA);

		assertEquals(2, containsA.size());
		assertTrue(containsA.contains(testFiles[0]));
		assertTrue(containsA.contains(testFiles[5]));
	}

	@Test
	public void testMatchUnaryWildcard() {
		final String glob1 = "a?c";
		final String glob2 = "de??";

		final PathMatcher matcher1 = getPathMatcher(glob1);
		final PathMatcher matcher2 = getPathMatcher(glob2);

		final Set<File> matched1 = getAllMatchesIn(temporaryFolder, matcher1);

		assertEquals(1, matched1.size());
		assertTrue(matched1.contains(testFiles[0]));

		final Set<File> matched2 = getAllMatchesIn(temporaryFolder, matcher2);

		assertEquals(1, matched2.size());
		assertTrue(matched2.contains(testFiles[5]));
	}

	@Test
	public void testMatchMixed() {
		final String glob1 = "t??t/**c*";
		final String glob2 = "**/???";

		final PathMatcher matcher1 = getPathMatcher(glob1);
		final PathMatcher matcher2 = getPathMatcher(glob2);

		// Should match all "files" under the "test" sub-folder which name
		// contains "c"
		final Set<File> matched1 = getAllMatchesIn(temporaryFolder, matcher1);

		assertEquals(4, matched1.size());
		assertTrue(matched1.contains(testFiles[1]));
		assertTrue(matched1.contains(testFiles[2]));
		assertTrue(matched1.contains(testFiles[3]));
		assertTrue(matched1.contains(testFiles[4]));

		// Should match all files which name is three characters long, except
		// those in the current folder
		final Set<File> matched2 = getAllMatchesIn(temporaryFolder, matcher2);

		assertEquals(4, matched2.size());
		assertTrue(matched2.contains(testFiles[1]));
		assertTrue(matched2.contains(testFiles[2]));
		assertTrue(matched2.contains(testFiles[6]));
		assertTrue(matched2.contains(testFiles[7]));
	}

	protected PathMatcher getPathMatcher(String glob) {
		return new PathMatcher_Java5(glob);
	}

	/**
	 * This will try and match all paths under the given folder with the given
	 * matcher.
	 *
	 * @param matchRoot
	 * @param matcher
	 * @return The Set of paths under or at the level of <code>file</code>
	 *         matching this particular matcher.
	 */
	protected static Set<File> getAllMatchesIn(File matchRoot,
			PathMatcher matcher) {
		final Set<File> matches = new LinkedHashSet<File>();
		for (File child : matchRoot.listFiles())
			matches.addAll(recursiveMatch(matchRoot, child, matcher));
		return matches;
	}

	private static Set<File> recursiveMatch(File matchRoot, File file,
			PathMatcher matcher) {
		final Set<File> matches = new LinkedHashSet<File>();
		if (file.isDirectory()) {
			for (File child : file.listFiles())
				matches.addAll(recursiveMatch(matchRoot, child, matcher));
		} else if (matcher.matches(FileUtils.relativize(matchRoot.getPath(),
				file.getPath())))
			matches.add(file);
		return matches;
	}

	private static File createFile(File folder, String path) throws IOException {
		final File file = new File(folder, path);
		final File parent = file.getParentFile();
		if (!parent.exists() && !parent.mkdirs()) {
			throw new IOException("Couldn't create dir: " + parent);
		}
		file.createNewFile();
		return file;
	}

}
