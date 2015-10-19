package org.eclipse.jgit.internal.storage.pack;

import static org.eclipse.jgit.storage.pack.PackConfig.DEFAULT_BITMAP_DISTANT_COMMIT_SPAN;
import static org.eclipse.jgit.storage.pack.PackConfig.DEFAULT_BITMAP_RECENT_COMMIT_COUNT;
import static org.eclipse.jgit.storage.pack.PackConfig.DEFAULT_BITMAP_RECENT_COMMIT_SPAN;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;

/** Tests for the {@link PackWriterBitmapPreparer}. */
public class PackWriterBitmapPreparerTest {
	private static class StubObjectReader extends ObjectReader {
		@Override
		public ObjectReader newReader() {
			return null;
		}

		@Override
		public Collection<ObjectId> resolve(AbbreviatedObjectId id)
				throws IOException {
			return null;
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			return null;
		}

		@Override
		public Set<ObjectId> getShallowCommits() throws IOException {
			return null;
		}

		@Override
		public void close() {
			// stub
		}
	}

	@Test
	public void testNextSelectionDistanceForActiveBranch() throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT, // 20000
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN, // 100
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN); // 5000
		int[][] distancesAndSpans = { { 0, 100 }, { 100, 100 }, { 10000, 100 },
				{ 20000, 100 }, { 20100, 100 }, { 20102, 102 }, { 20200, 200 },
				{ 22200, 2200 }, { 24999, 4999 }, { 25000, 5000 },
				{ 50000, 5000 }, { 1000000, 5000 }, };

		for (int[] pair : distancesAndSpans) {
			assertEquals(pair[1], preparer.nextSpan(pair[0]));
		}
	}

	@Test
	public void testNextSelectionDistanceWithFewerRecentCommits()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(1000,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN, // 100
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN); // 5000
		int[][] distancesAndSpans = { { 0, 100 }, { 100, 100 }, { 1000, 100 },
				{ 1100, 100 }, { 1111, 111 }, { 2000, 1000 }, { 5999, 4999 },
				{ 6000, 5000 }, { 10000, 5000 }, { 50000, 5000 },
				{ 1000000, 5000 } };

		for (int[] pair : distancesAndSpans) {
			assertEquals(pair[1], preparer.nextSpan(pair[0]));
		}
	}

	@Test
	public void testNextSelectionDistanceWithSmallerRecentSpan()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT, // 20000
				10, // recent span
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN); // 5000
		int[][] distancesAndSpans = { { 0, 10 }, { 100, 10 }, { 10000, 10 },
				{ 20000, 10 }, { 20010, 10 }, { 20012, 12 }, { 20050, 50 },
				{ 20200, 200 }, { 22200, 2200 }, { 24999, 4999 },
				{ 25000, 5000 }, { 50000, 5000 }, { 1000000, 5000 } };

		for (int[] pair : distancesAndSpans) {
			assertEquals(pair[1], preparer.nextSpan(pair[0]));
		}
	}

	@Test
	public void testNextSelectionDistanceWithSmallerDistantSpan()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT, // 20000
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN, // 100
				1000);
		int[][] distancesAndSpans = { { 0, 100 }, { 100, 100 }, { 10000, 100 },
				{ 20000, 100 }, { 20100, 100 }, { 20102, 102 }, { 20200, 200 },
				{ 20999, 999 }, { 21000, 1000 }, { 22000, 1000 },
				{ 25000, 1000 }, { 50000, 1000 }, { 1000000, 1000 } };

		for (int[] pair : distancesAndSpans) {
			assertEquals(pair[1], preparer.nextSpan(pair[0]));
		}
	}

	private PackWriterBitmapPreparer newPeparer(int recentCount, int recentSpan,
			int distantSpan) throws IOException {
		List<ObjectToPack> objects = Collections.emptyList();
		Set<ObjectId> wants = Collections.emptySet();
		PackConfig config = new PackConfig();
		config.setBitmapRecentCommitCount(recentCount);
		config.setBitmapRecentCommitSpan(recentSpan);
		config.setBitmapDistantCommitSpan(distantSpan);
		PackBitmapIndexBuilder indexBuilder = new PackBitmapIndexBuilder(
				objects);
		return new PackWriterBitmapPreparer(new StubObjectReader(),
				indexBuilder, null, wants, config);
	}
}
