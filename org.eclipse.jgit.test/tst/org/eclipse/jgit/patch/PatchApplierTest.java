package org.eclipse.jgit.patch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.Arrays;
import java.util.Collection;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.junit.TestRepository;
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
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ //
		PatchApplierTest.Text.class, //
		PatchApplierTest.Binary.class, //
})
public class PatchApplierTest {

	public abstract static class Base extends RepositoryTestCase {

		protected String name;
		protected RawText expectedText;
		protected RevTree baseTip;

		protected void init(boolean inCore, final String name, final boolean preExists,
				final boolean postExists) throws Exception {
			this.name = name;
			if (postExists) {
				expectedText = new RawText(readFile(name + "_PostImage"));
			}
			if (inCore) {
				initInCore(preExists);
			} else {
				initWithWorkTree(preExists);
			}
		}

		protected void initWithWorkTree(final boolean preExists)
				throws Exception {
			try (Git git = new Git(db)) {
				if (preExists) {
					RawText originText = new RawText(readFile(name + "_PreImage"));
					write(new File(db.getDirectory().getParent(), name),
							originText.getString(0, originText.size(), false));

					git.add().addFilepattern(name).call();
					git.commit().setMessage("PreImage").call();
				}
			}
		}

		protected void initInCore(final boolean preExists) throws Exception {
			try (Git git = new Git(db)) {
				if (preExists) {
					RawText originText = new RawText(readFile(name + "_PreImage"));
					writeTrashFile(name, new String(originText.getRawContent(), StandardCharsets.UTF_8));
					git.add().addFilepattern(name).call();
				}
				RevCommit base = git.commit().setMessage("PreImage").call();
				baseTip = base.getTree();
				if (preExists) {
					git.rm().addFilepattern(name).call();
				}
			}
		}

		protected ApplyPatchResult applyPatch(boolean inCore)
				throws PatchApplyException, PatchFormatException {
			if (inCore) {
				try (ObjectInserter oi = db.newObjectInserter()) {
					return new PatchApplier(db, baseTip, oi).applyPatch(
							getTestResource(name + ".patch"));
				}
			}
			return new PatchApplier(db).applyPatch(getTestResource(name + ".patch"));
		}

		protected static byte[] readFile(String patchFile) throws IOException {
			final InputStream in = getTestResource(patchFile);
			if (in == null) {
				fail("No " + patchFile + " test vector");
				return null; // Never happens
			}
			try {
				final byte[] buf = new byte[1024];
				final ByteArrayOutputStream temp = new ByteArrayOutputStream();
				int n;
				while ((n = in.read(buf)) > 0) {
					temp.write(buf, 0, n);
				}
				return temp.toByteArray();
			} finally {
				in.close();
			}
		}

		protected static InputStream getTestResource(String patchFile) {
			return PatchApplierTest.class.getClassLoader()
					.getResourceAsStream("org/eclipse/jgit/diff/" + patchFile);
		}
	}

	@RunWith(Parameterized.class)
	public static class Text extends Base {

		@Parameter(0)
		public boolean inCore;

		@Parameters(name = "inCore={0}")
		public static Collection<Boolean> getUseAnnotatedTagsValues() {
			return Arrays.asList(new Boolean[]{Boolean.TRUE, Boolean.FALSE});
		}

		private void init(final String name, final boolean preExists,
				final boolean postExists) throws Exception {
			super.init(inCore, name, preExists, postExists);
		}

		private ApplyPatchResult applyPatch()
				throws PatchApplyException, PatchFormatException {
			return super.applyPatch(inCore);
		}

		private void verifyChange(ApplyPatchResult result, String name) throws Exception {
			assertEquals(1, result.appliedPaths.size());
			if (inCore) {
				verifyInCoreChange(result, name);
			} else {
				verifyWorkTreeChange(result, name);
			}
		}

		private void verifyWorkTreeChange(ApplyPatchResult result, String name) throws IOException {
			assertEquals(new File(db.getWorkTree(), name),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), name),
					expectedText.getString(0, expectedText.size(), false));
		}

		private void verifyInCoreChange(ApplyPatchResult result, String name) throws Exception {
			checkInputStream(new ByteArrayInputStream(readBlob(result.appliedTree, name).getRawContent()),
					expectedText.getString(0, expectedText.size(), false));
		}

		private RawText readBlob(ObjectId treeish, String path) throws Exception {
			try (TestRepository<?> tr = new TestRepository<>(db);
					RevWalk rw = tr.getRevWalk()) {
				db.incrementOpen();
				RevTree tree = rw.parseTree(treeish);
				RevObject obj = tr.get(tree, path);
				if (obj == null) {
					return null;
				}
				return new RawText(
						rw.getObjectReader().open(obj, OBJ_BLOB).getBytes());
			}
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

	public static class Binary extends Base {

		private void init(final String name, final boolean preExists,
				final boolean postExists) throws Exception {
			super.init(false, name, preExists, postExists);
		}

		private void init(final String name) throws Exception {
			init(name, true, true);
		}

		private ApplyPatchResult applyPatch()
				throws PatchApplyException, PatchFormatException {
			return super.applyPatch(false);
		}

		private void checkBinary(String name, boolean hasPreImage)
				throws Exception {
			checkBinary(name, hasPreImage, 1);
		}

		private void checkBinary(String name, boolean hasPreImage,
				int numberOfFiles) throws Exception {
			try (Git git = new Git(db)) {
				byte[] post = IO
						.readWholeStream(getTestResource(name + "_PostImage"), 0)
						.array();
				File f = new File(db.getWorkTree(), name);
				if (hasPreImage) {
					byte[] pre = IO
							.readWholeStream(getTestResource(name + "_PreImage"), 0)
							.array();
					Files.write(f.toPath(), pre);
					git.add().addFilepattern(name).call();
					git.commit().setMessage("PreImage").call();
				}
				ApplyPatchResult result = new PatchApplier(db).applyPatch(getTestResource(name + ".patch"));
				assertEquals(numberOfFiles, result.appliedFiles.size());
				assertEquals(f, result.appliedFiles.get(0));
				assertArrayEquals(post, Files.readAllBytes(f.toPath()));
			}
		}

		@Test
		public void testModifyM2() throws Exception {
			init("M2", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			if (FS.DETECTED.supportsExecute()) {
				assertTrue(FS.DETECTED.canExecute(result.appliedFiles.get(0)));
			}
			checkFile(new File(db.getWorkTree(), "M2"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testModifyM3() throws Exception {
			init("M3", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			if (FS.DETECTED.supportsExecute()) {
				assertFalse(
						FS.DETECTED.canExecute(result.appliedFiles.get(0)));
			}
			checkFile(new File(db.getWorkTree(), "M3"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testModifyX() throws Exception {
			init("X");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "X"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "X"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testModifyY() throws Exception {
			init("Y");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "Y"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "Y"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testModifyZ() throws Exception {
			init("Z");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "Z"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "Z"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testModifyNL1() throws Exception {
			init("NL1");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NL1"), result
					.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "NL1"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testNonASCII() throws Exception {
			init("NonASCII");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NonASCII"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "NonASCII"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testNonASCII2() throws Exception {
			init("NonASCII2");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NonASCII2"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "NonASCII2"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testNonASCIIAdd() throws Exception {
			init("NonASCIIAdd");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NonASCIIAdd"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "NonASCIIAdd"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testNonASCIIAdd2() throws Exception {
			init("NonASCIIAdd2", false, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NonASCIIAdd2"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "NonASCIIAdd2"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testNonASCIIDel() throws Exception {
			init("NonASCIIDel", true, false);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "NonASCIIDel"),
					result.appliedFiles.get(0));
			assertFalse(new File(db.getWorkTree(), "NonASCIIDel").exists());
		}

		@Test
		public void testRenameNoHunks() throws Exception {
			init("RenameNoHunks", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "RenameNoHunks"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testRenameWithHunks() throws Exception {
			init("RenameWithHunks", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "RenameWithHunks"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testCopyWithHunks() throws Exception {
			init("CopyWithHunks", true, true);

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "CopyWithHunks"), result.appliedFiles
					.get(0));
			checkFile(new File(db.getWorkTree(), "CopyResult"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testShiftUp() throws Exception {
			init("ShiftUp");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "ShiftUp"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "ShiftUp"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testShiftUp2() throws Exception {
			init("ShiftUp2");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "ShiftUp2"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "ShiftUp2"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testShiftDown() throws Exception {
			init("ShiftDown");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "ShiftDown"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "ShiftDown"),
					expectedText.getString(0, expectedText.size(), false));
		}

		@Test
		public void testShiftDown2() throws Exception {
			init("ShiftDown2");

			ApplyPatchResult result = applyPatch();

			assertEquals(1, result.appliedFiles.size());
			assertEquals(new File(db.getWorkTree(), "ShiftDown2"),
					result.appliedFiles.get(0));
			checkFile(new File(db.getWorkTree(), "ShiftDown2"),
					expectedText.getString(0, expectedText.size(), false));
		}
	}
}
