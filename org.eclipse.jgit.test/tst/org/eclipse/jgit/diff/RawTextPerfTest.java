package org.eclipse.jgit.diff;

import java.io.File;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.CPUTimeStopWatch;

public class RawTextPerfTest extends TestCase {
	private static final long longTaskBoundary = 5000000000L;

	private static final int minCPUTimerTicks = 10;

	private CPUTimeStopWatch stopwatch = CPUTimeStopWatch.createInstance();

	private Repository db;

	private RawText a;

	private RawText b;

	protected void setUp() throws Exception {
		super.setUp();

		db = new FileRepositoryBuilder().addCeilingDirectory(new File("/"))
				.findGitDir().build();
		ObjectReader or = db.newObjectReader();

		// final String aId = "29a89bc", bId = "372a978";
		final String aId = "3fad2c3", bId = "821d06b";

		a = new RawText(or.open(
				or.resolve(AbbreviatedObjectId.fromString(aId))
						.iterator().next()).getCachedBytes(5 * 1 << 20));
		b = new RawText(or.open(
				or.resolve(AbbreviatedObjectId.fromString(bId))
						.iterator().next()).getCachedBytes(5 * 1 << 20));
	}

	public void testDefault() {
		final RawTextComparator cmp = RawTextComparator.DEFAULT;
		run("Myers", cmp, MyersDiff.INSTANCE);
		run("Patience", cmp, new PatienceDiff());
		run("Histogram", cmp, HistogramDiff.INSTANCE);
		System.out.println();
	}

	public void testIgnoreAll() {
		final RawTextComparator cmp = RawTextComparator.WS_IGNORE_ALL;
		run("Myers", cmp, MyersDiff.INSTANCE);
		run("Patience", cmp, new PatienceDiff());
		run("Histogram", cmp, HistogramDiff.INSTANCE);
		System.out.println();
	}

	private void run(String cmpName, SequenceComparator<RawText> cmp,
			DiffAlgorithm alg) {
		int d = 0;
		for (int warmUp = 0; warmUp < 200; warmUp++)
			d = alg.diff(cmp, a, b).size();

		int cpuTimeChanges = 0;
		long lastReadout = 0;
		long interimTime = 0;
		int repetitions = 0;
		stopwatch.start();
		while (cpuTimeChanges < minCPUTimerTicks
				&& interimTime < longTaskBoundary) {
			d = alg.diff(cmp, a, b).size();
			repetitions++;
			interimTime = stopwatch.readout();
			if (interimTime != lastReadout) {
				cpuTimeChanges++;
				lastReadout = interimTime;
			}
		}
		long runningTime = stopwatch.stop() / repetitions;
		System.out.format("%-15s %-15s %12d ns %4d edits\n", getName(),
				cmpName, runningTime, d);
	}
}
