/*
 * Copyright (C) 2018, Salesforce.
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

/**
 * Typed access to GPG related configuration options.
 *
 * @since 5.2
 */
public class GpgConfig {

	/**
	 * Config values for gpg.format.
	 */
	public enum GpgFormat implements Config.ConfigEnum {

		/** Value for openpgp */
		OPENPGP("openpgp"), //$NON-NLS-1$
		/** Value for x509 */
		X509("x509"); //$NON-NLS-1$

		private final String configValue;

		private GpgFormat(String configValue) {
			this.configValue = configValue;
		}

		@Override
		public boolean matchConfigValue(String s) {
			return configValue.equals(s);
		}

		@Override
		public String toConfigValue() {
			return configValue;
		}
	}

	private final Config config;

	/**
	 * Create a new GPG config, which will read configuration from config.
	 *
	 * @param config
	 *            the config to read from
	 */
	public GpgConfig(Config config) {
		this.config = config;
	}

	/**
	 * Retrieves the config value of gpg.format.
	 *
	 * @return the {@link org.eclipse.jgit.lib.GpgConfig.GpgFormat}
	 */
	public GpgFormat getKeyFormat() {
		return config.getEnum(GpgFormat.values(),
				ConfigConstants.CONFIG_GPG_SECTION, null,
				ConfigConstants.CONFIG_KEY_FORMAT, GpgFormat.OPENPGP);
	}

	/**
	 * Retrieves the config value of user.signingKey.
	 *
	 * @return the value of user.signingKey (may be <code>null</code>)
	 */
	public String getSigningKey() {
		return config.getString(ConfigConstants.CONFIG_USER_SECTION, null,
				ConfigConstants.CONFIG_KEY_SIGNINGKEY);
	}

	/**
	 * Retrieves the config value of commit.gpgSign.
	 *
	 * @return the value of commit.gpgSign (defaults to <code>false</code>)
	 */
	public boolean isSignCommits() {
		return config.getBoolean(ConfigConstants.CONFIG_COMMIT_SECTION,
				ConfigConstants.CONFIG_KEY_GPGSIGN, false);
	}
}
