/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.benchmarks;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.TreeFormatter;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Builds a DirCache from trees, the way an in-core merge does, and writes it
 * as a tree.
 * <p>
 * The tree has 100 directories of {@code subdirs} subdirectories with 100
 * files each: 100,000 files for the default of 10.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DirCacheWriteTreeBenchmark {
	private static final int DIRS = 100;

	private static final int FILES = 100;

	@Param({ "10" })
	int subdirs;

	private InMemoryRepository repo;

	private ObjectInserter ins;

	private ObjectReader reader;

	private ObjectId root;

	private ObjectId[] dirIds;

	private ObjectId[][] subdirIds;

	private ObjectId changed;

	@Setup
	public void setUp() throws IOException {
		repo = new InMemoryRepository(new DfsRepositoryDescription("bench"));
		ins = repo.newObjectInserter();
		reader = ins.newReader();
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		dirIds = new ObjectId[DIRS];
		subdirIds = new ObjectId[DIRS][subdirs];
		TreeFormatter top = new TreeFormatter();
		int n = 0;
		for (int d = 0; d < DIRS; d++) {
			TreeFormatter dir = new TreeFormatter();
			for (int s = 0; s < subdirs; s++) {
				TreeFormatter sub = new TreeFormatter();
				for (int f = 0; f < FILES; f++) {
					sub.append(name("f", f), FileMode.REGULAR_FILE,
							fmt.idFor(OBJ_BLOB, ("" + n++).getBytes(UTF_8)));
				}
				subdirIds[d][s] = ins.insert(sub);
				dir.append(name("s", s), FileMode.TREE, subdirIds[d][s]);
			}
			dirIds[d] = ins.insert(dir);
			top.append(name("d", d), FileMode.TREE, dirIds[d]);
		}
		root = ins.insert(top);
		ins.flush();
		changed = fmt.idFor(OBJ_BLOB, "changed".getBytes(UTF_8));
	}

	@TearDown
	public void tearDown() {
		reader.close();
		ins.close();
		repo.close();
	}

	/** Cost of addTree alone. */
	@Benchmark
	public DirCache readTree() throws IOException {
		return DirCache.read(reader, root);
	}

	@Benchmark
	public ObjectId readTreeAndWriteTree() throws IOException {
		return DirCache.read(reader, root).writeTree(ins);
	}

	/**
	 * Like an in-core merge that changed one file: unchanged directories are
	 * added as trees, the changed directory entry by entry.
	 */
	@Benchmark
	public ObjectId mergeOneFileChanged() throws IOException {
		DirCache dc = DirCache.newInCore();
		DirCacheBuilder b = dc.builder();
		int cd = DIRS / 2;
		for (int d = 0; d < DIRS; d++) {
			if (d != cd) {
				b.addTree(name("d", d).getBytes(UTF_8),
						DirCacheEntry.STAGE_0, reader, dirIds[d]);
				continue;
			}
			for (int s = 0; s < subdirs; s++) {
				String path = name("d", d) + '/' + name("s", s);
				if (s != 0) {
					b.addTree(path.getBytes(UTF_8), DirCacheEntry.STAGE_0,
							reader, subdirIds[d][s]);
					continue;
				}
				DirCache files = DirCache.read(reader, subdirIds[d][s]);
				for (int f = 0; f < files.getEntryCount(); f++) {
					DirCacheEntry e = new DirCacheEntry(
							path + '/' + files.getEntry(f).getPathString());
					e.setFileMode(FileMode.REGULAR_FILE);
					e.setObjectId(
							f == 0 ? changed : files.getEntry(f).getObjectId());
					b.add(e);
				}
			}
		}
		b.finish();
		return dc.writeTree(ins);
	}

	private static String name(String prefix, int i) {
		return String.format("%s%04d", prefix, Integer.valueOf(i));
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(DirCacheWriteTreeBenchmark.class.getSimpleName())
				.build();
		new Runner(opt).run();
	}
}
