/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.util;

/**
 * A class that is registered as an OSGi service via the manifest. If JGit runs
 * in OSGi, OSGi will instantiate a singleton as soon as the bundle is activated
 * since this class is an immediate OSGi component with no dependencies. OSGi
 * will then call its {@link #start()} method. If JGit is not running in OSGi,
 * {@link #getInstance()} will lazily create an instance.
 * <p>
 * An OSGi-created {@link CleanupService} will run the registered cleanup when
 * the {@code org.eclipse.jgit} bundle is deactivated. A lazily created instance
 * will register the cleanup as a JVM shutdown hook.
 * </p>
 */
public final class CleanupService {

	private static final Object LOCK = new Object();

	private static CleanupService INSTANCE;

	private final boolean isOsgi;

	private Runnable cleanup;

	/**
	 * Public component constructor for OSGi DS. Do <em>not</em> call this
	 * explicitly! (Unfortunately this constructor must be public because of
	 * OSGi requirements.)
	 */
	public CleanupService() {
		this.isOsgi = true;
		setInstance(this);
	}

	private CleanupService(boolean isOsgi) {
		this.isOsgi = isOsgi;
	}

	private static void setInstance(CleanupService service) {
		synchronized (LOCK) {
			INSTANCE = service;
		}
	}

	/**
	 * Obtains the singleton instance of the {@link CleanupService} that knows
	 * whether or not it is running on OSGi.
	 *
	 * @return the {@link CleanupService} singleton instance
	 */
	public static CleanupService getInstance() {
		synchronized (LOCK) {
			if (INSTANCE == null) {
				INSTANCE = new CleanupService(false);
			}
			return INSTANCE;
		}
	}

	void start() {
		// Nothing to do
	}

	void register(Runnable cleanUp) {
		if (isOsgi) {
			cleanup = cleanUp;
		} else {
			try {
				Runtime.getRuntime().addShutdownHook(new Thread(cleanUp));
			} catch (IllegalStateException e) {
				// Ignore -- the JVM is already shutting down.
			}
		}
	}

	void shutDown() {
		if (isOsgi && cleanup != null) {
			Runnable r = cleanup;
			cleanup = null;
			r.run();
		}
	}
}
