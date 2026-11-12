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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded, process-local disk cache of generated pack streams.
 * <p>
 * Concurrent callers with the same key share one producer. A pack is made
 * available only after its producer completes successfully; clients then read
 * independent streams from the completed file. The first client therefore waits
 * for generation before receiving pack bytes. No protocol framing or progress
 * messages should be included in the producer's output.
 * <p>
 * Callers must authorize every request before looking up a key, and must include
 * the repository, authorization scope and all pack inputs in the key. This class
 * does not perform authorization or interpret keys. It creates a private
 * directory below the supplied directory and never reopens files from a previous
 * instance.
 *
 * @since 8.0
 */
public final class PackOutputCache implements AutoCloseable {

	/** Generates the raw bytes and statistics of one pack. */
	@FunctionalInterface
	public interface PackProducer {
		/**
		 * Generate a pack without closing the supplied output stream.
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

	private static final class Entry {
		final Path path;

		final CompletableFuture<PackStatistics> result = new CompletableFuture<>();

		int users;

		long bytes;

		long completedAt;

		boolean producing = true;

		boolean retained = true;

		Entry(Path path) {
			this.path = path;
		}
	}

	private final Path directory;

	private final long maxBytes;

	private final int maxEntries;

	private final long maxAgeNanos;

	private final Map<String, Entry> entries = new HashMap<>();

	private final Set<Entry> files = new HashSet<>();

	private final ScheduledExecutorService reaper;

	private final ScheduledFuture<?> eviction;

	private long bytes;

	private boolean closed;

	/**
	 * Create a cache. The byte limit includes files being generated and files
	 * still being read after eviction.
	 *
	 * @param directory
	 *            parent of a new private cache directory
	 * @param maxBytes
	 *            positive maximum total size of cache files
	 * @param maxEntries
	 *            positive maximum number of cache files, including active files
	 * @param maxAge
	 *            positive retention time after generation completes
	 * @throws IOException
	 *             the cache directory cannot be created
	 */
	public PackOutputCache(Path directory, long maxBytes, int maxEntries,
			Duration maxAge) throws IOException {
		if (maxBytes <= 0 || maxEntries <= 0 || maxAge.isZero()
				|| maxAge.isNegative()) {
			throw new IllegalArgumentException("Cache limits must be positive"); //$NON-NLS-1$
		}
		this.maxBytes = maxBytes;
		this.maxEntries = maxEntries;
		this.maxAgeNanos = maxAge.toNanos();
		Files.createDirectories(directory);
		this.directory = Files.createTempDirectory(directory,
				"jgit-pack-output-"); //$NON-NLS-1$
		reaper = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "JGit pack output cache eviction"); //$NON-NLS-1$
			thread.setDaemon(true);
			return thread;
		});
		long interval = Math.min(maxAgeNanos, TimeUnit.MINUTES.toNanos(1));
		eviction = reaper.scheduleWithFixedDelay(this::expire, interval, interval,
				TimeUnit.NANOSECONDS);
	}

	/**
	 * Generate or replay a pack. Each caller's producer is invoked at most once.
	 * A full cache bypasses caching. If generation exceeds available cache space,
	 * the producer continues directly to this client's output after replaying its
	 * buffered prefix; waiting callers generate their own packs. A client output
	 * failure never invalidates a successfully completed cached pack.
	 *
	 * @param key
	 *            complete, application-scoped pack key
	 * @param out
	 *            client's output stream; not closed by this method
	 * @param producer
	 *            request-local generator used on a miss or bypass
	 * @return statistics of the pack producer, or a detached snapshot on a hit;
	 *         snapshots omit references to reused repository packs
	 * @throws IOException
	 *             generation, disk access, or client output failed
	 */
	public PackStatistics write(String key, OutputStream out,
			PackProducer producer) throws IOException {
		Objects.requireNonNull(key);
		Objects.requireNonNull(out);
		Objects.requireNonNull(producer);
		Entry entry;
		boolean leader = false;
		synchronized (this) {
			if (closed) {
				throw new IOException("Pack output cache is closed"); //$NON-NLS-1$
			}
			expire();
			entry = entries.get(key);
			if (entry == null && files.size() < maxEntries && bytes < maxBytes) {
				entry = new Entry(Files.createTempFile(directory, "pack-", //$NON-NLS-1$
						".pack")); //$NON-NLS-1$
				entries.put(key, entry);
				files.add(entry);
				leader = true;
			}
			if (entry != null) {
				entry.users++;
			}
		}
		if (entry == null) {
			return producer.write(out);
		}
		try {
			if (leader) {
				return produce(entry, out, producer);
			}
			PackStatistics statistics = await(entry);
			if (statistics == null) {
				return producer.write(out);
			}
			copy(entry.path, out);
			return statistics;
		} finally {
			synchronized (this) {
				entry.users--;
				cleanup(entry);
			}
		}
	}

	private PackStatistics produce(Entry entry, OutputStream out,
			PackProducer producer) throws IOException {
		PackStatistics statistics;
		boolean bypassed;
		try (Spool spool = new Spool(entry, out)) {
			statistics = Objects
					.requireNonNull(producer.write(spool));
			spool.finish();
			bypassed = spool.bypassed;
			synchronized (this) {
				entry.producing = false;
				entry.completedAt = System.nanoTime();
				if (!bypassed) {
					entry.result.complete(snapshot(statistics));
				}
			}
		} catch (IOException | RuntimeException | Error e) {
			synchronized (this) {
				entry.producing = false;
				evict(entry);
				entry.result.completeExceptionally(e);
			}
			throw e;
		}
		if (!bypassed) {
			copy(entry.path, out);
		}
		return statistics;
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

	private static PackStatistics await(Entry entry) throws IOException {
		try {
			return entry.result.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted waiting for pack output"); //$NON-NLS-1$
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}
			throw new IOException("Pack generation failed", e.getCause()); //$NON-NLS-1$
		}
	}

	private static void copy(Path path, OutputStream out) throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			in.transferTo(out);
		}
	}

	private synchronized void expire() {
		long now = System.nanoTime();
		for (Entry entry : new HashSet<>(files)) {
			if (!entry.producing && entry.retained
					&& now - entry.completedAt >= maxAgeNanos) {
				evict(entry);
			}
			cleanup(entry);
		}
	}

	private void evict(Entry entry) {
		entry.retained = false;
		entries.values().remove(entry);
	}

	private void cleanup(Entry entry) {
		if (entry.retained || entry.producing || entry.users != 0) {
			return;
		}
		try {
			Files.deleteIfExists(entry.path);
			if (files.remove(entry)) {
				bytes -= entry.bytes;
			}
			if (closed && files.isEmpty()) {
				Files.deleteIfExists(directory);
			}
		} catch (IOException e) {
			LOG.warn("Cannot remove expired pack output {}", entry.path, e); //$NON-NLS-1$
		}
	}

	/**
	 * Stop retaining packs and remove files once active callers release them.
	 * Active generators and readers are allowed to finish.
	 */
	@Override
	public synchronized void close() {
		closed = true;
		eviction.cancel(false);
		reaper.shutdown();
		for (Entry entry : new HashSet<>(files)) {
			evict(entry);
			cleanup(entry);
		}
		if (files.isEmpty()) {
			try {
				Files.deleteIfExists(directory);
			} catch (IOException e) {
				LOG.warn("Cannot remove pack output cache {}", directory, e); //$NON-NLS-1$
			}
		}
	}

	private final class Spool extends OutputStream {
		private final Entry entry;

		private final OutputStream client;

		private OutputStream file;

		private boolean bypassed;

		Spool(Entry entry, OutputStream client) throws IOException {
			this.entry = entry;
			this.client = client;
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
			if (!bypassed) {
				boolean available;
				synchronized (PackOutputCache.this) {
					available = !closed && length <= maxBytes - bytes;
					if (available) {
						bytes += length;
						entry.bytes += length;
					}
				}
				if (available) {
					file.write(buffer, offset, length);
					return;
				}
				file.close();
				file = null;
				bypassed = true;
				synchronized (PackOutputCache.this) {
					evict(entry);
					entry.result.complete(null);
				}
				copy(entry.path, client);
			}
			client.write(buffer, offset, length);
		}

		@Override
		public void flush() throws IOException {
			if (bypassed) {
				client.flush();
			} else {
				file.flush();
			}
		}

		void finish() throws IOException {
			if (file != null) {
				file.close();
				file = null;
			}
		}

		@Override
		public void close() throws IOException {
			finish();
		}
	}
}
