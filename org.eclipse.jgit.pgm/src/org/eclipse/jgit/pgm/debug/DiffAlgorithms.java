/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_DiffAlgorithms")
class DiffAlgorithms extends TextBuiltin {

	final Algorithm myers = new Algorithm() {
		@Override
		DiffAlgorithm create() {
			return MyersDiff.INSTANCE;
		}
	};

	final Algorithm histogram = new Algorithm() {
		@Override
		DiffAlgorithm create() {
			HistogramDiff d = new HistogramDiff();
			d.setFallbackAlgorithm(null);
			return d;
		}
	};

	final Algorithm histogram_myers = new Algorithm() {
		@Override
		DiffAlgorithm create() {
			HistogramDiff d = new HistogramDiff();
			d.setFallbackAlgorithm(MyersDiff.INSTANCE);
			return d;
		}
	};

	// -----------------------------------------------------------------------
	//
	// Implementation of the suite lives below this line.
	//
	//

	@Option(name = "--algorithm", metaVar = "NAME", usage = "Enable algorithm(s)")
	List<String> algorithms = new ArrayList<>();

	@Option(name = "--text-limit", metaVar = "LIMIT", usage = "Maximum size in KiB to scan per file revision")
	int textLimit = 15 * 1024; // 15 MiB as later we do * 1024.

	@Option(name = "--repository", aliases = { "-r" }, metaVar = "GIT_DIR", usage = "Repository to scan")
	List<File> gitDirs = new ArrayList<>();

	@Option(name = "--count", metaVar = "LIMIT", usage = "Number of file revisions to be compared")
	int count = 0; // unlimited

	private final RawTextComparator cmp = RawTextComparator.DEFAULT;

	private ThreadMXBean mxBean;

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		mxBean = ManagementFactory.getThreadMXBean();
		if (!mxBean.isCurrentThreadCpuTimeSupported())
			throw die("Current thread CPU time not supported on this JRE"); //$NON-NLS-1$

		if (gitDirs.isEmpty()) {
			RepositoryBuilder rb = new RepositoryBuilder() //
					.setGitDir(new File(gitdir)) //
					.readEnvironment() //
					.findGitDir();
			if (rb.getGitDir() == null)
				throw die(CLIText.get().cantFindGitDirectory);
			gitDirs.add(rb.getGitDir());
		}

		for (File dir : gitDirs) {
			RepositoryBuilder rb = new RepositoryBuilder();
			if (RepositoryCache.FileKey.isGitRepository(dir, FS.DETECTED))
				rb.setGitDir(dir);
			else
				rb.findGitDir(dir);

			try (Repository repo = rb.build()) {
				run(repo);
			}
		}
	}

	private void run(Repository repo) throws Exception {
		List<Test> all = init();

		long files = 0;
		int commits = 0;
		int minN = Integer.MAX_VALUE;
		int maxN = 0;

		AbbreviatedObjectId startId;
		try (ObjectReader or = repo.newObjectReader();
			RevWalk rw = new RevWalk(or)) {
			final MutableObjectId id = new MutableObjectId();
			TreeWalk tw = new TreeWalk(or);
			tw.setFilter(TreeFilter.ANY_DIFF);
			tw.setRecursive(true);

			ObjectId start = repo.resolve(Constants.HEAD);
			startId = or.abbreviate(start);
			rw.markStart(rw.parseCommit(start));
			for (;;) {
				final RevCommit c = rw.next();
				if (c == null)
					break;
				commits++;
				if (c.getParentCount() != 1)
					continue;

				RevCommit p = c.getParent(0);
				rw.parseHeaders(p);
				tw.reset(p.getTree(), c.getTree());
				while (tw.next()) {
					if (!isFile(tw, 0) || !isFile(tw, 1))
						continue;

					byte[] raw0;
					try {
						tw.getObjectId(id, 0);
						raw0 = or.open(id).getCachedBytes(textLimit * 1024);
					} catch (LargeObjectException tooBig) {
						continue;
					}
					if (RawText.isBinary(raw0, raw0.length, true))
						continue;

					byte[] raw1;
					try {
						tw.getObjectId(id, 1);
						raw1 = or.open(id).getCachedBytes(textLimit * 1024);
					} catch (LargeObjectException tooBig) {
						continue;
					}
					if (RawText.isBinary(raw1, raw1.length, true))
						continue;

					RawText txt0 = new RawText(raw0);
					RawText txt1 = new RawText(raw1);

					minN = Math.min(minN, txt0.size() + txt1.size());
					maxN = Math.max(maxN, txt0.size() + txt1.size());

					for (Test test : all)
						testOne(test, txt0, txt1);
					files++;
				}
				if (count > 0 && files > count)
					break;
			}
		}

		Collections.sort(all, (Test a, Test b) -> {
			int result = Long.signum(a.runningTimeNanos - b.runningTimeNanos);
			if (result == 0) {
				result = a.algorithm.name.compareTo(b.algorithm.name);
			}
			return result;
		});

		File directory = repo.getDirectory();
		if (directory != null) {
			String name = directory.getName();
			File parent = directory.getParentFile();
			if (name.equals(Constants.DOT_GIT) && parent != null)
				name = parent.getName();
			outw.println(name + ": start at " + startId.name()); //$NON-NLS-1$
		}

		outw.format("  %12d files,     %8d commits\n", Long.valueOf(files), //$NON-NLS-1$
				Integer.valueOf(commits));
		outw.format("  N=%10d min lines, %8d max lines\n", //$NON-NLS-1$
				Integer.valueOf(minN), Integer.valueOf(maxN));

		outw.format("%-25s %12s ( %12s  %12s )\n", //$NON-NLS-1$
				"Algorithm", "Time(ns)", "Time(ns) on", "Time(ns) on"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		outw.format("%-25s %12s ( %12s  %12s )\n", //$NON-NLS-1$
				"", "", "N=" + minN, "N=" + maxN); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		outw.println("-----------------------------------------------------" //$NON-NLS-1$
				+ "----------------"); //$NON-NLS-1$

		for (Test test : all) {
			outw.format("%-25s %12d ( %12d  %12d )", // //$NON-NLS-1$
					test.algorithm.name, //
					Long.valueOf(test.runningTimeNanos), //
					Long.valueOf(test.minN.runningTimeNanos), //
					Long.valueOf(test.maxN.runningTimeNanos));
			outw.println();
		}
		outw.println();
		outw.flush();
	}

	private static boolean isFile(TreeWalk tw, int ithTree) {
		FileMode fm = tw.getFileMode(ithTree);
		return FileMode.REGULAR_FILE.equals(fm)
				|| FileMode.EXECUTABLE_FILE.equals(fm);
	}

	private static final int minCPUTimerTicks = 10;

	private void testOne(Test test, RawText a, RawText b) {
		final DiffAlgorithm da = test.algorithm.create();
		int cpuTimeChanges = 0;
		int cnt = 0;

		final long startTime = mxBean.getCurrentThreadCpuTime();
		long lastTime = startTime;
		while (cpuTimeChanges < minCPUTimerTicks) {
			da.diff(cmp, a, b);
			cnt++;

			long interimTime = mxBean.getCurrentThreadCpuTime();
			if (interimTime != lastTime) {
				cpuTimeChanges++;
				lastTime = interimTime;
			}
		}
		final long stopTime = mxBean.getCurrentThreadCpuTime();
		final long runTime = (stopTime - startTime) / cnt;

		test.runningTimeNanos += runTime;

		if (test.minN == null || a.size() + b.size() < test.minN.n) {
			test.minN = new Run();
			test.minN.n = a.size() + b.size();
			test.minN.runningTimeNanos = runTime;
		}

		if (test.maxN == null || a.size() + b.size() > test.maxN.n) {
			test.maxN = new Run();
			test.maxN.n = a.size() + b.size();
			test.maxN.runningTimeNanos = runTime;
		}
	}

	private List<Test> init() {
		List<Test> all = new ArrayList<>();

		try {
			for (Field f : DiffAlgorithms.class.getDeclaredFields()) {
				if (f.getType() == Algorithm.class) {
					f.setAccessible(true);
					Algorithm alg = (Algorithm) f.get(this);
					alg.name = f.getName();
					if (included(alg.name, algorithms)) {
						Test test = new Test();
						test.algorithm = alg;
						all.add(test);
					}
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw die("Cannot determine names", e); //$NON-NLS-1$
		}
		return all;
	}

	private static boolean included(String name, List<String> want) {
		if (want.isEmpty())
			return true;
		for (String s : want) {
			if (s.equalsIgnoreCase(name))
				return true;
		}
		return false;
	}

	private abstract static class Algorithm {
		String name;

		abstract DiffAlgorithm create();
	}

	private static class Test {
		Algorithm algorithm;

		long runningTimeNanos;

		Run minN;

		Run maxN;
	}

	private static class Run {
		int n;

		long runningTimeNanos;
	}
}
