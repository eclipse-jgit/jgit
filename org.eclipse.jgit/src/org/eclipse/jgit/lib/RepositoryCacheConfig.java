/*
 * Copyright (C) 2016 Ericsson
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
