package org.eclipse.jgit.revwalk;

import java.io.*;
import java.util.*;

/**
 * @author Marc Strapetz
 *
 *         A queue of commits which will discard ancestors of commits which are
 *         common to all start commits. It helps to reduce running time of
 *         certain queries, for instance queries which require topological order
 *         of commits, but only for a subset of the complete database.
 *
 *         When installing the queue for a RevWalk, the RevWalk will be
 *         configured properly to work with the queue. After installing the
 *         queue, markStart-methods of the RevWalk must not be called. When
 *         resetting the RevWalk, the queue will be uninstalled.
 */
public class CommonCommitLimitedRevQueue extends DateRevQueue {

	public static CommonCommitLimitedRevQueue configureRevWalk(RevWalk revWalk,
			List<RevCommit> startCommits,
			ReachabilityRegistry reachabilityRegistry) throws IOException {
		final RevFlag processedFlag = revWalk
				.newFlag("CommonCommitLimitedRevQueue-processed");
		final CommonCommitLimitedRevQueue queue = new CommonCommitLimitedRevQueue(
				startCommits, reachabilityRegistry, processedFlag);
		revWalk.reset();
		revWalk.queue = queue;
		revWalk.markStart(startCommits);
		revWalk.sort(RevSort.COMMIT_TIME_DESC);
		return queue;
	}

	private final List<RevCommit> startCommits;

	private final RevFlag processedFlag;

	private final Map<RevCommit, BitSet> commitToReachability;

	private final ReachabilityRegistry reachabilityRegistry;

	private CommonCommitLimitedRevQueue(List<RevCommit> startCommits,
			ReachabilityRegistry reachabilityRegistry, RevFlag processedFlag) {
		this.startCommits = startCommits;
		this.processedFlag = processedFlag;
		this.commitToReachability = new HashMap<RevCommit, BitSet>();
		this.reachabilityRegistry = reachabilityRegistry;

		clear();
	}

	@Override
	public void add(RevCommit c) {
		final BitSet reachability = commitToReachability.get(c);
		if (reachability == null) {
			return;
		}

		super.add(c);
	}

	@Override
	public RevCommit next() {
		final RevCommit commit = super.next();
		if (commit == null) {
			return null;
		}

		propagateReachabilityToParents(commit, startCommits);
		return commit;
	}

	@Override
	public void clear() {
		super.clear();

		commitToReachability.clear();

		for (int index = 0; index < startCommits.size(); index++) {
			final RevCommit commit = startCommits.get(index);
			final BitSet reachability = new BitSet(startCommits.size());
			reachability.set(index);
			commitToReachability.put(commit, reachability);
			commit.flags |= processedFlag.mask;
			if (reachabilityRegistry != null) {
				reachabilityRegistry.registerReachability(commit, commit);
			}
		}
	}

	private void propagateReachabilityToParents(RevCommit commit,
			List<RevCommit> startCommits) {
		final BitSet reachability = commitToReachability.remove(commit);
		if (reachability == null
				|| reachability.cardinality() == startCommits.size()) {
			return;
		}

		final RevCommit[] parents = commit.getParents();
		if (parents == null) {
			return;
		}

		for (RevCommit parent : parents) {
			final BitSet parentReachability = commitToReachability.get(parent);
			if (parentReachability == null) {
				if ((parent.flags & processedFlag.mask) != 0) {
					propagateReachabilityOfLateCommit(parent, reachability,
							new HashSet<RevCommit>());
				} else {
					commitToReachability.put(parent, reachability);
				}
			} else {
				final BitSet newReachability = new BitSet(startCommits.size());
				newReachability.or(reachability);
				newReachability.or(parentReachability);
				commitToReachability.put(parent, newReachability);
			}

			parent.flags |= processedFlag.mask;

			if (reachabilityRegistry != null) {
				for (int index = 0; index < startCommits.size(); index++) {
					if (reachability.get(index)) {
						reachabilityRegistry.registerReachability(
								startCommits.get(index), parent);
					}
				}
			}
		}
	}

	private void propagateReachabilityOfLateCommit(RevCommit commit,
			BitSet reachability, Set<RevCommit> propagateParents) {
		final BitSet existingReachability = commitToReachability.get(commit);
		if (existingReachability != null) { // we have reached the "bitset"
											// front.
			final BitSet newReachability = new BitSet(startCommits.size());
			newReachability.or(reachability);
			newReachability.or(existingReachability);
			commitToReachability.put(commit, newReachability);
			return;
		}

		if ((commit.flags & processedFlag.mask) == 0) {
			// Not yet processed parent, stop.
			return;
		}

		for (RevCommit parentCommit : commit.getParents()) {
			if (!propagateParents.contains(parentCommit)) {
				propagateReachabilityOfLateCommit(parentCommit, reachability,
						propagateParents);
			}
		}
	}

	public static interface ReachabilityRegistry {
		void registerReachability(RevCommit startCommit, RevCommit commit);
	}
}