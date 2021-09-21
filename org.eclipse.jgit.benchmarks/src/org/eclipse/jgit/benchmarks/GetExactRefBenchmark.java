/*
 * Copyright (C) 2021, Luca Milanesio <luca.milanesio@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.benchmarks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class GetExactRefBenchmark {

	AtomicInteger branchIndex = new AtomicInteger();

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		boolean useRefTable = false;

		@Param({ "100", "500", "2500" })
		int numBranches;

		@Param({ "true", "false" })
		boolean doGc;

		@Param({ "true", "false" })
		boolean trustFolderStat;

		List<String> branches = new ArrayList<>(numBranches);

		Path testDir;

		Repository repo;

		@Setup
		@SuppressWarnings("boxing")
		public void setupBenchmark() throws IOException, GitAPIException {
			testDir = Files.createTempDirectory("dir");
			Path workDir = testDir.resolve("testrepo");
			Path repoPath = workDir.resolve(".git");
			Git git = Git.init().setDirectory(workDir.toFile()).call();
			git.commit().setMessage("First commit").call();

			if (useRefTable) {
				((FileRepository) git.getRepository()).convertRefStorage(
						ConfigConstants.CONFIG_REF_STORAGE_REFTABLE, false,
						false);
			} else {
				StoredConfig cfg = git.getRepository().getConfig();
				cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT,
						trustFolderStat);
				cfg.save();
			}

			System.out.println("Preparing test");
			System.out.println("- repository: \t\t" + repoPath);
			System.out.println("- refDatabase: \t\t"
					+ (useRefTable ? "reftable" : "refdir"));
			System.out.println("- trustFolderStat: \t" + trustFolderStat);
			System.out.println("- doGc: \t\t" + doGc);
			System.out.println("- branches: \t\t" + numBranches);
			for (int branchIdx = 0; branchIdx < numBranches;) {
				String branchName = "branch-" + branchIdx;
				branches.add(branchName);
				git.checkout().setCreateBranch(true).setName(branchName).call();
				git.commit().setMessage("Test commit on " + branchName).call();
				branchIdx++;
				if (branchIdx % (numBranches / 10) == 0) {
					System.out.print(String.format("\r%d%% of branches created",
							(100 * branchIdx) / numBranches));
					if (doGc) {
						git.gc().call();
					}
				}
			}
			System.out.println();
			repo = RepositoryCache.open(RepositoryCache.FileKey
					.lenient(repoPath.toFile(), FS.DETECTED));
		}

		@TearDown
		public void teardown() throws IOException {
			repo.close();
			FileUtils.delete(testDir.toFile(),
					FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetExactRef(Blackhole blackhole, BenchmarkState state)
			throws IOException {
		String branchName = state.branches
				.get(branchIndex.incrementAndGet() % state.numBranches);
		blackhole.consume(state.repo.exactRef(branchName));
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(GetExactRefBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
