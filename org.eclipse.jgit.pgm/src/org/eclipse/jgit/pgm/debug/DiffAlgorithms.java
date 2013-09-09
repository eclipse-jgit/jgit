/*
 * Copyright (C) 2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.pgm.debug;

import static java.lang.Integer.valueOf;
import static java.lang.Long.valueOf;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		DiffAlgorithm create() {
			return MyersDiff.INSTANCE;
		}
	};

	final Algorithm histogram = new Algorithm() {
		DiffAlgorithm create() {
			HistogramDiff d = new HistogramDiff();
			d.setFallbackAlgorithm(null);
			return d;
		}
	};

	final Algorithm histogram_myers = new Algorithm() {
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

	@Option(name = "--algorithm", multiValued = true, metaVar = "NAME", usage = "Enable algorithm(s)")
	List<String> algorithms = new ArrayList<String>();

	@Option(name = "--text-limit", metaVar = "LIMIT", usage = "Maximum size in KiB to scan per file revision")
	int textLimit = 15 * 1024; // 15 MiB as later we do * 1024.

	@Option(name = "--repository", aliases = { "-r" }, multiValued = true, metaVar = "GIT_DIR", usage = "Repository to scan")
	List<File> gitDirs = new ArrayList<File>();

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
			throw die("Current thread CPU time not supported on this JRE");

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

			Repository db = rb.build();
			try {
				run(db);
			} finally {
				db.close();
			}
		}
	}

	private void run(Repository db) throws Exception {
		List<Test> all = init();

		long files = 0;
		int commits = 0;
		int minN = Integer.MAX_VALUE;
		int maxN = 0;

		AbbreviatedObjectId startId;
		ObjectReader or = db.newObjectReader();
		try {
			final MutableObjectId id = new MutableObjectId();
			RevWalk rw = new RevWalk(or);
			TreeWalk tw = new TreeWalk(or);
			tw.setFilter(TreeFilter.ANY_DIFF);
			tw.setRecursive(true);

			ObjectId start = db.resolve(Constants.HEAD);
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
					if (RawText.isBinary(raw0))
						continue;

					byte[] raw1;
					try {
						tw.getObjectId(id, 1);
						raw1 = or.open(id).getCachedBytes(textLimit * 1024);
					} catch (LargeObjectException tooBig) {
						continue;
					}
					if (RawText.isBinary(raw1))
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
		} finally {
			or.release();
		}

		Collections.sort(all, new Comparator<Test>() {
			public int compare(Test a, Test b) {
				int cmp = Long.signum(a.runningTimeNanos - b.runningTimeNanos);
				if (cmp == 0)
					cmp = a.algorithm.name.compareTo(b.algorithm.name);
				return cmp;
			}
		});

		if (db.getDirectory() != null) {
			String name = db.getDirectory().getName();
			File parent = db.getDirectory().getParentFile();
			if (name.equals(Constants.DOT_GIT) && parent != null)
				name = parent.getName();
			outw.println(name + ": start at " + startId.name());
		}

		outw.format("  %12d files,     %8d commits\n", valueOf(files),
				valueOf(commits));
		outw.format("  N=%10d min lines, %8d max lines\n", valueOf(minN),
				valueOf(maxN));

		outw.format("%-25s %12s ( %12s  %12s )\n", //
				"Algorithm", "Time(ns)", "Time(ns) on", "Time(ns) on");
		outw.format("%-25s %12s ( %12s  %12s )\n", //
				"", "", "N=" + minN, "N=" + maxN);
		outw.println("-----------------------------------------------------" //$NON-NLS-1$
				+ "----------------"); //$NON-NLS-1$

		for (Test test : all) {
			outw.format("%-25s %12d ( %12d  %12d )", // //$NON-NLS-1$
					test.algorithm.name, //
					valueOf(test.runningTimeNanos), //
					valueOf(test.minN.runningTimeNanos), //
					valueOf(test.maxN.runningTimeNanos));
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
		List<Test> all = new ArrayList<Test>();

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
		} catch (IllegalArgumentException e) {
			throw die("Cannot determine names", e);
		} catch (IllegalAccessException e) {
			throw die("Cannot determine names", e);
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

	private static abstract class Algorithm {
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
