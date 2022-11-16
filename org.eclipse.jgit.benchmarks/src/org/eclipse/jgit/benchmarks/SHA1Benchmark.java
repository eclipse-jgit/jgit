/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.benchmarks;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.sha1.SHA1;
import org.eclipse.jgit.util.sha1.SHA1.Sha1Implementation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
public class SHA1Benchmark {

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		@Param({ "1", "2", "4", "8", "16", "32", "64" })
		int size;

		@Param({ "false", "true" })
		boolean detectCollision;

		@Param({ "java", "jdkNative" })
		String impl;

		private SecureRandom rnd;

		byte[] content;

		@Setup
		public void setupBenchmark() {
			SystemReader.setInstance(new MockSystemReader());
			if (impl.equalsIgnoreCase(Sha1Implementation.JDKNATIVE.name())) {
				System.setProperty("org.eclipse.jgit.util.sha1.implementation",
						Sha1Implementation.JDKNATIVE.name());
			}
			content = new byte[size * 1024];
			try {
				rnd = SecureRandom.getInstanceStrong();
			} catch (NoSuchAlgorithmException e) {
				// ignore
			}
			rnd.nextBytes(content);
		}

		@TearDown
		public void teardown() {
			SystemReader.setInstance(null);
			rnd = null;
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testSHA1(Blackhole blackhole, BenchmarkState state) {
		SHA1 hash = SHA1.newInstance();
		hash.setDetectCollision(state.detectCollision);
		hash.update(state.content);
		blackhole.consume(hash.digest());
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(SHA1Benchmark.class.getSimpleName())
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
