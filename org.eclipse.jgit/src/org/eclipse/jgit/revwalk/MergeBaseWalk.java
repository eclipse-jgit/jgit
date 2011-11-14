package org.eclipse.jgit.revwalk;

import java.io.*;
import java.util.*;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;

/**
 */
public class MergeBaseWalk extends RevWalk {

	private RevFlag startFlag;
	private RevFlag mergeBaseFlag;
	private RevFlag processedFlag;

	public MergeBaseWalk(Repository repo) {
		super(repo);

		init();
	}

	public MergeBaseWalk(ObjectReader or) {
		super(or);

		init();
	}

	@Override
	protected RevCommit createCommit(AnyObjectId id) {
		return new Commit(id);
	}

	@Override
	public void markStart(RevCommit c) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		assertNotStarted(); // we have to know all roots before the RevWalk is started otherwise allocated BitSets might not match anymore
		assertRevCommitType(c);
		super.markStart(c);
		c.add(startFlag);
	}

	@Override
	protected void reset(int retainFlags) {
		super.reset(retainFlags);
		queue = new Queue();
	}

	private void propagateReachabilityToParents(Commit commit) {
		if (commit.has(mergeBaseFlag)) {
			return;
		}

		if (commit.reachability == null) {
			commit.reachability = new BitSet(roots.size());
		}

		if (commit.has(startFlag)) {
			commit.reachability.set(roots.indexOf(commit));
			commit.remove(startFlag);
		}

		final BitSet reachability = commit.reachability;
		commit.reachability = null;
		commit.add(processedFlag);
		if (reachability.cardinality() == roots.size()) {
			commit.add(mergeBaseFlag);
			carryFlagsImpl(commit);
			return;
		}

		final RevCommit[] parents = commit.getParents();
		if (parents == null) {
			return;
		}

		for (RevCommit parentCommit : parents) {
			final Commit parent = assertRevCommitType(parentCommit);
			if (parent.reachability == null) {
				if (parent.has(processedFlag)) {
					carryReachabilityOfLateCommitToParents(parent, reachability, new HashSet<Commit>());
				} else {
					parent.reachability = reachability;
				}
			} else {
				final BitSet jointReachability = new BitSet(roots.size());
				jointReachability.or(reachability);
				jointReachability.or(parent.reachability);
				parent.reachability = jointReachability;
			}

			parent.flags |= processedFlag.mask;
		}
	}

	private void carryReachabilityOfLateCommitToParents(Commit commit,
	                                                    BitSet reachability,
	                                                    Set<Commit> commitsCarriedTo) {
		commitsCarriedTo.add(commit);

		final BitSet existingReachability = commit.reachability;
		if (existingReachability != null) {
			// we have reached the "bitset" front.
			final BitSet jointReachability = new BitSet(roots.size());
			jointReachability.or(reachability);
			jointReachability.or(existingReachability);
			commit.reachability = jointReachability;
			return;
		}

		if (!commit.has(processedFlag)) {
			// Not yet processed parent, let it be processed in the
			// normal way.
			return;
		}

		for (RevCommit parentCommit : commit.getParents()) {
			final Commit parent = assertRevCommitType(parentCommit);
			if (!commitsCarriedTo.contains(parent)) {
				carryReachabilityOfLateCommitToParents(parent, reachability,
				                                       commitsCarriedTo);
			}
		}
	}

	private static Commit assertRevCommitType(RevCommit c) {
		if (c == null) {
			return null;
		}

		if (!(c instanceof Commit)) {
			throw new IllegalArgumentException("Commit '" + c + "' must have been created through this RevWalk instance.");
		}

		return (Commit)c;
	}

	private static class Commit extends RevCommit {
		private BitSet reachability;

		private Commit(AnyObjectId id) {
			super(id);
		}
	}

	private void init() {
		startFlag = newFlag("start");
		mergeBaseFlag = newFlag("merge-base");
		processedFlag = newFlag("processed");
		queue = new Queue();

		carry(mergeBaseFlag);
	}

	private class Queue extends DateRevQueue {
		@Override
		public void add(RevCommit c) {
			final Commit commit = assertRevCommitType(c);
			if (commit.has(mergeBaseFlag)) {
				// Commit has the mergeBase flag set, i.e. it's an ancestor of the merge base. Hence don't report.
				return;
			}

			super.add(c);
		}

		@Override
		public RevCommit next() {
			final Commit commit = assertRevCommitType(super.next());
			if (commit == null) {
				return null;
			}

			propagateReachabilityToParents(commit);
			return commit;
		}
	}
}