package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertFalse;

import java.util.Map;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExactRefTest extends LocalDiskRepositoryTestCase {
	private FileRepository repo;

	private static final String REFS_META_CONFIG = "refs/meta/config";

	@Before
	public void setUp() throws Exception {
		repo = createBareRepository();
	}

	@After
	public void tearDown() {
		repo.close();
	}

	@Test
	public void nullHeadForEmptyRepo() throws Exception {
		Map<String, Ref> refs = repo.getRefDatabase().exactRef(HEAD,
				REFS_META_CONFIG);
		assertFalse(refs.containsKey(HEAD));
		assertFalse(refs.containsKey(REFS_META_CONFIG));
	}

	@Test
	public void nullHeadForHeadSymlinkedToNonexistentRef() throws Exception {
		RefUpdate u = repo.updateRef(HEAD);
		u.disableRefLog();
		u.link("refs/heads/master");

		Map<String, Ref> refs = repo.getRefDatabase().exactRef(HEAD,
				REFS_META_CONFIG);
		assertFalse(refs.containsKey(HEAD));
		assertFalse(refs.containsKey(REFS_META_CONFIG));
	}

}
