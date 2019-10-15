/*
 * Copyright (C) 2014, Obeo.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the attributes are correctly computed in a {@link TreeWalk}.
 *
 * @see TreeWalk#getAttributes()
 */
public class TreeWalkAttributeTest extends RepositoryTestCase {

	private static final FileMode M = FileMode.MISSING;

	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	private static Attribute EOL_CRLF = new Attribute("eol", "crlf");

	private static Attribute EOL_LF = new Attribute("eol", "lf");

	private static Attribute TEXT_SET = new Attribute("text", State.SET);

	private static Attribute TEXT_UNSET = new Attribute("text", State.UNSET);

	private static Attribute DELTA_UNSET = new Attribute("delta", State.UNSET);

	private static Attribute DELTA_SET = new Attribute("delta", State.SET);

	private static Attribute CUSTOM_GLOBAL = new Attribute("custom", "global");

	private static Attribute CUSTOM_INFO = new Attribute("custom", "info");

	private static Attribute CUSTOM_ROOT = new Attribute("custom", "root");

	private static Attribute CUSTOM_PARENT = new Attribute("custom", "parent");

	private static Attribute CUSTOM_CURRENT = new Attribute("custom", "current");

	private static Attribute CUSTOM2_UNSET = new Attribute("custom2",
			State.UNSET);

	private static Attribute CUSTOM2_SET = new Attribute("custom2", State.SET);

	private TreeWalk walk;

	private TreeWalk ci_walk;

	private Git git;

	private File customAttributeFile;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (walk != null) {
			walk.close();
		}
		if (ci_walk != null) {
			ci_walk.close();
		}
		super.tearDown();
		if (customAttributeFile != null)
			customAttributeFile.delete();
	}

	/**
	 * Checks that the attributes are computed correctly depending on the
	 * operation type.
	 * <p>
	 * In this test we changed the content of the attribute files in the working
	 * tree compared to the one in the index.
	 * </p>
	 *
	 * @throws IOException
	 * @throws NoFilepatternException
	 * @throws GitAPIException
	 */
	@Test
	public void testCheckinCheckoutDifferences() throws IOException,
			NoFilepatternException, GitAPIException {

		writeGlobalAttributeFile("globalAttributesFile", "*.txt -custom2");
		writeAttributesFile(".git/info/attributes", "*.txt eol=crlf");
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt text");
		writeAttributesFile("level1/level2/.gitattributes", "*.txt -delta");

		writeTrashFile("l0.txt", "");

		writeTrashFile("level1/l1.txt", "");

		writeTrashFile("level1/level2/l2.txt", "");

		git.add().addFilepattern(".").call();

		beginWalk();

		// Modify all attributes
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom2");
		writeAttributesFile(".git/info/attributes", "*.txt eol=lf");
		writeAttributesFile(".gitattributes", "*.txt custom=info");
		writeAttributesFile("level1/.gitattributes", "*.txt -text");
		writeAttributesFile("level1/level2/.gitattributes", "*.txt delta");

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.txt", asSet(EOL_LF, CUSTOM_INFO, CUSTOM2_SET),
				asSet(EOL_LF, CUSTOM_ROOT, CUSTOM2_SET));

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.txt",
				asSet(EOL_LF, CUSTOM_INFO, CUSTOM2_SET, TEXT_UNSET),
				asSet(EOL_LF, CUSTOM_ROOT, CUSTOM2_SET, TEXT_SET));

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.txt",
				asSet(EOL_LF, CUSTOM_INFO, CUSTOM2_SET, TEXT_UNSET, DELTA_SET),
				asSet(EOL_LF, CUSTOM_ROOT, CUSTOM2_SET, TEXT_SET, DELTA_UNSET));

		endWalk();
	}

	/**
	 * Checks that the index is used as fallback when the git attributes file
	 * are missing in the working tree.
	 *
	 * @throws IOException
	 * @throws NoFilepatternException
	 * @throws GitAPIException
	 */
	@Test
	public void testIndexOnly() throws IOException, NoFilepatternException,
			GitAPIException {
		List<File> attrFiles = new ArrayList<>();
		attrFiles.add(writeGlobalAttributeFile("globalAttributesFile",
				"*.txt -custom2"));
		attrFiles.add(writeAttributesFile(".git/info/attributes",
				"*.txt eol=crlf"));
		attrFiles
				.add(writeAttributesFile(".gitattributes", "*.txt custom=root"));
		attrFiles
				.add(writeAttributesFile("level1/.gitattributes", "*.txt text"));
		attrFiles.add(writeAttributesFile("level1/level2/.gitattributes",
				"*.txt -delta"));

		writeTrashFile("l0.txt", "");

		writeTrashFile("level1/l1.txt", "");

		writeTrashFile("level1/level2/l2.txt", "");

		git.add().addFilepattern(".").call();

		// Modify all attributes
		for (File attrFile : attrFiles)
			attrFile.delete();

		beginWalk();

		assertEntry(M, ".gitattributes");
		assertEntry(F, "l0.txt", asSet(CUSTOM_ROOT));

		assertEntry(D, "level1");
		assertEntry(M, "level1/.gitattributes");
		assertEntry(F, "level1/l1.txt",

		asSet(CUSTOM_ROOT, TEXT_SET));

		assertEntry(D, "level1/level2");
		assertEntry(M, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.txt",

		asSet(CUSTOM_ROOT, TEXT_SET, DELTA_UNSET));

		endWalk();
	}

	/**
	 * Check that we search in the working tree for attributes although the file
	 * we are currently inspecting does not exist anymore in the working tree.
	 *
	 * @throws IOException
	 * @throws NoFilepatternException
	 * @throws GitAPIException
	 */
	@Test
	public void testIndexOnly2()
			throws IOException, NoFilepatternException, GitAPIException {
		File l2 = writeTrashFile("level1/level2/l2.txt", "");
		writeTrashFile("level1/level2/l1.txt", "");

		git.add().addFilepattern(".").call();

		writeAttributesFile(".gitattributes", "*.txt custom=root");
		assertTrue(l2.delete());

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(D, "level1");
		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/l1.txt", asSet(CUSTOM_ROOT));
		assertEntry(M, "level1/level2/l2.txt", asSet(CUSTOM_ROOT));

		endWalk();
	}

	/**
	 * Basic test for git attributes.
	 * <p>
	 * In this use case files are present in both the working tree and the index
	 * </p>
	 *
	 * @throws IOException
	 * @throws NoFilepatternException
	 * @throws GitAPIException
	 */
	@Test
	public void testRules() throws IOException, NoFilepatternException,
			GitAPIException {
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

		git.add().addFilepattern(".").call();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "readme.txt", asSet(EOL_LF));

		assertEntry(D, "src");
		assertEntry(D, "src/config");
		assertEntry(F, "src/config/.gitattributes");
		assertEntry(F, "src/config/readme.txt", asSet(DELTA_UNSET, EOL_LF));
		assertEntry(F, "src/config/windows.file", asSet(EOL_CRLF));
		assertEntry(F, "src/config/windows.txt", asSet(DELTA_UNSET, EOL_CRLF));

		assertEntry(F, "windows.file", asSet(EOL_CRLF));
		assertEntry(F, "windows.txt", asSet(EOL_CRLF));

		endWalk();
	}

	/**
	 * Checks that if there is no .gitattributes file in the repository
	 * everything still work fine.
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

		endWalk();
	}

	/**
	 * Checks that an empty .gitattribute file does not return incorrect value.
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

		endWalk();
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

		endWalk();
	}

	/**
	 * Checks that $GIT_DIR/info/attributes file has the highest precedence.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceInfo() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom=global");
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
		assertEntry(F, "level1/level2/file.txt", asSet(CUSTOM_INFO));

		endWalk();
	}

	/**
	 * Checks that a subfolder ".gitattributes" file has precedence over its
	 * parent.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceCurrent() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom=global");
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
		assertEntry(F, "level1/level2/file.txt", asSet(CUSTOM_CURRENT));

		endWalk();
	}

	/**
	 * Checks that the parent ".gitattributes" file is used as fallback.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceParent() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom=global");
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt custom=parent");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/file.txt", asSet(CUSTOM_PARENT));

		endWalk();
	}

	/**
	 * Checks that the grand parent ".gitattributes" file is used as fallback.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceRoot() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom=global");
		writeAttributesFile(".gitattributes", "*.txt custom=root");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");

		assertEntry(D, "level1");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/file.txt", asSet(CUSTOM_ROOT));

		endWalk();
	}

	/**
	 * Checks that the global attribute file is used as fallback.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPrecedenceGlobal() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt custom=global");

		writeTrashFile("level1/level2/file.txt", "");

		beginWalk();

		assertEntry(D, "level1");

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/file.txt", asSet(CUSTOM_GLOBAL));

		endWalk();
	}

	/**
	 * Checks the precedence on a hierarchy with multiple attributes.
	 * <p>
	 * In this test all file are present in both the working tree and the index.
	 * </p>
	 *
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws NoFilepatternException
	 */
	@Test
	public void testHierarchyBothIterator() throws IOException,
			NoFilepatternException, GitAPIException {
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

		git.add().addFilepattern(".").call();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.global", asSet(EOL_CRLF));
		assertEntry(F, "l0.local", asSet(EOL_LF));

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.global", asSet(EOL_CRLF));
		assertEntry(F, "level1/l1.local", asSet(EOL_LF, TEXT_SET));

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.global", asSet(EOL_CRLF));
		assertEntry(F, "level1/level2/l2.local", asSet(EOL_LF, TEXT_UNSET));

		endWalk();

	}

	/**
	 * Checks the precedence on a hierarchy with multiple attributes.
	 * <p>
	 * In this test all file are present only in the working tree.
	 * </p>
	 *
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws NoFilepatternException
	 */
	@Test
	public void testHierarchyWorktreeOnly()
			throws IOException, NoFilepatternException, GitAPIException {
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
		assertEntry(F, "l0.global", asSet(EOL_CRLF));
		assertEntry(F, "l0.local", asSet(EOL_LF));

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.global", asSet(EOL_CRLF));
		assertEntry(F, "level1/l1.local", asSet(EOL_LF, TEXT_SET));

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(F, "level1/level2/l2.global", asSet(EOL_CRLF));
		assertEntry(F, "level1/level2/l2.local", asSet(EOL_LF, TEXT_UNSET));

		endWalk();

	}

	/**
	 * Checks that the list of attributes is an aggregation of all the
	 * attributes from the attributes files hierarchy.
	 *
	 * @throws IOException
	 */
	@Test
	public void testAggregation() throws IOException {
		writeGlobalAttributeFile("globalAttributesFile", "*.txt -custom2");
		writeAttributesFile(".git/info/attributes", "*.txt eol=crlf");
		writeAttributesFile(".gitattributes", "*.txt custom=root");
		writeAttributesFile("level1/.gitattributes", "*.txt text");
		writeAttributesFile("level1/level2/.gitattributes", "*.txt -delta");

		writeTrashFile("l0.txt", "");

		writeTrashFile("level1/l1.txt", "");

		writeTrashFile("level1/level2/l2.txt", "");

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(F, "l0.txt", asSet(EOL_CRLF, CUSTOM_ROOT, CUSTOM2_UNSET));

		assertEntry(D, "level1");
		assertEntry(F, "level1/.gitattributes");
		assertEntry(F, "level1/l1.txt",
				asSet(EOL_CRLF, CUSTOM_ROOT, TEXT_SET, CUSTOM2_UNSET));

		assertEntry(D, "level1/level2");
		assertEntry(F, "level1/level2/.gitattributes");
		assertEntry(
				F,
				"level1/level2/l2.txt",
				asSet(EOL_CRLF, CUSTOM_ROOT, TEXT_SET, DELTA_UNSET,
						CUSTOM2_UNSET));

		endWalk();

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

		assertEntry(F, "l0.txt",
				asSet(TEXT_UNSET, EOL_CRLF, DELTA_UNSET, CUSTOM_INFO));

		endWalk();
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

		assertEntry(F, "l0.txt",
				asSet(TEXT_UNSET, EOL_CRLF, DELTA_UNSET, CUSTOM_INFO));

		endWalk();
	}

	@Test
	public void testRulesInherited() throws Exception {
		writeAttributesFile(".gitattributes", "**/*.txt eol=lf");
		writeTrashFile("src/config/readme.txt", "");
		writeTrashFile("src/config/windows.file", "");

		beginWalk();

		assertEntry(F, ".gitattributes");
		assertEntry(D, "src");
		assertEntry(D, "src/config");

		assertEntry(F, "src/config/readme.txt", asSet(EOL_LF));
		assertEntry(F, "src/config/windows.file",
				Collections.<Attribute> emptySet());

		endWalk();
	}

	private void beginWalk() throws NoWorkTreeException, IOException {
		walk = new TreeWalk(db);
		walk.addTree(new FileTreeIterator(db));
		walk.addTree(new DirCacheIterator(db.readDirCache()));

		ci_walk = new TreeWalk(db);
		ci_walk.setOperationType(OperationType.CHECKIN_OP);
		ci_walk.addTree(new FileTreeIterator(db));
		ci_walk.addTree(new DirCacheIterator(db.readDirCache()));
	}

	/**
	 * Assert an entry in which checkin and checkout attributes are expected to
	 * be the same.
	 *
	 * @param type
	 * @param pathName
	 * @param forBothOperaiton
	 * @throws IOException
	 */
	private void assertEntry(FileMode type, String pathName,
			Set<Attribute> forBothOperaiton) throws IOException {
		assertEntry(type, pathName, forBothOperaiton, forBothOperaiton);
	}

	/**
	 * Assert an entry with no attribute expected.
	 *
	 * @param type
	 * @param pathName
	 * @throws IOException
	 */
	private void assertEntry(FileMode type, String pathName) throws IOException {
		assertEntry(type, pathName, Collections.<Attribute> emptySet(),
				Collections.<Attribute> emptySet());
	}

	/**
	 * Assert that an entry;
	 * <ul>
	 * <li>Has the correct type</li>
	 * <li>Exist in the tree walk</li>
	 * <li>Has the expected attributes on a checkin operation</li>
	 * <li>Has the expected attributes on a checkout operation</li>
	 * </ul>
	 *
	 * @param type
	 * @param pathName
	 * @param checkinAttributes
	 * @param checkoutAttributes
	 * @throws IOException
	 */
	private void assertEntry(FileMode type, String pathName,
			Set<Attribute> checkinAttributes, Set<Attribute> checkoutAttributes)
			throws IOException {
		assertTrue("walk has entry", walk.next());
		assertTrue("walk has entry", ci_walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		assertEquals(checkinAttributes,
				asSet(ci_walk.getAttributes().getAll()));
		assertEquals(checkoutAttributes,
				asSet(walk.getAttributes().getAll()));

		if (D.equals(type)) {
			walk.enterSubtree();
			ci_walk.enterSubtree();
		}
	}

	private static Set<Attribute> asSet(Collection<Attribute> attributes) {
		Set<Attribute> ret = new HashSet<>();
		for (Attribute a : attributes) {
			ret.add(a);
		}
		return (ret);
	}

	private File writeAttributesFile(String name, String... rules)
			throws IOException {
		StringBuilder data = new StringBuilder();
		for (String line : rules)
			data.append(line + "\n");
		return writeTrashFile(name, data.toString());
	}

	/**
	 * Creates an attributes file and set its location in the git configuration.
	 *
	 * @param fileName
	 * @param attributes
	 * @return The attribute file
	 * @throws IOException
	 * @see Repository#getConfig()
	 */
	private File writeGlobalAttributeFile(String fileName, String... attributes)
			throws IOException {
		customAttributeFile = File.createTempFile("tmp_", fileName, null);
		customAttributeFile.deleteOnExit();
		StringBuilder attributesFileContent = new StringBuilder();
		for (String attr : attributes) {
			attributesFileContent.append(attr).append("\n");
		}
		JGitTestUtil.write(customAttributeFile,
				attributesFileContent.toString());
		db.getConfig().setString("core", null, "attributesfile",
				customAttributeFile.getAbsolutePath());
		return customAttributeFile;
	}

	static Set<Attribute> asSet(Attribute... attrs) {
		HashSet<Attribute> result = new HashSet<>();
		result.addAll(Arrays.asList(attrs));
		return result;
	}

	private void endWalk() throws IOException {
		assertFalse("Not all files tested", walk.next());
		assertFalse("Not all files tested", ci_walk.next());
	}
}
