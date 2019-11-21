package org.eclipse.jgit.revwalk;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Fake implementation of rev commit for test usage.
 */
public class FakeRevCommit extends RevCommit {
	public FakeRevCommit(AnyObjectId id) {
		super(id);
	}

	public void setParents(RevCommit[] parents) {
		this.parents = parents;
	}
}