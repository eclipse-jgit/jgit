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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-local, disk-backed cache of generated pack streams.
 * <p>
 * Matching requests share one producer and independently read its growing file.
 * The producer runs on a bounded executor. Disk writes cannot run more than
 * 64 KiB ahead of the greatest progress reported by a consumer. Disconnecting
 * one client does not stop other clients;
 * removing the last reader cancels the producer. The request that supplied the
 * producer always waits for it to terminate before returning, so its repository
 * resources remain valid throughout generation.
 * <p>
 * The byte budget limits completed retained packs. Active spools and files still
 * being read after eviction can exceed this budget. Provision storage for active
 * transfers as well as retained data. Entry and producer counts are bounded.
 * <p>
 * Callers must authorize every request before lookup and include the repository,
 * authorization scope and all pack inputs in the key. Only raw pack bytes belong
 * in the producer output, without framing, progress or other protocol messages.
 *
 * @since 8.0
 */
public final class PackOutputCache implements AutoCloseable {
	/** Generates the raw bytes and statistics of one pack. */
	@FunctionalInterface
	public interface PackProducer {
		/**
		 * Generate a pack on a cache worker, without closing its output stream.
		 * The generator must not require the calling thread's context.
		 *
		 * @param out
		 *            destination of raw pack bytes
		 * @return statistics of the generated pack
		 * @throws IOException
		 *             generation or output failed
		 */
		PackStatistics write(OutputStream out) throws IOException;
	}

	private static final Logger LOG = LoggerFactory
			.getLogger(PackOutputCache.class);

	private static final String DIRECTORY_PREFIX = "jgit-pack-output-"; //$NON-NLS-1$

	private static final int CHUNK_SIZE = 64 << 10;

	private static final class Reader {
		long position;
	}


	private static final class Entry {
		final String key;

		final Path path;

		final Set<Reader> readers = new HashSet<>();

		final long createdAt = System.nanoTime();

		long bytes;

		long consumed;

		long completedAt;

		boolean producing = true;

		boolean retained = true;

		boolean counted;

		boolean cancelled;

		Thread thread;

		Throwable failure;

		PackStatistics statistics;

		Entry(String key, Path path) {
			this.key = key;
			this.path = path;
		}
	}

	private final Path directory;

	private final long maxBytes;

	private final int maxEntries;

	private final long maxAgeNanos;

	private final Map<String, Entry> entries = new HashMap<>();

	private final Set<Entry> files = new HashSet<>();

	private final ThreadPoolExecutor producers;

	private final ScheduledExecutorService reaper;

	private final ScheduledFuture<?> eviction;

	private long retainedBytes;

	private boolean closed;

	/**
	 * Create a cache in a new private directory. Each instance owns only files
	 * created below that directory and removes them when closed.
	 *
	 * @param directory
	 *            parent directory dedicated to pack output caches
	 * @param maxBytes
	 *            positive budget for completed retained pack bytes
	 * @param maxEntries
	 *            positive limit on all live pack files
	 * @param maxAge
	 *            positive time from entry creation until lookup expiry;
	 *            existing producers and readers may finish after expiry
	 * @param maxConcurrentProducers
	 *            positive limit on background pack generators; excess misses
	 *            bypass caching
	 * @throws IOException
	 *             cache storage cannot be created
	 */
	public PackOutputCache(Path directory, long maxBytes, int maxEntries,
			Duration maxAge, int maxConcurrentProducers)
			throws IOException {
		if (maxBytes <= 0 || maxEntries <= 0 || maxAge.isZero()
				|| maxAge.isNegative()
				|| maxConcurrentProducers <= 0) {
			throw new IllegalArgumentException("Invalid pack output cache limits"); //$NON-NLS-1$
		}
		this.maxBytes = maxBytes;
		this.maxEntries = maxEntries;
		this.maxAgeNanos = maxAge.toNanos();
		Files.createDirectories(directory);
		this.directory = Files.createTempDirectory(directory, DIRECTORY_PREFIX);
		producers = new ThreadPoolExecutor(maxConcurrentProducers,
				maxConcurrentProducers, 0, TimeUnit.MILLISECONDS,
				new SynchronousQueue<>(), r -> daemon(r, "JGit pack producer")); //$NON-NLS-1$
		reaper = Executors.newSingleThreadScheduledExecutor(
				r -> daemon(r, "JGit pack output cache eviction")); //$NON-NLS-1$
		long interval = Math.max(TimeUnit.MILLISECONDS.toNanos(10),
				Math.min(maxAgeNanos, TimeUnit.MINUTES.toNanos(1)));
		eviction = reaper.scheduleWithFixedDelay(this::maintain, interval,
				interval, TimeUnit.NANOSECONDS);
	}

	private static Thread daemon(Runnable task, String name) {
		Thread thread = new Thread(task, name);
		thread.setDaemon(true);
		return thread;
	}

	/**
	 * Generate or stream a pack. Each caller's producer is invoked at most once.
	 * Successful cached data is streamed before generation completes. A producer
	 * failure terminates all its readers; partial data is never retained as a
	 * successful entry. Client output errors affect only that client.
	 *
	 * @param key
	 *            complete, application-scoped pack key
	 * @param out
	 *            client's stream; not closed by this method
	 * @param producer
	 *            request-local generator used on a miss or bypass
	 * @return producer statistics, or a detached snapshot for a cache hit
	 * @throws IOException
	 *             generation, disk access or client output failed
	 */
	public PackStatistics write(String key, OutputStream out,
			PackProducer producer) throws IOException {
		Objects.requireNonNull(key);
		Objects.requireNonNull(out);
		Objects.requireNonNull(producer);
		Entry entry;
		Reader reader = new Reader();
		PackStatistics[] original = new PackStatistics[1];
		boolean leader = false;
		synchronized (this) {
			if (closed) {
				throw new IOException("Pack output cache is closed"); //$NON-NLS-1$
			}
			expire();
			entry = entries.get(key);
			if (entry != null) {
				entry.readers.add(reader);
			} else {
				makeRoom();
				if (files.size() < maxEntries) {
					entry = new Entry(key, Files.createTempFile(directory,
							"pack-", ".pack")); //$NON-NLS-1$ //$NON-NLS-2$
					entry.readers.add(reader);
					entries.put(key, entry);
					files.add(entry);
					Entry admitted = entry;
					try {
						producers.execute(() -> produce(admitted, producer, original));
						leader = true;
					} catch (RejectedExecutionException e) {
						entry.producing = false;
						entry.readers.clear();
						evict(entry);
						cleanup(entry);
						entry = null;
					}
				}
			}
		}
		if (entry == null) {
			return producer.write(out);
		}
		try {
			PackStatistics statistics = consume(entry, reader, out);
			return leader ? original[0] : statistics;
		} finally {
			synchronized (this) {
				entry.readers.remove(reader);
				if (entry.producing && entry.readers.isEmpty()) {
					entry.cancelled = true;
					evict(entry);
					if (entry.thread != null) {
						entry.thread.interrupt();
					}
				}
				notifyAll();
				cleanup(entry);
			}
			if (leader) {
				awaitProducer(entry);
			}
		}
	}


	private void produce(Entry entry, PackProducer producer,
			PackStatistics[] original) {
		Throwable failure = null;
		PackStatistics statistics = null;
		synchronized (this) {
			entry.thread = Thread.currentThread();
		}
		try (OutputStream out = new BufferedOutputStream(new Spool(entry),
				CHUNK_SIZE)) {
			synchronized (this) {
				checkReaders(entry);
			}
			original[0] = Objects.requireNonNull(producer.write(out));
			out.flush();
			statistics = snapshot(original[0]);
		} catch (IOException | RuntimeException | Error e) {
			failure = e;
		} finally {
			synchronized (this) {
				if (failure == null && entry.cancelled) {
					failure = new IOException("All pack readers disconnected"); //$NON-NLS-1$
				}
				entry.thread = null;
				entry.producing = false;
				entry.completedAt = System.nanoTime();
				entry.failure = failure;
				entry.statistics = statistics;
				if (failure != null) {
					evict(entry);
				} else if (entry.retained && entry.bytes <= maxBytes) {
					entry.counted = true;
					retainedBytes += entry.bytes;
					trimRetained();
				} else {
					evict(entry);
				}
				notifyAll();
				cleanup(entry);
			}
			Thread.interrupted();
		}
	}

	private PackStatistics consume(Entry entry, Reader reader, OutputStream out)
			throws IOException {
		try (InputStream in = Files.newInputStream(entry.path)) {
			byte[] buffer = new byte[CHUNK_SIZE];
			for (;;) {
				int available;
				synchronized (this) {
					while (entry.failure == null && entry.producing
							&& reader.position == entry.bytes) {
						waitForProgress();
					}
					if (entry.failure != null) {
						throw asIOException(entry.failure);
					}
					available = (int) Math.min(buffer.length,
							entry.bytes - reader.position);
					if (available == 0) {
						return entry.statistics;
					}
				}
				int count = in.read(buffer, 0, available);
				if (count < 0) {
					throw new IOException("Truncated cached pack output"); //$NON-NLS-1$
				}
				out.write(buffer, 0, count);
				out.flush();
				synchronized (this) {
					reader.position += count;
					entry.consumed = Math.max(entry.consumed, reader.position);
					notifyAll();
				}
			}
		}
	}

	private void checkReaders(Entry entry) throws IOException {
		if (entry.cancelled || entry.readers.isEmpty()) {
			throw new IOException("All pack readers disconnected"); //$NON-NLS-1$
		}
	}

	@SuppressWarnings("WaitNotInLoop") // Both callers recheck their condition in a loop.
	private void waitForProgress() throws InterruptedIOException {
		try {
			wait();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted waiting for pack output"); //$NON-NLS-1$
		}
	}

	private synchronized void awaitProducer(Entry entry) {
		boolean interrupted = false;
		while (entry.producing) {
			try {
				wait();
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private static IOException asIOException(Throwable failure) {
		return failure instanceof IOException ? (IOException) failure
				: new IOException("Pack generation failed", failure); //$NON-NLS-1$
	}

	private static PackStatistics snapshot(PackStatistics statistics) {
		PackStatistics.Accumulator result = new PackStatistics.Accumulator();
		result.advertised = statistics.getAdvertised();
		result.wants = statistics.getWants();
		result.haves = statistics.getHaves();
		result.notAdvertisedWants = statistics.getNotAdvertisedWants();
		result.timeNegotiating = statistics.getTimeNegotiating();
		result.interestingObjects = copyIds(statistics.getInterestingObjects());
		result.uninterestingObjects = copyIds(statistics.getUninterestingObjects());
		result.clientShallowCommits = copyIds(statistics.getClientShallowCommits());
		result.reusedPacks = List.of();
		result.rootCommits = copyIds(statistics.getRootCommits());
		result.depth = statistics.getDepth();
		result.deltaSearchNonEdgeObjects = statistics.getDeltaSearchNonEdgeObjects();
		result.deltasFound = statistics.getDeltasFound();
		result.totalObjects = statistics.getTotalObjects();
		result.bitmapIndexMisses = statistics.getBitmapIndexMisses();
		result.totalDeltas = statistics.getTotalDeltas();
		result.reusedObjects = statistics.getReusedObjects();
		result.reusedDeltas = statistics.getReusedDeltas();
		result.totalBytes = statistics.getTotalBytes();
		result.thinPackBytes = statistics.getThinPackBytes();
		result.timeCounting = statistics.getTimeCounting();
		result.timeSearchingForReuse = statistics.getTimeSearchingForReuse();
		result.timeSearchingForSizes = statistics.getTimeSearchingForSizes();
		result.timeCompressing = statistics.getTimeCompressing();
		result.timeWriting = statistics.getTimeWriting();
		result.reachabilityCheckDuration = statistics.getReachabilityCheckDuration();
		result.treesTraversed = statistics.getTreesTraversed();
		result.offloadedPackfiles = statistics.getOffloadedPackfiles();
		result.offloadedPackfileSize = statistics.getOffloadedPackfilesSize();
		for (int type : new int[] { Constants.OBJ_COMMIT, Constants.OBJ_TREE,
				Constants.OBJ_BLOB, Constants.OBJ_TAG }) {
			PackStatistics.ObjectType source = statistics.byObjectType(type);
			PackStatistics.ObjectType.Accumulator target = result.objectTypes[type];
			target.cntObjects = source.getObjects();
			target.cntDeltas = source.getDeltas();
			target.reusedObjects = source.getReusedObjects();
			target.reusedDeltas = source.getReusedDeltas();
			target.bytes = source.getBytes();
			target.deltaBytes = source.getDeltaBytes();
		}
		return new PackStatistics(result);
	}

	private static Set<ObjectId> copyIds(Set<ObjectId> source) {
		if (source == null) {
			return null;
		}
		Set<ObjectId> result = new HashSet<>();
		for (ObjectId id : source) {
			result.add(id.copy());
		}
		return Set.copyOf(result);
	}


	private synchronized void maintain() {
		expire();
	}

	private void expire() {
		long now = System.nanoTime();
		for (Entry entry : new HashSet<>(files)) {
			if (entry.retained && now - entry.createdAt >= maxAgeNanos) {
				evict(entry);
			}
			cleanup(entry);
		}
	}

	private void makeRoom() {
		while (files.size() >= maxEntries) {
			Entry oldest = oldestCompleted(true);
			if (oldest == null) {
				return;
			}
			evict(oldest);
			cleanup(oldest);
		}
	}

	private void trimRetained() {
		while (retainedBytes > maxBytes) {
			Entry oldest = oldestCompleted(false);
			if (oldest == null) {
				return;
			}
			evict(oldest);
			cleanup(oldest);
		}
	}

	private Entry oldestCompleted(boolean idleOnly) {
		Entry oldest = null;
		for (Entry entry : files) {
			if (!entry.producing && entry.retained
					&& (!idleOnly || entry.readers.isEmpty())
					&& (oldest == null || entry.completedAt < oldest.completedAt)) {
				oldest = entry;
			}
		}
		return oldest;
	}

	private void evict(Entry entry) {
		entry.retained = false;
		entries.remove(entry.key, entry);
		if (entry.counted) {
			retainedBytes -= entry.bytes;
			entry.counted = false;
		}
	}

	private void cleanup(Entry entry) {
		if (entry.retained || entry.producing || !entry.readers.isEmpty()) {
			return;
		}
		try {
			Files.deleteIfExists(entry.path);
			files.remove(entry);
			finishClose();
		} catch (IOException e) {
			LOG.warn("Cannot remove expired pack output {}", entry.path, e); //$NON-NLS-1$
		}
	}

	private void finishClose() throws IOException {
		if (closed && files.isEmpty()) {
			Files.deleteIfExists(directory);
		}
	}

	/**
	 * Stop admitting requests and retaining packs. Existing readers and their
	 * producers can finish; their files are removed after all readers release them.
	 */
	@Override
	public synchronized void close() {
		closed = true;
		eviction.cancel(false);
		reaper.shutdown();
		producers.shutdown();
		for (Entry entry : new HashSet<>(files)) {
			evict(entry);
			cleanup(entry);
		}
		try {
			finishClose();
		} catch (IOException e) {
			LOG.warn("Cannot remove pack output cache {}", directory, e); //$NON-NLS-1$
		}
	}


	private final class Spool extends OutputStream {
		private final Entry entry;

		private final OutputStream file;

		Spool(Entry entry) throws IOException {
			this.entry = entry;
			file = Files.newOutputStream(entry.path);
		}

		@Override
		public void write(int value) throws IOException {
			write(new byte[] { (byte) value }, 0, 1);
		}

		@Override
		public void write(byte[] buffer, int offset, int length)
				throws IOException {
			Objects.checkFromIndexSize(offset, length, buffer.length);
			while (length > 0) {
				int count;
				synchronized (PackOutputCache.this) {
					checkReaders(entry);
					while (entry.bytes - entry.consumed >= CHUNK_SIZE) {
						waitForProgress();
						checkReaders(entry);
					}
					count = (int) Math.min(length,
							CHUNK_SIZE - (entry.bytes - entry.consumed));
				}
				file.write(buffer, offset, count);
				synchronized (PackOutputCache.this) {
					entry.bytes += count;
					PackOutputCache.this.notifyAll();
				}
				offset += count;
				length -= count;
			}
		}

		@Override
		public void close() throws IOException {
			file.close();
		}
	}
}
