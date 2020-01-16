/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.util.StringUtils;

/**
 * Submodule section of a Git configuration file.
 *
 * @since 4.7
 */
public class SubmoduleConfig {

	/**
	 * Config values for submodule.[name].fetchRecurseSubmodules.
	 */
	public enum FetchRecurseSubmodulesMode implements Config.ConfigEnum {
		/** Unconditionally recurse into all populated submodules. */
		YES("true"), //$NON-NLS-1$

		/**
		 * Only recurse into a populated submodule when the superproject
		 * retrieves a commit that updates the submodule's reference to a commit
		 * that isn't already in the local submodule clone.
		 */
		ON_DEMAND("on-demand"), //$NON-NLS-1$

		/** Completely disable recursion. */
		NO("false"); //$NON-NLS-1$

		private final String configValue;

		private FetchRecurseSubmodulesMode(String configValue) {
			this.configValue = configValue;
		}

		@Override
		public String toConfigValue() {
			return configValue;
		}

		@Override
		public boolean matchConfigValue(String s) {
			if (StringUtils.isEmptyOrNull(s)) {
				return false;
			}
			s = s.replace('-', '_');
			return name().equalsIgnoreCase(s)
					|| configValue.equalsIgnoreCase(s);
		}
	}
}
