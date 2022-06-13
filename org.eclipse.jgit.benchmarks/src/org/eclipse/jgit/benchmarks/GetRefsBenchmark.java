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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
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

import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;

@State(Scope.Thread)
public class GetRefsBenchmark {

	ThreadLocalRandom branchIndex = ThreadLocalRandom.current();

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		@Param({ "true", "false" })
		boolean useRefTable;

		@Param({ "100", "2500", "10000", "50000" })
		int numBranches;

		@Param({ "true", "false" })
		boolean trustFolderStat;

		List<String> branches = new ArrayList<>(numBranches);

		Path testDir;

		Repository repo;

		@Setup
		@SuppressWarnings("boxing")
		public void setupBenchmark() throws IOException, GitAPIException {
			String firstBranch = "firstbranch";
			testDir = Files.createDirectory(Paths.get("testrepos"));
			String repoName = "branches-" + numBranches + "-trustFolderStat-"
					+ trustFolderStat + "-" + refDatabaseType();
			Path workDir = testDir.resolve(repoName);
			Path repoPath = workDir.resolve(".git");
			Git git = Git.init().setDirectory(workDir.toFile()).call();
			RevCommit firstCommit = git.commit().setMessage("First commit")
					.call();
			git.branchCreate().setName(firstBranch).call();

			StoredConfig cfg = git.getRepository().getConfig();
			if (useRefTable) {
				((FileRepository) git.getRepository()).convertRefStorage(
						ConfigConstants.CONFIG_REF_STORAGE_REFTABLE, false,
						false);
			} else {
				cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT,
						trustFolderStat);
			}
			cfg.setInt(ConfigConstants.CONFIG_RECEIVE_SECTION, null,
					"maxCommandBytes", Integer.MAX_VALUE);
			cfg.save();

			repo = RepositoryCache.open(RepositoryCache.FileKey
					.lenient(repoPath.toFile(), FS.DETECTED));

			System.out.println("Preparing test");
			System.out.println("- repository: \t\t" + repoPath);
			System.out.println("- refDatabase: \t\t" + refDatabaseType());
			System.out.println("- trustFolderStat: \t" + trustFolderStat);
			System.out.println("- branches: \t\t" + numBranches);

			BatchRefUpdate u = repo.getRefDatabase().newBatchUpdate();

			branches = IntStream.range(0, numBranches)
					.mapToObj(i -> "branch/" + i % 100 + "/" + i)
					.collect(Collectors.toList());
			for (String branch : branches) {
				u.addCommand(new ReceiveCommand(ObjectId.zeroId(),
						firstCommit.toObjectId(), Constants.R_HEADS + branch,
						CREATE));
			}

			System.out.println();
			System.out.print(
					String.format("Creating %d branches ... ", numBranches));

			try (RevWalk rw = new RevWalk(repo)) {
				u.execute(rw, new TextProgressMonitor());
			}
			System.out.println("DONE");
		}

		private String refDatabaseType() {
			return useRefTable ? "reftable" : "refdir";
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
	@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetExactRef(Blackhole blackhole, BenchmarkState state)
			throws IOException {
		String branchName = state.branches
				.get(branchIndex.nextInt(state.numBranches));
		blackhole.consume(state.repo.exactRef(branchName));
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetRefsByPrefix(Blackhole blackhole, BenchmarkState state)
			throws IOException {
		String branchPrefix = "refs/heads/branch/" + branchIndex.nextInt(100)
				+ "/";
		blackhole.consume(
				state.repo.getRefDatabase().getRefsByPrefix(branchPrefix));
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(GetRefsBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
