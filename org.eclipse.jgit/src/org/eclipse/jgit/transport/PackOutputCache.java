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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
import org.eclipse.jgit.util.io.CountingOutputStream;
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
 * The byte budget limits completed retained packs. Active spools and files pinned by
 * readers or publishers after eviction can exceed this budget. Provision storage for active
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

	/** Publishes a completed, self-contained pack outside the request thread. */
	@FunctionalInterface
	public interface PackPublisher {
		/**
		 * Publish the bytes before making their URI discoverable. The stream is
		 * valid only during this callback and is closed by the cache. Callbacks
		 * must use bounded I/O timeouts and independently owned resources, not a
		 * request's repository reader, authentication context or output stream.
		 *
		 * @param pack
		 *            completed pack bytes; must not be closed or retained
		 * @param size
		 *            byte length
		 * @param checksum
		 *            pack trailer, usable as a URI hash only for self-contained packs
		 * @throws IOException
		 *             publication failed; the fetch is unaffected
		 */
		void publish(InputStream pack, long size, ObjectId checksum)
				throws IOException;
	}

	/**
	 * Immutable counters and current resource usage.
	 *
	 * @param hits
	 *            requests joining an active or completed pack
	 * @param misses
	 *            admitted pack producers
	 * @param bypasses
	 *            requests generated without caching
	 * @param generationFailures
	 *            failed or cancelled producers
	 * @param generatedBytes
	 *            raw pack bytes generated, including uncached bypasses
	 * @param servedBytes
	 *            pack bytes successfully served, including uncached bypasses
	 * @param removedFiles
	 *            pack files deleted by eviction or abandoned-instance cleanup
	 * @param activeProducers
	 *            producers that have not terminated
	 * @param entries
	 *            live files, including evicted files with active readers
	 * @param bytes
	 *            total bytes in live pack files
	 * @param retainedBytes
	 *            completed bytes retained for future requests
	 */
	public record Stats(long hits, long misses, long bypasses,
			long generationFailures, long generatedBytes, long servedBytes,
			long removedFiles, int activeProducers, int entries, long bytes,
			long retainedBytes) {
		// Immutable snapshot.
	}

	private static final Logger LOG = LoggerFactory
			.getLogger(PackOutputCache.class);

	// Opening and closing a second descriptor can release this JVM's POSIX lock.
	private static final Set<Path> OWNED_DIRECTORIES = ConcurrentHashMap.newKeySet();

	private static final String DIRECTORY_PREFIX = "jgit-pack-output-"; //$NON-NLS-1$

	private static final String LOCK_FILE = "lock"; //$NON-NLS-1$

	private static final String READY_FILE = "ready"; //$NON-NLS-1$

	private static final int CHUNK_SIZE = 64 << 10;

	private static final class Reader {
		long position;
	}

	private static final class Observation {
		long count;

		long seenAt;
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

		boolean publishing;

		boolean publicationAttempted;

		Thread thread;

		Throwable failure;

		PackStatistics statistics;

		Entry(String key, Path path) {
			this.key = key;
			this.path = path;
		}
	}

	private final Path parent;

	private final Path directory;

	private final FileChannel lockChannel;

	private final FileLock directoryLock;

	private final long maxBytes;

	private final int maxEntries;

	private final long maxAgeNanos;

	private final int minOccurrences;

	private final int maxObservations;

	private final Map<String, Entry> entries = new HashMap<>();

	private final Set<Entry> files = new HashSet<>();

	private final Map<String, Observation> observations = new LinkedHashMap<>(
			16, 0.75f, true);

	private final ThreadPoolExecutor producers;

	private final ThreadPoolExecutor publishers;

	private final ScheduledExecutorService reaper;

	private final ScheduledFuture<?> eviction;

	private long hits;

	private long misses;

	private long bypasses;

	private long generationFailures;

	private long generatedBytes;

	private long servedBytes;

	private long removedFiles;

	private int activeProducers;

	private long bytes;

	private long retainedBytes;

	private boolean closed;

	/**
	 * Create a cache in a new private directory, removing abandoned instances
	 * whose ownership lock can be acquired. Live instances are never removed.
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
	 * @param minOccurrences
	 *            number of prior observations required before caching a key;
	 *            zero admits its first request
	 * @param maxConcurrentProducers
	 *            positive limit on background pack generators; excess misses
	 *            bypass caching
	 * @throws IOException
	 *             cache storage or its ownership lock cannot be created
	 */
	public PackOutputCache(Path directory, long maxBytes, int maxEntries,
			Duration maxAge, int minOccurrences, int maxConcurrentProducers)
			throws IOException {
		if (maxBytes <= 0 || maxEntries <= 0 || maxAge.isZero()
				|| maxAge.isNegative() || minOccurrences < 0
				|| maxConcurrentProducers <= 0) {
			throw new IllegalArgumentException("Invalid pack output cache limits"); //$NON-NLS-1$
		}
		this.maxBytes = maxBytes;
		this.maxEntries = maxEntries;
		this.maxAgeNanos = maxAge.toNanos();
		this.minOccurrences = minOccurrences;
		maxObservations = (int) Math.min((long) maxEntries * 4, 65536);
		Files.createDirectories(directory);
		parent = directory.toRealPath();
		removedFiles = cleanAbandoned(parent);
		this.directory = Files.createTempDirectory(parent, DIRECTORY_PREFIX);
		Path lock = Files.createFile(this.directory.resolve(LOCK_FILE));
		lockChannel = FileChannel.open(lock, StandardOpenOption.WRITE,
				LinkOption.NOFOLLOW_LINKS);
		directoryLock = lockChannel.lock();
		OWNED_DIRECTORIES.add(this.directory);
		try {
			Files.createFile(this.directory.resolve(READY_FILE));
		} catch (IOException | RuntimeException | Error e) {
			try {
				directoryLock.release();
				lockChannel.close();
			} catch (IOException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			} finally {
				OWNED_DIRECTORIES.remove(this.directory);
			}
			throw e;
		}
		producers = new ThreadPoolExecutor(maxConcurrentProducers,
				maxConcurrentProducers, 0, TimeUnit.MILLISECONDS,
				new SynchronousQueue<>(), r -> daemon(r, "JGit pack producer")); //$NON-NLS-1$
		publishers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(1),
				r -> daemon(r, "JGit pack publisher")); //$NON-NLS-1$
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
				admit(key);
				hits++;
				entry.readers.add(reader);
			} else if (admit(key)) {
				makeRoom();
				if (files.size() < maxEntries) {
					entry = new Entry(key, Files.createTempFile(directory,
							"pack-", ".pack")); //$NON-NLS-1$ //$NON-NLS-2$
					entry.readers.add(reader);
					entries.put(key, entry);
					files.add(entry);
					Entry admitted = entry;
					activeProducers++;
					try {
						producers.execute(() -> produce(admitted, producer, original));
						misses++;
						leader = true;
					} catch (RejectedExecutionException e) {
						activeProducers--;
						entry.producing = false;
						entry.readers.clear();
						evict(entry);
						cleanup(entry);
						entry = null;
					}
				}
			}
			if (entry == null) {
				bypasses++;
			}
		}
		if (entry == null) {
			CountingOutputStream direct = new CountingOutputStream(out);
			try {
				return producer.write(direct);
			} finally {
				synchronized (this) {
					generatedBytes += direct.getCount();
					servedBytes += direct.getCount();
				}
			}
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

	/**
	 * Schedule best-effort publication of a successfully retained completed pack.
	 * At most one active and one queued publication pin local files; excess work
	 * is declined without blocking. Each admitted entry is attempted once, even
	 * after a callback failure. Expiry, eviction and close preserve pinned files
	 * until their callbacks finish. Failed or bypassed generations are ineligible.
	 * The caller must ensure the pack is self-contained before advertising its
	 * trailer as a URI hash.
	 *
	 * @param key
	 *            exact pack key previously passed to {@link #write}
	 * @param publisher
	 *            thread-safe publisher; invoked without cache locks
	 * @return whether publication was scheduled
	 * @since 8.0
	 */
	public synchronized boolean publish(String key, PackPublisher publisher) {
		Objects.requireNonNull(publisher);
		if (closed) {
			return false;
		}
		expire();
		Entry entry = entries.get(key);
		if (entry == null || entry.producing || entry.failure != null
				|| entry.publicationAttempted) {
			return false;
		}
		entry.publishing = true;
		entry.publicationAttempted = true;
		try {
			publishers.execute(() -> publish(entry, publisher));
			return true;
		} catch (RejectedExecutionException e) {
			entry.publishing = false;
			entry.publicationAttempted = false;
			cleanup(entry);
			return false;
		}
	}

	private void publish(Entry entry, PackPublisher publisher) {
		try (FileChannel channel = FileChannel.open(entry.path,
				StandardOpenOption.READ);
				InputStream in = Files.newInputStream(entry.path)) {
			if (entry.bytes < 32) {
				throw new IOException("Invalid completed pack"); //$NON-NLS-1$
			}
			MessageDigest digest = Constants.newMessageDigest();
			ByteBuffer data = ByteBuffer.allocate(CHUNK_SIZE);
			long remaining = entry.bytes - Constants.OBJECT_ID_LENGTH;
			while (remaining > 0) {
				data.clear();
				data.limit((int) Math.min(data.capacity(), remaining));
				int count = channel.read(data);
				if (count < 0) {
					throw new IOException("Truncated completed pack"); //$NON-NLS-1$
				}
				digest.update(data.array(), 0, count);
				remaining -= count;
			}
			ByteBuffer trailer = ByteBuffer.allocate(Constants.OBJECT_ID_LENGTH);
			while (trailer.hasRemaining()) {
				if (channel.read(trailer) < 0) {
					throw new IOException("Truncated completed pack"); //$NON-NLS-1$
				}
			}
			if (!MessageDigest.isEqual(digest.digest(), trailer.array())) {
				throw new IOException("Corrupt completed pack"); //$NON-NLS-1$
			}
			publisher.publish(in, entry.bytes, ObjectId.fromRaw(trailer.array()));
		} catch (IOException | RuntimeException e) {
			LOG.warn("Cannot publish generated pack", e); //$NON-NLS-1$
		} finally {
			synchronized (this) {
				entry.publishing = false;
				cleanup(entry);
			}
		}
	}

	private boolean admit(String key) {
		if (minOccurrences == 0) {
			return true;
		}
		Observation observation = observations.computeIfAbsent(key,
				unused -> new Observation());
		observation.seenAt = System.nanoTime();
		if (observation.count <= minOccurrences) {
			observation.count++;
		}
		if (observations.size() > maxObservations) {
			observations.remove(observations.keySet().iterator().next());
		}
		return observation.count > minOccurrences;
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
				activeProducers--;
				if (failure != null) {
					generationFailures++;
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
					servedBytes += count;
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


	/**
	 * Get an immutable view of cumulative counters and current resource usage.
	 *
	 * @return current cache statistics
	 */
	public synchronized Stats getStats() {
		return new Stats(hits, misses, bypasses, generationFailures,
				generatedBytes, servedBytes, removedFiles, activeProducers,
				files.size(), bytes, retainedBytes);
	}

	private void maintain() {
		synchronized (this) {
			expire();
			if (closed) {
				return;
			}
		}
		try {
			long removed = cleanAbandoned(parent);
			synchronized (this) {
				removedFiles += removed;
			}
		} catch (IOException e) {
			LOG.warn("Cannot clean abandoned pack output caches", e); //$NON-NLS-1$
		}
	}

	private void expire() {
		long now = System.nanoTime();
		observations.values().removeIf(o -> now - o.seenAt >= maxAgeNanos);
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
		if (entry.retained || entry.producing || entry.publishing
				|| !entry.readers.isEmpty()) {
			return;
		}
		try {
			if (Files.deleteIfExists(entry.path)) {
				removedFiles++;
			}
			if (files.remove(entry)) {
				bytes -= entry.bytes;
			}
			finishClose();
		} catch (IOException e) {
			LOG.warn("Cannot remove expired pack output {}", entry.path, e); //$NON-NLS-1$
		}
	}

	private void finishClose() throws IOException {
		if (closed && files.isEmpty() && lockChannel.isOpen()) {
			directoryLock.release();
			lockChannel.close();
			Files.deleteIfExists(directory.resolve(READY_FILE));
			Files.deleteIfExists(directory.resolve(LOCK_FILE));
			Files.deleteIfExists(directory);
			OWNED_DIRECTORIES.remove(directory);
		}
	}

	/**
	 * Stop admitting requests and retaining packs. Existing readers and their
	 * producers and accepted publications can finish; ownership is released after
	 * all files are removed. Callbacks must enforce their own I/O timeouts.
	 */
	@Override
	public synchronized void close() {
		closed = true;
		eviction.cancel(false);
		reaper.shutdown();
		producers.shutdown();
		publishers.shutdown();
		observations.clear();
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

	private static long cleanAbandoned(Path parent) throws IOException {
		long removed = 0;
		try (DirectoryStream<Path> directories = Files.newDirectoryStream(parent,
				DIRECTORY_PREFIX + '*')) {
			for (Path directory : directories) {
				if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
					continue;
				}
				Path lockPath = directory.resolve(LOCK_FILE);
				if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
						|| !Files.isRegularFile(directory.resolve(READY_FILE),
								LinkOption.NOFOLLOW_LINKS)) {
					continue;
				}
				if (OWNED_DIRECTORIES.contains(directory)) {
					continue;
				}
				try (FileChannel channel = FileChannel.open(lockPath,
						StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
					FileLock lock;
					try {
						lock = channel.tryLock();
					} catch (OverlappingFileLockException e) {
						continue;
					}
					if (lock == null) {
						continue;
					}
					boolean ownedOnly = true;
					try (lock;
							DirectoryStream<Path> paths = Files
									.newDirectoryStream(directory)) {
						for (Path path : paths) {
							String name = path.getFileName().toString();
							if (name.equals(LOCK_FILE) || name.equals(READY_FILE)) {
								continue;
							}
							if (name.startsWith("pack-") //$NON-NLS-1$
									&& name.endsWith(".pack") //$NON-NLS-1$
									&& Files.isRegularFile(path,
											LinkOption.NOFOLLOW_LINKS)) {
								if (Files.deleteIfExists(path)) {
									removed++;
								}
							} else {
								ownedOnly = false;
							}
						}
					}
					if (ownedOnly) {
						channel.close();
						Files.deleteIfExists(directory.resolve(READY_FILE));
						Files.deleteIfExists(lockPath);
						Files.deleteIfExists(directory);
					}
				} catch (java.nio.file.NoSuchFileException e) {
					// Another cache may have removed this abandoned instance.
				}
			}
		}
		return removed;
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
					bytes += count;
					generatedBytes += count;
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
