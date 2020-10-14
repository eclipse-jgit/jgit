/*
 * Copyright (C) 2020, Marc Strapetz <marc.strapetz@syntevo.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.benchmarks;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Thread)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
public class PackIndexV2FindOffsetBenchmark extends PackIndexV2AbstractBenchmark {

    public static void main(String[] args) throws RunnerException, IOException {
        Options opt = new OptionsBuilder()
                .include(PackIndexV2FindOffsetBenchmark.class.getSimpleName())
                .forks(1).build();
        new Runner(opt).run();
    }

    @Param({"false", "true"})
    protected boolean useMmap;

    private Path packIndexFile;

    @Setup
    public void setupBenchmark() throws IOException {
        super.setupBenchmark();
        packIndexFile = determinePackIndexFile(repoRoot);
    }

    @Benchmark
    public long testFindSingleOffset() throws IOException {
        final PackIndex index = PackIndex.open(packIndexFile.toFile(), useMmap);
        try {
            return index.findOffset(commitIds[random.nextInt(commitIds.length)]);
        } finally {
            index.close();
        }
    }

    @Benchmark
   	public long testFindAllOffsets() throws IOException {
        final PackIndex index = PackIndex.open(packIndexFile.toFile(), useMmap);
        try {
            long offsetSum = 0;
            for (PackIndex.MutableEntry next : index) {
                final long offset = index.findOffset(next.toObjectId());
                offsetSum += offset;
            }

            return offsetSum;
        } finally {
            index.close();
        }
    }

    private Path determinePackIndexFile(Path repoRoot) throws IOException {
        final Path packDir = repoRoot.resolve(".git/objects/pack");
        if (!Files.isDirectory(packDir)) {
            throw new IOException("Pack-dir '" + packDir + "' does not exist.");
        }

        final List<Path> indexFiles = Files.list(packDir).filter(path -> path.toString().endsWith(".idx")).collect(Collectors.toList());
        if (indexFiles.size() != 1) {
            throw new IOException("Expected exactly one pack index in Pack-dir '" + packDir + "'.");
        }

        return indexFiles.get(0);
    }
}
