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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hook registered as a JVM shutdown hook managing a set of objects needing
 * cleanup during JVM shutdown. See {@link Runtime#addShutdownHook}.
 */
public class ShutdownHook {
	private static final Logger LOG = LoggerFactory.getLogger(LockFile.class);

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

	/** the only instance */
	private static final ShutdownHook INSTANCE = new ShutdownHook();

	/**
	 * Get the (singleton) CleanupHook instance
	 *
	 * @return the CleanupHook instance
	 */
	public static ShutdownHook getInstance() {
		return INSTANCE;
	}

	private boolean shutdownInProgress;

	private ShutdownHook() {
		try {
			Runtime.getRuntime()
					.addShutdownHook(new Thread(() -> INSTANCE.cleanup()));
			LOG.debug("registered shutdown hook"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// ignore - the VM is already shutting down
		}
	}

	final Set<Listener> listeners = ConcurrentHashMap.newKeySet();

	private void cleanup() {
		shutdownInProgress = true;
		listeners.parallelStream().forEach(l -> {
			LOG.warn(JGitText.get().cleanupLockFile, l);
			l.onShutdown();
		});
	}

	/**
	 * Register object that needs cleanup during JVM shutdown
	 *
	 * @param l
	 *            the object to call {@link Listener#onShutdown} on when JVM
	 *            shuts down
	 * @return {@code true} if this object wasn't already registered
	 */
	public boolean register(Listener l) {
		LOG.debug("register {} with shutdown hook", l); //$NON-NLS-1$
		return listeners.add(l);
	}

	/**
	 * Unregister object that no longer needs cleanup during JVM shutdown.
	 *
	 * @param l
	 *            the object registered to be notified for cleanup when the JVM
	 *            shuts down
	 * @return {@code true} if the object being unregistered was registered
	 */
	public boolean unregister(Listener l) {
		LOG.debug("unregister {} from shutdown hook", l); //$NON-NLS-1$
		return listeners.remove(l);
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
