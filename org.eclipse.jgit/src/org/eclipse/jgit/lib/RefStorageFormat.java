/*
 * Copyright (C) 2026, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

/**
 * Ref storage format of a repository, using git's native names.
 *
 * @since 7.9
 */
public enum RefStorageFormat implements Config.ConfigEnum {
	/** Files format ({@code files}). */
	FILES(ConfigConstants.CONFIG_REF_STORAGE_FILES),
	/** Reftable format ({@code reftable}). */
	REFTABLE(ConfigConstants.CONFIG_REF_STORAGE_REFTABLE);

	private final String configValue;

	RefStorageFormat(String configValue) {
		this.configValue = configValue;
	}

	@Override
	public String toConfigValue() {
		return configValue;
	}

	@Override
	public boolean matchConfigValue(String in) {
		return configValue.equals(in);
	}

	/**
	 * Parse a git ref storage format name.
	 *
	 * @param name
	 *            format name, e.g. {@code files} or {@code reftable}.
	 * @return the matching format, or {@code null} if {@code name} is
	 *         {@code null} or not a known format.
	 */
	public static RefStorageFormat fromConfigValue(String name) {
		if (name != null) {
			for (RefStorageFormat format : values()) {
				if (format.matchConfigValue(name)) {
					return format;
				}
			}
		}
		return null;
	}
}
