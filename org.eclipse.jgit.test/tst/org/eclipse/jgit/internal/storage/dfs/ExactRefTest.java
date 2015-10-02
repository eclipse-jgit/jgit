package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertFalse;

import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExactRefTest {
	private TestRepository<InMemoryRepository> tr;

	private InMemoryRepository repo;

	private static final String REFS_META_CONFIG = "refs/meta/config";

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("test")));
		repo = tr.getRepository();
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
