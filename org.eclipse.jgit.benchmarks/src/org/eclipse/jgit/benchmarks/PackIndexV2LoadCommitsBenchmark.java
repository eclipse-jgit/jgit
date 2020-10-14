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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.SingleShotTime}) // Mode.SingleShotTime for a constant amount of iterations and thus comparable hashes
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
public class PackIndexV2LoadCommitsBenchmark extends PackIndexV2AbstractBenchmark {

    public static void main(String[] args) throws RunnerException, IOException {
        Options opt = new OptionsBuilder()
                .include(PackIndexV2LoadCommitsBenchmark.class.getSimpleName())
                .forks(1).jvmArgs("-ea").build();
        new Runner(opt).run();
    }

    private long hash;

    @Param({"false", "true"})
    protected boolean useMmap;

    @Param({"1", "10", "100", "1000"})
    private int commitCount;

    @TearDown
    public void assertHash() {
        if (commitCount == 1) {
            assert hash == 0xD6C451BC6F4D0CD3L;
        }
        else if (commitCount == 10) {
            assert hash == 0xAFEE1FC9D6408A45L;
        }
        else if (commitCount == 100) {
            assert hash == 0x6742E728EDC9603CL;
        }
        else if (commitCount == 1000) {
            assert hash == 0xB097C5250BD0BF33L;
        }
    }

    @Benchmark
    public long testLoadRandomCommits() throws IOException {
        try (Repository repository = new FileRepositoryBuilder().setWorkTree(repoRoot.toFile()).setUseMmap(useMmap).build()) {
            for (int i = 0; i < commitCount; i++) {
                final ObjectId commit = commitIds[random.nextInt(commitIds.length)];
                final ObjectLoader loader = repository.newObjectReader().open(commit);
                final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                loader.openStream().transferTo(stream);
                hash = 31 * hash + Arrays.hashCode(stream.toByteArray());
            }
        }
        return hash;
    }
}
