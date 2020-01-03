/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;

/**
 * Push section of a Git configuration file.
 *
 * @since 4.9
 */
public class PushConfig {
	/**
	 * Config values for push.recurseSubmodules.
	 */
	public enum PushRecurseSubmodulesMode implements Config.ConfigEnum {
		/**
		 * Verify that all submodule commits that changed in the revisions to be
		 * pushed are available on at least one remote of the submodule.
		 */
		CHECK("check"), //$NON-NLS-1$

		/**
		 * All submodules that changed in the revisions to be pushed will be
		 * pushed.
		 */
		ON_DEMAND("on-demand"), //$NON-NLS-1$

		/** Default behavior of ignoring submodules when pushing is retained. */
		NO("false"); //$NON-NLS-1$

		private final String configValue;

		private PushRecurseSubmodulesMode(String configValue) {
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
