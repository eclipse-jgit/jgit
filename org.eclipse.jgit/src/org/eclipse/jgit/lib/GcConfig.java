/*
 * Copyright (C) 2026, Matthias Sohn <matthias.sohn@sap.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * GC configuration settings.
 *
 * @since 7.6
 */
public class GcConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<GcConfig> KEY = GcConfig::new;

	/**
	 * Whether to pack refs during gc.
	 */
	public enum PackRefsMode {
		/**
		 * Pack no refs.
		 */
		FALSE,
		/**
		 * Pack all refs except symbolic refs.
		 */
		TRUE,
		/**
		 * Pack all refs except symbolic refs within all non-bare repositories.
		 */
		NOTBARE
	}

	private final PackRefsMode packRefs;

	private final boolean cruftPacks;

	/**
	 * Create a new gc configuration from the passed configuration.
	 *
	 * @param rc
	 *            git configuration
	 */
	public GcConfig(Config rc) {
		this.packRefs = rc.getEnum(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_PACK_REFS, PackRefsMode.TRUE);
		this.cruftPacks = rc.getBoolean(ConfigConstants.CONFIG_GC_SECTION,
				null, ConfigConstants.CONFIG_KEY_CRUFTPACKS, true);
	}

	/**
	 * Create a new gc configuration with the given settings.
	 *
	 * @param packRefs
	 *            whether to pack refs during gc
	 */
	public GcConfig(PackRefsMode packRefs) {
		this.packRefs = packRefs;
		this.cruftPacks = true;
	}

	private GcConfig(PackRefsMode packRefs, boolean cruftPacks) {
		this.packRefs = packRefs;
		this.cruftPacks = cruftPacks;
	}

	/**
	 * Get the pack refs mode configuring which refs to pack during gc.
	 *
	 * @return the pack refs mode configuring which refs to pack during gc.
	 */
	public PackRefsMode getPackRefs() {
		return packRefs;
	}

	/**
	 * Whether to pack unreachable objects into a cruft pack.
	 *
	 * @return {@code true} if unreachable objects should be packed into a cruft
	 *         pack instead of being loosened
	 * @since 8.0
	 */
	public boolean isCruftPacks() {
		return cruftPacks;
	}

	/**
	 * Return a copy of this configuration with {@code cruftPacks} overridden.
	 *
	 * @param value
	 *            the new value for the cruft packs setting
	 * @return a new {@link GcConfig} with the given cruft packs setting
	 * @since 8.0
	 */
	public GcConfig withCruftPacks(boolean value) {
		return new GcConfig(packRefs, value);
	}

}
