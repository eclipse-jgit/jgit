package org.eclipse.jgit.benchmarks;

import java.security.SecureRandom;
import java.util.Map;
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
			100);
	// volatile ConcurrentHashMap<String, String> cache = new
	// ConcurrentHashMap<>();

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
	public Map<String, String> testCacheWrite() {
		cache.put("k" + i, "v" + i);
		return cache;
	}

	@Benchmark
	@Group("readwrite")
	@GroupThreads(1)
	@BenchmarkMode({ Mode.AverageTime })
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public Map<String, String> testCacheRead() {
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
