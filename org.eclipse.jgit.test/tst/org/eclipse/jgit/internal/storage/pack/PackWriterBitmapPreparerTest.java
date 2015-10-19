package org.eclipse.jgit.internal.storage.pack;

import static org.eclipse.jgit.storage.pack.PackConfig.DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT;
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
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 1, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 10, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 99, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 100, true));
		assertEquals(1, preparer.nextSelectionDistance(0, 101, true));
		assertEquals(2, preparer.nextSelectionDistance(0, 102, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 110, true));
		assertEquals(64, preparer.nextSelectionDistance(0, 164, true));
		assertEquals(99, preparer.nextSelectionDistance(0, 199, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 200, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 201, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 500, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 1000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 5000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 10000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 19999, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20001, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20099, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20100, true));
		assertEquals(101, preparer.nextSelectionDistance(0, 20101, true));
		assertEquals(200, preparer.nextSelectionDistance(0, 20200, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 21000, true));
		assertEquals(4999, preparer.nextSelectionDistance(0, 24999, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 25000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 100000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	@Test
	public void testNextSelectionDistanceWithMoreContiguousCommits()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(1000,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 10, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 100, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 1000, true));
		assertEquals(1, preparer.nextSelectionDistance(0, 1001, true));
		assertEquals(99, preparer.nextSelectionDistance(0, 1099, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 1100, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 5000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 10000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 19999, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20001, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20099, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20100, true));
		assertEquals(101, preparer.nextSelectionDistance(0, 20101, true));
		assertEquals(200, preparer.nextSelectionDistance(0, 20200, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 21000, true));
		assertEquals(4999, preparer.nextSelectionDistance(0, 24999, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 25000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 100000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	@Test
	public void testNextSelectionDistanceWithLessRecentCommits()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT, 1000,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 1, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 10, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 99, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 100, true));
		assertEquals(1, preparer.nextSelectionDistance(0, 101, true));
		assertEquals(2, preparer.nextSelectionDistance(0, 102, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 110, true));
		assertEquals(64, preparer.nextSelectionDistance(0, 164, true));
		assertEquals(99, preparer.nextSelectionDistance(0, 199, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 200, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 201, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 500, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 1100, true));
		assertEquals(101, preparer.nextSelectionDistance(0, 1101, true));
		assertEquals(300, preparer.nextSelectionDistance(0, 1300, true));
		assertEquals(4999, preparer.nextSelectionDistance(0, 5999, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 6100, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 100000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	@Test
	public void testNextSelectionDistanceWithSmallerRecentSpan()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT, 10,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 1, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 10, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 99, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 100, true));
		assertEquals(1, preparer.nextSelectionDistance(0, 101, true));
		assertEquals(2, preparer.nextSelectionDistance(0, 102, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 110, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 111, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 200, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 20010, true));
		assertEquals(11, preparer.nextSelectionDistance(0, 20011, true));
		assertEquals(200, preparer.nextSelectionDistance(0, 20200, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 21000, true));
		assertEquals(4999, preparer.nextSelectionDistance(0, 24999, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 25000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 100000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	@Test
	public void testNextSelectionDistanceWithShorterDistantSpan()
			throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN, 1000);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 1, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 10, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 99, true));
		assertEquals(0, preparer.nextSelectionDistance(0, 100, true));
		assertEquals(1, preparer.nextSelectionDistance(0, 101, true));
		assertEquals(2, preparer.nextSelectionDistance(0, 102, true));
		assertEquals(10, preparer.nextSelectionDistance(0, 110, true));
		assertEquals(64, preparer.nextSelectionDistance(0, 164, true));
		assertEquals(99, preparer.nextSelectionDistance(0, 199, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 200, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 201, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 500, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 1000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 5000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 10000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 19999, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20000, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20001, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20099, true));
		assertEquals(100, preparer.nextSelectionDistance(0, 20100, true));
		assertEquals(101, preparer.nextSelectionDistance(0, 20101, true));
		assertEquals(200, preparer.nextSelectionDistance(0, 20200, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 21000, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 25000, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 100000, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(1000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	@Test
	public void testNextSelectionDistanceWithCommitOffset() throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(10000, 10000, true));
		assertEquals(0, preparer.nextSelectionDistance(10000, 10001, true));
		assertEquals(0, preparer.nextSelectionDistance(10000, 10099, true));
		assertEquals(0, preparer.nextSelectionDistance(10000, 10100, true));
		assertEquals(1, preparer.nextSelectionDistance(10000, 10101, true));
		assertEquals(99, preparer.nextSelectionDistance(10000, 10199, true));
		assertEquals(100, preparer.nextSelectionDistance(10000, 10200, true));
		assertEquals(100, preparer.nextSelectionDistance(10000, 30099, true));
		assertEquals(100, preparer.nextSelectionDistance(10000, 30100, true));
		assertEquals(101, preparer.nextSelectionDistance(10000, 30101, true));
		assertEquals(4999, preparer.nextSelectionDistance(10000, 34999, true));
		assertEquals(5000, preparer.nextSelectionDistance(10000, 35000, true));
		assertEquals(5000, preparer.nextSelectionDistance(10000, 110000, true));
		assertEquals(5000,
				preparer.nextSelectionDistance(10000, 1010000, true));
		assertEquals(5000,
				preparer.nextSelectionDistance(10000, 10010000, true));
	}

	@Test
	public void testNextSelectionDistanceForInactiveBranch() throws Exception {
		PackWriterBitmapPreparer preparer = newPeparer(
				DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_COUNT,
				DEFAULT_BITMAP_RECENT_COMMIT_SPAN,
				DEFAULT_BITMAP_DISTANT_COMMIT_SPAN);
		assertEquals(0, preparer.nextSelectionDistance(0, 0, false));
		assertEquals(100, preparer.nextSelectionDistance(0, 1, false));
		assertEquals(100, preparer.nextSelectionDistance(0, 100, false));
		assertEquals(100, preparer.nextSelectionDistance(0, 19999, false));
		assertEquals(5000, preparer.nextSelectionDistance(0, 20000, false));
		assertEquals(5000, preparer.nextSelectionDistance(0, 100000, false));
		assertEquals(5000, preparer.nextSelectionDistance(0, 1000000, true));
		assertEquals(5000, preparer.nextSelectionDistance(0, 10000000, true));
	}

	private PackWriterBitmapPreparer newPeparer(int consecutive,
			int recentCount, int recentSpan, int distantSpan)
					throws IOException {
		List<ObjectToPack> objects = Collections.emptyList();
		Set<ObjectId> wants = Collections.emptySet();
		PackConfig config = new PackConfig();
		config.setBitmapContiguousCommitCount(consecutive);
		config.setBitmapRecentCommitCount(recentCount);
		config.setBitmapRecentCommitSpan(recentSpan);
		config.setBitmapDistantCommitSpan(distantSpan);
		PackBitmapIndexBuilder indexBuilder = new PackBitmapIndexBuilder(
				objects);
		return new PackWriterBitmapPreparer(new StubObjectReader(),
				indexBuilder, null, wants, config);
	}
}
