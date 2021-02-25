/*
 * Copyright (C) 2018, 2021 Salesforce and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	private final GpgFormat keyFormat;

	private final String signingKey;

	private final String program;

	private final boolean signCommits;

	private final boolean signAllTags;

	private final boolean forceAnnotated;

	/**
	 * Create a {@link GpgConfig} with the given parameters and default
	 * {@code true} for signing commits and {@code false} for tags.
	 *
	 * @param keySpec
	 *            to use
	 * @param format
	 *            to use
	 * @param gpgProgram
	 *            to use
	 * @since 5.11
	 */
	public GpgConfig(String keySpec, GpgFormat format, String gpgProgram) {
		keyFormat = format;
		signingKey = keySpec;
		program = gpgProgram;
		signCommits = true;
		signAllTags = false;
		forceAnnotated = false;
	}

	/**
	 * Create a new GPG config that reads the configuration from config.
	 *
	 * @param config
	 *            the config to read from
	 */
	public GpgConfig(Config config) {
		keyFormat = config.getEnum(GpgFormat.values(),
				ConfigConstants.CONFIG_GPG_SECTION, null,
				ConfigConstants.CONFIG_KEY_FORMAT, GpgFormat.OPENPGP);
		signingKey = config.getString(ConfigConstants.CONFIG_USER_SECTION, null,
				ConfigConstants.CONFIG_KEY_SIGNINGKEY);

		String exe = config.getString(ConfigConstants.CONFIG_GPG_SECTION,
				keyFormat.toConfigValue(), ConfigConstants.CONFIG_KEY_PROGRAM);
		if (exe == null) {
			exe = config.getString(ConfigConstants.CONFIG_GPG_SECTION, null,
					ConfigConstants.CONFIG_KEY_PROGRAM);
		}
		program = exe;
		signCommits = config.getBoolean(ConfigConstants.CONFIG_COMMIT_SECTION,
				ConfigConstants.CONFIG_KEY_GPGSIGN, false);
		signAllTags = config.getBoolean(ConfigConstants.CONFIG_TAG_SECTION,
				ConfigConstants.CONFIG_KEY_GPGSIGN, false);
		forceAnnotated = config.getBoolean(ConfigConstants.CONFIG_TAG_SECTION,
				ConfigConstants.CONFIG_KEY_FORCE_SIGN_ANNOTATED, false);
	}

	/**
	 * Retrieves the config value of gpg.format.
	 *
	 * @return the {@link org.eclipse.jgit.lib.GpgConfig.GpgFormat}
	 */
	public GpgFormat getKeyFormat() {
		return keyFormat;
	}

	/**
	 * Retrieves the value of the configured GPG program to use, as defined by
	 * gpg.openpgp.program, gpg.x509.program (depending on the defined
	 * {@link #getKeyFormat() format}), or gpg.program.
	 *
	 * @return the program string configured, or {@code null} if none
	 * @since 5.11
	 */
	public String getProgram() {
		return program;
	}

	/**
	 * Retrieves the config value of user.signingKey.
	 *
	 * @return the value of user.signingKey (may be <code>null</code>)
	 */
	public String getSigningKey() {
		return signingKey;
	}

	/**
	 * Retrieves the config value of commit.gpgSign.
	 *
	 * @return the value of commit.gpgSign (defaults to <code>false</code>)
	 */
	public boolean isSignCommits() {
		return signCommits;
	}

	/**
	 * Retrieves the value of git config {@code tag.gpgSign}.
	 *
	 * @return the value of {@code tag.gpgSign}; by default {@code false}
	 *
	 * @since 5.11
	 */
	public boolean isSignAllTags() {
		return signAllTags;
	}

	/**
	 * Retrieves the value of git config {@code tag.forceSignAnnotated}.
	 *
	 * @return the value of {@code tag.forceSignAnnotated}; by default
	 *         {@code false}
	 *
	 * @since 5.11
	 */
	public boolean isSignAnnotated() {
		return forceAnnotated;
	}
}
