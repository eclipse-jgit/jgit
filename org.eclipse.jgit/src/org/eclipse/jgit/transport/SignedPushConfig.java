/*
 * Copyright (C) 2015, Google Inc.
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

	/** Create a new config with default values disabling push verification. */
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
	 * using the default {@link HMACSHA1NonceGenerator} implementation, if a
	 * different implementation was not set using {@link
	 * #setNonceGenerator(NonceGenerator)}.
	 *
	 * @param seed
	 *            new seed value.
	 */
	public void setCertNonceSeed(String seed) {
		certNonceSeed = seed;
	}

	/** @return the configured seed. */
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

	/** @return the configured nonce slop limit. */
	public int getCertNonceSlopLimit() {
		return certNonceSlopLimit;
	}

	/**
	 * Set the {@link NonceGenerator} used for signed pushes.
	 * <p>
	 * Setting this to a non-null value enables push certificate verification. If
	 * this method is called, this implementation will be used instead of the
	 * default {@link HMACSHA1NonceGenerator} even if {@link
	 * #setCertNonceSeed(String)} was called.
	 *
	 * @param generator
	 *            new nonce generator.
	 */
	public void setNonceGenerator(NonceGenerator generator) {
		nonceGenerator = generator;
	}

	/**
	 * Get the {@link NonceGenerator} used for signed pushes.
	 * <p>
	 * If {@link #setNonceGenerator(NonceGenerator)} was used to set a non-null
	 * implementation, that will be returned. If no custom implementation was set
	 * but {@link #setCertNonceSeed(String)} was called, returns a newly-created
	 * {@link HMACSHA1NonceGenerator}.
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
