/*
 * Copyright (C) 2010, Red Hat Inc.
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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.junit.Test;

/**
 * Tests attributes node behavior on the local filesystem.
 */
public class AttributesNodeTest extends RepositoryTestCase {

	private static final FileMode D = FileMode.TREE;
	private static final FileMode F = FileMode.REGULAR_FILE;

	private static Attribute EOL_CRLF = new Attribute("eol", "crlf");

	private static Attribute EOL_LF = new Attribute("eol", "lf");

	private static Attribute TEXT_SET = new Attribute("text", State.SET);

	private static Attribute TEXT_UNSET = new Attribute("text", State.UNSET);

	private static Attribute DELTA_UNSET = new Attribute("delta", State.UNSET);

	private static Attribute CUSTOM_INFO = new Attribute("custom", "info");

	private static Attribute CUSTOM_ROOT = new Attribute("custom", "root");

	private static Attribute CUSTOM_PARENT = new Attribute("custom", "parent");

	private static Attribute CUSTOM_CURRENT = new Attribute("custom", "current");

	private TreeWalk walk;

	@Test
	public void testRules() throws IOException {
		writeAttributesFile(".git/info/attributes", "windows* eol=crlf");

		writeAttributesFile(".gitattributes", "*.txt eol=lf");
		writeTrashFile("windows.file", "");
		writeTrashFile("windows.txt", "");
		writeTrashFile("readme.txt", "");

		writeAttributesFile("src/config/.gitattributes", "*.txt -delta");
		writeTrashFile("src/config/readme.txt", "");
		writeTrashFile("src/config/windows.file", "");
		writeTrashFile("src/config/windows.txt", "");

		beginWalk();
		assertEntry(F, ".gitattributes");
		assertEntry(F, "readme.txt", EOL_LF);

		assertEntry(D, "src");
		assertEntry(D, "src/config");
		assertEntry(F, "src/config/.gitattributes");
		assertEntry(F, "src/config/readme.txt", DELTA_UNSET, EOL_LF);
		assertEntry(F, "src/config/windows.file", EOL_CRLF);
		assertEntry(F, "src/config/windows.txt", DELTA_UNSET, EOL_CRLF);

		assertEntry(F, "windows.file", EOL_CRLF);
		assertEntry(F, "windows.txt", EOL_CRLF);
	}

	/**
	 * Checks that if there is no .gitattributes file in the repository everything
	 * still work fine.
	 *
	 * @throws IOException
	 */
	@Test
	public void testNoAttributes() throws IOException {
		writeTrashFile("l0.txt", "");
		writeTrashFile("level1/l1.txt", "");
		writeTrashFile("level1/level2/l2.txt", "");

		beginWalk();

		assertEntry(F, "l0.txt");

		assertEntry(D, "level1");
		assertEntry(F, "level1/l1.txt");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/l2.txt");
	}

	/**
	 * Checks that the empty .gitattribute files do not return incorrect value.
	 *
	 * @throws IOException
	 */
	@Test
	public void testEmptyGitAttributeFile() throws IOException {
		writeAttributesFile(".git/info/attributes", "");
		writeTrashFile("l0.txt", "");
		writeAttributesFile(".gitattributes", "");
		writeTrashFile("level1/l1.txt", "");
		writeTrashFile("level1/level2/l2.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.txt");

		assertEntry(D, "level1");
		assertEntry(F, "level1/l1.txt");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/l2.txt");
	}

	@Test
	public void testNoMatchingAttributes() throws IOException {
		writeAttributesFile(".git/info/attributes", "*.java delta");
		writeAttributesFile(".gitattributes", "*.java -delta");
		writeAttributesFile("levelA/.gitattributes", "*.java eol=lf");
		writeAttributesFile("levelB/.gitattributes", "*.txt eol=lf");

		writeTrashFile("levelA/lA.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "levelA");
		assertEntry(F, "levelA/.gitattributes");
		assertEntry(F, "levelA/lA.txt");

		assertEntry(D, "levelB");
		assertEntry(F, "levelB/.gitattributes");
	}

	/**
	 * Checks that $GIT_DIR/info/attributes file has the highest precedence.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceInfo() throws IOException {
		writeAttributesFile(".git/info/attributes", "*.txt custom=info");
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt custom=parent");
		writeAttributesFile("level1/level2/.gitattributes",
				"*.txt custom=current");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/file.txt", CUSTOM_INFO);
	}

	/**
	 * Checks that a subfolder ".gitattributes" file has precedence over its
	 * parent.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceCurrent() throws IOException {
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt custom=parent");
		writeAttributesFile("level1/level2/.gitattributes",
				"*.txt custom=current");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/file.txt", CUSTOM_CURRENT);
	}

	/**
	 * Checks that the parent ".gitattributes" file is used as fallback.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceParent() throws IOException {
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt custom=parent");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/file.txt", CUSTOM_PARENT);
	}

	/**
	 * Checks that the grand parent ".gitattributes" file is used as fallback.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceRoot() throws IOException {
		writeAttributesFile(".gitattributes", "*.txt custom=root");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/file.txt", CUSTOM_ROOT);
	}

	/**
	 * Checks the precedence on a hierarchy with multiple attributes.
	 *
	 * @throws IOException
	 */
	@Test
	public void testHierarchy() throws IOException {
		writeAttributesFile(".git/info/attributes", "*.global eol=crlf");
		writeAttributesFile(".gitattributes", "*.local eol=lf");
		writeAttributesFile("level1/.gitattributes", "*.local text");
		writeAttributesFile("level1/level2/.gitattributes", "*.local -text");

		writeTrashFile("l0.global", "");
		writeTrashFile("l0.local", "");

		writeTrashFile("level1/l1.global", "");
		writeTrashFile("level1/l1.local", "");

		writeTrashFile("level1/level2/l2.global", "");
		writeTrashFile("level1/level2/l2.local", "");

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.global", EOL_CRLF);
		assertEntry(F, "l0.local", EOL_LF);

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.global", EOL_CRLF);
		assertEntry(F, "level1/l1.local", EOL_LF, TEXT_SET);

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.global", EOL_CRLF);
		assertEntry(F, "level1/level2/l2.local", EOL_LF, TEXT_UNSET);

	}

	/**
	 * Checks that the list of attributes is an aggregation of all the
	 * attributes from the .gitattributes files hierarchy.
	 *
	 * @throws IOException
	 */
	@Test
	public void testAggregation() throws IOException {
		writeAttributesFile(".git/info/attributes", "*.txt eol=crlf");
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt text");
		writeAttributesFile("level1/level2/.gitattributes", "*.txt -delta");

		writeTrashFile("l0.txt", "");

		writeTrashFile("level1/l1.txt", "");

		writeTrashFile("level1/level2/l2.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.txt", EOL_CRLF, CUSTOM_ROOT);

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.txt", EOL_CRLF, CUSTOM_ROOT, TEXT_SET);

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.txt", EOL_CRLF, CUSTOM_ROOT, TEXT_SET,
				DELTA_UNSET);

	}

	/**
	 * Checks that the last entry in .gitattributes is used if 2 lines match the
	 * same attribute
	 *
	 * @throws IOException
	 */
	@Test
	public void testOverriding() throws IOException {
		writeAttributesFile(".git/info/attributes",//
				//
				"*.txt custom=current",//
				"*.txt custom=parent",//
				"*.txt custom=root",//
				"*.txt custom=info",
				//
				"*.txt delta",//
				"*.txt -delta",
				//
				"*.txt eol=lf",//
				"*.txt eol=crlf",
				//
				"*.txt text",//
				"*.txt -text");

		writeTrashFile("l0.txt", "");
		beginWalk();

		assertEntry(F, "l0.txt", TEXT_UNSET, EOL_CRLF, DELTA_UNSET, CUSTOM_INFO);
	}

	/**
	 * Checks that the last value of an attribute is used if in the same line an
	 * attribute is defined several time.
	 *
	 * @throws IOException
	 */
	@Test
	public void testOverriding2() throws IOException {
		writeAttributesFile(".git/info/attributes",
				"*.txt custom=current custom=parent custom=root custom=info",//
				"*.txt delta -delta",//
				"*.txt eol=lf eol=crlf",//
				"*.txt text -text");
		writeTrashFile("l0.txt", "");
		beginWalk();

		assertEntry(F, "l0.txt", TEXT_UNSET, EOL_CRLF, DELTA_UNSET, CUSTOM_INFO);
	}

	private void beginWalk() throws CorruptObjectException {
		walk = new TreeWalk(db);
		walk.addTree(new FileTreeIterator(db));
	}

	private void assertEntry(FileMode type, String pathName,
			Attribute... attributes) throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		WorkingTreeIterator itr = walk.getTree(0, WorkingTreeIterator.class);
		assertNotNull("has tree", itr);
		List<Attribute> entryAttributes = itr.getEntryAttributes();
		if (attributes != null && attributes.length > 0) {
			for (Attribute attribute : attributes) {
				assertThat(entryAttributes, hasItem(attribute));
			}
		} else {
			assertTrue(
					"The entry "
							+ pathName
							+ " should not have any attributes. Instead, the following attributes appliy to this file "
							+ entryAttributes.toString(),
					entryAttributes.isEmpty());
		}
		if (D.equals(type))
			walk.enterSubtree();
	}

	private void writeAttributesFile(String name, String... rules)
			throws IOException {
		StringBuilder data = new StringBuilder();
		for (String line : rules)
			data.append(line + "\n");
		writeTrashFile(name, data.toString());
	}
}
