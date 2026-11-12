/*
 * Copyright (C) 2026, Son Luong Ngoc and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PackOutputCacheTest {

	private Path directory;

	private ExecutorService executor;

	private PackOutputCache cache;

	@Before
	public void setUp() throws IOException {
		directory = Files.createTempDirectory("pack-output-cache-test-");
		executor = Executors.newFixedThreadPool(2);
		cache = new PackOutputCache(directory, 1024, 8, Duration.ofMinutes(1), 0, 2);
	}

	@After
	public void tearDown() throws Exception {
		cache.close();
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		FileUtils.delete(directory.toFile(),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	@Test
	public void concurrentRequestsGenerateOneValidPack() throws Exception {
		byte[] contents = "cached pack".getBytes(UTF_8);
		byte[] pack = new InMemoryPack().header(1)
				.write((Constants.OBJ_BLOB << 4) | contents.length)
				.deflate(contents).digest().toByteArray();
		AtomicInteger generated = new AtomicInteger();
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		PackOutputCache.PackProducer producer = out -> {
			generated.incrementAndGet();
			out.write(pack, 0, pack.length / 2);
			producing.countDown();
			await(complete);
			out.write(pack, pack.length / 2, pack.length - pack.length / 2);
			return statistics();
		};
		Future<byte[]> leader = executor.submit(() -> write("same", producer));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		CountDownLatch following = new CountDownLatch(1);
		Future<byte[]> follower = executor.submit(() -> {
			following.countDown();
			return write("same", producer);
		});
		assertTrue(following.await(5, TimeUnit.SECONDS));
		try {
			assertThrows(TimeoutException.class,
					() -> follower.get(100, TimeUnit.MILLISECONDS));
		} finally {
			complete.countDown();
		}

		byte[] first = leader.get(5, TimeUnit.SECONDS);
		byte[] second = follower.get(5, TimeUnit.SECONDS);
		assertEquals(1, generated.get());
		assertArrayEquals(pack, first);
		assertArrayEquals(pack, second);
		ObjectId blob = new ObjectInserter.Formatter()
				.idFor(Constants.OBJ_BLOB, contents);
		for (byte[] received : new byte[][] { first, second }) {
			try (InMemoryRepository repo = new InMemoryRepository(
					new DfsRepositoryDescription("client"));
					ObjectInserter inserter = repo.newObjectInserter()) {
				inserter.newPackParser(new ByteArrayInputStream(received))
						.parse(NullProgressMonitor.INSTANCE);
				inserter.flush();
				assertTrue(repo.getObjectDatabase().has(blob));
			}
		}
	}

	@Test
	public void differentKeysDoNotSharePacks() throws Exception {
		AtomicInteger generated = new AtomicInteger();
		PackOutputCache.PackProducer producer = out -> {
			out.write(generated.incrementAndGet());
			return statistics();
		};
		assertArrayEquals(new byte[] { 1 }, write("first", producer));
		assertArrayEquals(new byte[] { 2 }, write("second", producer));
		assertArrayEquals(new byte[] { 1 }, write("first", producer));
		assertEquals(2, generated.get());
	}

	@Test
	public void failedProducerDoesNotRetainPartialPackAndCanRetry()
			throws Exception {
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		PackOutputCache.PackProducer failing = out -> {
			out.write(1);
			out.flush();
			producing.countDown();
			await(complete);
			throw new IOException("generation failed");
		};
		ByteArrayOutputStream first = new ByteArrayOutputStream();
		Future<?> leader = executor
				.submit(() -> cache.write("failure", first, failing));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		ByteArrayOutputStream second = new ByteArrayOutputStream();
		CountDownLatch following = new CountDownLatch(1);
		Future<?> follower = executor.submit(() -> {
			following.countDown();
			return cache.write("failure", second, failing);
		});
		assertTrue(following.await(5, TimeUnit.SECONDS));
		try {
			assertThrows(TimeoutException.class,
					() -> follower.get(100, TimeUnit.MILLISECONDS));
		} finally {
			complete.countDown();
		}
		for (Future<?> request : new Future<?>[] { leader, follower }) {
			ExecutionException error = assertThrows(ExecutionException.class,
					() -> request.get(5, TimeUnit.SECONDS));
			assertEquals("generation failed", error.getCause().getMessage());
		}
		assertTrue(first.size() <= 1);
		assertTrue(second.size() <= 1);
		assertEquals(0, storedBytes());
		assertArrayEquals(new byte[] { 2 }, write("failure", out -> {
			out.write(2);
			return statistics();
		}));
	}

	@Test
	public void disconnectedClientDoesNotInvalidateCompletedPack()
			throws Exception {
		AtomicInteger generated = new AtomicInteger();
		PackOutputCache.PackProducer producer = out -> {
			generated.incrementAndGet();
			out.write(new byte[] { 1, 2, 3 });
			return statistics();
		};
		assertArrayEquals(new byte[] { 1, 2, 3 },
				write("disconnect", producer));
		assertThrows(IOException.class, () -> cache.write("disconnect",
				new OutputStream() {
					@Override
					public void write(int value) throws IOException {
						throw new IOException("client disconnected");
					}
				}, producer));
		assertArrayEquals(new byte[] { 1, 2, 3 },
				write("disconnect", producer));
		assertEquals(1, generated.get());
	}

	@Test
	public void cachedStatisticsDoNotRetainMutableProducerState()
			throws Exception {
		PackStatistics.Accumulator original = new PackStatistics.Accumulator();
		try (InMemoryRepository repo = new InMemoryRepository(
				new DfsRepositoryDescription("statistics"));
				RevWalk walk = new RevWalk(repo)) {
			RevCommit commit = walk.lookupCommit(ObjectId.zeroId());
			original.interestingObjects = new HashSet<>(Set.of(commit));
			original.uninterestingObjects = new HashSet<>(Set.of(commit));
			original.clientShallowCommits = new HashSet<>(Set.of(commit));
			original.rootCommits = new HashSet<>(Set.of(commit));
			original.totalObjects = 7;
			original.objectTypes[Constants.OBJ_COMMIT].cntObjects = 3;
			cache.write("statistics", new ByteArrayOutputStream(), out -> {
				out.write(1);
				return new PackStatistics(original);
			});
		}

		original.totalObjects = 99;
		original.objectTypes[Constants.OBJ_COMMIT].cntObjects = 99;
		original.interestingObjects.clear();
		PackStatistics cached = cache.write("statistics",
				new ByteArrayOutputStream(), out -> {
					throw new AssertionError("cached pack must be used");
				});
		assertEquals(7, cached.getTotalObjects());
		assertEquals(3, cached.byObjectType(Constants.OBJ_COMMIT).getObjects());
		assertTrue(cached.getReusedPacks().isEmpty());
		for (Set<ObjectId> ids : List.of(cached.getInterestingObjects(),
				cached.getUninterestingObjects(), cached.getClientShallowCommits(),
				cached.getRootCommits())) {
			assertEquals(1, ids.size());
			assertEquals(ObjectId.class, ids.iterator().next().getClass());
			assertThrows(UnsupportedOperationException.class, ids::clear);
		}
	}

	@Test
	public void oversizedPackStreamsSuccessfullyWithoutRetention()
			throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 3, 1, Duration.ofMinutes(1), 0, 2);
		AtomicInteger generated = new AtomicInteger();
		byte[] pack = { 1, 2, 3, 4, 5 };
		PackOutputCache.PackProducer producer = out -> {
			generated.incrementAndGet();
			out.write(pack, 0, 2);
			assertTrue(cache.getStats().retainedBytes() <= 3);
			out.write(pack, 2, 3);
			assertTrue(cache.getStats().retainedBytes() <= 3);
			return statistics();
		};
		assertArrayEquals(pack, write("oversized", producer));
		assertEquals(1, generated.get());
		assertEquals(0, storedBytes());
		assertArrayEquals(pack, write("oversized", producer));
		assertEquals(2, generated.get());
	}

	@Test
	public void entryLimitEvictsIdleCompletedPacks() throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 1024, 1, Duration.ofMinutes(1), 0, 2);
		write("retained", out -> {
			out.write(1);
			return statistics();
		});
		AtomicInteger generated = new AtomicInteger();
		PackOutputCache.PackProducer producer = out -> {
			out.write(generated.incrementAndGet());
			return statistics();
		};
		assertArrayEquals(new byte[] { 1 }, write("bypassed", producer));
		assertArrayEquals(new byte[] { 1 }, write("bypassed", producer));
		assertEquals(1, generated.get());
		assertEquals(1, storedBytes());
	}

	@Test
	public void retentionBudgetDoesNotFailConcurrentInflightPacks() throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 3, 2, Duration.ofMinutes(1), 0, 2);
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		Future<byte[]> first = executor.submit(() -> write("inflight", out -> {
			out.write(new byte[] { 1, 2 });
			out.flush();
			producing.countDown();
			await(complete);
			return statistics();
		}));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		try {
			assertArrayEquals(new byte[] { 3, 4 }, write("other", out -> {
				out.write(new byte[] { 3, 4 });
				assertTrue(cache.getStats().retainedBytes() <= 3);
				return statistics();
			}));
			assertTrue(cache.getStats().retainedBytes() <= 3);
		} finally {
			complete.countDown();
		}
		assertArrayEquals(new byte[] { 1, 2 }, first.get(5, TimeUnit.SECONDS));
	}

	@Test
	public void idleEntriesExpireAndClosePreservesUnrelatedFiles()
			throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 1024, 8, Duration.ofMillis(20), 0, 2);
		Path unrelated = directory.resolve("unrelated");
		Files.write(unrelated, new byte[] { 42 });
		write("expiring", out -> {
			out.write(1);
			return statistics();
		});
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (storedBytes() != 1 && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertEquals(1, storedBytes());
		cache.close();
		assertArrayEquals(new byte[] { 42 }, Files.readAllBytes(unrelated));
		try (Stream<Path> paths = Files.list(directory)) {
			assertEquals(1, paths.count());
		}
		assertThrows(IOException.class, () -> write("closed", out -> statistics()));
	}

	@Test
	public void closeLetsActiveReadersFinish() throws Exception {
		byte[] pack = { 1, 2, 3 };
		write("active", out -> {
			out.write(pack);
			return statistics();
		});
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		ByteArrayOutputStream received = new ByteArrayOutputStream();
		Future<?> reader = executor.submit(() -> cache.write("active",
				new OutputStream() {
					@Override
					public void write(int value) throws IOException {
						reading.countDown();
						await(complete);
						received.write(value);
					}
				}, out -> {
					throw new AssertionError("cached pack must be used");
				}));
		assertTrue(reading.await(5, TimeUnit.SECONDS));
		try {
			cache.close();
			assertEquals(pack.length, storedBytes());
			assertFalse(reader.isDone());

		} finally {
			complete.countDown();
		}
		reader.get(5, TimeUnit.SECONDS);
		assertArrayEquals(pack, received.toByteArray());
		assertEquals(0, storedBytes());
	}

	@Test
	public void streamsBeforeProducerCompletes() throws Exception {
		CountDownLatch prefix = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		ByteArrayOutputStream received = new ByteArrayOutputStream();
		Future<?> request = executor.submit(() -> cache.write("streaming",
				new OutputStream() {
					@Override
					public void write(int value) {
						received.write(value);
						prefix.countDown();
					}
				}, out -> {
					out.write(1);
					out.flush();
					await(complete);
					out.write(2);
					return statistics();
				}));
		try {
			assertTrue(prefix.await(5, TimeUnit.SECONDS));
			assertFalse(request.isDone());
			assertArrayEquals(new byte[] { 1 }, received.toByteArray());
		} finally {
			complete.countDown();
		}
		request.get(5, TimeUnit.SECONDS);
		assertArrayEquals(new byte[] { 1, 2 }, received.toByteArray());
	}

	@Test
	public void producerWaitsForConsumerProgress() throws Exception {
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		CountDownLatch secondChunk = new CountDownLatch(1);
		byte[] chunk = new byte[64 << 10];
		Future<?> request = executor.submit(() -> cache.write("backpressure",
				new OutputStream() {
					@Override
					public void write(int value) {
						throw new AssertionError("bulk writes expected");
					}

					@Override
					public void write(byte[] data, int offset, int length)
							throws IOException {
						reading.countDown();
						await(complete);
					}
				}, out -> {
					out.write(chunk);
					out.write(chunk);
					secondChunk.countDown();
					return statistics();
				}));
		try {
			assertTrue(reading.await(5, TimeUnit.SECONDS));
			assertFalse(secondChunk.await(100, TimeUnit.MILLISECONDS));
			assertEquals(chunk.length, cache.getStats().generatedBytes());
		} finally {
			complete.countDown();
		}
		request.get(5, TimeUnit.SECONDS);
		assertEquals(2L * chunk.length, cache.getStats().servedBytes());
	}

	@Test
	public void ownerDisconnectWaitsForActualProducerTermination()
			throws Exception {
		CountDownLatch interrupted = new CountDownLatch(1);
		CountDownLatch cleanup = new CountDownLatch(1);
		AtomicInteger terminated = new AtomicInteger();
		Future<?> request = executor.submit(() -> cache.write("cancelled",
				new OutputStream() {
					@Override
					public void write(int value) throws IOException {
						throw new IOException("client disconnected");
					}
				}, out -> {
					out.write(1);
					out.flush();
					try {
						new CountDownLatch(1).await();
					} catch (InterruptedException e) {
						interrupted.countDown();
						await(cleanup);
					}
					terminated.incrementAndGet();
					return statistics();
				}));
		try {
			assertTrue(interrupted.await(5, TimeUnit.SECONDS));
			assertFalse(request.isDone());
			assertEquals(1, cache.getStats().activeProducers());
		} finally {
			cleanup.countDown();
		}
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> request.get(5, TimeUnit.SECONDS));
		assertEquals("client disconnected", error.getCause().getMessage());
		assertEquals(1, terminated.get());
		assertEquals(0, cache.getStats().activeProducers());
		assertEquals(0, cache.getStats().entries());
	}

	@Test
	public void ownerDisconnectDoesNotStopFollowingClient() throws Exception {
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch prefix = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		PackOutputCache.PackProducer producer = out -> {
			producing.countDown();
			await(prefix);
			out.write(1);
			out.flush();
			await(complete);
			out.write(2);
			return statistics();
		};
		Future<?> owner = executor.submit(() -> cache.write("shared",
				new OutputStream() {
					@Override
					public void write(int value) throws IOException {
						throw new IOException("owner disconnected");
					}
				}, producer));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		Future<byte[]> follower = executor.submit(() -> write("shared", producer));
		try {
			awaitHits(1);
			prefix.countDown();
			assertThrows(TimeoutException.class,
					() -> owner.get(100, TimeUnit.MILLISECONDS));
			assertEquals(1, cache.getStats().activeProducers());
		} finally {
			prefix.countDown();
			complete.countDown();
		}
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> owner.get(5, TimeUnit.SECONDS));
		assertEquals("owner disconnected", error.getCause().getMessage());
		assertArrayEquals(new byte[] { 1, 2 }, follower.get(5, TimeUnit.SECONDS));
		assertEquals(1, cache.getStats().misses());
		assertEquals(0, cache.getStats().generationFailures());
	}

	@Test
	public void executorSaturationBypassesWithoutQueueing() throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 1024, 8, Duration.ofMinutes(1),
				0, 1);
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		Future<byte[]> owner = executor.submit(() -> write("busy", out -> {
			producing.countDown();
			await(complete);
			out.write(1);
			return statistics();
		}));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		try {
			Thread requestThread = Thread.currentThread();
			AtomicReference<Thread> generationThread = new AtomicReference<>();
			assertArrayEquals(new byte[] { 2 }, write("bypass", out -> {
				generationThread.set(Thread.currentThread());
				out.write(2);
				return statistics();
			}));
			assertEquals(requestThread, generationThread.get());
			assertEquals(1, cache.getStats().activeProducers());
			assertEquals(1, cache.getStats().bypasses());
		} finally {
			complete.countDown();
		}
		assertArrayEquals(new byte[] { 1 }, owner.get(5, TimeUnit.SECONDS));
	}

	@Test
	public void admissionRequiresConfiguredPriorOccurrences() throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 1024, 8, Duration.ofMinutes(1),
				1, 2);
		AtomicInteger generated = new AtomicInteger();
		PackOutputCache.PackProducer producer = out -> {
			generated.incrementAndGet();
			out.write(new byte[] { 1, 2, 3 });
			return statistics();
		};
		for (int request = 0; request < 3; request++) {
			assertArrayEquals(new byte[] { 1, 2, 3 }, write("admitted", producer));
		}
		assertEquals(2, generated.get());
		PackOutputCache.Stats stats = cache.getStats();
		assertEquals(1, stats.bypasses());
		assertEquals(1, stats.misses());
		assertEquals(1, stats.hits());
		assertEquals(6, stats.generatedBytes());
		assertEquals(9, stats.servedBytes());
		assertEquals(3, stats.bytes());
		assertEquals(3, stats.retainedBytes());
	}

	@Test
	public void expiredInflightEntryFinishesWithoutAdmittingNewReaders()
			throws Exception {
		cache.close();
		cache = new PackOutputCache(directory, 1024, 8, Duration.ofMillis(20),
				0, 2);
		CountDownLatch producing = new CountDownLatch(1);
		CountDownLatch complete = new CountDownLatch(1);
		Future<byte[]> owner = executor.submit(() -> write("expired", out -> {
			producing.countDown();
			await(complete);
			out.write(1);
			return statistics();
		}));
		assertTrue(producing.await(5, TimeUnit.SECONDS));
		try {
			Thread.sleep(100);
			assertArrayEquals(new byte[] { 2 }, write("expired", out -> {
				out.write(2);
				return statistics();
			}));
			assertEquals(2, cache.getStats().misses());
		} finally {
			complete.countDown();
		}
		assertArrayEquals(new byte[] { 1 }, owner.get(5, TimeUnit.SECONDS));
	}

	private void awaitHits(long count) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (cache.getStats().hits() < count && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertEquals(count, cache.getStats().hits());
	}

	private byte[] write(String key, PackOutputCache.PackProducer producer)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cache.write(key, out, producer);
		return out.toByteArray();
	}

	private long storedBytes() throws IOException {
		try (Stream<Path> paths = Files.walk(directory)) {
			long result = 0;
			for (Path path : (Iterable<Path>) paths
					.filter(Files::isRegularFile)::iterator) {
				try {
					result += Files.size(path);
				} catch (NoSuchFileException e) {
					// The expiry thread may delete an entry after listing it.
				}
			}
			return result;
		}
	}

	private static PackStatistics statistics() {
		return new PackStatistics(new PackStatistics.Accumulator());
	}

	private static void await(CountDownLatch latch) throws IOException {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IOException("Timed out waiting for test");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}
}
