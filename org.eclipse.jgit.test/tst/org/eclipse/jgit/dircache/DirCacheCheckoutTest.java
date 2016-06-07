package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class DirCacheCheckoutTest extends RepositoryTestCase {

	@Test
	public void testSkipConflicts() throws Exception {
		RevCommit headCommit = commitFile("a", "initial content",
				"master");
		RevCommit checkoutCommit = commitFile("a", "side content", "side");
		writeTrashFile("a", "changed in work dir");

		DirCacheCheckout dco = createDirCacheCheckout(headCommit, checkoutCommit);
		dco.setFailOnConflict(false);
		dco.setSkipConflicts(true);

		boolean checkoutOk = dco.checkout();

		assertTrue(checkoutOk);
		assertEquals("a", dco.getConflicts().get(0));
	}

	private DirCacheCheckout createDirCacheCheckout(RevCommit headCommit,
			RevCommit checkoutCommit)
			throws IOException {
		return new DirCacheCheckout(db, headCommit.getTree(),
				db.lockDirCache(), checkoutCommit.getTree());
	}
}
