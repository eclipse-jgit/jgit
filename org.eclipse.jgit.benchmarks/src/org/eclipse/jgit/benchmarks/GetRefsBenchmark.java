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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
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
public class GetRefsBenchmark {

	AtomicInteger branchIndex = new AtomicInteger();

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		boolean useRefTable = false;

		@Param({ "100", "2500", "10000", "50000" })
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
			AtomicInteger branchIdx = new AtomicInteger();
			String firstBranch = "firstbranch";
			testDir = Files.createTempDirectory("dir");
			Path workDir = testDir.resolve("testrepo");
			Path repoPath = workDir.resolve(".git");
			Git git = Git.init().setDirectory(workDir.toFile()).call();
			git.commit().setMessage("First commit").call();
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

			System.out.println("Preparing test");
			System.out.println("- repository: \t\t" + repoPath);
			System.out.println("- refDatabase: \t\t"
					+ (useRefTable ? "reftable" : "refdir"));
			System.out.println("- trustFolderStat: \t" + trustFolderStat);
			System.out.println("- doGc: \t\t" + doGc);
			System.out.println("- branches: \t\t" + numBranches);

			Git gitInMem = newInMemoryRepositoryFrom(repoPath);
			Ref firstBranchRef = gitInMem.fetch()
					.setRemote(repoPath.toString())
					.setRefSpecs(Constants.R_HEADS + firstBranch).call()
					.getAdvertisedRef(Constants.R_HEADS + firstBranch);

			branches = IntStream.range(0, numBranches)
					.mapToObj(i -> "branch/" + i % 100 + "/" + i)
					.collect(Collectors.toList());

			branches.parallelStream().forEach((branchName) -> {
				createBranch(gitInMem, firstBranchRef, branchName);
				int currBranchIndx = branchIdx.incrementAndGet();
				if (currBranchIndx % (numBranches / 100) == 0) {
					System.out.print(String.format("\r%d%% of branches created",
							(100 * currBranchIndx) / numBranches));
				}
			});

			System.out.println();
			System.out.print(String.format("Pushing %d branches ... ",
					numBranches));
			gitInMem.push().setRemote(repoPath.toString()).setPushAll().call();
			System.out.println("DONE");

			if (doGc) {
				System.out.print(
						String.format("Running GC ... "));
				git.gc().call();
				System.out.println("DONE");
			}

			repo = RepositoryCache.open(RepositoryCache.FileKey
					.lenient(repoPath.toFile(), FS.DETECTED));
		}

		private void createBranch(Git gitInMem, Ref firstBranchRef,
				String branchName) {
			try {
				gitInMem.branchCreate().setName(branchName)
						.setStartPoint(
								firstBranchRef.getObjectId().getName())
						.call();
			} catch (GitAPIException e) {
				System.err.println("Unable to create branch " + branchName);
				e.printStackTrace();
				System.exit(-1);
			}
		}

		private Git newInMemoryRepositoryFrom(Path repoPath)
				throws IOException {
			DfsRepositoryDescription desc = new DfsRepositoryDescription(
					"Clone of " + repoPath);
			InMemoryRepository.Builder b = new InMemoryRepository.Builder()
					.setRepositoryDescription(desc);
			b.setFS(FS.detect().setUserHome(null));
			InMemoryRepository dest = b.build();
			Git gitInMem = Git.wrap(dest);
			return gitInMem;
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
				.get(branchIndex.incrementAndGet() % state.numBranches);
		blackhole.consume(state.repo.exactRef(branchName));
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetRefsByPrefix(Blackhole blackhole, BenchmarkState state) throws IOException {
		String branchPrefix = "refs/heads/branch/" + branchIndex.incrementAndGet() % 100 + "/";
		blackhole.consume(state.repo.getRefDatabase().getRefsByPrefix(branchPrefix));
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
