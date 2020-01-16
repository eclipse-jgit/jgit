/*
 * Copyright (C) 2017 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that verify that the attributes of files in a repository are the same
 * in JGit and in C-git.
 */
public class CGitAttributesTest extends RepositoryTestCase {

	@Before
	public void initRepo() throws IOException {
		// Because we run C-git, we must ensure that global or user exclude
		// files cannot influence the tests. So we set core.excludesFile to an
		// empty file inside the repository.
		StoredConfig config = db.getConfig();
		File fakeUserGitignore = writeTrashFile(".fake_user_gitignore", "");
		config.setString("core", null, "excludesFile",
				fakeUserGitignore.getAbsolutePath());
		// Disable case-insensitivity -- JGit doesn't handle that yet.
		config.setBoolean("core", null, "ignoreCase", false);
		// And try to switch off the global attributes file, too.
		config.setString("core", null, "attributesFile",
				fakeUserGitignore.getAbsolutePath());
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

	private Attribute fromString(String key, String value) {
		if ("set".equals(value)) {
			return new Attribute(key, Attribute.State.SET);
		}
		if ("unset".equals(value)) {
			return new Attribute(key, Attribute.State.UNSET);
		}
		if ("unspecified".equals(value)) {
			return new Attribute(key, Attribute.State.UNSPECIFIED);
		}
		return new Attribute(key, value);
	}

	private LinkedHashMap<String, Attributes> cgitAttributes(
			Set<String> allFiles) throws Exception {
		FS fs = db.getFS();
		StringBuilder input = new StringBuilder();
		for (String filename : allFiles) {
			input.append(filename).append('\n');
		}
		ProcessBuilder builder = fs.runInShell("git",
				new String[] { "check-attr", "--stdin", "--all" });
		builder.directory(db.getWorkTree());
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				input.toString().getBytes(UTF_8)));
		String errorOut = toString(result.getStderr());
		assertEquals("External git failed", "exit 0\n",
				"exit " + result.getRc() + '\n' + errorOut);
		LinkedHashMap<String, Attributes> map = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new BufferedInputStream(result.getStdout().openInputStream()),
				UTF_8))) {
			r.lines().forEach(line -> {
				// Parse the line and add to result map
				int start = 0;
				int i = line.indexOf(':');
				String path = line.substring(0, i).trim();
				start = i + 1;
				i = line.indexOf(':', start);
				String key = line.substring(start, i).trim();
				String value = line.substring(i + 1).trim();
				Attribute attr = fromString(key, value);
				Attributes attrs = map.get(path);
				if (attrs == null) {
					attrs = new Attributes(attr);
					map.put(path, attrs);
				} else {
					attrs.put(attr);
				}
			});
		}
		return map;
	}

	private LinkedHashMap<String, Attributes> jgitAttributes()
			throws IOException {
		// Do a tree walk and return a list of all files and directories with
		// their attributes
		LinkedHashMap<String, Attributes> result = new LinkedHashMap<>();
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			walk.setFilter(new NotIgnoredFilter(0));
			while (walk.next()) {
				String path = walk.getPathString();
				if (walk.isSubtree() && !path.endsWith("/")) {
					// git check-attr expects directory paths to end with a
					// slash
					path += '/';
				}
				Attributes attrs = walk.getAttributes();
				if (attrs != null && !attrs.isEmpty()) {
					result.put(path, attrs);
				} else {
					result.put(path, null);
				}
				if (walk.isSubtree()) {
					walk.enterSubtree();
				}
			}
		}
		return result;
	}

	private void assertSameAsCGit() throws Exception {
		LinkedHashMap<String, Attributes> jgit = jgitAttributes();
		LinkedHashMap<String, Attributes> cgit = cgitAttributes(jgit.keySet());
		// remove all without attributes
		Iterator<Map.Entry<String, Attributes>> iterator = jgit.entrySet()
				.iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Attributes> entry = iterator.next();
			if (entry.getValue() == null) {
				iterator.remove();
			}
		}
		assertArrayEquals("JGit attributes differ from C git",
				cgit.entrySet().toArray(), jgit.entrySet().toArray());
	}

	@Test
	public void testBug508568() throws Exception {
		createFiles("foo.xml/bar.jar", "sub/foo.xml/bar.jar");
		writeTrashFile(".gitattributes", "*.xml xml\n" + "*.jar jar\n");
		assertSameAsCGit();
	}

	@Test
	public void testRelativePath() throws Exception {
		createFiles("sub/foo.txt");
		writeTrashFile("sub/.gitattributes", "sub/** sub\n" + "*.txt txt\n");
		assertSameAsCGit();
	}

	@Test
	public void testRelativePaths() throws Exception {
		createFiles("sub/foo.txt", "sub/sub/bar", "foo/sub/a.txt",
				"foo/sub/bar/a.tmp");
		writeTrashFile(".gitattributes", "sub/** sub\n" + "*.txt txt\n");
		assertSameAsCGit();
	}

	@Test
	public void testNestedMatchNot() throws Exception {
		createFiles("foo.xml/bar.jar", "foo.xml/bar.xml", "sub/b.jar",
				"sub/b.xml");
		writeTrashFile("sub/.gitattributes", "*.xml xml\n" + "*.jar jar\n");
		assertSameAsCGit();
	}

	@Test
	public void testNestedMatch() throws Exception {
		// This is an interesting test. At the time of this writing, the
		// gitignore documentation says: "In other words, foo/ will match a
		// directory foo AND PATHS UNDERNEATH IT, but will not match a regular
		// file or a symbolic link foo". (Emphasis added.) And gitattributes is
		// supposed to follow the same rules. But the documentation appears to
		// lie: C-git will *not* apply the attribute "xml" to *any* files in
		// any subfolder "foo" here. It will only apply the "jar" attribute
		// to the three *.jar files.
		//
		// The point is probably that ignores are handled top-down, and once a
		// directory "foo" is matched (here: on paths "foo" and "sub/foo" by
		// pattern "foo/"), the directory is excluded and the gitignore
		// documentation also says: "It is not possible to re-include a file if
		// a parent directory of that file is excluded." So once the pattern
		// "foo/" has matched, it appears as if everything beneath would also be
		// matched.
		//
		// But not so for gitattributes! The foo/ rule only matches the
		// directory itself, but not anything beneath.
		createFiles("foo/bar.jar", "foo/bar.xml", "sub/b.jar", "sub/b.xml",
				"sub/foo/b.jar");
		writeTrashFile(".gitattributes",
				"foo/ xml\n" + "sub/foo/ sub\n" + "*.jar jar\n");
		assertSameAsCGit();
	}

	@Test
	public void testNestedMatchWithWildcard() throws Exception {
		// See above.
		createFiles("foo/bar.jar", "foo/bar.xml", "sub/b.jar", "sub/b.xml",
				"sub/foo/b.jar");
		writeTrashFile(".gitattributes",
				"**/foo/ xml\n" + "*/foo/ sub\n" + "*.jar jar\n");
		assertSameAsCGit();
	}

	@Test
	public void testNestedMatchRecursive() throws Exception {
		createFiles("foo/bar.jar", "foo/bar.xml", "sub/b.jar", "sub/b.xml",
				"sub/foo/b.jar");
		writeTrashFile(".gitattributes", "foo/** xml\n" + "*.jar jar\n");
		assertSameAsCGit();
	}

	@Test
	public void testStarMatchOnSlashNot() throws Exception {
		createFiles("sub/a.txt", "foo/sext", "foo/s.txt");
		writeTrashFile(".gitattributes", "s*xt bar");
		assertSameAsCGit();
	}

	@Test
	public void testPrefixMatchNot() throws Exception {
		createFiles("src/new/foo.txt");
		writeTrashFile(".gitattributes", "src/new bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testComplexPathMatchNot() throws Exception {
		createFiles("src/new/foo.txt", "src/ndw");
		writeTrashFile(".gitattributes", "s[p-s]c/n[de]w bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testStarPathMatchNot() throws Exception {
		createFiles("src/new/foo.txt", "src/ndw");
		writeTrashFile(".gitattributes", "src/* bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubSimple() throws Exception {
		createFiles("src/new/foo.txt", "foo/src/new/foo.txt", "sub/src/new");
		writeTrashFile(".gitattributes", "src/new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursive() throws Exception {
		createFiles("src/new/foo.txt", "foo/src/new/foo.txt", "sub/src/new");
		writeTrashFile(".gitattributes", "**/src/new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack() throws Exception {
		createFiles("src/new/foo.txt", "src/src/new/foo.txt");
		writeTrashFile(".gitattributes", "**/src/new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack2() throws Exception {
		createFiles("src/new/foo.txt", "src/src/new/foo.txt");
		writeTrashFile(".gitattributes", "**/**/src/new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack3() throws Exception {
		createFiles("src/new/src/new/foo.txt",
				"foo/src/new/bar/src/new/foo.txt");
		writeTrashFile(".gitattributes", "**/src/new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack4() throws Exception {
		createFiles("src/src/src/new/foo.txt",
				"foo/src/src/bar/src/new/foo.txt");
		writeTrashFile(".gitattributes", "**/src/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack5() throws Exception {
		createFiles("x/a/a/b/foo.txt", "x/y/z/b/a/b/foo.txt",
				"x/y/a/a/a/a/b/foo.txt", "x/y/a/a/a/a/b/a/b/foo.txt");
		writeTrashFile(".gitattributes", "**/*/a/b bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack6() throws Exception {
		createFiles("x/a/a/b/foo.txt", "x/y/a/b/a/b/foo.txt");
		writeTrashFile(".gitattributes", "**/*/**/a/b bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles1() throws Exception {
		createFiles("a", "dir/b", "dir/sub/c");
		writeTrashFile(".gitattributes", "**/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles2() throws Exception {
		createFiles("a", "dir/b", "dir/sub/c");
		writeTrashFile(".gitattributes", "**/**/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles3() throws Exception {
		createFiles("a", "x/b", "sub/x/c", "sub/x/d/e");
		writeTrashFile(".gitattributes", "x/**/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryWildmatchDoesNotMatchFiles4() throws Exception {
		createFiles("a", "dir/x", "dir/sub1/x", "dir/sub2/x/y");
		writeTrashFile(".gitattributes", "x/**/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatchSubComplex() throws Exception {
		createFiles("src/new/foo.txt", "foo/src/new/foo.txt", "sub/src/new");
		writeTrashFile(".gitattributes", "s[rs]c/n*/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testDirectoryMatch() throws Exception {
		createFiles("src/new/foo.txt", "foo/src/new/foo.txt", "sub/src/new");
		writeTrashFile(".gitattributes", "new/ bar\n");
		assertSameAsCGit();
	}

	@Test
	public void testBracketsInGroup() throws Exception {
		createFiles("[", "]", "[]", "][", "[[]", "[]]", "[[]]");
		writeTrashFile(".gitattributes", "[[]] bar1\n" + "[\\[]] bar2\n"
				+ "[[\\]] bar3\n" + "[\\[\\]] bar4\n");
		assertSameAsCGit();
	}
}
