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
	/**
	 * Key for {@link Config#get(SectionParser)}.
	 *
	 * @since 4.1
	 */
	public static final SectionParser<SignedPushConfig> KEY =
			new SectionParser<SignedPushConfig>() {
		public SignedPushConfig parse(Config cfg) {
			return new SignedPushConfig(cfg);
		}
	};

	String certNonceSeed;
	int certNonceSlopLimit;

	/**
	 * Create a new config with default values disabling push verification.
	 *
	 * @since 4.1
	 */
	public SignedPushConfig() {
	}

	SignedPushConfig(Config cfg) {
		certNonceSeed = cfg.getString("receive", null, "certnonceseed"); //$NON-NLS-1$ //$NON-NLS-2$
		certNonceSlopLimit = cfg.getInt("receive", "certnonceslop", 0); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Set the seed used by the nonce verifier.
	 * <p>
	 * Setting this to a non-null value enables push certificate verification.
	 *
	 * @param seed
	 *            new seed value.
	 * @since 4.1
	 */
	public void setCertNonceSeed(String seed) {
		certNonceSeed = seed;
	}

	/**
	 * @return the configured seed used by the nonce verifier.
	 * @since 4.1
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
	 * @since 4.1
	 */
	public void setCertNonceSlopLimit(int limit) {
		certNonceSlopLimit = limit;
	}

	/**
	 * @return the configured nonce slop limit.
	 * @since 4.1
	 */
	public int getCertNonceSlopLimit() {
		return certNonceSlopLimit;
	}
}
