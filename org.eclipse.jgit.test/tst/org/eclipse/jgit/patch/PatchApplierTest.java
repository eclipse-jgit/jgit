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
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.patch.PatchApplier.ApplyPatchResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
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

		protected String name;

		protected byte[] preImage;
		protected byte[] postImage;

		protected RawText expectedText;
		protected RevTree baseTip;
		public boolean inCore;

		Base(boolean inCore) {
			this.inCore = inCore;
		}

		protected void init(final String name, final boolean preExists,
				final boolean postExists) throws Exception {
			this.name = name;
			if (postExists) {
				postImage = IO
						.readWholeStream(getTestResource(name + "_PostImage"), 0)
						.array();
				expectedText = new RawText(postImage);
			}

			File f = new File(db.getWorkTree(), name);
			if (preExists) {
				preImage = IO
						.readWholeStream(getTestResource(name + "_PreImage"), 0)
						.array();
				try (Git git = new Git(db)) {
					Files.write(f.toPath(), preImage);
					git.add().addFilepattern(name).call();
				}
			}
			try (Git git = new Git(db)) {
				RevCommit base = git.commit().setMessage("PreImage").call();
				baseTip = base.getTree();
			}
		}

		void init(final String name) throws Exception {
			init(name, true, true);
		}

		protected ApplyPatchResult applyPatch()
				throws PatchApplyException, PatchFormatException {
			InputStream patchStream = getTestResource(name + ".patch");
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
		void verifyChange(ApplyPatchResult result, String name) throws Exception {
			verifyChange(result, name, true);
		}

		protected void verifyContent(ApplyPatchResult result, String path, boolean exists) throws Exception {
			if (inCore) {
				byte[] output = readBlob(result.appliedTree, path);
				if (!exists)
					assertNull(output);
				else {
					assertNotNull(output);
					checkInputStream(
							new ByteArrayInputStream(output),
							expectedText.getString(0, expectedText.size(), false));
				}
			} else {
				File f = new File(db.getWorkTree(), path);
				if (!exists)
					assertFalse(f.exists());
				else
					checkFile(f, expectedText.getString(0, expectedText.size(), false));
			}
		}

		void verifyChange(ApplyPatchResult result, String name, boolean exists) throws Exception {
			if (inCore) {
				assertEquals(1, result.appliedPaths.size());
				verifyContent(result, name, exists);
			} else {
				assertEquals(1, result.appliedFiles.size());
				verifyContent(result, name, exists);
			}
		}

		/** Like TestRepository#get, but return null for nonexistent entry */
		public RevObject getOrNull(RevTree tree, String path)
				throws Exception {
			/* NOSUBMIT - cut & paste from test Repo. */
			try (RevWalk pool = new RevWalk(db);
					 TreeWalk tw = new TreeWalk(pool.getObjectReader())) {
				tw.setFilter(PathFilterGroup.createFromStrings(Collections
						.singleton(path)));
				tw.reset(tree);
				while (tw.next()) {
					if (tw.isSubtree() && !path.equals(tw.getPathString())) {
						tw.enterSubtree();
						continue;
					}
					final ObjectId entid = tw.getObjectId(0);
					final FileMode entmode = tw.getFileMode(0);
					return pool.lookupAny(entid, entmode.getObjectType());
				}
			}
			return null;
		}

		protected byte[] readBlob(ObjectId treeish, String path) throws Exception {
			try (TestRepository<?> tr = new TestRepository<>(db);
					RevWalk rw = tr.getRevWalk()) {
				db.incrementOpen();
				RevTree tree = rw.parseTree(treeish);
				RevObject obj = getOrNull(tree, path);
				if (obj == null) {
					return null;
				}
				return rw.getObjectReader().open(obj, OBJ_BLOB).getBytes();
			}
		}

		protected void checkBinary(ApplyPatchResult result, int numberOfFiles) throws Exception {
			if (inCore) {
				assertEquals(numberOfFiles, result.appliedPaths.size());
				assertArrayEquals(postImage, readBlob(result.appliedTree, result.appliedPaths.get(0)));
			} else {
				assertEquals(numberOfFiles, result.appliedFiles.size());
				File f = new File(db.getWorkTree(), name);
				assertEquals(f, result.appliedFiles.get(0));
				assertArrayEquals(postImage, Files.readAllBytes(f.toPath()));
			}
		}

		/* tests */

		@Test
		public void testBinaryDelta() throws Exception {
			init("delta");
			checkBinary(applyPatch(), 1);
		}

		@Test
		public void testBinaryLiteral() throws Exception {
			init("literal");
			checkBinary(applyPatch(), 1);
		}

		@Test
		public void testBinaryLiteralAdd() throws Exception {
			init("literal_add", false, true);
			checkBinary(applyPatch(), 1);
		}

		@Test
		public void testModifyM2() throws Exception {
			init("M2", true, true);

			ApplyPatchResult result = applyPatch();

			if (!inCore && FS.DETECTED.supportsExecute()) {
				assertEquals(1, result.appliedFiles.size());
				assertTrue(FS.DETECTED.canExecute(result.appliedFiles.get(0)));
			}

			verifyChange(result, "M2");
		}

		@Test
		public void testModifyM3() throws Exception {
			init("M3", true, true);

			ApplyPatchResult result = applyPatch();

			verifyChange(result, "M3");
			if (!inCore && FS.DETECTED.supportsExecute()) {
				assertFalse(
						FS.DETECTED.canExecute(result.appliedFiles.get(0)));
			}
		}

		@Test
		public void testModifyX() throws Exception {
			init("X");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "X");
		}

		@Test
		public void testModifyY() throws Exception {
			init("Y");

			ApplyPatchResult result = applyPatch();

			verifyChange(result, "Y");
		}

		@Test
		public void testModifyZ() throws Exception {
			init("Z");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "Z");
		}

		@Test
		public void testNonASCII() throws Exception {
			init("NonASCII");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NonASCII");
		}

		@Test
		public void testNonASCII2() throws Exception {
			init("NonASCII2");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NonASCII2");
		}

		@Test
		public void testNonASCIIAdd() throws Exception {
			init("NonASCIIAdd");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NonASCIIAdd");
		}

		@Test
		public void testNonASCIIAdd2() throws Exception {
			init("NonASCIIAdd2", false, true);

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NonASCIIAdd2");
		}

		@Test
		public void testNonASCIIDel() throws Exception {
			init("NonASCIIDel", true, false);

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NonASCIIDel", false);
			assertEquals("NonASCIIDel", result.appliedPaths.get(0));
		}

		@Test
		public void testRenameNoHunks() throws Exception {
			init("RenameNoHunks", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(2, result.appliedPaths.size());
			assertTrue(result.appliedPaths.contains("RenameNoHunks"));
			assertTrue(result.appliedPaths.contains("nested/subdir/Renamed"));

			verifyContent(result,"nested/subdir/Renamed", true);
		}

		@Test
		public void testRenameWithHunks() throws Exception {
			init("RenameWithHunks", true, true);

			ApplyPatchResult result = applyPatch();
			assertEquals(2, result.appliedPaths.size());
			assertTrue(result.appliedPaths.contains("RenameWithHunks"));
			assertTrue(result.appliedPaths.contains("nested/subdir/Renamed"));

			verifyContent(result,"nested/subdir/Renamed", true);
		}

		@Test
		public void testCopyWithHunks() throws Exception {
			init("CopyWithHunks", true, true);

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "CopyResult", true);
		}

		@Test
		public void testShiftUp() throws Exception {
			init("ShiftUp");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "ShiftUp");
		}

		@Test
		public void testShiftUp2() throws Exception {
			init("ShiftUp2");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "ShiftUp2");
		}

		@Test
		public void testShiftDown() throws Exception {
			init("ShiftDown");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "ShiftDown");
		}

		@Test
		public void testShiftDown2() throws Exception {
			init("ShiftDown2");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "ShiftDown2");
		}
	}

	public static class InCore extends Base {

		public InCore() {
			super(true);
		}

		@Test
		public void testBinaryDeltax() throws Exception {
			init("delta");
			checkBinary(applyPatch(), 1);
		}
	}

	public static class WithWorktree extends Base {
		public WithWorktree() { super(false); }

		// NOSUBMIT: NL1 has CRLF in the pre-image, post-image and patch. What to do for inCore ?
		@Test
		public void testModifyNL1() throws Exception {
			init("NL1");

			ApplyPatchResult result = applyPatch();
			verifyChange(result, "NL1");
		}

		@Test
		public void testCrLf() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				init("crlf", true, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, "crlf");
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfOff() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				init("crlf", true, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, "crlf");
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfEmptyCommitted() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				init("crlf3", true, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, "crlf3");
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testCrLfNewFile() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
				init("crlf4", false, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, "crlf4");
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testPatchWithCrLf() throws Exception {
			try {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				init("crlf2", true, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, "crlf2");
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		@Test
		public void testPatchWithCrLf2() throws Exception {
			String name = "crlf2";
			try (Git git = new Git(db)) {
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
				init(name, true, true);
				db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, name);
			} finally {
				db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOCRLF);
			}
		}

		// Clean/smudge filter for testFiltering. The smudgetest test resources were
		// created with C git using a clean filter sed -e "s/A/E/g" and the smudge
		// filter sed -e "s/E/A/g". To keep the test independent of the presence of
		// sed, implement this with a built-in filter.
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
			FilterCommandFactory clean = (repo, in, out) -> new ReplaceFilter(in, out, 'A', 'E');
			FilterCommandFactory smudge = (repo, in, out) -> new ReplaceFilter(in, out, 'E', 'A');
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
				init("smudgetest", true, true);

				ApplyPatchResult result = applyPatch();

				verifyChange(result, name);
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
