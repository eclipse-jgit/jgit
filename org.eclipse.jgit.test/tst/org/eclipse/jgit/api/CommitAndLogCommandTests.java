package org.eclipse.jgit.api;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitAndLogCommandTests extends RepositoryTestCase {
	public void testSomeCommits() throws CorruptObjectException,
			UnmergedPathException, IOException {

		// do 4 commits
		Git git = new Git(db);
		git.commit().setMessage("initial commit").run();
		git.commit().setMessage("second commit").setCommitter(committer).run();
		git.commit().setMessage("third commit").setAuthor(author).run();
		git.commit().setMessage("fourth commit").setAuthor(author)
				.setCommitter(committer).run();

		// check that all commits came in correctly
		String defaultCommitter = new PersonIdent(db).getName();
		String expectedAuthors[] = new String[] { defaultCommitter,
				committer.getName(), author.getName(), author.getName() };
		String expectedCommitters[] = new String[] { defaultCommitter,
				committer.getName(), defaultCommitter, committer.getName() };
		String expectedMessages[] = new String[] { "initial", "second",
				"third", "fourth" };
		int l = expectedAuthors.length - 1;
		for (RevCommit c : git.log().run()) {
			assertEquals(expectedAuthors[l], c.getAuthorIdent().getName());
			assertEquals(expectedCommitters[l], c.getCommitterIdent().getName());
			assertTrue(c.getFullMessage().startsWith(expectedMessages[l]));
			l--;
		}
		assertTrue(l==-1);
	}

	// try to do a commit without specifying a message. Should fail!
	public void testWrongParams() throws CorruptObjectException, UnmergedPathException, IOException {
		Git git = new Git(db);
		try {
			git.commit().setAuthor(author).run();
			fail("Didn't got the expected exception");
		} catch (IllegalArgumentException e) {
		}
	}
}
