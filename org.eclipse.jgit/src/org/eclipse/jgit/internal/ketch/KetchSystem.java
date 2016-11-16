/*
 * Copyright (C) 2016, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.ketch;

import static org.eclipse.jgit.internal.ketch.KetchConstants.ACCEPTED;
import static org.eclipse.jgit.internal.ketch.KetchConstants.COMMITTED;
import static org.eclipse.jgit.internal.ketch.KetchConstants.CONFIG_KEY_TYPE;
import static org.eclipse.jgit.internal.ketch.KetchConstants.CONFIG_SECTION_KETCH;
import static org.eclipse.jgit.internal.ketch.KetchConstants.DEFAULT_TXN_NAMESPACE;
import static org.eclipse.jgit.internal.ketch.KetchConstants.STAGE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_NAME;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.MonotonicSystemClock;
import org.eclipse.jgit.util.time.ProposedTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ketch system-wide configuration.
 * <p>
 * This class provides useful defaults for testing and small proof of concepts.
 * Full scale installations are expected to subclass and override methods to
 * provide consistent configuration across all managed repositories.
 * <p>
 * Servers should configure their own {@link ScheduledExecutorService}.
 */
public class KetchSystem {
	private static final Random RNG = new Random();

	/** @return default executor, one thread per available processor. */
	public static ScheduledExecutorService defaultExecutor() {
		return DefaultExecutorHolder.I;
	}

	private final ScheduledExecutorService executor;
	private final MonotonicClock clock;
	private final String txnNamespace;
	private final String txnAccepted;
	private final String txnCommitted;
	private final String txnStage;

	/** Create a default system with a thread pool of 1 thread per CPU. */
	public KetchSystem() {
		this(defaultExecutor(), new MonotonicSystemClock(), DEFAULT_TXN_NAMESPACE);
	}

	/**
	 * Create a Ketch system with the provided executor service.
	 *
	 * @param executor
	 *            thread pool to run background operations.
	 * @param clock
	 *            clock to create timestamps.
	 * @param txnNamespace
	 *            reference namespace for the RefTree graph and associated
	 *            transaction state. Must begin with {@code "refs/"} and end
	 *            with {@code '/'}, for example {@code "refs/txn/"}.
	 */
	public KetchSystem(ScheduledExecutorService executor, MonotonicClock clock,
			String txnNamespace) {
		this.executor = executor;
		this.clock = clock;
		this.txnNamespace = txnNamespace;
		this.txnAccepted = txnNamespace + ACCEPTED;
		this.txnCommitted = txnNamespace + COMMITTED;
		this.txnStage = txnNamespace + STAGE;
	}

	/** @return executor to perform background operations. */
	public ScheduledExecutorService getExecutor() {
		return executor;
	}

	/** @return clock to obtain timestamps from. */
	public MonotonicClock getClock() {
		return clock;
	}

	/**
	 * @return how long the leader will wait for the {@link #getClock()}'s
	 *         {@code ProposedTimestamp} used in commits proposed to the RefTree
	 *         graph ({@link #getTxnAccepted()}). Defaults to 5 seconds.
	 */
	public Duration getMaxWaitForMonotonicClock() {
		return Duration.ofSeconds(5);
	}

	/**
	 * @return true if elections should require monotonically increasing commit
	 *         timestamps. This requires a very good {@link MonotonicClock}.
	 */
	public boolean requireMonotonicLeaderElections() {
		return false;
	}

	/**
	 * Get the namespace used for the RefTree graph and transaction management.
	 *
	 * @return reference namespace such as {@code "refs/txn/"}.
	 */
	public String getTxnNamespace() {
		return txnNamespace;
	}

	/** @return name of the accepted RefTree graph. */
	public String getTxnAccepted() {
		return txnAccepted;
	}

	/** @return name of the committed RefTree graph. */
	public String getTxnCommitted() {
		return txnCommitted;
	}

	/** @return prefix for staged objects, e.g. {@code "refs/txn/stage/"}. */
	public String getTxnStage() {
		return txnStage;
	}

	/**
	 * @param time
	 *            timestamp for the committer.
	 * @return identity line for the committer header of a RefTreeGraph.
	 */
	public PersonIdent newCommitter(ProposedTimestamp time) {
		String name = "ketch"; //$NON-NLS-1$
		String email = "ketch@system"; //$NON-NLS-1$
		return new PersonIdent(name, email, time);
	}

	/**
	 * Construct a random tag to identify a candidate during leader election.
	 * <p>
	 * Multiple processes trying to elect themselves leaders at exactly the same
	 * time (rounded to seconds) using the same
	 * {@link #newCommitter(ProposedTimestamp)} identity strings, for the same
	 * term, may generate the same ObjectId for the election commit and falsely
	 * assume they have both won.
	 * <p>
	 * Candidates add this tag to their election ballot commit to disambiguate
	 * the election. The tag only needs to be unique for a given triplet of
	 * {@link #newCommitter(ProposedTimestamp)}, system time (rounded to
	 * seconds), and term. If every replica in the system uses a unique
	 * {@code newCommitter} (such as including the host name after the
	 * {@code "@"} in the email address) the tag could be the empty string.
	 * <p>
	 * The default implementation generates a few bytes of random data.
	 *
	 * @return unique tag; null or empty string if {@code newCommitter()} is
	 *         sufficiently unique to identify the leader.
	 */
	@Nullable
	public String newLeaderTag() {
		int n = RNG.nextInt(1 << (6 * 4));
		return String.format("%06x", Integer.valueOf(n)); //$NON-NLS-1$
	}

	/**
	 * Construct the KetchLeader instance of a repository.
	 *
	 * @param repo
	 *            local repository stored by the leader.
	 * @return leader instance.
	 * @throws URISyntaxException
	 *             a follower configuration contains an unsupported URI.
	 */
	public KetchLeader createLeader(final Repository repo)
			throws URISyntaxException {
		KetchLeader leader = new KetchLeader(this) {
			@Override
			protected Repository openRepository() {
				repo.incrementOpen();
				return repo;
			}
		};
		leader.setReplicas(createReplicas(leader, repo));
		return leader;
	}

	/**
	 * Get the collection of replicas for a repository.
	 * <p>
	 * The collection of replicas must include the local repository.
	 *
	 * @param leader
	 *            the leader driving these replicas.
	 * @param repo
	 *            repository to get the replicas of.
	 * @return collection of replicas for the specified repository.
	 * @throws URISyntaxException
	 *             a configured URI is invalid.
	 */
	protected List<KetchReplica> createReplicas(KetchLeader leader,
			Repository repo) throws URISyntaxException {
		List<KetchReplica> replicas = new ArrayList<>();
		Config cfg = repo.getConfig();
		String localName = getLocalName(cfg);
		for (String name : cfg.getSubsections(CONFIG_KEY_REMOTE)) {
			if (!hasParticipation(cfg, name)) {
				continue;
			}

			ReplicaConfig kc = ReplicaConfig.newFromConfig(cfg, name);
			if (name.equals(localName)) {
				replicas.add(new LocalReplica(leader, name, kc));
				continue;
			}

			RemoteConfig rc = new RemoteConfig(cfg, name);
			List<URIish> uris = rc.getPushURIs();
			if (uris.isEmpty()) {
				uris = rc.getURIs();
			}
			for (URIish uri : uris) {
				String n = uris.size() == 1 ? name : uri.getHost();
				replicas.add(new RemoteGitReplica(leader, n, uri, kc, rc));
			}
		}
		return replicas;
	}

	private static boolean hasParticipation(Config cfg, String name) {
		return cfg.getString(CONFIG_KEY_REMOTE, name, CONFIG_KEY_TYPE) != null;
	}

	private static String getLocalName(Config cfg) {
		return cfg.getString(CONFIG_SECTION_KETCH, null, CONFIG_KEY_NAME);
	}

	static class DefaultExecutorHolder {
		private static final Logger log = LoggerFactory.getLogger(KetchSystem.class);
		static final ScheduledExecutorService I = create();

		private static ScheduledExecutorService create() {
			int cores = Runtime.getRuntime().availableProcessors();
			int threads = Math.max(5, cores);
			log.info("Using {} threads", Integer.valueOf(threads)); //$NON-NLS-1$
			return Executors.newScheduledThreadPool(
				threads,
				new ThreadFactory() {
					private final AtomicInteger threadCnt = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						int id = threadCnt.incrementAndGet();
						Thread thr = new Thread(r);
						thr.setName("KetchExecutor-" + id); //$NON-NLS-1$
						return thr;
					}
				});
		}

		private DefaultExecutorHolder() {
		}
	}

	/**
	 * Compute a delay in a {@code min..max} interval with random jitter.
	 *
	 * @param last
	 *            amount of delay waited before the last attempt. This is used
	 *            to seed the next delay interval. Should be 0 if there was no
	 *            prior delay.
	 * @param min
	 *            shortest amount of allowable delay between attempts.
	 * @param max
	 *            longest amount of allowable delay between attempts.
	 * @return new amount of delay to wait before the next attempt.
	 */
	static long delay(long last, long min, long max) {
		long r = Math.max(0, last * 3 - min);
		if (r > 0) {
			int c = (int) Math.min(r + 1, Integer.MAX_VALUE);
			r = RNG.nextInt(c);
		}
		return Math.max(Math.min(min + r, max), min);
	}
}
