package org.eclipse.jgit.revwalk;

import java.io.IOException;

/**
 * Create a reachability checker from the RevWalk on a repository.
 * <p>
 * It will return the most efficient implementation based on the repository
 * configuration.
 *
 * @since 5.4
 */
public class ReachabilityCheckerFactory {

	/**
	 * It returns a reachability checker for the repository of the walk.
	 *
	 * @param walk
	 *            RevWalk to traverse the commit graph as needed. It will be
	 *            reset inside the checker (maybe multiple times).
	 * @return the most efficient reachability checker instance for the repo.
	 * @throws IOException
	 *             If the underlying indices can not be loaded.
	 *
	 */
	public static ReachabilityChecker getReachabilityChecker(RevWalk walk)
			throws IOException {
		if (walk.getObjectReader().getBitmapIndex() != null) {
			return new BitmappedReachabilityChecker(walk);
		}

		return new PedestrianReachabilityChecker(true, walk);
	}
}
