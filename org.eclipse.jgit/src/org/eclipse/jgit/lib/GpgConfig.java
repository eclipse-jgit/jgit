/**
 * Copyright (c) 2018 Salesforce and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
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
	 *
	 * @since 5.2
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
	 * @since 5.2
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
	 * @since 5.2
	 */
	public String getSigningKey() {
		return config.getString(ConfigConstants.CONFIG_USER_SECTION, null,
				ConfigConstants.CONFIG_KEY_SIGNINGKEY);
	}

	/**
	 * Retrieves the config value of commit.gpgSign.
	 *
	 * @return the value of commit.gpgSign (defaults to <code>false</code>)
	 * @since 5.2
	 */
	public boolean isSignCommits() {
		return config.getBoolean(ConfigConstants.CONFIG_COMMIT_SECTION,
				ConfigConstants.CONFIG_KEY_GPGSIGN, false);
	}
}
