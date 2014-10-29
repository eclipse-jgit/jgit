/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr>
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
package org.eclipse.jgit.attributes.ident;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.util.IO;
import org.junit.Before;
import org.junit.Test;

public class IdentAttributeTest extends RepositoryTestCase {

	private Git git;

	private int workingTreeIndex;

	private int dirCachTreeIndex;

	private int revTreeIndex;

	private static final FileMode F = FileMode.REGULAR_FILE;

	private static final FileMode D = FileMode.TREE;

	private static final String FAKE_BLOB_NAME = "3.14159265358979323846264338327950288419";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		workingTreeIndex = -1;
		dirCachTreeIndex = -1;
		revTreeIndex = -1;
	}

	/**
	 * Checks that checkouting a file with an $Id$ pattern and the "ident"
	 * attribute set works fine.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckout_IdentAttrSet() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent ident");

		File fileWithIdent = writeTrashFile("AFile.withIdent", "Id : $Id$");
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", "Id : $Id$");

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		git.checkout().setAllPaths(true).call();

		TreeWalk walk = new TWBuilder() //
				.withDirCach()//
				.withWorkingTree()//
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");
		assertModified(walk, false);


		assertWorkingEntryContent(walk, "Id : $Id$");
	}

	/**
	 * Checks that checkouting a file with an $Id$ pattern and the "ident"
	 * attribute unset works fine.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckout_IdentAttrUnSet() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent -ident");

		File fileWithIdent = writeTrashFile("AFile.withIdent", "Id : $Id$");
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", "Id : $Id$");

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		git.checkout().setAllPaths(true).call();

		TreeWalk walk = new TWBuilder()//
				.withDirCach()//
				.withWorkingTree()//
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent -ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent, "Id : $Id$");
		assertWorkingEntryContent(walk, "Id : $Id$");
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree, "Id : $Id$");
		assertModified(walk, false);
	}

	/**
	 * Checks that checkouting a file with an $Id$ pattern and the "ident"
	 * attribute set works fine. The "ident" attribute is unset in the index and
	 * set in the working tree. Since the operation is a checkout operation, the
	 * ident attribute value from the index is used.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckout_LocalIdentAttrChange() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent -ident");

		File fileWithIdent = writeTrashFile("AFile.withIdent", "Id : $Id$");
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", "Id : $Id$");

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		git.checkout().setAllPaths(true).call();

		writeTrashFile(".gitattributes", "*.withIdent ident");

		TreeWalk walk = new TWBuilder()//
				.withDirCach()//
				.withWorkingTree()//
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent -ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent, "Id : $Id$");
		assertWorkingEntryContent(walk, "Id : $Id$");
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree, "Id : $Id$");
		assertModified(walk, false);
	}

	/**
	 * Checks that adding a file with an $Id$ pattern and the "ident" attribute
	 * set works fine.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckinOperation_IdentAttrSet() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";

		File fileWithIdent = writeTrashFile("AFile.withIdent",
				initialFileContent);
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		TreeWalk walk = new TWBuilder()//
				.withWorkingTree() //
				.withDirCach()//
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertModified(walk, false);

		RevCommit initialCommit = git.commit()//
				.setMessage("Initial commit") //
				.setAll(true) //
				.call();

		walk = new TWBuilder()//
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertWorkingEntryContent(walk, "*.withIdent ident");
		assertRevTreeEntryContent(walk, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, "Id : $Id$");
		assertRevTreeEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent, initialFileContent);

		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree, initialFileContent);
		assertModified(walk, false);
	}

	/**
	 * Checks that the value of the "ident" attribute is computed from the
	 * working tree on an add operation.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckinOperation_LocalIdentAttrChange() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";

		writeTrashFile("AFile.withIdent", initialFileContent);
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		// Change the ident attribute to unset
		writeTrashFile(".gitattributes", "*.withIdent -ident");

		TreeWalk walk = new TWBuilder().withDirCach().build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();
		walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertWorkingEntryContent(walk, "*.withIdent -ident");
		assertRevTreeEntryContent(walk, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, initialFileContent);
		assertRevTreeEntryContent(walk, "Id : $Id$");
		// Git see a modification since the ident attr has changed
		assertModified(walk, true);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree, initialFileContent);
		// Git see a modification since the ident attr has changed
		assertModified(walk, true);
	}

	/**
	 * Checks the normal behavior of an add operation with the "ident" attribute
	 * unset.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckinOperation_IdentAttrUnset() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent -ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";
		writeTrashFile("AFile.withIdent", initialFileContent);
		writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		TreeWalk walk = new TWBuilder().withDirCach().build();

		assertIteration(walk, F, ".gitattributes");
		assertIndexEntryContent(walk, "*.withIdent -ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertIndexEntryContent(walk, initialFileContent);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, initialFileContent);

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();

		walk = new TWBuilder()//
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertWorkingEntryContent(walk, "*.withIdent -ident");
		assertRevTreeEntryContent(walk, "*.withIdent -ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, initialFileContent);
		assertRevTreeEntryContent(walk, initialFileContent);
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertWorkingEntryContent(walk, initialFileContent);
		assertRevTreeEntryContent(walk, initialFileContent);
		assertIndexEntryContent(walk, initialFileContent);
		assertModified(walk, false);
	}

	/**
	 * Checks the following workflow with the "ident" attribute set:
	 * <p>
	 * <ul>
	 * <li>Add and commit files with the "$Id$" pattern and the "Ident"
	 * attribute set</li>
	 * <li>Checkout those files</li>
	 * </ul>
	 * The "$Id$" pattern should be replace by the blob id
	 * </p>
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckinCheckout_IdentAttrSet() throws Exception {
		writeTrashFile(".gitattributes", "*.withIdent ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";
		File fileWithIdent = writeTrashFile("AFile.withIdent",
				initialFileContent);
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();

		git.checkout()//
				.setStartPoint(initialCommit)//
				.addPath("AFile.withIdent")//
				.addPath("folder1/AFile2.withIdent") //
				.call();

		TreeWalk walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, "Id : $Id$");
		assertRevTreeEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");
		assertModified(walk, false);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");
		assertModified(walk, false);

	}

	/**
	 * Checks the following workflow with the "ident" attribute set:
	 * <p>
	 * <ul>
	 * <li>Add and commit files with the "$Id$" pattern and the "Ident"
	 * attribute set</li>
	 * <li>Delete the local files</li>
	 * <li>Checkout those files</li>
	 * </ul>
	 * The "$Id$" pattern should be replace by the blob id
	 * </p>
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckInCheckout_IdentAttrSetWithDelete() throws Exception {
		File attrFile = writeTrashFile(".gitattributes", "*.withIdent ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";

		File fileWithIdent = writeTrashFile("AFile.withIdent",
				initialFileContent);
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();

		fileWithIdent.delete();

		git.checkout()//
				.setStartPoint(initialCommit)//
				.addPath("AFile.withIdent")//
				.addPath("folder1/AFile2.withIdent") //
				.call();

		TreeWalk walk = new TWBuilder()//
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");
		assertWorkingEntryContent(walk, "*.withIdent ident");
		assertRevTreeEntryContent(walk, "*.withIdent ident");
		assertIndexEntryContent(walk, "*.withIdent ident");
		assertFileContent(attrFile, "*.withIdent ident");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, "Id : $Id$");
		assertRevTreeEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree,
				"Id : $Id: c17c0ee73b62fa7ea04616bb0a2a50750544fea4 $");
		assertModified(walk, false);

	}

	/**
	 * Checks that during a merge operation different blob id will not provoke a
	 * conflict.
	 *
	 * @throws IOException
	 * @throws NoFilepatternException
	 * @throws GitAPIException
	 */
	@Test
	public void testMerge_IdentAttrSet() throws IOException,
			NoFilepatternException, GitAPIException {
		writeTrashFile(".gitattributes", "*.script ident");

		String fileFirstContent = "I have to believe in a world outside my own mind. I have to believe that my actions still have meaning, even if I can't remember them.\n"
				+ "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "We all need mirrors to remind ourselves who we are. I'm no different. $Id$";

		String fileFirstContentWithBlob = "I have to believe in a world outside my own mind. I have to believe that my actions still have meaning, even if I can't remember them.\n"
				+ "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "We all need mirrors to remind ourselves who we are. I'm no different. $Id: ab275d25f1582249d2d79089eaea8a740e540cb4 $";

		String movieFileName = "movie.script";

		File scriptFile = writeTrashFile(movieFileName, fileFirstContent);

		git.add().addFilepattern(".gitattributes")
				.addFilepattern(movieFileName).call();

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();


		git.checkout()//
				.setCreateBranch(true).setName("branchA") //
				.setStartPoint(initialCommit)//
				.addPath(movieFileName) //
				.call();

		TreeWalk walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");

		assertIteration(walk, F, movieFileName);
		assertWorkingEntryContent(walk, fileFirstContent);
		assertRevTreeEntryContent(walk, fileFirstContent);
		assertFileContent(scriptFile, fileFirstContentWithBlob);


		String fileSecondContent = "I have to believe in a world outside my own mind. I have to believe that my actions still have meaning, even if I can't remember them.\n"
				+ "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "$Id: ab275d25f1582249d2d79089eaea8a740e540cb4 $";

		String fileSecondContentNoBlob = "I have to believe in a world outside my own mind. I have to believe that my actions still have meaning, even if I can't remember them.\n"
				+ "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "$Id$";

		String fileSecondContentUpdateBlob = "I have to believe in a world outside my own mind. I have to believe that my actions still have meaning, even if I can't remember them.\n"
				+ "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "$Id: 63090a48733e1a29e457b25a42570e5c91eda49d $";

		writeTrashFile(movieFileName, fileSecondContent);

		git.add().addFilepattern(movieFileName).call();

		RevCommit HEADBranchA = git.commit().setMessage("Branch A").call();

		git.checkout().setStartPoint(HEADBranchA).addPath(movieFileName)
				.call();

		walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(HEADBranchA.getTree()) //
				.build();


		assertIteration(walk, F, ".gitattributes");

		assertIteration(walk, F, movieFileName);
		assertWorkingEntryContent(walk, fileSecondContentNoBlob);
		assertRevTreeEntryContent(walk, fileSecondContentNoBlob);
		assertFileContent(scriptFile, fileSecondContentUpdateBlob);

		git.checkout().setStartPoint(initialCommit).setCreateBranch(true)
				.setName("branchB").call();

		String fileThirdContent = "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "We all need mirrors to remind ourselves who we are. I'm no different. $Id: ab275d25f1582249d2d79089eaea8a740e540cb4 $";

		String fileThirdContentNoBlob = "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "We all need mirrors to remind ourselves who we are. I'm no different. $Id$";

		String fileThirdContentUpdatedBlob = "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "We all need mirrors to remind ourselves who we are. I'm no different. $Id: 98cf252213350194cc5ff96d7cdcf30a1fa15f20 $";

		writeTrashFile(movieFileName, fileThirdContent);

		git.add().addFilepattern(movieFileName).call();

		RevCommit HEADBranchB = git.commit().setMessage("branchB").call();

		git.checkout().setStartPoint(HEADBranchB).setCreateBranch(true)
				.addPath(movieFileName).call();

		walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(HEADBranchB.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");

		assertIteration(walk, F, movieFileName);
		assertWorkingEntryContent(walk, fileThirdContentNoBlob);
		assertRevTreeEntryContent(walk, fileThirdContentNoBlob);
		assertFileContent(scriptFile, fileThirdContentUpdatedBlob);

		MergeResult result = git.merge().include(HEADBranchA).call();
		assertTrue(result.getMergeStatus().isSuccessful());


		walk = new TWBuilder() //
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(((RevCommit) result.getNewHead()).getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");

		String finalFileContent = "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "$Id: cc69e1a11b8c9140c8a14baa4a397594365a5ff7 $\n";

		String finalFileContentNoBlob = "I have to believe that when my eyes are closed, the world's still here. Do I believe the world's still here? Is it still out there?...Yeah.\n"
				+ "$Id$\n";

		assertIteration(walk, F, movieFileName);
		assertWorkingEntryContent(walk, finalFileContentNoBlob);
		assertRevTreeEntryContent(walk, finalFileContentNoBlob);
		assertFileContent(scriptFile, finalFileContent);
	}

	@Test
	public void testCheckoutBranch() throws IOException,
			NoFilepatternException, GitAPIException {
		writeTrashFile(".gitattributes", "*.withIdent ident");

		String initialFileContent = "Id : $Id: " + FAKE_BLOB_NAME + " $";

		File fileWithIdent = writeTrashFile("AFile.withIdent",
				initialFileContent);
		File fileWithIdentInSubTree = writeTrashFile(
				"folder1/AFile2.withIdent", initialFileContent);

		git.add()//
				.addFilepattern(".gitattributes") //
				.addFilepattern("AFile.withIdent")//
				.addFilepattern("folder1/AFile2.withIdent")//
				.call();

		RevCommit initialCommit = git.commit().setMessage("Initial commit")
				.call();

		String newFileContent = initialFileContent + "extrat content";
		writeTrashFile("AFile.withIdent", newFileContent);

		writeTrashFile("folder1/AFile2.withIdent", newFileContent);

		git.checkout().setCreateBranch(true).setName("newBranch")
				.setStartPoint(initialCommit).call();

		TreeWalk walk = new TWBuilder()//
				.withDirCach() //
				.withWorkingTree() //
				.withRevTree(initialCommit.getTree()) //
				.build();

		assertIteration(walk, F, ".gitattributes");

		assertIteration(walk, F, "AFile.withIdent");
		assertWorkingEntryContent(walk, "Id : $Id$extrat content");
		assertRevTreeEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdent, newFileContent);
		assertModified(walk, true);

		assertIteration(walk, D, "folder1");

		assertIteration(walk, F, "folder1/AFile2.withIdent");
		assertWorkingEntryContent(walk, "Id : $Id$extrat content");
		assertIndexEntryContent(walk, "Id : $Id$");
		assertFileContent(fileWithIdentInSubTree, newFileContent);
		assertModified(walk, true);

	}

	private void assertModified(TreeWalk walk, boolean modified)
			throws IOException {
		WorkingTreeIterator workingTreeIterator = walk.getTree(
				workingTreeIndex, WorkingTreeIterator.class);
		assertNotNull(workingTreeIterator);
		DirCacheIterator dirCacheIterator = walk.getTree(dirCachTreeIndex,
				DirCacheIterator.class);
		assertNotNull(dirCacheIterator);
		boolean actualModified = workingTreeIterator.isModified(
				dirCacheIterator.getDirCacheEntry(),
				false, db.newObjectReader());
		assertTrue(modified == actualModified);

	}

	private void assertIteration(TreeWalk walk, FileMode type, String pathName)
			throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		if (D.equals(type))
			walk.enterSubtree();
	}

	private void assertFileContent(File file, String content)
			throws FileNotFoundException, IOException {
		assertThat(new String(IO.readFully(file)), is(content));
	}

	private void assertWorkingEntryContent(TreeWalk walk, String content)
			throws IOException {
		WorkingTreeIterator itr = walk.getTree(workingTreeIndex,
				WorkingTreeIterator.class);
		assertNotNull("has WorkingTreeIterator", itr);

		InputStream openEntryStream = itr.openEntryStream();
		assertThat(toString(openEntryStream), is(content));
	}

	static String toString(java.io.InputStream is) throws IOException {
		java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8");
		scanner.useDelimiter("\\A");
		try {
			String result = scanner.hasNext() ? scanner.next() : "";
			return result;
		} finally {
			scanner.close();
			is.close();
		}
	}

	private void assertIndexEntryContent(TreeWalk walk, String content)
			throws IOException {
		DirCacheIterator itr = walk.getTree(dirCachTreeIndex,
				DirCacheIterator.class);
		assertNotNull("has WorkingTreeIterator", itr);

		String actual = new String(db.open(
				itr.getDirCacheEntry().getObjectId(), Constants.OBJ_BLOB)
				.getCachedBytes(), "UTF-8");
		assertThat(actual, is(content));
	}

	private void assertRevTreeEntryContent(TreeWalk walk, String content)
			throws IOException {
		CanonicalTreeParser itr = walk.getTree(revTreeIndex,
				CanonicalTreeParser.class);
		assertNotNull("has WorkingTreeIterator", itr);

		String actual = new String(db.open(itr.getEntryObjectId(),
				Constants.OBJ_BLOB).getCachedBytes(), "UTF-8");
		assertThat(actual, is(content));

	}

	private class TWBuilder {

		private boolean withWorkingTreeIte;

		private boolean withDirCachIte;

		private ObjectId withRetree;

		public TWBuilder withWorkingTree() {
			withWorkingTreeIte = true;
			return this;
		}

		public TWBuilder withDirCach() {
			withDirCachIte = true;
			return this;
		}

		public TWBuilder withRevTree(ObjectId tree) {
			this.withRetree = tree;
			return this;
		}


		public TreeWalk build() throws MissingObjectException,
				IncorrectObjectTypeException, CorruptObjectException,
				IOException {
			TreeWalk walk = new TreeWalk(db);
			if (withRetree != null)
				revTreeIndex = walk.addTree(withRetree);
			if (withWorkingTreeIte)
				workingTreeIndex = walk.addTree(new FileTreeIterator(db));
			if (withDirCachIte)
				dirCachTreeIndex = walk.addTree(new DirCacheIterator(db
						.readDirCache()));
			return walk;
		}

	}

}
