package org.eclipse.jgit.merge;

import org.eclipse.jgit.lib.Repository;

/**
 * @author d032780
 *
 */
public class StrategyResolve extends ThreeWayMergeStrategy {
	@Override
	public ThreeWayMerger newMerger(Repository db) {
		return new ResolveMerger(db);
	}

	@Override
	public String getName() {
		return "StrategyResolve";
	}
}