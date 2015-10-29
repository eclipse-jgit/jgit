/*
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

package org.eclipse.jgit.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.attributes.AttributeSet;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.EOL;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.IO;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for end-of-line conversion and settings using core.autocrlf,
 * core.eol and the .gitattributes eol, text, binary (macro for -diff -merge
 * -text)
 */
public class EndOfLineRepositoryTest extends RepositoryTestCase {

	private final HashSet<File> repoFiles = new HashSet<File>();

	@Test
	public void testDefaultSetup() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(null, null, null, null, "* text=auto");
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.FALSE, null, null, null, "* text=auto");
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_forceEOL() throws Exception {
		String origFlag = System.getProperty("jgit.forceEOL", "false");
		System.setProperty("jgit.forceEOL", "true");
		try {
			// for EOL to work, the text attribute must be set
			Git git = setupGit(AutoCRLF.FALSE, null, null, null, "* text=auto");
			textFileContentIs(git, "a\r\nb", "a\r\nb");
		} finally {
			System.setProperty("jgit.forceEOL", origFlag);
		}
	}

	@Test
	public void test_ConfigAuto_true() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.TRUE, null, null, null, "* text=auto");
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_input() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.INPUT, null, null, null, "* text=auto");
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(null, EOL.LF, "*.txt text", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigEOL_crlf() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(null, EOL.CRLF, "*.txt text", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigEOL_native_windows() throws Exception {
		String origLineSeparator = System.getProperty("line.separator", "\n");
		System.setProperty("line.separator", "\r\n");
		try {
			// for EOL to work, the text attribute must be set
			Git git = setupGit(null, EOL.NATIVE, "*.txt text", null, null);
			textFileContentIs(git, "a\nb", "a\nb");
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
			Git git = setupGit(null, EOL.NATIVE, "*.txt text", null, null);
			textFileContentIs(git, "a\nb", "a\nb");
		} finally {
			System.setProperty("line.separator", origLineSeparator);
		}
	}

	@Test
	public void test_ConfigAutoCRLF_false_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.FALSE, EOL.LF, "*.txt text", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_ConfigEOL_native() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.FALSE, EOL.NATIVE, "*.txt text", null,
				null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_ConfigEOL_native_forceEOL()
			throws Exception {
		String origFlag = System.getProperty("jgit.forceEOL", "false");
		System.setProperty("jgit.forceEOL", "true");
		try {
			// for EOL to work, the text attribute must be set
			Git git = setupGit(AutoCRLF.FALSE, EOL.NATIVE, "*.txt text", null,
					null);
			textFileContentIs(git, "a\r\nb", "a\r\nb");
		} finally {
			System.setProperty("jgit.forceEOL", origFlag);
		}
	}

	@Test
	public void test_ConfigAutoCRLF_true_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.TRUE, EOL.LF, "*.txt text", null, null);
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_input_ConfigEOL_lf() throws Exception {
		// for EOL to work, the text attribute must be set
		Git git = setupGit(AutoCRLF.INPUT, EOL.LF, "*.txt text", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_lf() throws Exception {
		Git git = setupGit(AutoCRLF.TRUE, EOL.LF, "*.txt eol=lf", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_lf() throws Exception {
		Git git = setupGit(AutoCRLF.FALSE, EOL.LF, "*.txt eol=lf", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_input_GlobalEOL_lf() throws Exception {
		Git git = setupGit(AutoCRLF.INPUT, EOL.LF, "*.txt eol=lf", null, null);
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_crlf() throws Exception {
		Git git = setupGit(AutoCRLF.TRUE, EOL.LF, "*.txt eol=crlf", null, null);
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_crlf() throws Exception {
		Git git = setupGit(AutoCRLF.FALSE, EOL.LF, "*.txt eol=crlf", null,
				null);
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_input_GlobalEOL_crlf() throws Exception {
		Git git = setupGit(AutoCRLF.INPUT, EOL.LF, "*.txt eol=crlf", null,
				null);
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_true_GlobalEOL_lf_InfoEOL_crlf()
			throws Exception {
		Git git = setupGit(AutoCRLF.TRUE, null, "*.txt eol=lf",
				"*.txt eol=crlf", null);
		// info decides
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_ConfigAutoCRLF_false_GlobalEOL_crlf_InfoEOL_lf()
			throws Exception {
		Git git = setupGit(AutoCRLF.FALSE, null, "*.txt eol=crlf",
				"*.txt eol=lf", null);
		// info decides
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void test_GlobalEOL_crlf_RootEOL_lf() throws Exception {
		Git git = setupGit(null, null, "*.txt eol=lf", null, "*.txt eol=crlf");
		// root over global
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_crlf_RootEOL_lf() throws Exception {
		Git git = setupGit(null, null, "*.txt eol=lf", "*.txt eol=crlf",
				"*.txt eol=lf");
		// info overrides all
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_crlf_RootEOL_unspec()
			throws Exception {
		Git git = setupGit(null, null, "*.txt eol=lf", "*.txt eol=crlf",
				"*.txt text !eol");
		AttributeSet expectedAttrs = new AttributeSet(
				new Attribute("text", State.SET), new Attribute("eol", "crlf"));
		AttributeSet actualAttrs = git.getRepository().getAttributeManager()
				.getAttributes("lf.txt", FileMode.REGULAR_FILE);
		Assert.assertEquals(expectedAttrs, actualAttrs);
		// info overrides all
		textFileContentIs(git, "a\r\nb", "a\r\nb");
	}

	@Test
	public void test_GlobalEOL_lf_InfoEOL_unspec_RootEOL_crlf()
			throws Exception {
		Git git = setupGit(null, null, "*.txt eol=lf", "*.txt !eol",
				"*.txt text eol=crlf");
		AttributeSet expectedAttrs = new AttributeSet(
				new Attribute("text", State.SET));
		AttributeSet actualAttrs = git.getRepository().getAttributeManager()
				.getAttributes("lf.txt", FileMode.REGULAR_FILE);
		Assert.assertEquals(expectedAttrs, actualAttrs);
		// info overrides all
		textFileContentIs(git, "a\nb", "a\nb");
	}

	@Test
	public void testBinary1() throws Exception {
		Git git = setupGit(AutoCRLF.TRUE, EOL.CRLF, "*.txt text",
				"*.txt binary", "*.txt eol=crlf");
		// info overrides all
		textFileContentIs(git, "a\nb", "a\r\nb");
	}

	@Test
	public void testBinary2() throws Exception {
		Git git = setupGit(AutoCRLF.TRUE, EOL.CRLF, "*.txt text eol=crlf", null,
				"*.txt binary");
		// root over global
		textFileContentIs(git, "a\nb", "a\r\nb");
	}

	// create new repo with
	// global .gitattributes
	// info .git/config/info/.gitattributes
	// workdir root .gitattributes
	// text file lf.txt "a\nb"
	// text file crlf.txt "a\r\nb"
	//
	// commit files (checkin)
	// delete working dir files
	// reset hard (checkout)
	private Git setupGit(AutoCRLF autoCRLF, EOL eol,
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
		if(globalAttributesContent!=null){
			File f = new File(db.getDirectory(),"global/attributes");
			write(f,globalAttributesContent);
			config.setString(ConfigConstants.CONFIG_CORE_SECTION,null,
					ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE,f.getAbsolutePath());

		}
		if(infoAttributesContent!=null){
			File f = new File(db.getDirectory(), Constants.INFO_ATTRIBUTES);
			write(f,infoAttributesContent);
		}
		config.save();

		if (workDirRootAttributesContent != null) {
			createFile(git, Constants.DOT_GIT_ATTRIBUTES,
					workDirRootAttributesContent);
		}

		createFile(git, "lf.txt", "a\nb");

		createFile(git, "crlf.txt", "a\r\nb");

		/* RevCommit commit = */git.commit().setMessage("add files").call();
		// re-create file from the repo
		for (File f : repoFiles) {
			f.delete();
			Assert.assertFalse(f.exists());
		}

		git.reset().setMode(ResetType.HARD).call();

		return git;
	}

	// create a file and add it to the repo
	private void createFile(Git git, String path,
			String content)
			throws Exception {
		File f;
		int pos = path.lastIndexOf('/');
		if (pos < 0) {
			f = writeTrashFile(path, content);
		} else {
			f = writeTrashFile(path.substring(0, pos), path.substring(pos + 1),
					content);
		}
		git.add().addFilepattern(path).call();
		fsTick(f);
		Assert.assertTrue(f.exists());
		repoFiles.add(f);
	}

	private void textFileContentIs(Git git, String expectedLFTxtContent,
			String expectedCRLFTxtContent)
					throws Exception {
		Assert.assertEquals(expectedLFTxtContent,
				new String(IO.readFully(
						new File(git.getRepository().getWorkTree(), "lf.txt")),
				StandardCharsets.UTF_8));

		Assert.assertEquals(expectedCRLFTxtContent, new String(IO.readFully(
				new File(git.getRepository().getWorkTree(), "crlf.txt")),
				StandardCharsets.UTF_8));
	}

}
