package org.eclipse.jgit.blame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BlameGeneratorMergeTest extends RepositoryTestCase {
	private static final String FILE = "file.txt";

	@Test
	public void correctness_blameMerge() throws Exception {
		RevCommit root, sideA, sideB, mergedTip;
		try (TestRepository<FileRepository> r = new TestRepository<>(db)) {
			root = rawCommit(r, "---");
			sideA = rawCommit(r, "aaaa---", root);
			sideB = rawCommit(r, "---bbbb", root);
			mergedTip = rawCommit(r, "aaaa---bbbb", sideA, sideB);
		}

		List<EmittedRegion> expectedRegions = Arrays.asList(
				new EmittedRegion(sideA, 0, 4, 0, 4),
				new EmittedRegion(root, 0, 3, 4, 7),
				new EmittedRegion(sideB, 3, 7, 7, 11));

		try (BlameGenerator gen = new BlameGenerator(db, FILE)) {
			gen.push(null, db.parseCommit(mergedTip));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsResultEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ 11, regions);
			assertFalse(gen.getStats().isCacheHit());
			assertEquals(4, gen.getStats().getCandidatesVisited());
		}

		// tip in cache
		try (BlameGenerator gen = new BlameGenerator(db, FILE,
				blameAndCache(mergedTip))) {
			gen.push(null, db.parseCommit(mergedTip));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsResultEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ 11, regions);
			assertTrue(gen.getStats().isCacheHit());
			assertEquals(1, gen.getStats().getCandidatesVisited());
		}

		// While splitting unblamed regions to parents, sideA comes first
		// and gets "aaaa----". Processing is by commit time though, to sideB is
		// explored first

		// One side in cache
		try (BlameGenerator gen = new BlameGenerator(db, FILE,
				blameAndCache(sideA))) {
			gen.push(null, db.parseCommit(mergedTip));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsResultEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ 11, regions);
			assertTrue(gen.getStats().isCacheHit());
			// tip -> sideB -> sideA
			assertEquals(3, gen.getStats().getCandidatesVisited());
		}

		// Another side in cache
		try (BlameGenerator gen = new BlameGenerator(db, FILE,
				blameAndCache(sideB))) {
			gen.push(null, db.parseCommit(mergedTip));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsResultEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ 11, regions);
			assertTrue(gen.getStats().isCacheHit());
			// tip -> sideB -> sideA -> root
			assertEquals(4, gen.getStats().getCandidatesVisited());
		}

		// root in cache
		try (BlameGenerator gen = new BlameGenerator(db, FILE,
				blameAndCache(root))) {
			gen.push(null, db.parseCommit(mergedTip));
			List<EmittedRegion> regions = consume(gen);
			assertRegionsResultEquals(expectedRegions, regions);
			assertAllLinesCovered(/* lines= */ 11, regions);
			assertTrue(gen.getStats().isCacheHit());
			assertEquals(4, gen.getStats().getCandidatesVisited());
		}
	}

	private List<EmittedRegion> consume(BlameGenerator generator)
			throws IOException {
		List<EmittedRegion> result = new ArrayList<>();
		while (generator.next()) {
			EmittedRegion genRegion = new EmittedRegion(
					generator.getSourceCommit().toObjectId(),
					generator.getSourceStart(), generator.getSourceEnd(),
					generator.getResultStart(), generator.getResultEnd());
			result.add(genRegion);
		}
		return result;
	}

	private InMemoryBlameCache blameAndCache(RevCommit commit)
			throws IOException {
		List<CacheRegion> regions;
		try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
			generator.push(null, commit);
			regions = consume(generator).stream()
					.map(EmittedRegion::asCacheRegion)
					.collect(Collectors.toUnmodifiableList());
		}
		InMemoryBlameCache cache = new InMemoryBlameCache("<x>");
		cache.put(commit, FILE, regions);
		return cache;
	}

	private static RevCommit rawCommit(TestRepository<?> r, String charPerLine,
			RevCommit... parents) throws Exception {
		return r.commit(
				r.tree(r.file(FILE,
						r.blob(charPerLine.replaceAll("\\S", "$0\n")))),
				parents);
	}

	private static void assertAllLinesCovered(int lines,
			List<EmittedRegion> regions) {
		Collections.sort(regions);
		assertEquals("Starts in first line", 0, regions.get(0).resultStart());
		for (int i = 1; i < regions.size(); i++) {
			assertEquals("No gaps", regions.get(i).resultStart(),
					regions.get(i - 1).resultEnd());
		}
		assertEquals("Ends in last line", lines,
				regions.get(regions.size() - 1).resultEnd());
	}

	private static void assertRegionsResultEquals(List<EmittedRegion> expected,
			List<EmittedRegion> actual) {
		assertEquals(expected.size(), actual.size());
		Collections.sort(actual);
		for (int i = 0; i < expected.size(); i++) {
			assertTrue(String.format("List differ in element %d", i),
					expected.get(i).equalsIgnoreSource(actual.get(i)));
		}
	}

	public record EmittedRegion(ObjectId oid, int sourceStart, int sourceEnd,
			int resultStart, int resultEnd)
			implements Comparable<EmittedRegion> {
		@Override
		public int compareTo(EmittedRegion o) {
			return resultStart - o.resultStart;
		}

		boolean equalsIgnoreSource(EmittedRegion other) {
			return this.oid.equals(other.oid)
					&& this.resultStart == other.resultStart
					&& this.resultEnd == other.resultEnd;
		}

		CacheRegion asCacheRegion() {
			return new CacheRegion(FILE, oid, resultStart, resultEnd);
		}
	}
}
