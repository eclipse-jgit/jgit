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
package org.eclipse.jgit.attributes;

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
import org.eclipse.jgit.lib.Constants;
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
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				input.toString().getBytes(Constants.CHARSET)));
		assertEquals("External git reported errors", "",
				toString(result.getStderr()));
		assertEquals("External git failed", 0, result.getRc());
		LinkedHashMap<String, Attributes> map = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new BufferedInputStream(result.getStdout().openInputStream()),
				Constants.CHARSET))) {
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
}
