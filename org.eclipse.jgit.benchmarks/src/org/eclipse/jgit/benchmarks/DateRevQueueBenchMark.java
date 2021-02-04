/*
 * Copyright (c) 2021 Qualcomm Innovation Center, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.benchmarks;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DateRevQueue;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class DateRevQueueBenchMark {

    @Param({"<path_to_repo>"})
    String pathToRepo;

    @Param({"allCommits", "commitsOnBranch"})
    String testType;

    @Param({"50"})
    int addAtOnce;

    @Param({"50"})
    int removeAtOnce;

    @Param({"-1"})
    int addUpFront;

    @Param({"refs/heads/master"})
    String branch;

    List<RevCommit> commits = new ArrayList<>();
    DateRevQueue q;

    @Setup
    public void setupBenchmark() throws IOException, GitAPIException {
        Repository repo = (new FileRepositoryBuilder()).setGitDir(Paths.get(pathToRepo).toFile()).build();
        Iterator<RevCommit> logCall;
        if (Objects.equals(testType, "allCommits")) {
            logCall = (new Git(repo).log()).all().call().iterator();
        } else {
            Ref branchRef = repo.exactRef(branch);
            ObjectId id = branchRef.getPeeledObjectId();
            if (id == null) {
                id = branchRef.getObjectId();
            }
            logCall = (new Git(repo).log()).add(id).call().iterator();
        }
        while(logCall.hasNext()) {
            commits.add(logCall.next());
        }
        if (addUpFront == -1) {
            addUpFront = commits.size() / 2;
        }
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void dateRevQueueAddBenchMark() {
        q = new DateRevQueue();
        commits.forEach(q::add);
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void dateRevQueueAddAndNextBenchMark() {
        q = new DateRevQueue();
        commits.forEach(q::add);
        while (q.next() != null) {}
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void interleavingAddAndNext() {
        q = new DateRevQueue();
        Queue<RevCommit> commitsToUse = new LinkedList<>(commits);
        for (int i = 0; i < addUpFront && !commitsToUse.isEmpty(); i++) {
            q.add(commitsToUse.poll());
        }
        while (!(commitsToUse.isEmpty() && q.peek() == null)) {
            for (int i = 0; i < addAtOnce && !commitsToUse.isEmpty(); i++) {
                q.add(commitsToUse.poll());
            }
            for (int i = 0; i < removeAtOnce && q.next() != null; i++) {}
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(DateRevQueueBenchMark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
