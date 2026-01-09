package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.BitmapWalker;
import org.eclipse.jgit.storage.pack.PackConfig;

/**
 * Helper to find out interesting commits, create their bitmaps and write them
 * into the PackBitmapIndexBuilder.
 */
public class PackBitmapCalculator {

	private final PackConfig config;

	/**
	 * Constructor
	 *
	 * @param config
	 *            configuration
	 */
	public PackBitmapCalculator(PackConfig config) {
		this.config = config;
	}

	/**
	 * Choose commits, create bitmaps and send them to the builder
	 *
	 * @param reader
	 *            an object reader
	 * @param pm
	 *            a progress monitor
	 * @param wants
	 *            where to start walking looking for commits to bitmap
	 * @param numCommits
	 *            expected number of new commits to include in the bitmap
	 * @param excludeFromBitmapSelection
	 *            commits to ignore
	 * @param writeBitmaps
	 *            store of newly created bitmaps
	 * @throws IOException
	 *             an error
	 */
	public void write(ObjectReader reader, ProgressMonitor pm, int numCommits,
			Set<? extends ObjectId> wants,
			Set<? extends ObjectId> excludeFromBitmapSelection,
			PackBitmapIndexBuilder writeBitmaps) throws IOException {
		PackWriterBitmapPreparer bitmapPreparer = new PackWriterBitmapPreparer(
				reader, writeBitmaps, pm, wants, config);

		Collection<BitmapCommit> selectedCommits = bitmapPreparer
				.selectCommits(numCommits, excludeFromBitmapSelection);
		pm.beginTask(JGitText.get().buildingBitmaps, selectedCommits.size());

		BitmapWalker walker = bitmapPreparer.newBitmapWalker();
		AnyObjectId last = null;
		for (BitmapCommit cmit : selectedCommits) {
			if (!cmit.isReuseWalker()) {
				walker = bitmapPreparer.newBitmapWalker();
			}
			BitmapIndex.BitmapBuilder bitmap = walker
					.findObjects(Collections.singleton(cmit), null, false);

			if (last != null && cmit.isReuseWalker() && !bitmap.contains(last))
				throw new IllegalStateException(
						MessageFormat.format(JGitText.get().bitmapMissingObject,
								cmit.name(), last.name()));
			last = BitmapCommit.copyFrom(cmit).build();
			writeBitmaps.processBitmapForWrite(cmit, bitmap.build(),
					cmit.getFlags());

			// The bitmap walker should stop when the walk hits the previous
			// commit, which saves time.
			walker.setPrevCommit(last);
			walker.setPrevBitmap(bitmap);

			pm.update(1);
		}
		pm.endTask();
	}
}
