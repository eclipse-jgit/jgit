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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Thread)
public class GetExactRefBenchmark {
	Path repoPath;
	Path testDir;
	Path workDir;
	Git git;
	int numBranches = 100000;
	List<String> branches = new ArrayList<>(numBranches);
	AtomicInteger branchIndex = new AtomicInteger();
	boolean trustFolderStat = true;

	@Setup
	public void setupBenchmark() throws IOException, GitAPIException {
		testDir = Files.createTempDirectory("dir");
		workDir = testDir.resolve("testrepo");
		repoPath = workDir.resolve(".git");
		git = Git.init().setDirectory(workDir.toFile()).setGitDir(repoPath.toFile()).call();
		git.getRepository().getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, trustFolderStat);

		git.commit().setMessage("First commit").call();

		System.out.println("Preparing a test repository " + repoPath + " with " + numBranches + " branches ...");
		for (int branchIdx = 0; branchIdx < numBranches;) {
			String branchName = "branch-" + branchIdx;
			branches.add(branchName);
			git.checkout().setCreateBranch(true).setName(branchName).call();
			git.commit().setMessage("Test commit on " + branchName).call();
			branchIdx++;
			if ((branchIdx) % 1000 == 0) {
				System.out.print(String.format("%d%% of branches created\r", (100 * branchIdx) / numBranches));
			}
		}

		System.out.println("Running GC on " + repoPath);
		git.gc().setProgressMonitor(new TextProgressMonitor());
		System.out.println("Starting benchmark on " + repoPath);
	}

	@TearDown
	public void teardown() throws IOException {
		FileUtils.delete(testDir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
	public Ref testOpenRepositoryAndGetExactRef() throws IOException {
		try (Repository repo = RepositoryCache.open(RepositoryCache.FileKey.lenient(repoPath.toFile(), FS.DETECTED))) {
			String branchName = branches.get(branchIndex.incrementAndGet() % numBranches);
			return repo.exactRef(branchName);
		}
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder().include(GetExactRefBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
