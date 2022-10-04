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

import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class FetchRefToBlobBenchmark {
	ThreadLocalRandom branchIndex = ThreadLocalRandom.current();

	private static final String REFS_SEQUENCES_CHANGES = "refs/sequences/changes";

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		@Param({ "100", "1000", "10000", "100000" })
		int numBranches;

		List<String> branches = new ArrayList<>(numBranches);

		Path testDir;

		Path remoteRepoPath;

		Repository remoteRepo;

		Path localRepoPath;

		Git localGit;

		FetchCommand fetch;

		@Setup(Level.Trial)
		@SuppressWarnings("boxing")
		public void setupRemoteRepo() throws Exception {
			String firstBranch = "firstbranch";
			testDir = Files.createDirectories(Paths.get("target/testrepos"));

			String repoName = "branches-" + numBranches;
			Path workDir = testDir.resolve(repoName);
			remoteRepoPath = workDir.resolve(".git");
			Git git = Git.init().setDirectory(workDir.toFile())
					.setInitialBranch("main").call();
			RevCommit firstCommit = git.commit().setMessage("First commit")
					.call();
			git.branchCreate().setName(firstBranch).setForce(true).call();
			StoredConfig cfg = git.getRepository().getConfig();
			cfg.setInt(ConfigConstants.CONFIG_RECEIVE_SECTION, null,
					"maxCommandBytes", Integer.MAX_VALUE);
			cfg.save();
			remoteRepo = RepositoryCache.open(RepositoryCache.FileKey
					.lenient(remoteRepoPath.toFile(), FS.DETECTED));
			System.out.println("Preparing test");
			System.out.println("- repository: \t\t" + remoteRepoPath);
			System.out.println("- branches: \t\t" + numBranches);
			BatchRefUpdate u = remoteRepo.getRefDatabase().newBatchUpdate();
			branches = IntStream.range(0, numBranches)
					.mapToObj(i -> "branch/" + i % 100 + "/" + i)
					.collect(Collectors.toList());
			for (String branch : branches) {
				u.addCommand(new ReceiveCommand(ObjectId.zeroId(),
						firstCommit.toObjectId(), Constants.R_HEADS + branch,
						CREATE));
			}
			System.out.println();
			System.out.println(
					String.format("Creating %d branches ... ", numBranches));
			try (RevWalk rw = new RevWalk(remoteRepo)) {
				u.execute(rw, new TextProgressMonitor());
			}

			System.out.println(
					"Creating a Ref 'refs/sequences/changes' referring to a blob with content '42'");
			createExampleRefToBlob();

			System.out.println("DONE");
		}

		private void createExampleRefToBlob()
				throws UnsupportedEncodingException, IOException {
			ObjectInserter objectInserter = remoteRepo.newObjectInserter();
			byte[] bytes = "42".getBytes("utf-8");
			ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, bytes);
			objectInserter.flush();
			RefDatabase refDb = remoteRepo.getRefDatabase();
			RefUpdate ru = refDb.newUpdate(REFS_SEQUENCES_CHANGES, false);
			ru.setNewObjectId(blobId);
			ru.update();
		}

		@TearDown(Level.Trial)
		public void teardown() throws IOException {
			remoteRepo.close();
			FileUtils.delete(testDir.toFile(), FileUtils.RECURSIVE
					| FileUtils.RETRY | FileUtils.IGNORE_ERRORS);
		}

		@Setup(Level.Invocation)
		public void setupLocalRepo() throws Exception {
			localRepoPath = testDir.resolve("localrepo.git");
			localGit = Git.init().setBare(true)
					.setGitDir(localRepoPath.toFile())
					.call();
			localGit.remoteAdd().setName("origin")
					.setUri(new URIish(
							"file://" + remoteRepoPath.toAbsolutePath()))
					.call();
			fetch = localGit.fetch()
					.setRemote("origin")
					.setRefSpecs(
							REFS_SEQUENCES_CHANGES + ":refs/heads/sequence");
		}

		@TearDown(Level.Invocation)
		public void teardownLocalRepo() throws IOException {
			localGit.close();
			FileUtils.delete(localRepoPath.toFile(), FileUtils.RECURSIVE
					| FileUtils.RETRY | FileUtils.IGNORE_ERRORS);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testFetchRefToBlob(Blackhole blackhole, BenchmarkState state)
			throws Exception {
		blackhole.consume(state.fetch.call());
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(FetchRefToBlobBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
