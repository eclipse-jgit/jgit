/*
 * Copyright (C) 2023, GerritForge Ltd
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class DateRevQueueBenchmark {

	ThreadLocalRandom commitsIndex = ThreadLocalRandom.current();

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		@Param({ "5", "10", "50", "100", "500", "1000", "5000", "10000",
				"50000", "100000", "500000" })
		int numCommits;

		@Param({ "true", "false" })
		boolean usePriorityQueue;

		int low, count;

		RevCommit[] commits = new RevCommit[numCommits];

		private Path testDir;

		private TestRepository<Repository> repoUtil;

		DateRevQueue queue;

		@Setup
		public void setupBenchmark() throws Exception {
			testDir = Files.createTempDirectory("testrepos");
			String repoName = "commits-" + numCommits + "-usePriorityQueue-"
					+ usePriorityQueue;
			Path workDir = testDir.resolve(repoName);
			Git git = Git.init().setDirectory(workDir.toFile()).call();
			repoUtil = new TestRepository<>(git.getRepository());

			RevCommit parent = repoUtil.commit().create();
			commits = new RevCommit[numCommits];
			commits[0] = parent;
			for (int i = 1; i < numCommits; i++) {
				parent = repoUtil.parseBody(repoUtil.commit(i, parent));
				commits[i] = parent;
				if (i % 10000 == 0) {
					System.out.println(" " + i + " done");
				}
			}

			if (usePriorityQueue) {
				queue = new DateRevPriorityQueue(false);
			} else {
				queue = new DateRevQueue(false);
			}

			low = 9 * numCommits / 10;
			ThreadLocalRandom random = ThreadLocalRandom.current();
			// add 90% * numCommits commits, benchmark adding commits from
			// 90-100%
			for (int i = 0; i < low; i++) {
				RevCommit commit = commits[random.nextInt(numCommits)];
				queue.add(commit);
				++count;
			}
		}

		@TearDown(Level.Invocation)
		public void check() {
			// if queue is full remove 10% of its entries
			if (++count == numCommits) {
				do {
					queue.next();
				} while (--count > low);
			}
		}

		@TearDown
		public void teardown() throws IOException {
			repoUtil.close();
			FileUtils.delete(testDir.toFile(),
					FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testDataRevQueue(BenchmarkState state) throws Exception {
		RevCommit commit = state.commits[commitsIndex
				.nextInt(state.numCommits)];
		state.queue.add(commit);
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(DateRevQueueBenchmark.class.getSimpleName()).forks(1)
				.jvmArgs("-ea").build();
		new Runner(opt).run();
	}

}
