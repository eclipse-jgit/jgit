/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.benchmarks;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.util.SimpleLruCache;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class SimpleLruCacheBenchmark {

	SecureRandom rnd = new SecureRandom();

	private volatile SimpleLruCache<String, String> cache = new SimpleLruCache<>(
			100, 0.2f);

	private volatile int i;

	@Setup
	public void setupBenchmark() {
		i = rnd.nextInt(250);
	}

	@TearDown
	public void teardown() {
		cache = null;
	}

	@Benchmark
	@Group("readwrite")
	@GroupThreads(1)
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public SimpleLruCache<String, String> testCacheWrite() {
		cache.put("k" + i, "v" + i);
		return cache;
	}

	@Benchmark
	@Group("readwrite")
	@GroupThreads(1)
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public SimpleLruCache<String, String> testCacheRead() {
		cache.get("k" + i);
		return cache;
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(SimpleLruCacheBenchmark.class.getSimpleName())
				// .addProfiler(StackProfiler.class)
				// .addProfiler(GCProfiler.class)
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
