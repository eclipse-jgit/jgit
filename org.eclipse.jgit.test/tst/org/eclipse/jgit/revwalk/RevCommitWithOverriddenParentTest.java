package org.eclipse.jgit.revwalk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.Before;
import org.junit.Test;

public class RevCommitWithOverriddenParentTest {
	private TestRepository<InMemoryRepository> tr;

	private RevWalk rw;

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("test")));
		rw = tr.getRevWalk();
	}

	@Test
	public void testParseBody() throws Exception {
		RevCommit a = tr.commit().add("a", "foo").create();
		RevCommit b = tr.commit().parent(a).add("b", "bar").create();
		RevCommit c = tr.commit().parent(b).message("commit3").add("a", "foo'")
				.create();

		RevCommit cBar = new RevCommit(c.getId()) {
			@Override
			public int getParentCount() {
				return 1;
			}

			@Override
			public RevCommit getParent(int nth) {
				return a;
			}

			@Override
			public RevCommit[] getParents() {
				return new RevCommit[] { a };
			}
		};

		rw.parseBody(cBar);
		assertEquals(a, Arrays.stream(cBar.getParents()).findFirst().get());
		assertEquals("commit3", cBar.getFullMessage());
		assertEquals("foo'", blobAsString(cBar, "a"));
	}

	@Test
	public void testParseCommit() throws Exception {
		RevCommit a = tr.commit().add("a", "foo").create();
		RevCommit b = tr.commit().parent(a).add("b", "bar").create();
		RevCommit c = tr.commit().parent(b).message("commit3").add("a", "foo'")
				.create();

		RevCommit cBar = new RevCommit(c.getId()) {
			@Override
			public int getParentCount() {
				return 1;
			}

			@Override
			public RevCommit getParent(int nth) {
				return a;
			}

			@Override
			public RevCommit[] getParents() {
				return new RevCommit[] { a };
			}
		};

		rw.updateCommit(cBar);
		RevCommit parsed = rw.parseCommit(cBar.getId());

		assertEquals(c.getId(), parsed.getId());
		assertArrayEquals(cBar.getParents(), parsed.getParents());
	}

	@Test
	public void testFilter() throws Exception {
		RevCommit a = tr.commit().add("a", "foo").create();
		RevCommit b = tr.commit().parent(a).add("b", "bar").create();
		RevCommit c = tr.commit().parent(b).message("commit3").add("a", "foo'")
				.create();

		RevCommit cBar = new RevCommit(c.getId()) {
			@Override
			public int getParentCount() {
				return 1;
			}

			@Override
			public RevCommit getParent(int nth) {
				return a;
			}

			@Override
			public RevCommit[] getParents() {
				return new RevCommit[] { a };
			}
		};

		rw.setRevFilter(RevFilter.ALL);
		rw.markStart(cBar);
		assertSame(cBar, rw.next());
		assertSame(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFlag() throws Exception {
		RevCommit root = tr.commit().add("todelete", "to be deleted").create();
		RevCommit orig = tr.commit().parent(root).rm("todelete")
				.add("foo", "foo contents").add("bar", "bar contents")
				.add("dir/baz", "baz contents").create();

		RevCommit commitOrigBar = new RevCommit(orig.getId()) {
			@Override
			public int getParentCount() {
				return 1;
			}

			@Override
			public RevCommit getParent(int nth) {
				return root;
			}

			@Override
			public RevCommit[] getParents() {
				return new RevCommit[] { root };
			}
		};

		assertEquals(RevObject.PARSED, orig.flags);
		assertEquals(0, commitOrigBar.flags);
		commitOrigBar.parseBody(rw);
		assertEquals(RevObject.PARSED, commitOrigBar.flags);
	}

	private String blobAsString(AnyObjectId treeish, String path)
			throws Exception {
		RevObject obj = tr.get(rw.parseTree(treeish), path);
		assertSame(RevBlob.class, obj.getClass());
		ObjectLoader loader = rw.getObjectReader().open(obj);
		return new String(loader.getCachedBytes(), UTF_8);
	}
}
