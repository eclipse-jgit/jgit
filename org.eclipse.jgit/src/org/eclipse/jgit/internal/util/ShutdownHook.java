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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hook registered as a JVM shutdown hook managing a set of objects needing
 * cleanup during JVM shutdown. See {@link Runtime#addShutdownHook}.
 */
public enum ShutdownHook {
	/**
	 * Singleton
	 */
	INSTANCE;

	/**
	 * Object that needs to cleanup on JVM shutdown.
	 */
	public interface Listener {
		/**
		 * Cleanup resources when JVM shuts down, called from JVM shutdown hook.
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

	private volatile boolean shutdownInProgress;

	private ShutdownHook() {
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
		} catch (IllegalStateException e) {
			// ignore - the VM is already shutting down
		}
	}

	private void cleanup() {
		shutdownInProgress = true;
		ExecutorService runner = Executors.newWorkStealingPool();
		try {
			runner.submit(() -> {
				this.doCleanup();
				return null;
			}).get(30L, TimeUnit.SECONDS);
		} catch (RejectedExecutionException | InterruptedException
				| ExecutionException | TimeoutException e) {
			LOG.error(JGitText.get().shutdownCleanupFailed, e);
		}
		runner.shutdownNow();
	}

	private void doCleanup() {
		listeners.parallelStream().forEach(this::notify);
	}

	private void notify(Listener l) {
		LOG.warn(JGitText.get().shutdownCleanup, l);
		try {
			l.onShutdown();
		} catch (RuntimeException e) {
			LOG.error(MessageFormat.format(
					JGitText.get().shutdownCleanupListenerFailed, l), e);
		}
	}

	/**
	 * Register object that needs cleanup during JVM shutdown if it is not
	 * already registered. Registration is disabled when JVM shutdown is already
	 * in progress.
	 *
	 * @param l
	 *            the object to call {@link Listener#onShutdown} on when JVM
	 *            shuts down
	 * @return {@code true} if this object has been registered
	 */
	public boolean register(Listener l) {
		if (shutdownInProgress) {
			return listeners.contains(l);
		}
		LOG.debug("register {} with shutdown hook", l); //$NON-NLS-1$
		listeners.add(l);
		return true;
	}

	/**
	 * Unregister object that no longer needs cleanup during JVM shutdown if it
	 * is still registered. Unregistration is disabled when JVM shutdown is
	 * already in progress.
	 *
	 * @param l
	 *            the object registered to be notified for cleanup when the JVM
	 *            shuts down
	 * @return {@code true} if this object is no longer registered
	 */
	public boolean unregister(Listener l) {
		if (shutdownInProgress) {
			return !listeners.contains(l);
		}
		LOG.debug("unregister {} from shutdown hook", l); //$NON-NLS-1$
		listeners.remove(l);
		return true;
	}

	/**
	 * Whether a JVM shutdown is in progress
	 *
	 * @return {@code true} if a JVM shutdown is in progress
	 */
	public boolean isShutdownInProgress() {
		return shutdownInProgress;
	}
}
