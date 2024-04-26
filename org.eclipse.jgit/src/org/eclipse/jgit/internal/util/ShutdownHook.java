/*
 * Copyright (C) 2023, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.util;

import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The singleton {@link ShutdownHook} provides a means to register
 * {@link Listener}s that are run when JGit is uninstalled, either
 * <ul>
 * <li>in an OSGi framework when this bundle is deactivated, or</li>
 * <li>otherwise, when the JVM as a whole shuts down.</li>
 * </ul>
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum ShutdownHook {
	/**
	 * Singleton
	 */
	INSTANCE;

	/**
	 * Object that needs to cleanup on shutdown.
	 */
	public interface Listener {
		/**
		 * Cleanup resources when JGit is shut down.
		 * <p>
		 * Implementations should be coded defensively
		 * <ul>
		 * <li>they should finish their work quickly
		 * <li>they should be written to be thread-safe and to avoid deadlocks
		 * insofar as possible
		 * <li>they should not rely blindly upon services that may have
		 * registered their own shutdown hooks and therefore may themselves be
		 * in the process of shutting down
		 * <li>attempts to use other thread-based services may lead to
		 * deadlocks.
		 * </ul>
		 * See {@link Runtime#addShutdownHook} for more details.
		 */
		public void onShutdown();
	}

	private static final Logger LOG = LoggerFactory
			.getLogger(ShutdownHook.class);

	private final Set<Listener> listeners = ConcurrentHashMap.newKeySet();

	private final AtomicBoolean shutdownInProgress = new AtomicBoolean();

	private ShutdownHook() {
		CleanupService.getInstance().register(this::cleanup);
	}

	private void cleanup() {
		if (!shutdownInProgress.getAndSet(true)) {
			ExecutorService runner = Executors.newWorkStealingPool();
			try {
				runner.submit(() -> {
					this.doCleanup();
					return null;
				}).get(30L, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException
					| TimeoutException e) {
				throw new RuntimeException(e.getMessage(), e);
			} finally {
				runner.shutdownNow();
			}
		}
	}

	private void doCleanup() {
		listeners.parallelStream().forEach(this::notify);
	}

	private void notify(Listener l) {
		LOG.debug(JGitText.get().shutdownCleanup, l);
		try {
			l.onShutdown();
		} catch (RuntimeException e) {
			LOG.error(MessageFormat.format(
					JGitText.get().shutdownCleanupListenerFailed, l), e);
		}
	}

	/**
	 * Register object that needs cleanup during JGit shutdown if it is not
	 * already registered. Registration is disabled when JGit shutdown is
	 * already in progress.
	 *
	 * @param l
	 *            the object to call {@link Listener#onShutdown} on when JGit
	 *            shuts down
	 * @return {@code true} if this object has been registered
	 */
	public boolean register(Listener l) {
		if (shutdownInProgress.get()) {
			return listeners.contains(l);
		}
		LOG.debug("register {} with shutdown hook", l); //$NON-NLS-1$
		listeners.add(l);
		return true;
	}

	/**
	 * Unregister object that no longer needs cleanup during JGit shutdown if it
	 * is still registered. Unregistration is disabled when JGit shutdown is
	 * already in progress.
	 *
	 * @param l
	 *            the object registered to be notified for cleanup when the JVM
	 *            shuts down
	 * @return {@code true} if this object is no longer registered
	 */
	public boolean unregister(Listener l) {
		if (shutdownInProgress.get()) {
			return !listeners.contains(l);
		}
		LOG.debug("unregister {} from shutdown hook", l); //$NON-NLS-1$
		listeners.remove(l);
		return true;
	}

	/**
	 * Whether a JGit shutdown is in progress
	 *
	 * @return {@code true} if a JGit shutdown is in progress
	 */
	public boolean isShutdownInProgress() {
		return shutdownInProgress.get();
	}
}
