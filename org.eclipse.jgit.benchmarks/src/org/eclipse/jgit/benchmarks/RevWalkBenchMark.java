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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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
public class RevWalkBenchMark {

    @Param({"<path_to_repo>"})
    String pathToRepo;

    @Param({"refs/heads/master"})
    String branch;

    Ref branchTip;
    Repository repo;

    @Setup
    public void setupBenchmark() throws IOException {
        repo = (new FileRepositoryBuilder()).setGitDir(Paths.get(pathToRepo).toFile()).build();
        branchTip = repo.exactRef(branch);
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void revWalkBranch() throws IOException {
        RevWalk walk = new RevWalk(repo);
        walk.markStart(walk.parseCommit(getId(branchTip)));
        while (walk.next() != null) {}
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void revWalkBranchOnly() throws IOException {
        RevWalk walk = new RevWalk(repo);
        walk.markStart(walk.parseCommit(getId(branchTip)));
        for (RevCommit c: getAllRefsAsCommits(branch)) {
            walk.markUninteresting(c);
        }
        while (walk.next() != null) {}
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void revWalkAllExceptBranch() throws IOException {
        RevWalk walk = new RevWalk(repo);
        walk.markUninteresting(walk.parseCommit(getId(branchTip)));
        for (RevCommit c: getAllRefsAsCommits(branch)) {
            walk.markStart(c);
        }
        while (walk.next() != null) {}
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void revWalkIsMergedInto() throws IOException {
        RevWalk walk = new RevWalk(repo);
        RevCommit base = walk.parseCommit(getId(branchTip));
        boolean consume = false;
        for (RevCommit c: getAllRefsAsCommits(branch)) {
            consume |= walk.isMergedInto(base, c);
        }
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime })
    @Fork(value = 2, warmups = 2, jvmArgs = {"-Xms4G", "-Xmx4G"})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void revWalkModifiedIsMergedInto() throws IOException {
        RevWalk walk = new RevWalk(repo);
        RevCommit base = walk.parseCommit(getId(branchTip));
        for (RevCommit c: getAllRefsAsCommits(branch)) {
            boolean commitFound = false;
            walk.resetRetain(RevFlag.UNINTERESTING);
            walk.markStart(c);
            for (RevCommit n: walk) {
                if( n.equals(base)) {
                    commitFound = true;
                    break;
                }
            }
            if (!commitFound) {
                walk.markUninteresting(c);
            }
        }
    }

    private List<RevCommit> getAllRefsAsCommits(String skip) throws IOException {
        List<RevCommit> result = new ArrayList<>();
        RevWalk walk = new RevWalk(repo);
        for (Ref ref: repo.getRefDatabase().getRefs()) {
            if (!Objects.equals(ref.getName(), skip)) {
                RevCommit c = null;
                try {
                    c = walk.parseCommit(getId(ref));
                } catch (IncorrectObjectTypeException | MissingObjectException e) {
                    continue;
                }
                result.add(c);
            }
        }
        return result;
    }

    private ObjectId getId(Ref ref) {
        ObjectId id = ref.getPeeledObjectId();
        if (id == null) {
            id = ref.getObjectId();
        }
        return id;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RevWalkBenchMark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
