/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.EOL;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

/**
 * Unit tests for end-of-line conversion and settings using core.autocrlf, *
 * core.eol and the .gitattributes eol, text, binary (macro for -diff -merge
 * -text)
 */
@RunWith(Theories.class)
public class EolRepositoryTest extends RepositoryTestCase {
	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	@DataPoint
	public static boolean doSmudgeEntries = true;

	@DataPoint
	public static boolean dontSmudgeEntries = false;

	private boolean smudge;

	@DataPoint
	public static String smallContents[] = {
			generateTestData(3, 1, true, false),
			generateTestData(3, 1, false, true),
			generateTestData(3, 1, true, true) };

	@DataPoint
	public static String hugeContents[] = {
			generateTestData(1000000, 17, true, false),
			generateTestData(1000000, 17, false, true),
			generateTestData(1000000, 17, true, true) };

	static String generateTestData(int size, int lineSize, boolean withCRLF,
			boolean withLF) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i > 0 && i % lineSize == 0) {
				// newline
				if (withCRLF && withLF) {
					// mixed
					if (i % 2 == 0)
						sb.append("\r\n");
					else
						sb.append("\n");
				} else if (withCRLF) {
					sb.append("\r\n");
				} else if (withLF) {
					sb.append("\n");
				}
			}
			sb.append("A");
		}
		return sb.toString();
	}

	public EolRepositoryTest(String[] testContent, boolean smudgeEntries) {
		CONTENT_CRLF = testContent[0];
		CONTENT_LF = testContent[1];
		CONTENT_MIXED = testContent[2];
		this.smudge = smudgeEntries;
	}

	protected String CONTENT_CRLF;

	protected String CONTENT_LF;

	protected String CONTENT_MIXED;

	/** work tree root .gitattributes */
	private File dotGitattributes;

	/** file containing CRLF */
	private File fileCRLF;

	/** file containing LF */
	private File fileLF;

	/** file containing mixed CRLF and LF */
	private File fileMixed;

	/** this values are set in {@link #collectRepositoryState()} */
	private static class ActualEntry {
		private String attrs;

		private String file;

		private String index;

		private int indexContentLength;
	}

	private ActualEntry entryCRLF = new ActualEntry();

	private ActualEntry entryLF = new ActualEntry();

	private ActualEntry entryMixed = new ActualEntry();

	private DirCache dirCache;

	@Test
	public void testDefaultSetup() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(null, null, null, null, "* text=auto");
		collectRepositoryState();
		assertEquals("text=auto", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	public void checkEntryContent(ActualEntry entry, String fileContent,
			String indexContent) {
		assertEquals(fileContent, entry.file);
		assertEquals(indexContent, entry.index);
		if (entry.indexContentLength != 0) {
			assertEquals(fileContent.length(), entry.indexContentLength);
		}
	}

	@Test
	public void test_ConfigAutoCRLF_false() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.FALSE, null, null, null, "* text=auto");
		collectRepositoryState();
		assertEquals("text=auto", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_true() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.TRUE, null, null, null, "* text=auto");
		collectRepositoryState();
		assertEquals("text=auto", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_input() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.INPUT, null, null, null, "* text=auto");
		collectRepositoryState();
		assertEquals("text=auto", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(null, EOL.LF, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigEOL_crlf() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(null, EOL.CRLF, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigEOL_native_windows() throws Exception {
		String origLineSeparator = System.getProperty("line.separator", "\n");
		System.setProperty("line.separator", "\r\n");
		try {
			// for EOL to work, the text attribute must be set
			setupGitAndDoHardReset(null, EOL.NATIVE, "*.txt text", null, null);
			collectRepositoryState();
			assertEquals("text", entryCRLF.attrs);
			checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
			checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		} finally {
			System.setProperty("line.separator", origLineSeparator);
		}
	}

	@Test
	public void test_ConfigEOL_native_xnix() throws Exception {
		String origLineSeparator = System.getProperty("line.separator", "\n");
		System.setProperty("line.separator", "\n");
		try {
			// for EOL to work, the text attribute must be set
			setupGitAndDoHardReset(null, EOL.NATIVE, "*.txt text", null, null);
			collectRepositoryState();
			assertEquals("text", entryCRLF.attrs);
			checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
			checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		} finally {
			System.setProperty("line.separator", origLineSeparator);
		}
	}

	@Test
	public void test_ConfigAutoCRLF_false_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.LF, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_false_ConfigEOL_native() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.NATIVE, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_true_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.TRUE, EOL.LF, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_switchToBranchWithTextAttributes()
			throws Exception {
		Git git = Git.wrap(db);

		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.CRLF, null, null,
				"file1.txt text\nfile2.txt text\nfile3.txt text");
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);

		// switch to binary for file1
		dotGitattributes = createAndAddFile(git, Constants.DOT_GIT_ATTRIBUTES,
				"file1.txt binary\nfile2.txt text\nfile3.txt text");
		gitCommit(git, "switchedToBinaryFor1");
		recreateWorktree(git);
		collectRepositoryState();
		assertEquals("binary -diff -merge -text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		assertEquals("text", entryLF.attrs);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		assertEquals("text", entryMixed.attrs);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);

		// checkout the commit which has text for file1
		gitCheckout(git, "HEAD^");
		recreateWorktree(git);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_switchToBranchWithBinaryAttributes() throws Exception {
		Git git = Git.wrap(db);

		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.LF, null, null,
				"file1.txt binary\nfile2.txt binary\nfile3.txt binary");
		collectRepositoryState();
		assertEquals("binary -diff -merge -text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_CRLF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_MIXED, CONTENT_MIXED);

		// switch to text for file1
		dotGitattributes = createAndAddFile(git, Constants.DOT_GIT_ATTRIBUTES,
				"file1.txt text\nfile2.txt binary\nfile3.txt binary");
		gitCommit(git, "switchedToTextFor1");
		recreateWorktree(git);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		assertEquals("binary -diff -merge -text", entryLF.attrs);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		assertEquals("binary -diff -merge -text", entryMixed.attrs);
		checkEntryContent(entryMixed, CONTENT_MIXED, CONTENT_MIXED);

		// checkout the commit which has text for file1
		gitCheckout(git, "HEAD^");
		recreateWorktree(git);
		collectRepositoryState();
		assertEquals("binary -diff -merge -text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_CRLF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_MIXED, CONTENT_MIXED);
	}

	@Test
	public void test_ConfigAutoCRLF_input_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		setupGitAndDoHardReset(AutoCRLF.INPUT, EOL.LF, "*.txt text", null, null);
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_lf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.TRUE, EOL.LF, "*.txt eol=lf", null, null);
		collectRepositoryState();
		assertEquals("eol=lf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_lf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.LF, "*.txt eol=lf", null, null);
		collectRepositoryState();
		assertEquals("eol=lf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_input_GlobalEOL_lf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.INPUT, EOL.LF, "*.txt eol=lf", null, null);
		collectRepositoryState();
		assertEquals("eol=lf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_crlf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.TRUE, EOL.LF, "*.txt eol=crlf", null, null);
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_crlf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.FALSE, EOL.LF, "*.txt eol=crlf", null, null);
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_input_GlobalEOL_crlf() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.INPUT, EOL.LF, "*.txt eol=crlf", null, null);
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_lf_InfoEOL_crlf()
			throws Exception {
		setupGitAndDoHardReset(AutoCRLF.TRUE, null, "*.txt eol=lf", "*.txt eol=crlf", null);
		// info decides
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_crlf_InfoEOL_lf()
			throws Exception {
		setupGitAndDoHardReset(AutoCRLF.FALSE, null, "*.txt eol=crlf", "*.txt eol=lf", null);
		// info decides
		collectRepositoryState();
		assertEquals("eol=lf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void test_GlobalEOL_lf_RootEOL_crlf() throws Exception {
		setupGitAndDoHardReset(null, null, "*.txt eol=lf", null, "*.txt eol=crlf");
		// root over global
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_crlf_RootEOL_lf() throws Exception {
		setupGitAndDoHardReset(null, null, "*.txt eol=lf", "*.txt eol=crlf", "*.txt eol=lf");
		// info overrides all
		collectRepositoryState();
		assertEquals("eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_crlf_RootEOL_unspec()
			throws Exception {
		setupGitAndDoHardReset(null, null, "*.txt eol=lf", "*.txt eol=crlf",
				"*.txt text !eol");
		// info overrides all
		collectRepositoryState();
		assertEquals("eol=crlf text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_CRLF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_CRLF, CONTENT_LF);
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_unspec_RootEOL_crlf()
			throws Exception {
		setupGitAndDoHardReset(null, null, "*.txt eol=lf", "*.txt !eol",
				"*.txt text eol=crlf");
		// info overrides all
		collectRepositoryState();
		assertEquals("text", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_LF, CONTENT_LF);
	}

	@Test
	public void testBinary1() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.TRUE, EOL.CRLF, "*.txt text", "*.txt binary",
				"*.txt eol=crlf");
		// info overrides all
		collectRepositoryState();
		assertEquals("binary -diff -merge -text eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_CRLF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_MIXED, CONTENT_MIXED);
	}

	@Test
	public void testBinary2() throws Exception {
		setupGitAndDoHardReset(AutoCRLF.TRUE, EOL.CRLF, "*.txt text eol=crlf", null,
				"*.txt binary");
		// root over global
		collectRepositoryState();
		assertEquals("binary -diff -merge -text eol=crlf", entryCRLF.attrs);
		checkEntryContent(entryCRLF, CONTENT_CRLF, CONTENT_CRLF);
		checkEntryContent(entryLF, CONTENT_LF, CONTENT_LF);
		checkEntryContent(entryMixed, CONTENT_MIXED, CONTENT_MIXED);
	}

	// create new repo with
	// global .gitattributes
	// info .git/config/info/.gitattributes
	// workdir root .gitattributes
	// text file lf.txt CONTENT_LF
	// text file crlf.txt CONTENT_CRLF
	//
	// commit files (checkin)
	// delete working dir files
	// reset hard (checkout)
	private void setupGitAndDoHardReset(AutoCRLF autoCRLF, EOL eol,
			String globalAttributesContent, String infoAttributesContent,
			String workDirRootAttributesContent) throws Exception {
		Git git = new Git(db);
		FileBasedConfig config = db.getConfig();
		if (autoCRLF != null) {
			config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, autoCRLF);
		}
		if (eol != null) {
			config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_EOL, eol);
		}
		if (globalAttributesContent != null) {
			File f = new File(db.getDirectory(), "global/attrs");
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

		if (workDirRootAttributesContent != null) {
			dotGitattributes = createAndAddFile(git,
					Constants.DOT_GIT_ATTRIBUTES, workDirRootAttributesContent);
		} else {
			dotGitattributes = null;
		}

		fileCRLF = createAndAddFile(git, "file1.txt", "a");

		fileLF = createAndAddFile(git, "file2.txt", "a");

		fileMixed = createAndAddFile(git, "file3.txt", "a");

		RevCommit c = gitCommit(git, "create files");

		fileCRLF = createAndAddFile(git, "file1.txt", CONTENT_CRLF);

		fileLF = createAndAddFile(git, "file2.txt", CONTENT_LF);

		fileMixed = createAndAddFile(git, "file3.txt", CONTENT_MIXED);

		gitCommit(git, "addFiles");

		recreateWorktree(git);

		if (smudge) {
			DirCache dc = DirCache.lock(git.getRepository().getIndexFile(),
					FS.detect());
			DirCacheEditor editor = dc.editor();
			for (int i = 0; i < dc.getEntryCount(); i++) {
				editor.add(new DirCacheEditor.PathEdit(
						dc.getEntry(i).getPathString()) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.smudgeRacilyClean();
					}
				});
			}
			editor.commit();
		}

		// @TODO: find out why the following assertion would break the tests
		// assertTrue(git.status().call().isClean());
		git.checkout().setName(c.getName()).call();
		git.checkout().setName("master").call();
	}

	private void recreateWorktree(Git git)
			throws GitAPIException, CheckoutConflictException,
			InterruptedException, IOException, NoFilepatternException {
		// re-create file from the repo
		for (File f : new File[] { dotGitattributes, fileCRLF, fileLF, fileMixed }) {
			if (f == null)
				continue;
			f.delete();
			Assert.assertFalse(f.exists());
		}
		gitResetHard(git);
		fsTick(db.getIndexFile());
		gitAdd(git, ".");
	}

	protected RevCommit gitCommit(Git git, String msg) throws GitAPIException {
		return git.commit().setMessage(msg).call();
	}

	protected void gitAdd(Git git, String path) throws GitAPIException {
		git.add().addFilepattern(path).call();
	}

	protected void gitResetHard(Git git) throws GitAPIException {
		git.reset().setMode(ResetType.HARD).call();
	}

	protected void gitCheckout(Git git, String revstr)
			throws GitAPIException, RevisionSyntaxException, IOException {
		git.checkout().setName(db.resolve(revstr).getName()).call();
	}

	// create a file and add it to the repo
	private File createAndAddFile(Git git, String path, String content)
			throws Exception {
		File f;
		int pos = path.lastIndexOf('/');
		if (pos < 0) {
			f = writeTrashFile(path, content);
		} else {
			f = writeTrashFile(path.substring(0, pos), path.substring(pos + 1),
					content);
		}
		gitAdd(git, path);
		Assert.assertTrue(f.exists());
		return f;
	}

	private void collectRepositoryState() throws Exception {
		dirCache = db.readDirCache();
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			walk.addTree(new DirCacheIterator(db.readDirCache()));
			if (dotGitattributes != null) {
				collectEntryContentAndAttributes(walk, F, ".gitattributes",
						null);
			}
			collectEntryContentAndAttributes(walk, F, fileCRLF.getName(),
					entryCRLF);
			collectEntryContentAndAttributes(walk, F, fileLF.getName(),
					entryLF);
			collectEntryContentAndAttributes(walk, F, fileMixed.getName(),
					entryMixed);
			assertFalse("Not all files tested", walk.next());
		}
	}

	private void collectEntryContentAndAttributes(TreeWalk walk, FileMode type,
			String pathName,
			ActualEntry e) throws IOException {
		assertTrue("walk has entry", walk.next());

		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		if (e != null) {
			e.attrs = "";
			for (Attribute a : walk.getAttributes().getAll()) {
				e.attrs += " " + a.toString();
			}
			e.attrs = e.attrs.trim();
			e.file = new String(
					IO.readFully(new File(db.getWorkTree(), pathName)), UTF_8);
			DirCacheEntry dce = dirCache.getEntry(pathName);
			ObjectLoader open = walk.getObjectReader().open(dce.getObjectId());
			e.index = new String(open.getBytes(), UTF_8);
			e.indexContentLength = dce.getLength();
		}

		if (D.equals(type))
			walk.enterSubtree();
	}
}
