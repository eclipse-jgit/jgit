/*
 * Copyright (C) 2017 Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.ignore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that verify that the set of ignore files in a repository is the same in
 * JGit and in C-git.
 */
public class CGitIgnoreTest extends RepositoryTestCase {

	@Before
	public void initRepo() throws IOException {
		// These tests focus on .gitignore files inside the repository. Because
		// we run C-git, we must ensure that global or user exclude files cannot
		// influence the tests. So we set core.excludesFile to an empty file
		// inside the repository.
		File fakeUserGitignore = writeTrashFile(".fake_user_gitignore", "");
		StoredConfig config = db.getConfig();
		config.setString("core", null, "excludesFile",
				fakeUserGitignore.getAbsolutePath());
		// Disable case-insensitivity -- JGit doesn't handle that yet.
		config.setBoolean("core", null, "ignoreCase", false);
		config.save();
	}

	private void createFiles(String... paths) throws IOException {
		for (String path : paths) {
			writeTrashFile(path, "x");
		}
	}

	private String toString(TemporaryBuffer b) throws IOException {
		return RawParseUtils.decode(b.toByteArray());
	}

	private String[] cgitIgnored() throws Exception {
		FS fs = db.getFS();
		ProcessBuilder builder = fs.runInShell("git", new String[] { "ls-files",
				"--ignored", "--exclude-standard", "-o" });
		builder.directory(db.getWorkTree());
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		ExecutionResult result = fs.execute(builder,
				new ByteArrayInputStream(new byte[0]));
		String errorOut = toString(result.getStderr());
		assertEquals("External git failed", "exit 0\n",
				"exit " + result.getRc() + '\n' + errorOut);
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new BufferedInputStream(result.getStdout().openInputStream()),
				UTF_8))) {
			return r.lines().toArray(String[]::new);
		}
	}

	private String[] cgitUntracked() throws Exception {
		FS fs = db.getFS();
		ProcessBuilder builder = fs.runInShell("git",
				new String[] { "ls-files", "--exclude-standard", "-o" });
		builder.directory(db.getWorkTree());
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		ExecutionResult result = fs.execute(builder,
				new ByteArrayInputStream(new byte[0]));
		String errorOut = toString(result.getStderr());
		assertEquals("External git failed", "exit 0\n",
				"exit " + result.getRc() + '\n' + errorOut);
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new BufferedInputStream(result.getStdout().openInputStream()),
				UTF_8))) {
			return r.lines().toArray(String[]::new);
		}
	}

	private void jgitIgnoredAndUntracked(LinkedHashSet<String> ignored,
			LinkedHashSet<String> untracked) throws IOException {
		// Do a tree walk that does descend into ignored directories and return
		// a list of all ignored files
		try (TreeWalk walk = new TreeWalk(db)) {
			FileTreeIterator iter = new FileTreeIterator(db);
			iter.setWalkIgnoredDirectories(true);
			walk.addTree(iter);
			walk.setRecursive(true);
			while (walk.next()) {
				if (walk.getTree(WorkingTreeIterator.class).isEntryIgnored()) {
					ignored.add(walk.getPathString());
				} else {
					// tests of this class won't add any files to the index,
					// hence everything what is not ignored is untracked
					untracked.add(walk.getPathString());
				}
			}
		}
	}

	private void assertNoIgnoredVisited(Set<String> ignored) throws Exception {
		// Do a recursive tree walk with a NotIgnoredFilter and verify that none
		// of the files visited is in the ignored set
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			walk.setFilter(new NotIgnoredFilter(0));
			walk.setRecursive(true);
			while (walk.next()) {
				String path = walk.getPathString();
				assertFalse("File " + path + " is ignored, should not appear",
						ignored.contains(path));
			}
		}
	}

	private void assertSameAsCGit(String... notIgnored) throws Exception {
		LinkedHashSet<String> ignored = new LinkedHashSet<>();
		LinkedHashSet<String> untracked = new LinkedHashSet<>();
		jgitIgnoredAndUntracked(ignored, untracked);
		String[] cgit = cgitIgnored();
		String[] cgitUntracked = cgitUntracked();
		assertArrayEquals(cgit, ignored.toArray());
		assertArrayEquals(cgitUntracked, untracked.toArray());
		for (String notExcluded : notIgnored) {
			assertFalse("File " + notExcluded + " should not be ignored",
					ignored.contains(notExcluded));
		}
		assertNoIgnoredVisited(ignored);
	}

	@Test
	public void testSimpleIgnored() throws Exception {
		createFiles("a.txt", "a.tmp", "src/sub/a.txt", "src/a.tmp",
				"src/a.txt/b.tmp", "ignored/a.tmp", "ignored/not_ignored/a.tmp",
				"ignored/other/a.tmp");
		writeTrashFile(".gitignore",
				"*.txt\n" + "/ignored/*\n" + "!/ignored/not_ignored");
		assertSameAsCGit("ignored/not_ignored/a.tmp");
	}

	@Test
	public void testDirOnlyMatch() throws Exception {
		createFiles("a.txt", "src/foo/a.txt", "src/a.txt", "foo/a.txt");
		writeTrashFile(".gitignore", "foo/");
		assertSameAsCGit();
	}

	@Test
	public void testDirOnlyMatchDeep() throws Exception {
		createFiles("a.txt", "src/foo/a.txt", "src/a.txt", "foo/a.txt");
		writeTrashFile(".gitignore", "**/foo/");
		assertSameAsCGit();
	}

	@Test
	public void testStarMatchOnSlashNot() throws Exception {
		createFiles("sub/a.txt", "foo/sext", "foo/s.txt");
		writeTrashFile(".gitignore", "s*xt");
		assertSameAsCGit("sub/a.txt");
	}

	@Test
	public void testPrefixMatch() throws Exception {
		createFiles("src/new/foo.txt");
		writeTrashFile(".gitignore", "src/new");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursive() throws Exception {
		createFiles("src/new/foo.txt", "foo/src/new/foo.txt", "sub/src/new");
		writeTrashFile(".gitignore", "**/src/new/");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack() throws Exception {
		createFiles("src/new/foo.txt", "src/src/new/foo.txt");
		writeTrashFile(".gitignore", "**/src/new/");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack2() throws Exception {
		createFiles("src/new/foo.txt", "src/src/new/foo.txt");
		writeTrashFile(".gitignore", "**/**/src/new/");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack3() throws Exception {
		createFiles("x/a/a/b/foo.txt");
		writeTrashFile(".gitignore", "**/*/a/b/");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack4() throws Exception {
		createFiles("x/a/a/b/foo.txt", "x/y/z/b/a/b/foo.txt",
				"x/y/a/a/a/a/b/foo.txt", "x/y/a/a/a/a/b/a/b/foo.txt");
		writeTrashFile(".gitignore", "**/*/a/b bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack5() throws Exception {
		createFiles("x/a/a/b/foo.txt", "x/y/a/b/a/b/foo.txt");
		writeTrashFile(".gitignore", "**/*/**/a/b bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles1() throws Exception {
		createFiles("a", "dir/b", "dir/sub/c");
		writeTrashFile(".gitignore", "**/\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles2() throws Exception {
		createFiles("a", "dir/b", "dir/sub/c");
		writeTrashFile(".gitignore", "**/**/\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles3() throws Exception {
		createFiles("a", "x/b", "sub/x/c", "sub/x/d/e");
		writeTrashFile(".gitignore", "x/**/\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles4() throws Exception {
		createFiles("a", "dir/x", "dir/sub1/x", "dir/sub2/x/y");
		writeTrashFile(".gitignore", "**/x/\n");
		assertSameAsCGit();
	}

	@Test
	public void testUnescapedBracketsInGroup() throws Exception {
		createFiles("[", "]", "[]", "][", "[[]", "[]]", "[[]]");
		writeTrashFile(".gitignore", "[[]]\n");
		assertSameAsCGit();
	}

	@Test
	public void testEscapedFirstBracketInGroup() throws Exception {
		createFiles("[", "]", "[]", "][", "[[]", "[]]", "[[]]");
		writeTrashFile(".gitignore", "[\\[]]\n");
		assertSameAsCGit();
	}

	@Test
	public void testEscapedSecondBracketInGroup() throws Exception {
		createFiles("[", "]", "[]", "][", "[[]", "[]]", "[[]]");
		writeTrashFile(".gitignore", "[[\\]]\n");
		assertSameAsCGit();
	}

	@Test
	public void testEscapedBothBracketsInGroup() throws Exception {
		createFiles("[", "]", "[]", "][", "[[]", "[]]", "[[]]");
		writeTrashFile(".gitignore", "[\\[\\]]\n");
		assertSameAsCGit();
	}

	@Test
	public void testSimpleRootGitIgnoreGlobalNegation1() throws Exception {
		// see IgnoreNodeTest.testSimpleRootGitIgnoreGlobalNegation1
		createFiles("x1", "a/x2", "x3/y");
		writeTrashFile(".gitignore", "*\n!x*");
		assertSameAsCGit();
	}

	@Test
	public void testRepeatedNegationInDifferentFiles5() throws Exception {
		// see IgnoreNodeTest.testRepeatedNegationInDifferentFiles5
		createFiles("a/b/e/nothere.o");
		writeTrashFile(".gitignore", "e");
		writeTrashFile("a/.gitignore", "e");
		writeTrashFile("a/b/.gitignore", "!e");
		assertSameAsCGit();
	}

	@Test
	public void testRepeatedNegationInDifferentFilesWithWildmatcher1()
			throws Exception {
		createFiles("e", "x/e/f", "a/e/x1", "a/e/x2", "a/e/y", "a/e/sub/y");
		writeTrashFile(".gitignore", "a/e/**");
		writeTrashFile("a/.gitignore", "!e/x*");
		assertSameAsCGit();
	}

	@Test
	public void testRepeatedNegationInDifferentFilesWithWildmatcher2()
			throws Exception {
		createFiles("e", "dir/f", "dir/g/h", "a/dir/i", "a/dir/j/k",
				"a/b/dir/l", "a/b/dir/m/n", "a/b/dir/m/o/p", "a/q/dir/r",
				"a/q/dir/dir/s", "c/d/dir/x", "c/d/dir/y");
		writeTrashFile(".gitignore", "**/dir/*");
		writeTrashFile("a/.gitignore", "!dir/*");
		writeTrashFile("a/b/.gitignore", "!**/dir/*");
		writeTrashFile("c/.gitignore", "!d/dir/x");
		assertSameAsCGit();
	}

	@Test
	public void testNegationForSubDirectoryWithinIgnoredDirectoryHasNoEffect1()
			throws Exception {
		createFiles("e", "a/f", "a/b/g", "a/b/h/i");
		writeTrashFile(".gitignore", "a/b");
		writeTrashFile("a/.gitignore", "!b/*");
		assertSameAsCGit();
	}

	/*
	 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=407475
	 */
	@Test
	public void testNegationAllExceptJavaInSrcAndExceptChildDirInSrc()
			throws Exception {
		// see
		// IgnoreNodeTest.testNegationAllExceptJavaInSrcAndExceptChildDirInSrc
		createFiles("nothere.o", "src/keep.java", "src/nothere.o",
				"src/a/keep.java", "src/a/keep.o");
		writeTrashFile(".gitignore", "/*\n!/src/");
		writeTrashFile("src/.gitignore", "*\n!*.java\n!*/");
		assertSameAsCGit();
	}
}
