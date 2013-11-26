package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.MergeDriver;
import org.eclipse.jgit.merge.MergeDriverRegistry;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MergeDriverTest extends RepositoryTestCase {
	private Git git;

	private RevCommit branchCommit;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		writeTrashFile("b.txt", "initial content");
		writeTrashFile("c.txt", "initial content");
		writeTrashFile("d.txt", "initial content");
		git.add().addFilepattern("b.txt").addFilepattern("c.txt").addFilepattern("d.txt").call();
		RevCommit initialCommit = git.commit().setMessage("initial commit")
				.call();

		createBranch(initialCommit, "refs/heads/side");

		writeTrashFile("a.txt", "new ours file");
		writeTrashFile("b.txt", "changed ours content");
		writeTrashFile("d.txt", "changed ours content");
		git.rm().addFilepattern("c.txt").call();
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").addFilepattern("d.txt").call();
		git.commit().setMessage("master").call();

		checkoutBranch("refs/heads/side");

		writeTrashFile("a.txt", "new theirs file");
		writeTrashFile("b.txt", "changed theirs content");
		writeTrashFile("c.txt", "changed theirs content");
		git.rm().addFilepattern("d.txt").call();
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").addFilepattern("c.txt").call();
		branchCommit = git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");
	}

	@Override
	@After
	public void tearDown() throws Exception {
		MergeDriverRegistry.clear();
		super.tearDown();
	}

	@Test
	public void testDriverAssociation() {
		MergeDriver failing = new FailingDriver();
		MergeDriver ours = new Ours();
		MergeDriver theirs = new Theirs();

		MergeDriverRegistry.registerDriver(failing);
		MergeDriverRegistry.registerDriver(ours);
		MergeDriverRegistry.registerDriver(theirs);

		// empty registry : null (no failure)
		assertNull(MergeDriverRegistry.findMergeDriver("a.txt"));

		// register a single driver
		String namePattern = "*.txt";
		MergeDriverRegistry.associate(namePattern, failing.getName());
		assertSame(failing, MergeDriverRegistry.findMergeDriver("a.txt"));

		// register driver on existing pattern : override
		MergeDriverRegistry.associate(namePattern, ours.getName());
		assertSame(ours, MergeDriverRegistry.findMergeDriver("a.txt"));

		// asking driver on unmatched pattern : null (no failure)
		assertNull(MergeDriverRegistry.findMergeDriver("a"));

		// registering missing driver on pattern : null (no failure)
		MergeDriverRegistry.associate("abc", "missing");
		assertNull(MergeDriverRegistry.findMergeDriver("abc"));
	}

	@Test
	public void testOursMerge() throws Exception {
		// a.txt: ours added, theirs added : merge takes ours
		// b.txt : ours changed, theirs changed : merge takes ours
		/*
		 * One side deleted, the other changed (c.txt and d.txt). Considered a
		 * "trivial merge" by git, won't call our custom driver... and thus the
		 * "changed" side is taken and a conflict marked.
		 */
		MergeDriver driver = new Ours();
		MergeDriverRegistry.registerDriver(driver);
		MergeDriverRegistry.associate("*.txt", driver.getName());
		MergeResult result = git.merge().include(branchCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertTrue(new File(db.getWorkTree(), "b.txt").exists());
		assertEquals("new ours file", read("a.txt"));
		assertEquals("changed ours content", read("b.txt"));

		assertEquals(2, result.getConflicts().size());
		assertTrue(result.getConflicts().containsKey("c.txt"));
		assertTrue(result.getConflicts().containsKey("d.txt"));

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	@Test
	public void testTheirsMerge() throws Exception {
		// a.txt: ours added, theirs added : merge takes theirs
		// b.txt : ours changed, theirs changed : merge takes theirs
		/*
		 * One side deleted, the other changed (c.txt and d.txt). Considered a
		 * "trivial merge" by git, won't call our custom driver... and thus the
		 * "changed" side is taken and a conflict marked.
		 */
		MergeDriver driver = new Theirs();
		MergeDriverRegistry.registerDriver(driver);
		MergeDriverRegistry.associate("*.txt", driver.getName());
		MergeResult result = git.merge().include(branchCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertTrue(new File(db.getWorkTree(), "b.txt").exists());
		assertEquals("new theirs file", read("a.txt"));
		assertEquals("changed theirs content", read("b.txt"));

		assertEquals(2, result.getConflicts().size());
		assertTrue(result.getConflicts().containsKey("c.txt"));
		assertTrue(result.getConflicts().containsKey("d.txt"));

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	private static class Theirs implements MergeDriver {
		public boolean merge(Repository repository, File ours, File theirs,
				File base, String[] commitNames) throws IOException {
			// Use their version
			FS.DETECTED.copyFile(theirs, ours);

			/*
			 * If we've been called, there was a conflict on this file. However,
			 * we've resolved it by using "theirs" version, tell the caller that
			 * there are no conflicting chunks left.
			 */
			return true;
		}

		public String getName() {
			return "theirs";
		}
	}

	private static class Ours implements MergeDriver {
		public boolean merge(Repository repository, File ours, File theirs,
				File base, String[] commitNames) throws IOException {
			// No need for any explicit action. The local file will be kept.
			/*
			 * If we've been called, there was a conflict on this file. However,
			 * we've resolved it by using "ours" version, tell the caller that
			 * there are no conflicting chunks left.
			 */
			return true;
		}

		public String getName() {
			return "ours";
		}
	}

	private static class FailingDriver implements MergeDriver {
		public boolean merge(Repository repository, File ours, File theirs,
				File base, String[] commitNames) throws IOException {
			throw new RuntimeException();
		}

		public String getName() {
			return "failing";
		}
	}
}
