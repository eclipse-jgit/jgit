package org.eclipse.jgit.revwalk;

import java.io.*;
import java.util.*;

/**
 * @author Marc Strapetz
 */
public class CommitReachability {

	public static boolean isMergedIntoAny(final RevCommit base,
			Collection<RevCommit> tipsColl, RevWalk revWalk) throws IOException {
		final List<RevCommit> tipCommits = new ArrayList<RevCommit>(tipsColl);
		final List<RevCommit> startCommits = getStartCommit(tipCommits,
				Collections.singletonList(base));
		final boolean[] merged = { false };
		CommonCommitLimitedRevQueue.configureRevWalk(revWalk, startCommits,
				new CommonCommitLimitedRevQueue.ReachabilityRegistry() {
					public void registerReachability(RevCommit startCommit,
							RevCommit commit) {
						if (commit == base && tipCommits.contains(startCommit)) {
							merged[0] = true;
						}
					}
				});

		for (RevCommit commit : revWalk) {
			if (merged[0]) {
				return true;
			}
		}

		return false;
	}

	public static Set<RevCommit> getTipsThatContainsBase(final RevCommit base,
			Collection<RevCommit> tipsColl, RevWalk revWalk) throws IOException {
		final List<RevCommit> tipCommits = new ArrayList<RevCommit>(tipsColl);
		final List<RevCommit> startCommits = getStartCommit(tipCommits,
				Collections.singletonList(base));
		final Set<RevCommit> reachableTips = new HashSet<RevCommit>();
		CommonCommitLimitedRevQueue.configureRevWalk(revWalk, startCommits,
				new CommonCommitLimitedRevQueue.ReachabilityRegistry() {
					public void registerReachability(RevCommit startCommit,
							RevCommit commit) {
						if (commit == base && tipCommits.contains(startCommit)) {
							reachableTips.add(startCommit);
						}
					}
				});

		for (RevCommit commit : revWalk) {
			// run the walk
		}

		return reachableTips;
	}

	public static boolean isAnyMergedInto(final Collection<RevCommit> bases,
			final RevCommit tip, RevWalk revWalk) throws IOException {
		final List<RevCommit> startCommits = getStartCommit(
				Collections.singletonList(tip), new ArrayList<RevCommit>(bases));
		final Set<RevCommit> reachableBases = new HashSet<RevCommit>();
		final boolean[] merged = { false };
		CommonCommitLimitedRevQueue.configureRevWalk(revWalk, startCommits,
				new CommonCommitLimitedRevQueue.ReachabilityRegistry() {
					public void registerReachability(RevCommit startCommit,
							RevCommit commit) {
						if (tip == startCommit && bases.contains(commit)) {
							merged[0] = true;
						}
					}
				});

		for (RevCommit commit : revWalk) {
			if (merged[0]) {
				return true;
			}
		}

		return false;
	}

	public static Set<RevCommit> getBasesWhichAreContainedInTip(
			final Collection<RevCommit> bases, final RevCommit tip,
			RevWalk revWalk) throws IOException {
		final List<RevCommit> startCommits = getStartCommit(
				Collections.singletonList(tip), new ArrayList<RevCommit>(bases));
		final Set<RevCommit> reachableBases = new HashSet<RevCommit>();
		CommonCommitLimitedRevQueue.configureRevWalk(revWalk, startCommits,
				new CommonCommitLimitedRevQueue.ReachabilityRegistry() {
					public void registerReachability(RevCommit startCommit,
							RevCommit commit) {
						if (tip == startCommit && bases.contains(commit)) {
							reachableBases.add(commit);
						}
					}
				});

		for (RevCommit commit : revWalk) {
			// Run complete walk
		}

		return reachableBases;
	}

	private static List<RevCommit> getStartCommit(List<RevCommit> tipCommits,
			List<RevCommit> baseCommits) {
		final List<RevCommit> startCommits = new ArrayList<RevCommit>();
		startCommits.addAll(tipCommits);
		for (RevCommit baseCommit : baseCommits) {
			if (!tipCommits.contains(baseCommit)) {
				startCommits.add(baseCommit);
			}
		}
		return startCommits;
	}
}