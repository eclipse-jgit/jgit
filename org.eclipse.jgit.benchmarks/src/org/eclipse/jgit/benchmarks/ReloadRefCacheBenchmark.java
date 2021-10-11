/*
 * Copyright (C) 2021, Matthias Sohn <matthias.sohn@sap.com> and others
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
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.memory.InMemoryRefDatabase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
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
public class ReloadRefCacheBenchmark {

	@State(Scope.Benchmark)
	public static class BenchmarkState {
		@Param({ /*
					 * "~/tmp/git/jgit.git", "~/tmp/git/gerrit.git",
					 * "~/tmp/git/go.git",
					 *
					 * "~/tmp/git/large-repo-sap01.git",
					 * "~/tmp/git/All-Users-Eclipse.git",
					 */
				"~/tmp/git/All-Users-sap01.git",
				"~/tmp/git/All-Users-sap02.git" })
		String path;

		// @Param({ /* "true", */ "false" })
		boolean useRefTable = false;

		Repository repo;

		InMemoryRefDatabase refCache;

		private List<Ref> allRefs;

		private SecureRandom rnd;

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
			Path repoPath = Path.of(expandHome(path));
			repo = RepositoryCache.open(RepositoryCache.FileKey
					.lenient(repoPath.toFile(), FS.DETECTED));

			StoredConfig cfg = repo.getConfig();
			((FileRepository) repo).convertRefStorage(
					useRefTable ? ConfigConstants.CONFIG_REF_STORAGE_REFTABLE
							: ConfigConstants.CONFIG_REF_STORAGE_REFDIR,
					false, false);
			cfg.save();

			allRefs = repo.getRefDatabase().getRefs();
			System.out.println(String.format("repo: '%s', number of refs: %d",
					path, allRefs.size()));
			refCache = new InMemoryRefDatabase(repo);
			rnd = new SecureRandom();
			rnd.setSeed(System.currentTimeMillis());
		}

		@TearDown
		public void teardown() {
			repo.close();
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 20, timeUnit = TimeUnit.SECONDS)
	public void testReloadCached(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(state.refCache.reload());
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(ReloadRefCacheBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
