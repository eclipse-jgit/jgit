package org.eclipse.jgit.http.test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.RefDatabase;

/**
 * An {@link InMemoryRepository} whose refs can be made unreadable for testing
 * purposes.
 */
class RefsUnreadableInMemoryRepository extends InMemoryRepository {

	private final RefsUnreadableRefDatabase refs;

	private volatile boolean failing;

	RefsUnreadableInMemoryRepository(DfsRepositoryDescription repoDesc) {
		super(repoDesc);
		refs = new RefsUnreadableRefDatabase();
		failing = false;
	}

	@Override
	public RefDatabase getRefDatabase() {
		return refs;
	}

	/**
	 * Make the ref database unable to scan its refs.
	 * <p>
	 * It may be useful to follow a call to startFailing with a call to
	 * {@link RefDatabase#refresh()}, ensuring the next ref read fails.
	 */
	void startFailing() {
		failing = true;
	}

	private class RefsUnreadableRefDatabase extends MemRefDatabase {

		@Override
		protected RefCache scanAllRefs() throws IOException {
			if (failing) {
				throw new IOException("disk failed, no refs found");
			} else {
				return super.scanAllRefs();
			}
		}
	}
}
