/*
 * Copyright (C) 2015, 2017 Ivan Motsch <ivan.motsch@bsiag.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

/**
 * Tests {@link AttributesHandler}
 */
public class AttributesHandlerTest extends RepositoryTestCase {
	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	@Test
	public void testExpandNonMacro1() throws Exception {
		setupRepo(null, null, null, "*.txt text");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("text"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandNonMacro2() throws Exception {
		setupRepo(null, null, null, "*.txt -text");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("-text"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandNonMacro3() throws Exception {
		setupRepo(null, null, null, "*.txt !text");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs(""));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandNonMacro4() throws Exception {
		setupRepo(null, null, null, "*.txt text=auto");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("text=auto"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandBuiltInMacro1() throws Exception {
		setupRepo(null, null, null, "*.txt binary");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt",
					attrs("binary -diff -merge -text"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandBuiltInMacro2() throws Exception {
		setupRepo(null, null, null, "*.txt -binary");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt",
					attrs("-binary diff merge text"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testExpandBuiltInMacro3() throws Exception {
		setupRepo(null, null, null, "*.txt !binary");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs(""));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testCustomGlobalMacro1() throws Exception {
		setupRepo(
				"[attr]foo a -b !c d=e", null, null, "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo a -b d=e"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testCustomGlobalMacro2() throws Exception {
		setupRepo("[attr]foo a -b !c d=e", null, null, "*.txt -foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("-foo -a b d=e"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testCustomGlobalMacro3() throws Exception {
		setupRepo("[attr]foo a -b !c d=e", null, null, "*.txt !foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs(""));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testCustomGlobalMacro4() throws Exception {
		setupRepo("[attr]foo a -b !c d=e", null, null, "*.txt foo=bar");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo=bar a -b d=bar"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testInfoOverridesGlobal() throws Exception {
		setupRepo("[attr]foo bar1",
				"[attr]foo bar2", null, "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo bar2"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testWorkDirRootOverridesGlobal() throws Exception {
		setupRepo("[attr]foo bar1",
				null,
				"[attr]foo bar3", "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo bar3"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testInfoOverridesWorkDirRoot() throws Exception {
		setupRepo("[attr]foo bar1",
				"[attr]foo bar2", "[attr]foo bar3", "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo bar2"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testRecursiveMacro() throws Exception {
		setupRepo(
				"[attr]foo x bar -foo",
				null, null, "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo x bar"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testCyclicMacros() throws Exception {
		setupRepo(
				"[attr]foo x -bar\n[attr]bar y -foo", null, null, "*.txt foo");

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/.gitattributes");
			assertIteration(walk, F, "sub/a.txt", attrs("foo x -bar -y"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testRelativePaths() throws Exception {
		setupRepo("sub/ global", "sub/** init",
				"sub/** top_sub\n*.txt top",
				"sub/** subsub\nsub/ subsub2\n*.txt foo");
		// The last sub/** is in sub/.gitattributes. It must not
		// apply to any of the files here. It would match for a
		// further subdirectory sub/sub. The sub/ rules must match
		// only for directories.
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub", attrs("global"));
			assertIteration(walk, F, "sub/.gitattributes",
					attrs("init top_sub"));
			assertIteration(walk, F, "sub/a.txt",
					attrs("init foo top top_sub"));
			assertFalse("Not all files tested", walk.next());
		}
		// All right, let's see that they *do* apply in sub/sub:
		writeTrashFile("sub/sub/b.txt", "b");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub", attrs("global"));
			assertIteration(walk, F, "sub/.gitattributes",
					attrs("init top_sub"));
			assertIteration(walk, F, "sub/a.txt",
					attrs("init foo top top_sub"));
			assertIteration(walk, D, "sub/sub",
					attrs("init subsub2 top_sub global"));
			assertIteration(walk, F, "sub/sub/b.txt",
					attrs("init foo subsub top top_sub"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testNestedMatchNot() throws Exception {
		setupRepo(null, null, "*.xml xml\n*.jar jar", null);
		writeTrashFile("foo.xml/bar.jar", "b");
		writeTrashFile("foo.xml/bar.xml", "bx");
		writeTrashFile("sub/b.jar", "bj");
		writeTrashFile("sub/b.xml", "bx");
		// On foo.xml/bar.jar we must not have 'xml'
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo.xml", attrs("xml"));
			assertIteration(walk, F, "foo.xml/bar.jar", attrs("jar"));
			assertIteration(walk, F, "foo.xml/bar.xml", attrs("xml"));
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, F, "sub/b.jar", attrs("jar"));
			assertIteration(walk, F, "sub/b.xml", attrs("xml"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testNestedMatch() throws Exception {
		// See also CGitAttributeTest.testNestedMatch()
		setupRepo(null, null, "foo/ xml\nsub/foo/ sub\n*.jar jar", null);
		writeTrashFile("foo/bar.jar", "b");
		writeTrashFile("foo/bar.xml", "bx");
		writeTrashFile("sub/b.jar", "bj");
		writeTrashFile("sub/b.xml", "bx");
		writeTrashFile("sub/foo/b.jar", "bf");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo", attrs("xml"));
			assertIteration(walk, F, "foo/bar.jar", attrs("jar"));
			assertIteration(walk, F, "foo/bar.xml");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, F, "sub/b.jar", attrs("jar"));
			assertIteration(walk, F, "sub/b.xml");
			assertIteration(walk, D, "sub/foo", attrs("sub xml"));
			assertIteration(walk, F, "sub/foo/b.jar", attrs("jar"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testNestedMatchRecursive() throws Exception {
		setupRepo(null, null, "foo/** xml\n*.jar jar", null);
		writeTrashFile("foo/bar.jar", "b");
		writeTrashFile("foo/bar.xml", "bx");
		writeTrashFile("sub/b.jar", "bj");
		writeTrashFile("sub/b.xml", "bx");
		writeTrashFile("sub/foo/b.jar", "bf");
		// On foo.xml/bar.jar we must not have 'xml'
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, F, "foo/bar.jar", attrs("jar xml"));
			assertIteration(walk, F, "foo/bar.xml", attrs("xml"));
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, F, "sub/b.jar", attrs("jar"));
			assertIteration(walk, F, "sub/b.xml");
			assertIteration(walk, D, "sub/foo");
			assertIteration(walk, F, "sub/foo/b.jar", attrs("jar"));
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testStarMatchOnSlashNot() throws Exception {
		setupRepo(null, null, "s*xt bar", null);
		writeTrashFile("sub/a.txt", "1");
		writeTrashFile("foo/sext", "2");
		writeTrashFile("foo/s.txt", "3");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, F, "foo/s.txt", attrs("bar"));
			assertIteration(walk, F, "foo/sext", attrs("bar"));
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testPrefixMatchNot() throws Exception {
		setupRepo(null, null, "sub/new bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testComplexPathMatch() throws Exception {
		setupRepo(null, null, "s[t-v]b/n[de]w bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("sub/ndw", "2");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, F, "sub/ndw", attrs("bar"));
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testStarPathMatch() throws Exception {
		setupRepo(null, null, "sub/new/* bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("sub/new/lower/foo.txt", "2");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new");
			assertIteration(walk, F, "sub/new/foo.txt", attrs("bar"));
			assertIteration(walk, D, "sub/new/lower", attrs("bar"));
			assertIteration(walk, F, "sub/new/lower/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatchSubSimple() throws Exception {
		setupRepo(null, null, "sub/new/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		writeTrashFile("sub/sub/new/foo.txt", "3");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new");
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertIteration(walk, D, "sub/sub");
			assertIteration(walk, D, "sub/sub/new");
			assertIteration(walk, F, "sub/sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatchSubRecursive() throws Exception {
		setupRepo(null, null, "**/sub/new/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new", attrs("bar"));
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack() throws Exception {
		setupRepo(null, null, "**/sub/new/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		writeTrashFile("sub/sub/new/foo.txt", "3");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new", attrs("bar"));
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertIteration(walk, D, "sub/sub");
			assertIteration(walk, D, "sub/sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatchSubRecursiveBacktrack2() throws Exception {
		setupRepo(null, null, "**/**/sub/new/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		writeTrashFile("sub/sub/new/foo.txt", "3");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new", attrs("bar"));
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertIteration(walk, D, "sub/sub");
			assertIteration(walk, D, "sub/sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatchSubComplex() throws Exception {
		setupRepo(null, null, "s[uv]b/n*/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new");
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testDirectoryMatch() throws Exception {
		setupRepo(null, null, "new/ bar", null);
		writeTrashFile("sub/new/foo.txt", "1");
		writeTrashFile("foo/sub/new/foo.txt", "2");
		writeTrashFile("foo/new", "3");
		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, D, "foo");
			assertIteration(walk, F, "foo/new");
			assertIteration(walk, D, "foo/sub");
			assertIteration(walk, D, "foo/sub/new", attrs("bar"));
			assertIteration(walk, F, "foo/sub/new/foo.txt");
			assertIteration(walk, D, "sub");
			assertIteration(walk, F, "sub/a.txt");
			assertIteration(walk, D, "sub/new", attrs("bar"));
			assertIteration(walk, F, "sub/new/foo.txt");
			assertFalse("Not all files tested", walk.next());
		}
	}

	private static Collection<Attribute> attrs(String s) {
		return new AttributesRule("*", s).getAttributes();
	}

	private void assertIteration(TreeWalk walk, FileMode type, String pathName)
			throws IOException {
		assertIteration(walk, type, pathName,
				Collections.<Attribute> emptyList());
	}

	private void assertIteration(TreeWalk walk, FileMode type, String pathName,
			Collection<Attribute> expectedAttrs) throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		if (expectedAttrs != null) {
			assertEquals(new ArrayList<>(expectedAttrs),
					new ArrayList<>(walk.getAttributes().getAll()));
		}

		if (D.equals(type))
			walk.enterSubtree();
	}

	/**
	 * @param globalAttributesContent
	 * @param infoAttributesContent
	 * @param rootAttributesContent
	 * @param subDirAttributesContent
	 * @throws Exception
	 *             Setup a repo with .gitattributes files and a test file
	 *             sub/a.txt
	 */
	private void setupRepo(
			String globalAttributesContent,
			String infoAttributesContent, String rootAttributesContent, String subDirAttributesContent)
					throws Exception {
		FileBasedConfig config = db.getConfig();
		if (globalAttributesContent != null) {
			File f = new File(db.getDirectory(), "global/attributes");
			write(f, globalAttributesContent);
			config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE,
					f.getAbsolutePath());

		}
		if (infoAttributesContent != null) {
			File f = new File(db.getDirectory(), Constants.INFO_ATTRIBUTES);
			write(f, infoAttributesContent);
		}
		config.save();

		if (rootAttributesContent != null) {
			writeAttributesFile(Constants.DOT_GIT_ATTRIBUTES,
					rootAttributesContent);
		}

		if (subDirAttributesContent != null) {
			writeAttributesFile("sub/" + Constants.DOT_GIT_ATTRIBUTES,
					subDirAttributesContent);
		}

		writeTrashFile("sub/a.txt", "a");
	}

	private void writeAttributesFile(String name, String... rules)
			throws IOException {
		StringBuilder data = new StringBuilder();
		for (String line : rules)
			data.append(line + "\n");
		writeTrashFile(name, data.toString());
	}

	private TreeWalk beginWalk() {
		TreeWalk newWalk = new TreeWalk(db);
		newWalk.addTree(new FileTreeIterator(db));
		return newWalk;
	}
}
