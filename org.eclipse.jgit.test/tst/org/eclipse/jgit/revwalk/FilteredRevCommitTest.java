package org.eclipse.jgit.revwalk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.junit.Before;
import org.junit.Test;

public class FilteredRevCommitTest {
	private TestRepository<InMemoryRepository> tr;

	private RevWalk rw;

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("test")));
		rw = tr.getRevWalk();
	}

	@Test
	public void testParseBody_noParent() throws Exception {
		RevCommit root = tr.commit().add("todelete", "to be deleted").create();
		RevCommit orig = tr.commit().parent(root).rm("todelete")
				.add("foo", "foo contents").add("bar", "bar contents")
				.add("dir/baz", "baz contents").create();
		FilteredRevCommit filteredRevCommit = new FilteredRevCommit(orig);
		filteredRevCommit.parseBody(rw);
		tr.branch("master").update(filteredRevCommit);
		assertEquals("foo contents", blobAsString(filteredRevCommit, "foo"));
		assertEquals("bar contents", blobAsString(filteredRevCommit, "bar"));
		assertEquals("baz contents",
				blobAsString(filteredRevCommit, "dir/baz"));
	}

	@Test
	public void testParseBody_withParents() throws Exception {
		RevCommit commit1 = tr.commit().add("foo", "foo contents\n").create();
		RevCommit commit2 = tr.commit().parent(commit1)
				.message("original message").add("bar", "bar contents")
				.create();
		RevCommit commit3 = tr.commit().parent(commit2).message("commit3")
				.add("foo", "foo contents\n new line\n").create();

		FilteredRevCommit filteredCommitHead = new FilteredRevCommit(commit3,
				Arrays.asList(commit1));

		rw.parseBody(filteredCommitHead);
		assertEquals(commit1, Arrays.stream(filteredCommitHead.getParents())
				.findFirst().get());
		assertEquals("commit3", filteredCommitHead.getFullMessage());
		assertEquals("foo contents\n new line\n",
				blobAsString(filteredCommitHead, "foo"));
	}

	@Test
	public void testParseCommit_withParents() throws Exception {
		RevCommit commit1 = tr.commit().add("foo", "foo contents\n").create();
		RevCommit commit2 = tr.commit().parent(commit1)
				.message("original message").add("bar", "bar contents")
				.create();
		RevCommit commit3 = tr.commit().parent(commit2).message("commit3")
				.add("foo", "foo contents\n new line\n").create();

		FilteredRevCommit filteredCommitHead = new FilteredRevCommit(commit3,
				Arrays.asList(commit1));
		rw.updateCommit(filteredCommitHead);

		RevCommit parsedCommit = rw.parseCommit(filteredCommitHead.getId());

		assertEquals(filteredCommitHead.getId(), commit3.getId());
		assertEquals(Arrays.stream(parsedCommit.getParents()).findFirst().get(),
				Arrays.stream(filteredCommitHead.getParents()).findFirst()
						.get());
		assertNotEquals(
				Arrays.stream(parsedCommit.getParents()).findFirst().get(),
				Arrays.stream(commit3.getParents()).findFirst().get());
	}

	@Test
	public void testFlag() throws Exception {
		RevCommit root = tr.commit().add("todelete", "to be deleted").create();
		RevCommit orig = tr.commit().parent(root).rm("todelete")
				.add("foo", "foo contents").add("bar", "bar contents")
				.add("dir/baz", "baz contents").create();

		FilteredRevCommit filteredRevCommit = new FilteredRevCommit(orig,
				Arrays.asList(root));
		assertEquals(RevObject.PARSED, orig.flags);
		assertEquals(0, filteredRevCommit.flags);
		filteredRevCommit.parseBody(rw);
		assertEquals(RevObject.PARSED, filteredRevCommit.flags);
	}

	private String blobAsString(AnyObjectId treeish, String path)
			throws Exception {
		RevObject obj = tr.get(rw.parseTree(treeish), path);
		assertSame(RevBlob.class, obj.getClass());
		ObjectLoader loader = rw.getObjectReader().open(obj);
		return new String(loader.getCachedBytes(), UTF_8);
	}
}
