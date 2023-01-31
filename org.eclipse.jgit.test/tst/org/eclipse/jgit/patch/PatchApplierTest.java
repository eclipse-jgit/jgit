/*
 * Copyright (C) 2022, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.patch.PatchApplier.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
 		PatchApplierTest.WithWorktree. class, //
		PatchApplierTest.InCore.class, //
})
public class PatchApplierTest {

	public abstract static class Base extends RepositoryTestCase {

		protected String patchName;

		protected Set<String> preExist = new HashSet<>();

		protected Set<String> postExist = new HashSet<>();

		/** data before patching. */
		protected Map<String, byte[]> preImages = new HashMap<>();

		/** expected data after patching. */
		protected Map<String, byte[]> postImages = new HashMap<>();

		protected Map<String, String> expectedTexts = new HashMap<>();

		protected RevTree baseTip;

		public boolean inCore;

		Base(boolean inCore) {
			this.inCore = inCore;
		}

		protected void initRenameTest(String origName, String destName)
				throws Exception {
			initTest(origName, true, false);
			initFile(destName, origName, false, true);
		}

		protected void initTest(String patchName, boolean preExists,
				boolean postExists) throws Exception {
			this.patchName = patchName;
			initFile(patchName, preExists, postExists);
		}

		protected void initFile(String fileName, boolean preExists,
				boolean postExists) throws Exception {
			initFile(fileName, fileName, preExists, postExists);
		}

		protected void initFile(String fileName, String resourcePrefix,
				boolean preExists, boolean postExists) throws Exception {
			// Patch and pre/postimage are read from data
			// org.eclipse.jgit.test/tst-rsrc/org/eclipse/jgit/diff/
			if (postExists) {
				postExist.add(fileName);
				byte[] postImage = IO.readWholeStream(
						getTestResource(resourcePrefix + "_PostImage"), 0)
						.array();
				postImages.put(fileName, postImage);
				expectedTexts.put(fileName,
						new String(postImage, StandardCharsets.UTF_8));
			}

			File f = new File(db.getWorkTree(), fileName);
			if (preExists) {
				preExist.add(fileName);
				byte[] preImage = IO.readWholeStream(
						getTestResource(resourcePrefix + "_PreImage"), 0)
						.array();
				preImages.put(fileName, preImage);
				try (Git git = new Git(db)) {
					Files.write(f.toPath(), preImage);
					git.add().addFilepattern(fileName).call();
				}
			}
			try (Git git = new Git(db)) {
				RevCommit base = git.commit().setMessage("PreImage").call();
				baseTip = base.getTree();
			}
		}

		void initTest(final String patchName) throws Exception {
			initTest(patchName, true, true);
		}

		protected Result applyPatch()
				throws PatchApplyException, PatchFormatException, IOException {
			InputStream patchStream = getTestResource(patchName + ".patch");
			if (inCore) {
				try (ObjectInserter oi = db.newObjectInserter()) {
					return new PatchApplier(db, baseTip, oi).applyPatch(patchStream);
				}
			}
			return new PatchApplier(db).applyPatch(patchStream);
		}

		protected static InputStream getTestResource(String patchFile) {
			return PatchApplierTest.class.getClassLoader()
					.getResourceAsStream("org/eclipse/jgit/diff/" + patchFile);
		}

		protected void verifyContent(Result result, String path, boolean exists)
				throws Exception {
			if (inCore) {
				byte[] output = readBlob(result.getTreeId(), path);
				if (!exists)
					assertNull(output);
				else {
					assertNotNull(output);
					assertEquals(expectedTexts.get(path),
							new String(output, StandardCharsets.UTF_8));
				}
			} else {
				File f = new File(db.getWorkTree(), path);
				if (!exists)
					assertFalse(f.exists());
				else
					checkFile(f, expectedTexts.get(path));
			}
		}

		void verifyChange(Result result) throws Exception {
			Set<String> expectedAffectedFiles = preExist;
			expectedAffectedFiles.addAll(postExist);
			assertEquals(
					expectedAffectedFiles.stream().sorted().collect(toList()),
					result.getPaths());
			for (String path : expectedAffectedFiles)
				verifyContent(result, path, postExist.contains(path));
		}

		protected byte[] readBlob(ObjectId treeish, String path)
				throws Exception {
			try (TestRepository<?> tr = new TestRepository<>(db);
					RevWalk rw = tr.getRevWalk()) {
				db.incrementOpen();
				RevTree tree = rw.parseTree(treeish);
				try (TreeWalk tw = TreeWalk.forPath(db, path, tree)) {
					if (tw == null) {
						return null;
					}
					return tw.getObjectReader()
							.open(tw.getObjectId(0), OBJ_BLOB).getBytes();
				}
			}
		}

		protected void verifyBinaryChange(Result result) throws Exception {
			Set<String> expectedAffectedFiles = preExist;
			expectedAffectedFiles.addAll(postExist);
			assertEquals(
					expectedAffectedFiles.stream().sorted().collect(toList()),
					result.getPaths());
			for (String path : expectedAffectedFiles) {
				if (inCore) {
					assertArrayEquals(postImages.get(path), readBlob(
							result.getTreeId(), result.getPaths().get(0)));
				} else {
					File f = new File(db.getWorkTree(), path);
					assertArrayEquals(postImages.get(path),
							Files.readAllBytes(f.toPath()));
				}
			}
		}

		/* tests */

		@Test
		public void testBinaryDelta() throws Exception {
			initTest("delta");
			verifyBinaryChange(applyPatch());
		}

		@Test
		public void testBinaryLiteral() throws Exception {
			initTest("literal");
			verifyBinaryChange(applyPatch());
		}

		@Test
		public void testBinaryLiteralAdd() throws Exception {
			initTest("literal_add", false, true);
			verifyBinaryChange(applyPatch());
		}

		@Test
		public void testModifyM2() throws Exception {
			initTest("M2", true, true);

			Result result = applyPatch();

			if (!inCore && FS.DETECTED.supportsExecute()) {
				assertEquals(1, result.getPaths().size());
				File f = new File(db.getWorkTree(), result.getPaths().get(0));
				assertTrue(FS.DETECTED.canExecute(f));
			}

			verifyChange(result);
		}

		@Test
		public void testModifyM3() throws Exception {
			initTest("M3", true, true);

			Result result = applyPatch();

			verifyChange(result);
			if (!inCore && FS.DETECTED.supportsExecute()) {
				File f = new File(db.getWorkTree(), result.getPaths().get(0));
				assertFalse(FS.DETECTED.canExecute(f));
			}
		}

		@Test
		public void testModifyX() throws Exception {
			initTest("X");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testModifyY() throws Exception {
			initTest("Y");

			Result result = applyPatch();

			verifyChange(result);
		}

		@Test
		public void testModifyZ() throws Exception {
			initTest("Z");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNonASCII() throws Exception {
			initTest("NonASCII");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNonASCII2() throws Exception {
			initTest("NonASCII2");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNonASCIIAdd() throws Exception {
			initTest("NonASCIIAdd");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNonASCIIAdd2() throws Exception {
			initTest("NonASCIIAdd2", false, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNonASCIIDel() throws Exception {
			initTest("NonASCIIDel", true, false);

			Result result = applyPatch();
			verifyChange(result);
			assertEquals("NonASCIIDel", result.getPaths().get(0));
		}

		@Test
		public void testRenameNoHunks() throws Exception {
			initRenameTest("RenameNoHunks", "nested/subdir/Renamed");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testRenameWithHunks() throws Exception {
			initRenameTest("RenameWithHunks", "nested/subdir/Renamed");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testCopyWithHunks() throws Exception {
			initTest("CopyWithHunks", true, true);
			initFile("CopyResult", "CopyWithHunks", false, true);

			Result result = applyPatch();

			assertEquals(1, result.getPaths().size());
			assertEquals("CopyResult", result.getPaths().get(0));
			verifyContent(result, "CopyResult", true);
		}

		@Test
		public void testShiftUp() throws Exception {
			initTest("ShiftUp");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testShiftUp2() throws Exception {
			initTest("ShiftUp2");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testShiftDown() throws Exception {
			initTest("ShiftDown");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testShiftDown2() throws Exception {
			initTest("ShiftDown2");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testMultipleFilesPatch() throws Exception {
			patchName = "XAndY";
			initFile("X", true, true);
			initFile("Y", true, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testDoNotAffectOtherFiles() throws Exception {
			initTest("X");
			initFile("Unaffected", true, true);

			Result result = applyPatch();

			assertEquals(1, result.getPaths().size());
			assertEquals("X", result.getPaths().get(0));
			verifyContent(result, "X", true);
			verifyContent(result, "Unaffected", true);
		}
	}

	public static class InCore extends Base {

		public InCore() {
			super(true);
		}

		@Test
		public void testNoNewlineAtEnd() throws Exception {
			initTest("x_d");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testNoNewlineAtEndInHunk() throws Exception {
			initTest("x_e");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testAddNewlineAtEnd() throws Exception {
			initTest("x_add_nl");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testRemoveNewlineAtEnd() throws Exception {
			initTest("x_last_rm_nl");

			Result result = applyPatch();
			verifyChange(result);
		}
	}

	public static class WithWorktree extends Base {
		public WithWorktree() {
			super(false);
		}

		@Test
		public void testModifyNL1() throws Exception {
			initTest("NL1");

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testCrLf() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				initTest("crlf", true, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfOff() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				initTest("crlf", true, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfEmptyCommitted() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				initTest("crlf3", true, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfNewFile() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				initTest("crlf4", false, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testPatchWithCrLf() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				initTest("crlf2", true, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testPatchWithCrLf2() throws Exception {
			try (Git git = new Git(db)) {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				initTest("crlf2", true, true);
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndAutoCRLF_true() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				initTest("x_d_crlf", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndAutoCRLF_false() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);

				initTest("x_d", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndAutoCRLF_input() throws Exception {
			try {
				db.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");

				initTest("x_d", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndInHunkAutoCRLF_true() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				initTest("x_e_crlf", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndInHunkAutoCRLF_false() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);

				initTest("x_e", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testNoNewlineAtEndInHunkAutoCRLF_input() throws Exception {
			try {
				db.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");

				initTest("x_e", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testAddNewlineAtEndAutoCRLF_true() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				initTest("x_add_nl_crlf", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testAddNewlineAtEndAutoCRLF_false() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);

				initTest("x_add_nl", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testAddNewlineAtEndAutoCRLF_input() throws Exception {
			try {
				db.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");

				initTest("x_add_nl", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testRemoveNewlineAtEndAutoCRLF_true() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				initTest("x_last_rm_nl_crlf", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testRemoveNewlineAtEndAutoCRLF_false() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);

				initTest("x_last_rm_nl", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testRemoveNewlineAtEndAutoCRLF_input() throws Exception {
			try {
				db.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION,
						null, ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");

				initTest("x_last_rm_nl", true, true);

				Result result = applyPatch();
				verifyChange(result);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testEditExample() throws Exception {
			initTest("z_e", true, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testEditNoNewline() throws Exception {
			initTest("z_e_no_nl", true, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testEditAddNewline() throws Exception {
			initTest("z_e_add_nl", true, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		@Test
		public void testEditRemoveNewline() throws Exception {
			initTest("z_e_rm_nl", true, true);

			Result result = applyPatch();
			verifyChange(result);
		}

		// Clean/smudge filter for testFiltering. The smudgetest test resources
		// were created with C git using a clean filter sed -e "s/A/E/g" and the
		// smudge filter sed -e "s/E/A/g". To keep the test independent of the
		// presence of sed, implement this with a built-in filter.
		private static class ReplaceFilter extends FilterCommand {

			private final char toReplace;

			private final char replacement;

			ReplaceFilter(InputStream in, OutputStream out, char toReplace,
					char replacement) {
				super(in, out);
				this.toReplace = toReplace;
				this.replacement = replacement;
			}

			@Override
			public int run() throws IOException {
				int b = in.read();
				if (b < 0) {
					in.close();
					out.close();
					return -1;
				}
				if ((b & 0xFF) == toReplace) {
					b = replacement;
				}
				out.write(b);
				return 1;
			}
		}

		@Test
		public void testFiltering() throws Exception {
			// Set up filter
			FilterCommandFactory clean =
					(repo, in, out) -> new ReplaceFilter(in, out, 'A', 'E');
			FilterCommandFactory smudge =
					(repo, in, out) -> new ReplaceFilter(in, out, 'E', 'A');
			FilterCommandRegistry.register("jgit://builtin/a2e/clean", clean);
			FilterCommandRegistry.register("jgit://builtin/a2e/smudge", smudge);
			Config config = db.getConfig();
			try (Git git = new Git(db)) {
				config.setString(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
						"clean", "jgit://builtin/a2e/clean");
				config.setString(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
						"smudge", "jgit://builtin/a2e/smudge");
				write(new File(db.getWorkTree(), ".gitattributes"),
						"smudgetest filter=a2e");
				git.add().addFilepattern(".gitattributes").call();
				git.commit().setMessage("Attributes").call();
				initTest("smudgetest", true, true);

				Result result = applyPatch();

				verifyChange(result);
			} finally {
				config.unset(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
						"clean");
				config.unset(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
						"smudge");
				// Tear down filter
				FilterCommandRegistry.unregister("jgit://builtin/a2e/clean");
				FilterCommandRegistry.unregister("jgit://builtin/a2e/smudge");
			}
		}
	}
}
