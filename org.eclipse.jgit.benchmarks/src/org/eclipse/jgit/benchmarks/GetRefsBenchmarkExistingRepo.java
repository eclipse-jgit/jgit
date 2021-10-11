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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.memory.InMemoryRefDatabase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
public class GetRefsBenchmarkExistingRepo {

	@State(Scope.Benchmark)
	public static class BenchmarkState {

//		boolean useRefTable = false;

		@Param({ "~/tmp/git/jgit.git", "~/tmp/git/All-Users-sap02.git",
				"~/tmp/git/gerrit.git", "~/tmp/git/All-Users-sap01.git",
				"~/tmp/git/go.git", "~/tmp/git/All-Users-Eclipse.git",
				"~/tmp/git/large-repo-sap02.git",
				"~/tmp/git/large-repo-sap01.git" })
		String path;

		@Param({ "true", "false" })
		boolean cacheRefs;

		@Param({ "true", "false" })
		boolean trustFolderStat;

		Repository repo;

		RefDatabase refDb;

		private List<Ref> allRefs;

		private Random rnd;

		private static String userHome = System.getProperty("user.home");

		private static String expandHome(String path) {
			if (path.charAt(0) == '~') {
				return userHome + path.substring(1);
			}
			return path;
		}

		@Setup
		@SuppressWarnings("boxing")
		public void setupBenchmark() throws IOException {
			Path repoPath = Paths.get(expandHome(path));
			repo = new FileRepository(new FileRepositoryBuilder()
					.setGitDir(repoPath.toFile()).setCacheRefs(cacheRefs)
					.setup());

			StoredConfig cfg = repo.getConfig();
			cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT,
					trustFolderStat);
			cfg.save();

			refDb = repo.getRefDatabase();
			allRefs = repo.getRefDatabase().getRefs();
			System.out.println(String.format("repo: '%s', number of refs: %d",
					path, allRefs.size()));
			rnd = new SecureRandom();
			rnd.setSeed(System.currentTimeMillis());
		}

		public String getRandomRefName() {
			return allRefs.get(rnd.nextInt(allRefs.size())).getName();
		}

		public String getRandomRefsPrefix() {
			String prefix = path.contains("All-Users") ? "refs/users/"
					: "refs/changes/";
			return prefix + rnd.nextInt(100);
		}

		@TearDown
		public void teardown() {
			repo.close();
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetExactRef(Blackhole blackhole, BenchmarkState state)
			throws IOException {
		String refName = state.getRandomRefName();
		blackhole.consume(state.repo.exactRef(refName));
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
	public void testGetRefsByPrefix(Blackhole blackhole, BenchmarkState state)
			throws IOException {
		String randomChangePrefix = state.getRandomRefsPrefix();
		blackhole.consume(state.refDb.getRefsByPrefix(randomChangePrefix));
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(GetRefsBenchmarkExistingRepo.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
