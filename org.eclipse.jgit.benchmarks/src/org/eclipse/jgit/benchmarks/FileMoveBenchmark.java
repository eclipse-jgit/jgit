/*
 * Copyright (C) 2020, Matthias Sohn <matthias.sohn@sap.com> and others
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.util.FileUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
public class FileMoveBenchmark {
	int i;

	Path testDir;

	Path targetDir;

	@Setup
	public void setupBenchmark() throws IOException {
		testDir = Files.createTempDirectory("dir");
		targetDir = testDir.resolve("target");
		Files.createDirectory(targetDir);
	}

	@TearDown
	public void teardown() throws IOException {
		FileUtils.delete(testDir.toFile(),
				FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
	public Path moveFileToExistingDir() throws IOException {
		i++;
		Path tmp = testDir.resolve("tmp" + i++);
		Files.createFile(tmp);
		Path targetDirectory = targetDir;
		Path targetFile = targetDirectory.resolve("tmp" + i);
		try {
			return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
		} catch (NoSuchFileException e) {
			Files.createDirectory(targetDirectory);
			return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
	public Path moveFileToExistingDirExists() throws IOException {
		Path tmp = testDir.resolve("tmp" + i++);
		Files.createFile(tmp);
		Path targetDirectory = targetDir;
		Path targetFile = targetDir.resolve("tmp" + i);
		if (!targetDirectory.toFile().exists()) {
			Files.createDirectory(targetDirectory);
		}
		return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
	public Path moveFileToMissingDir() throws IOException {
		i++;
		Path tmp = testDir.resolve("tmp" + i);
		Files.createFile(tmp);
		Path targetDirectory = testDir.resolve("target" + i);
		Path targetFile = targetDirectory.resolve("tmp" + i);
		try {
			return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
		} catch (NoSuchFileException e) {
			Files.createDirectory(targetDirectory);
			return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 5, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
	public Path moveFileToMissingDirExists() throws IOException {
		i++;
		Path tmp = testDir.resolve("tmp" + i);
		Files.createFile(tmp);
		Path targetDirectory = testDir.resolve("target" + i);
		Path targetFile = targetDirectory.resolve("tmp" + i);
		if (!targetDirectory.toFile().exists()) {
			Files.createDirectory(targetDirectory);
		}
		return Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(FileMoveBenchmark.class
						.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}