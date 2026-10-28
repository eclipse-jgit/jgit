/*
 * Copyright (C) 2026, SAP SE and others
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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assume;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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

/**
 * Benchmark comparing JGit's cruft pack GC with C git's
 * {@code git repack --cruft} on the same repository.
 *
 * <p>
 * The benchmark measures total wall-clock time for one complete cruft-pack GC
 * cycle. Setup creates a repository with a reachable spine and a number of
 * unreachable objects (both loose and in an expiring pack), then snapshots the
 * pack directory so that each benchmark invocation starts from an identical
 * state.
 *
 * <p>
 * The C git benchmark is skipped automatically if {@code git} is not found on
 * {@code PATH} or is older than 2.41 (which introduced {@code --cruft}).
 */
@State(Scope.Thread)
public class CruftPackBenchmark {

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		/** Number of unreachable blobs to create (loose + packed). */
		@Param({ "100", "1000", "10000" })
		int numUnreachable;

		Path testDir;

		/** Working directory of the test repository. */
		Path workDir;

		/** Snapshot of objects/pack taken after the initial setup GC. */
		Path packSnapshot;

		/** Snapshot of the loose objects created after setup GC. */
		Path looseSnapshot;

		FileRepository repo;

		boolean cgitAvailable;

		@Setup(Level.Trial)
		public void setupTrial() throws Exception {
			testDir = Files.createTempDirectory("jgit-cruft-bench-");
			workDir = testDir.resolve("repo");

			// ------------------------------------------------------------------
			// Build the repository
			// ------------------------------------------------------------------
			try (Git git = Git.init().setDirectory(workDir.toFile()).call();
					TestRepository<FileRepository> tr = new TestRepository<>(
							(FileRepository) git.getRepository())) {

				repo = (FileRepository) git.getRepository();

				// Create a small reachable history on master.
				TestRepository<FileRepository>.BranchBuilder bb = tr
						.branch("refs/heads/master");
				bb.commit().message("root").add("root", "root").create();
				for (int i = 0; i < 10; i++) {
					bb.commit().message("commit-" + i).add("f" + i, "v" + i)
							.create();
				}

				// Enable cruft packs in config so both JGit and C git pick it up.
				StoredConfig cfg = repo.getConfig();
				cfg.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
						ConfigConstants.CONFIG_KEY_CRUFTPACKS, true);
				cfg.save();

				// Pack all reachable objects, expiring immediately.
				GC setupGc = new GC(repo);
				setupGc.setExpireAge(Duration.ZERO);
				setupGc.setPackExpireAge(Duration.ZERO);
				setupGc.gc().get();

				// Create unreachable blobs: half loose, half in a soon-to-expire
				// pack (written via a temp ref that we then delete).
				int half = numUnreachable / 2;
				for (int i = 0; i < half; i++) {
					tr.blob("loose-unreachable-" + i);
				}
				TestRepository<FileRepository>.BranchBuilder temp = tr
						.branch("refs/heads/temp");
				for (int i = half; i < numUnreachable; i++) {
					temp.commit().message("u" + i).add("u" + i, "u" + i)
							.create();
				}
				// Pack the temp branch objects into a separate pack.
				GC packGc = new GC(repo);
				packGc.setExpireAge(Duration.ofDays(365));
				packGc.setPackExpireAge(Duration.ofDays(365));
				packGc.repack();
				// Delete the temp ref — its objects are now unreachable and packed.
				repo.getRefDatabase().newUpdate("refs/heads/temp", false)
						.delete();
			}

			// ------------------------------------------------------------------
			// Snapshot the pack dir and loose objects for per-invocation restore
			// ------------------------------------------------------------------
			packSnapshot = testDir.resolve("pack-snapshot");
			Files.createDirectory(packSnapshot);
			copyDir(repo.getObjectDatabase().getPackDirectory().toPath(),
					packSnapshot);

			looseSnapshot = testDir.resolve("loose-snapshot");
			Files.createDirectory(looseSnapshot);
			copyLooseObjects(repo.getObjectsDirectory().toPath(), looseSnapshot);

			// ------------------------------------------------------------------
			// Check whether a sufficiently recent C git is available
			// ------------------------------------------------------------------
			cgitAvailable = isCgitAvailable();
		}

		/**
		 * Restore the pack directory and loose objects to the snapshot so each
		 * invocation starts from identical repository state.
		 */
		@Setup(Level.Invocation)
		public void setupInvocation() throws IOException {
			// Remove current pack files and loose objects, then restore snapshot.
			Path packDir = repo.getObjectDatabase().getPackDirectory().toPath();
			FileUtils.delete(packDir.toFile(),
					FileUtils.RECURSIVE | FileUtils.RETRY);
			Files.createDirectories(packDir);
			copyDir(packSnapshot, packDir);

			restoreLooseObjects(looseSnapshot,
					repo.getObjectsDirectory().toPath());

			repo.getObjectDatabase().close();
		}

		@TearDown(Level.Trial)
		public void teardown() throws IOException {
			if (repo != null) {
				repo.close();
			}
			FileUtils.delete(testDir.toFile(),
					FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		private static void copyDir(Path src, Path dst) throws IOException {
			try (var stream = Files.list(src)) {
				for (Path entry : (Iterable<Path>) stream::iterator) {
					Files.copy(entry, dst.resolve(entry.getFileName()),
							StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}

		private static void copyLooseObjects(Path objectsDir, Path snapshot)
				throws IOException {
			try (var fanout = Files.list(objectsDir)) {
				for (Path dir : (Iterable<Path>) fanout::iterator) {
					if (!Files.isDirectory(dir)
							|| dir.getFileName().toString().length() != 2) {
						continue;
					}
					Path snapFanout = snapshot
							.resolve(dir.getFileName().toString());
					Files.createDirectories(snapFanout);
					try (var entries = Files.list(dir)) {
						for (Path f : (Iterable<Path>) entries::iterator) {
							Files.copy(f,
									snapFanout.resolve(f.getFileName()),
									StandardCopyOption.REPLACE_EXISTING,
									StandardCopyOption.COPY_ATTRIBUTES);
						}
					}
				}
			}
		}

		private static void restoreLooseObjects(Path snapshot, Path objectsDir)
				throws IOException {
			// Remove existing loose fanout dirs.
			try (var fanout = Files.list(objectsDir)) {
				for (Path dir : (Iterable<Path>) fanout::iterator) {
					if (Files.isDirectory(dir)
							&& dir.getFileName().toString().length() == 2) {
						FileUtils.delete(dir.toFile(),
								FileUtils.RECURSIVE | FileUtils.RETRY);
					}
				}
			}
			// Restore from snapshot.
			try (var fanout = Files.list(snapshot)) {
				for (Path snapFanout : (Iterable<Path>) fanout::iterator) {
					Path dst = objectsDir
							.resolve(snapFanout.getFileName().toString());
					Files.createDirectories(dst);
					try (var entries = Files.list(snapFanout)) {
						for (Path f : (Iterable<Path>) entries::iterator) {
							Files.copy(f, dst.resolve(f.getFileName()),
									StandardCopyOption.REPLACE_EXISTING,
									StandardCopyOption.COPY_ATTRIBUTES);
						}
					}
				}
			}
		}

		private static boolean isCgitAvailable() {
			try {
				Process p = new ProcessBuilder("git", "--version")
						.redirectErrorStream(true).start();
				String output = new String(p.getInputStream().readAllBytes())
						.trim();
				p.waitFor();
				// "git version 2.XX.Y" — need >= 2.41 for --cruft
				String[] parts = output.split("[\\s.]");
				if (parts.length >= 3) {
					int major = Integer.parseInt(parts[2]);
					int minor = Integer.parseInt(parts[3]);
					return major > 2 || (major == 2 && minor >= 41);
				}
			} catch (Exception e) {
				// git not on PATH or output unparseable
			}
			return false;
		}
	}

	@Benchmark
	@BenchmarkMode(Mode.SingleShotTime)
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Warmup(iterations = 3)
	@Measurement(iterations = 10)
	@Fork(1)
	public void jgitCruftPack(BenchmarkState state) throws Exception {
		GC gc = new GC(state.repo);
		gc.setExpireAge(Duration.ZERO);
		gc.setPackExpireAge(Duration.ZERO);
		gc.gc().get();
	}

	@Benchmark
	@BenchmarkMode(Mode.SingleShotTime)
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Warmup(iterations = 3)
	@Measurement(iterations = 10)
	@Fork(1)
	public void cgitCruftPack(BenchmarkState state) throws Exception {
		Assume.assumeTrue("C git >= 2.41 not available", state.cgitAvailable);
		Process p = new ProcessBuilder("git", "repack", "-d", "--cruft",
				"--cruft-expiration=now")
				.directory(state.workDir.toFile())
				.redirectErrorStream(true)
				.start();
		// drain stdout/stderr to avoid blocking
		p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
		int exit = p.waitFor();
		if (exit != 0) {
			throw new IOException(
					"git repack exited with status " + exit); //$NON-NLS-1$
		}
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(CruftPackBenchmark.class.getSimpleName())
				.forks(1)
				.jvmArgs("-ea")
				.build();
		new Runner(opt).run();
	}
}
