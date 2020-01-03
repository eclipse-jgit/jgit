/*
 * Copyright (C) 2016 Ericsson and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.concurrent.TimeUnit;

/**
 * Configuration parameters for JVM-wide repository cache used by JGit.
 *
 * @since 4.4
 */
public class RepositoryCacheConfig {

	/**
	 * Set cleanupDelayMillis to this value in order to switch off time-based
	 * cache eviction. Expired cache entries will only be evicted when
	 * RepositoryCache.clearExpired or RepositoryCache.clear are called.
	 */
	public static final long NO_CLEANUP = 0;

	/**
	 * Set cleanupDelayMillis to this value in order to auto-set it to minimum
	 * of 1/10 of expireAfterMillis and 10 minutes
	 */
	public static final long AUTO_CLEANUP_DELAY = -1;

	private long expireAfterMillis;

	private long cleanupDelayMillis;

	/**
	 * Create a default configuration.
	 */
	public RepositoryCacheConfig() {
		expireAfterMillis = TimeUnit.HOURS.toMillis(1);
		cleanupDelayMillis = AUTO_CLEANUP_DELAY;
	}

	/**
	 * Get the time an unused repository should be expired and be evicted from
	 * the RepositoryCache in milliseconds.
	 *
	 * @return the time an unused repository should be expired and be evicted
	 *         from the RepositoryCache in milliseconds. <b>Default is 1
	 *         hour.</b>
	 */
	public long getExpireAfter() {
		return expireAfterMillis;
	}

	/**
	 * Set the time an unused repository should be expired and be evicted from
	 * the RepositoryCache in milliseconds.
	 *
	 * @param expireAfterMillis
	 *            the time an unused repository should be expired and be evicted
	 *            from the RepositoryCache in milliseconds.
	 */
	public void setExpireAfter(long expireAfterMillis) {
		this.expireAfterMillis = expireAfterMillis;
	}

	/**
	 * Get the delay between the periodic cleanup of expired repository in
	 * milliseconds.
	 *
	 * @return the delay between the periodic cleanup of expired repository in
	 *         milliseconds. <b>Default is minimum of 1/10 of expireAfterMillis
	 *         and 10 minutes</b>
	 */
	public long getCleanupDelay() {
		if (cleanupDelayMillis < 0) {
			return Math.min(expireAfterMillis / 10,
					TimeUnit.MINUTES.toMillis(10));
		}
		return cleanupDelayMillis;
	}

	/**
	 * Set the delay between the periodic cleanup of expired repository in
	 * milliseconds.
	 *
	 * @param cleanupDelayMillis
	 *            the delay between the periodic cleanup of expired repository
	 *            in milliseconds. Set it to {@link #AUTO_CLEANUP_DELAY} to
	 *            automatically derive cleanup delay from expireAfterMillis.
	 *            <p>
	 *            Set it to {@link #NO_CLEANUP} in order to switch off cache
	 *            expiration.
	 *            <p>
	 *            If cache expiration is switched off the JVM still can evict
	 *            cache entries when the JVM is running low on available heap
	 *            memory.
	 */
	public void setCleanupDelay(long cleanupDelayMillis) {
		this.cleanupDelayMillis = cleanupDelayMillis;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param config
	 *            configuration to read properties from.
	 * @return {@code this}.
	 */
	public RepositoryCacheConfig fromConfig(Config config) {
		setExpireAfter(
				config.getTimeUnit("core", null, "repositoryCacheExpireAfter", //$NON-NLS-1$//$NON-NLS-2$
						getExpireAfter(), TimeUnit.MILLISECONDS));
		setCleanupDelay(
				config.getTimeUnit("core", null, "repositoryCacheCleanupDelay", //$NON-NLS-1$ //$NON-NLS-2$
						AUTO_CLEANUP_DELAY, TimeUnit.MILLISECONDS));
		return this;
	}

	/**
	 * Install this configuration as the live settings.
	 * <p>
	 * The new configuration is applied immediately.
	 */
	public void install() {
		RepositoryCache.reconfigure(this);
	}
}
