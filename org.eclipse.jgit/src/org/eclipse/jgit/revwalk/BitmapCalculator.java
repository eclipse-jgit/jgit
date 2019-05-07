package org.eclipse.jgit.revwalk;

import static java.util.Objects.requireNonNull;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.revwalk.AddToBitmapFilter;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * @author ifrade
 *
 */
class BitmapCalculator {

	private final RevWalk walk;
	private final BitmapIndex bitmapIndex;

	BitmapCalculator(RevWalk walk) throws IOException {
		this.walk = walk;
		this.bitmapIndex = requireNonNull(
				walk.getObjectReader().getBitmapIndex());
	}

	/**
	 * Get the reachability bitmap from certain commit to other commits.
	 * <p>
	 * This will return a precalculated bitmap if available or walk building one
	 * until finding a precalculated bitmap (and returning the union).
	 * <p>
	 * Beware that the returned bitmap it is guaranteed to include ONLY the
	 * commits reachable from the initial commit. It COULD include other objects
	 * (because precalculated bitmaps have them) but caller shouldn't count on
	 * that. See @{link {@link BitmapWalker} for a full reachability bitmap.
	 *
	 * @param start
	 *            the commit
	 * @param pm
	 *            progress monitor. Updated by one per commit browsed in the
	 *            graph
	 * @return the bitmap of reachable commits (and maybe some extra objects)
	 *         for the commit
	 * @throws MissingObjectException
	 *             the supplied id doesn't exist
	 * @throws IncorrectObjectTypeException
	 *             the supplied id doens't refer to a commit or a tag
	 * @throws IOException
	 */
	BitmapBuilder getBitmap(ObjectId start, ProgressMonitor pm)
			throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		Bitmap precalculatedBitmap = bitmapIndex.getBitmap(start);
		if (precalculatedBitmap != null) {
			return asBitmapBuilder(precalculatedBitmap);
		}

		walk.reset();
		walk.sort(RevSort.TOPO);

		BitmapBuilder bitmapResult = bitmapIndex.newBitmapBuilder();
		walk.markStart(walk.parseCommit(start));
		walk.setRevFilter(new AddToBitmapFilter(bitmapResult));
		while (walk.next() != null) {
			// Iterate through all of the commits. The BitmapRevFilter does
			// the work.
			//
			// filter.include returns true for commits that do not have
			// a bitmap in bitmapIndex and are not reachable from a
			// bitmap in bitmapIndex encountered earlier in the walk.
			// Thus the number of commits returned by next() measures how
			// much history was traversed without being able to make use
			// of bitmaps.
			pm.update(1);
		}

		return bitmapResult;
	}

	private BitmapBuilder asBitmapBuilder(Bitmap bitmap) {
		return bitmapIndex.newBitmapBuilder().or(bitmap);
	}
}
