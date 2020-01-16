/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * Configuration for server-side signed push verification.
 *
 * @since 4.1
 */
public class SignedPushConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final SectionParser<SignedPushConfig> KEY =
			SignedPushConfig::new;

	private String certNonceSeed;
	private int certNonceSlopLimit;
	private NonceGenerator nonceGenerator;

	/**
	 * Create a new config with default values disabling push verification.
	 */
	public SignedPushConfig() {
	}

	SignedPushConfig(Config cfg) {
		setCertNonceSeed(cfg.getString("receive", null, "certnonceseed")); //$NON-NLS-1$ //$NON-NLS-2$
		certNonceSlopLimit = cfg.getInt("receive", "certnonceslop", 0); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Set the seed used by the nonce verifier.
	 * <p>
	 * Setting this to a non-null value enables push certificate verification
	 * using the default
	 * {@link org.eclipse.jgit.transport.HMACSHA1NonceGenerator} implementation,
	 * if a different implementation was not set using
	 * {@link #setNonceGenerator(NonceGenerator)}.
	 *
	 * @param seed
	 *            new seed value.
	 */
	public void setCertNonceSeed(String seed) {
		certNonceSeed = seed;
	}

	/**
	 * Get the configured seed.
	 *
	 * @return the configured seed.
	 */
	public String getCertNonceSeed() {
		return certNonceSeed;
	}

	/**
	 * Set the nonce slop limit.
	 * <p>
	 * Old but valid nonces within this limit will be accepted.
	 *
	 * @param limit
	 *            new limit in seconds.
	 */
	public void setCertNonceSlopLimit(int limit) {
		certNonceSlopLimit = limit;
	}

	/**
	 * Get the configured nonce slop limit.
	 *
	 * @return the configured nonce slop limit.
	 */
	public int getCertNonceSlopLimit() {
		return certNonceSlopLimit;
	}

	/**
	 * Set the {@link org.eclipse.jgit.transport.NonceGenerator} used for signed
	 * pushes.
	 * <p>
	 * Setting this to a non-null value enables push certificate verification.
	 * If this method is called, this implementation will be used instead of the
	 * default {@link org.eclipse.jgit.transport.HMACSHA1NonceGenerator} even if
	 * {@link #setCertNonceSeed(String)} was called.
	 *
	 * @param generator
	 *            new nonce generator.
	 */
	public void setNonceGenerator(NonceGenerator generator) {
		nonceGenerator = generator;
	}

	/**
	 * Get the {@link org.eclipse.jgit.transport.NonceGenerator} used for signed
	 * pushes.
	 * <p>
	 * If {@link #setNonceGenerator(NonceGenerator)} was used to set a non-null
	 * implementation, that will be returned. If no custom implementation was
	 * set but {@link #setCertNonceSeed(String)} was called, returns a
	 * newly-created {@link org.eclipse.jgit.transport.HMACSHA1NonceGenerator}.
	 *
	 * @return the configured nonce generator.
	 */
	public NonceGenerator getNonceGenerator() {
		if (nonceGenerator != null) {
			return nonceGenerator;
		} else if (certNonceSeed != null) {
			return new HMACSHA1NonceGenerator(certNonceSeed);
		}
		return null;
	}
}
