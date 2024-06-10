/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.IndexEventConsumer;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTables.DfsBlockCacheTablesFactory;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DfsBlockCacheTest {
	@Rule
	public TestName testName = new TestName();

	private TestRng rng;

	private DfsBlockCache cache;

	private ExecutorService pool;

	private TestBlockCacheTableFactory testBlockCacheTableFactory = new TestBlockCacheTableFactory();

	private enum CacheType {
		SINGLE_TABLE_CLOCK_BLOCK_CACHE, EXT_SPLIT_TABLE_CLOCK_BLOCK_CACHE, EXT_SPLIT_TABLE_CACHE_WITH_CUSTOM_FACTORY
	}

	@Parameters(name = "cache type: {0}")
	public static Iterable<? extends Object> data() {
		return Arrays.asList(CacheType.SINGLE_TABLE_CLOCK_BLOCK_CACHE,
				CacheType.EXT_SPLIT_TABLE_CLOCK_BLOCK_CACHE,
				CacheType.EXT_SPLIT_TABLE_CACHE_WITH_CUSTOM_FACTORY);
	}

	@Parameter
	public CacheType cacheType;

	@Before
	public void setUp() {
		rng = new TestRng(testName.getMethodName());
		pool = Executors.newFixedThreadPool(10);
		resetCache();
	}

	@SuppressWarnings("resource")
	@Test
	public void streamKeyReusesBlocks() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		long oldSize = LongStream.of(cache.getCurrentSize()).sum();
		assertTrue(oldSize > 2000);
		assertEquals(0, LongStream.of(cache.getHitCount()).sum());

		List<DfsPackDescription> packs = r1.getObjectDatabase().listPacks();
		InMemoryRepository r2 = new InMemoryRepository(repo);
		r2.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r2.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		assertEquals(0, LongStream.of(cache.getMissCount()).sum());
		long hitCount = LongStream.of(cache.getHitCount()).sum();
		assertTrue("hitCount " + LongStream.of(cache.getHitCount()),
				hitCount > 0);
		assertEquals(oldSize, LongStream.of(cache.getCurrentSize()).sum());
	}

	@SuppressWarnings("resource")
	@Test
	public void customFactoryUsed() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		try (ObjectInserter ins = r1.newObjectInserter()) {
			ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		assertTrue(LongStream.of(cache.getCurrentSize()).sum() > 2000);
		assertEquals(0, LongStream.of(cache.getHitCount()).sum());

		switch (cacheType) {
		case SINGLE_TABLE_CLOCK_BLOCK_CACHE:
		case EXT_SPLIT_TABLE_CLOCK_BLOCK_CACHE:
			assertNull(testBlockCacheTableFactory.testBlockCacheTables);
			break;
		case EXT_SPLIT_TABLE_CACHE_WITH_CUSTOM_FACTORY:
			assertNotNull(testBlockCacheTableFactory.testBlockCacheTables);
			assertThat(testBlockCacheTableFactory.testBlockCacheTables.called,
					greaterThan(0));
		}
	}

	@SuppressWarnings("resource")
	@Test
	public void weirdBlockSize() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);

		byte[] content1 = rng.nextBytes(4);
		byte[] content2 = rng.nextBytes(424242);
		ObjectId id1;
		ObjectId id2;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id1 = ins.insert(OBJ_BLOB, content1);
			id2 = ins.insert(OBJ_BLOB, content2);
			ins.flush();
		}

		resetCache();
		List<DfsPackDescription> packs = r1.getObjectDatabase().listPacks();

		InMemoryRepository r2 = new InMemoryRepository(repo);
		r2.getObjectDatabase().setReadableChannelBlockSizeForTest(500);
		r2.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r2.newObjectReader()) {
			byte[] actual = rdr.open(id1, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content1, actual));
		}

		InMemoryRepository r3 = new InMemoryRepository(repo);
		r3.getObjectDatabase().setReadableChannelBlockSizeForTest(500);
		r3.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r3.newObjectReader()) {
			byte[] actual = rdr.open(id2, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content2, actual));
		}
	}

	@SuppressWarnings("resource")
	@Test
	public void hasCacheHotMap() throws Exception {
		Map<PackExt, Integer> cacheHotMap = new HashMap<>();
		// Pack index will be kept in cache longer.
		cacheHotMap.put(PackExt.INDEX, Integer.valueOf(3));
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(512 * 4).setCacheHotMap(cacheHotMap));
		cache = DfsBlockCache.getInstance();

		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		try (ObjectReader rdr = r1.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		// All cache entries are hot and cache is at capacity.
		assertTrue(LongStream.of(cache.getHitCount()).sum() > 0);
		assertEquals(99, cache.getFillPercentage());

		InMemoryRepository r2 = new InMemoryRepository(repo);
		content = rng.nextBytes(424242);
		try (ObjectInserter ins = r2.newObjectInserter()) {
			ins.insert(OBJ_BLOB, content);
			ins.flush();
		}
		assertEquals(0, LongStream.of(cache.getMissCount()).sum());
		assertTrue(cache.getEvictions()[PackExt.PACK.getPosition()] > 0);
		assertEquals(0, cache.getEvictions()[PackExt.INDEX.getPosition()]);
	}

	@SuppressWarnings("resource")
	@Test
	public void hasIndexEventConsumerOnlyLoaded() throws Exception {
		AtomicInteger loaded = new AtomicInteger();
		IndexEventConsumer indexEventConsumer = new IndexEventConsumer() {
			@Override
			public void acceptRequestedEvent(int packExtPos, boolean cacheHit,
					long loadMicros, long bytes,
					Duration lastEvictionDuration) {
				assertEquals(PackExt.INDEX.getPosition(), packExtPos);
				assertTrue(cacheHit);
				assertTrue(lastEvictionDuration.isZero());
				loaded.incrementAndGet();
			}
		};

		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(512 * 4)
				.setIndexEventConsumer(indexEventConsumer));
		cache = DfsBlockCache.getInstance();

		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		try (ObjectReader rdr = r1.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		// All cache entries are hot and cache is at capacity.
		assertTrue(LongStream.of(cache.getHitCount()).sum() > 0);
		assertEquals(99, cache.getFillPercentage());

		InMemoryRepository r2 = new InMemoryRepository(repo);
		content = rng.nextBytes(424242);
		try (ObjectInserter ins = r2.newObjectInserter()) {
			ins.insert(OBJ_BLOB, content);
			ins.flush();
		}
		assertTrue(cache.getEvictions()[PackExt.PACK.getPosition()] > 0);
		assertEquals(1, cache.getEvictions()[PackExt.INDEX.getPosition()]);
		assertEquals(1, loaded.get());
	}

	@SuppressWarnings("resource")
	@Test
	public void hasIndexEventConsumerLoadedAndEvicted() throws Exception {
		AtomicInteger loaded = new AtomicInteger();
		AtomicInteger evicted = new AtomicInteger();
		IndexEventConsumer indexEventConsumer = new IndexEventConsumer() {
			@Override
			public void acceptRequestedEvent(int packExtPos, boolean cacheHit,
					long loadMicros, long bytes,
					Duration lastEvictionDuration) {
				assertEquals(PackExt.INDEX.getPosition(), packExtPos);
				assertTrue(cacheHit);
				assertTrue(lastEvictionDuration.isZero());
				loaded.incrementAndGet();
			}

			@Override
			public void acceptEvictedEvent(int packExtPos, long bytes,
					int totalCacheHitCount, Duration lastEvictionDuration) {
				assertEquals(PackExt.INDEX.getPosition(), packExtPos);
				assertTrue(totalCacheHitCount > 0);
				assertTrue(lastEvictionDuration.isZero());
				evicted.incrementAndGet();
			}

			@Override
			public boolean shouldReportEvictedEvent() {
				return true;
			}
		};

		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(512 * 4)
				.setIndexEventConsumer(indexEventConsumer));
		cache = DfsBlockCache.getInstance();

		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		try (ObjectReader rdr = r1.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		// All cache entries are hot and cache is at capacity.
		assertTrue(LongStream.of(cache.getHitCount()).sum() > 0);
		assertEquals(99, cache.getFillPercentage());

		InMemoryRepository r2 = new InMemoryRepository(repo);
		content = rng.nextBytes(424242);
		try (ObjectInserter ins = r2.newObjectInserter()) {
			ins.insert(OBJ_BLOB, content);
			ins.flush();
		}
		assertTrue(cache.getEvictions()[PackExt.PACK.getPosition()] > 0);
		assertEquals(1, cache.getEvictions()[PackExt.INDEX.getPosition()]);
		assertEquals(1, loaded.get());
		assertEquals(1, evicted.get());
	}

	@Test
	public void noConcurrencySerializedReads_oneRepo() throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test");
		// Reset cache with concurrency Level at 1 i.e. no concurrency.
		resetCache(1);

		DfsReader reader = (DfsReader) r1.newObjectReader();
		for (DfsPackFile pack : r1.getObjectDatabase().getPacks()) {
			// Only load non-garbage pack with bitmap.
			if (pack.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getPackIndex(reader));
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getCommitGraph(reader));
		}
		waitForExecutorPoolTermination();

		assertEquals(1, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	@SuppressWarnings("resource")
	@Test
	public void noConcurrencySerializedReads_twoRepos() throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test1");
		InMemoryRepository r2 = createRepoWithBitmap("test2");
		resetCache(1);

		DfsReader reader = (DfsReader) r1.newObjectReader();
		DfsPackFile[] r1Packs = r1.getObjectDatabase().getPacks();
		DfsPackFile[] r2Packs = r2.getObjectDatabase().getPacks();
		// Safety check that both repos have the same number of packs.
		assertEquals(r1Packs.length, r2Packs.length);

		for (int i = 0; i < r1.getObjectDatabase().getPacks().length; ++i) {
			DfsPackFile pack1 = r1Packs[i];
			DfsPackFile pack2 = r2Packs[i];
			if (pack1.isGarbage() || pack2.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack1.getBitmapIndex(reader));
			asyncRun(() -> pack2.getBitmapIndex(reader));
			asyncRun(() -> pack1.getCommitGraph(reader));
			asyncRun(() -> pack2.getCommitGraph(reader));
		}

		waitForExecutorPoolTermination();
		assertEquals(2, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	@SuppressWarnings("resource")
	@Test
	public void lowConcurrencyParallelReads_twoRepos() throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test1");
		InMemoryRepository r2 = createRepoWithBitmap("test2");
		resetCache(2);

		DfsReader reader = (DfsReader) r1.newObjectReader();
		DfsPackFile[] r1Packs = r1.getObjectDatabase().getPacks();
		DfsPackFile[] r2Packs = r2.getObjectDatabase().getPacks();
		// Safety check that both repos have the same number of packs.
		assertEquals(r1Packs.length, r2Packs.length);

		for (int i = 0; i < r1.getObjectDatabase().getPacks().length; ++i) {
			DfsPackFile pack1 = r1Packs[i];
			DfsPackFile pack2 = r2Packs[i];
			if (pack1.isGarbage() || pack2.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack1.getBitmapIndex(reader));
			asyncRun(() -> pack2.getBitmapIndex(reader));
			asyncRun(() -> pack1.getCommitGraph(reader));
			asyncRun(() -> pack2.getCommitGraph(reader));
		}

		waitForExecutorPoolTermination();
		assertEquals(2, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	@SuppressWarnings("resource")
	@Test
	public void lowConcurrencyParallelReads_twoReposAndIndex()
			throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test1");
		InMemoryRepository r2 = createRepoWithBitmap("test2");
		resetCache(2);

		DfsReader reader = (DfsReader) r1.newObjectReader();
		DfsPackFile[] r1Packs = r1.getObjectDatabase().getPacks();
		DfsPackFile[] r2Packs = r2.getObjectDatabase().getPacks();
		// Safety check that both repos have the same number of packs.
		assertEquals(r1Packs.length, r2Packs.length);

		for (int i = 0; i < r1.getObjectDatabase().getPacks().length; ++i) {
			DfsPackFile pack1 = r1Packs[i];
			DfsPackFile pack2 = r2Packs[i];
			if (pack1.isGarbage() || pack2.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack1.getBitmapIndex(reader));
			asyncRun(() -> pack1.getPackIndex(reader));
			asyncRun(() -> pack1.getCommitGraph(reader));
			asyncRun(() -> pack2.getBitmapIndex(reader));
			asyncRun(() -> pack2.getCommitGraph(reader));
		}
		waitForExecutorPoolTermination();

		assertEquals(2, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		// Index is loaded once for each repo.
		assertEquals(2, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(2, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	@Test
	public void highConcurrencyParallelReads_oneRepo() throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test");
		resetCache();

		DfsReader reader = (DfsReader) r1.newObjectReader();
		for (DfsPackFile pack : r1.getObjectDatabase().getPacks()) {
			// Only load non-garbage pack with bitmap.
			if (pack.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getPackIndex(reader));
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getCommitGraph(reader));
		}
		waitForExecutorPoolTermination();

		assertEquals(1, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	@Test
	public void highConcurrencyParallelReads_oneRepoParallelReverseIndex()
			throws Exception {
		InMemoryRepository r1 = createRepoWithBitmap("test");
		resetCache();

		DfsReader reader = (DfsReader) r1.newObjectReader();
		reader.getOptions().setLoadRevIndexInParallel(true);
		for (DfsPackFile pack : r1.getObjectDatabase().getPacks()) {
			// Only load non-garbage pack with bitmap.
			if (pack.isGarbage()) {
				continue;
			}
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getPackIndex(reader));
			asyncRun(() -> pack.getBitmapIndex(reader));
			asyncRun(() -> pack.getCommitGraph(reader));
		}
		waitForExecutorPoolTermination();

		assertEquals(1, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.REVERSE_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.COMMIT_GRAPH.ordinal()]);
	}

	private void resetCache() {
		resetCache(32);
	}

	private void resetCache(int concurrencyLevel) {
		DfsBlockCacheConfig cacheConfig = null;
		switch (cacheType) {
		case SINGLE_TABLE_CLOCK_BLOCK_CACHE:
			cacheConfig = new DfsBlockCacheConfig().setBlockSize(512)
					.setConcurrencyLevel(concurrencyLevel)
					.setBlockLimit(1 << 20);
			break;
		case EXT_SPLIT_TABLE_CLOCK_BLOCK_CACHE:
		case EXT_SPLIT_TABLE_CACHE_WITH_CUSTOM_FACTORY:
			List<DfsBlockCachePackExtConfig> packExtCacheConfigs = new ArrayList<>();
			for (PackExt packExt : PackExt.values()) {
				DfsBlockCacheConfig extCacheConfig = new DfsBlockCacheConfig()
						.setBlockSize(512).setConcurrencyLevel(concurrencyLevel)
						.setBlockLimit(1 << 20)
						.setPackExtCacheConfigurations(packExtCacheConfigs);
				packExtCacheConfigs.add(new DfsBlockCachePackExtConfig(
						Set.of(packExt), extCacheConfig));
			}
			cacheConfig = new DfsBlockCacheConfig().setBlockSize(512)
					.setConcurrencyLevel(concurrencyLevel)
					.setBlockLimit(1 << 20)
					.setPackExtCacheConfigurations(packExtCacheConfigs);
			break;
		}
		assertNotNull(cacheConfig);
		if (cacheType == CacheType.EXT_SPLIT_TABLE_CACHE_WITH_CUSTOM_FACTORY) {
			DfsBlockCache.reconfigure(cacheConfig, testBlockCacheTableFactory);
		} else {
			DfsBlockCache.reconfigure(cacheConfig);
		}
		cache = DfsBlockCache.getInstance();
	}

	private InMemoryRepository createRepoWithBitmap(String repoName)
			throws Exception {
		DfsRepositoryDescription repoDesc = new DfsRepositoryDescription(
				repoName);
		InMemoryRepository repo = new InMemoryRepository(repoDesc);
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				repo)) {
			RevCommit commit = repository.branch("/refs/ref1" + repoName)
					.commit().add("blob1", "blob1" + repoName).create();
			repository.branch("/refs/ref2" + repoName).commit()
					.add("blob2", "blob2" + repoName).parent(commit).create();
		}
		new DfsGarbageCollector(repo).setWriteCommitGraph(true).pack(null);
		return repo;
	}

	private void asyncRun(Callable<?> call) {
		pool.execute(() -> {
			try {
				call.call();
			} catch (Exception e) {
				// Ignore.
			}
		});
	}

	private void waitForExecutorPoolTermination() throws Exception {
		pool.shutdown();
		pool.awaitTermination(500, MILLISECONDS);
		assertTrue("Threads did not complete, likely due to a deadlock.",
				pool.isTerminated());
	}

	private static class TestBlockCacheTables implements DfsBlockCacheTables {
		private final DfsPackExtBlockCacheTables dfsPackExtBlockCacheTables;

		private int called = 0;

		TestBlockCacheTables(
				DfsPackExtBlockCacheTables dfsPackExtBlockCacheTables) {
			this.dfsPackExtBlockCacheTables = dfsPackExtBlockCacheTables;
		}

		@Override
		public DfsBlockCacheTable getTable(PackExt packExt) {
			called += 1;
			return dfsPackExtBlockCacheTables.getTable(packExt);
		}

		@Override
		public DfsBlockCacheTable getTable(DfsStreamKey key) {
			called += 1;
			return dfsPackExtBlockCacheTables.getTable(key);
		}

		@Override
		public List<DfsBlockCacheStats> getBlockCacheTableListStats() {
			called += 1;
			return dfsPackExtBlockCacheTables.getBlockCacheTableListStats();
		}
	}

	private static class TestBlockCacheTableFactory
			implements DfsBlockCacheTablesFactory {
		TestBlockCacheTables testBlockCacheTables = null;

		@Override
		public DfsBlockCacheTables fromPackExtCacheConfigs(
				List<DfsBlockCachePackExtConfig> packExtCacheConfigs) {
			testBlockCacheTables = new TestBlockCacheTables(
					DfsPackExtBlockCacheTables
							.fromPackExtCacheConfigs(packExtCacheConfigs));
			return testBlockCacheTables;
		}
	}
}
