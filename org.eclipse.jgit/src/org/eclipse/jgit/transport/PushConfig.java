/*
 * Copyright (C) 2017, 2022 David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Locale;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.StringUtils;

/**
 * Push section of a Git configuration file.
 *
 * @since 4.9
 */
public class PushConfig {

	/**
	 * Git config values for {@code push.recurseSubmodules}.
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

	/**
	 * Git config values for {@code push.default}.
	 *
	 * @since 6.1
	 */
	public enum PushDefault implements Config.ConfigEnum {

		/**
		 * Do not push if there are no explicit refspecs.
		 */
		NOTHING,

		/**
		 * Push the current branch to an upstream branch of the same name.
		 */
		CURRENT,

		/**
		 * Push the current branch to an upstream branch determined by git
		 * config {@code branch.<currentBranch>.merge}.
		 */
		UPSTREAM("tracking"), //$NON-NLS-1$

		/**
		 * Like {@link #UPSTREAM}, but only if the upstream name is the same as
		 * the name of the current local branch.
		 */
		SIMPLE,

		/**
		 * Push all current local branches that match a configured push refspec
		 * of the remote configuration.
		 */
		MATCHING;

		private final String alias;

		private PushDefault() {
			alias = null;
		}

		private PushDefault(String alias) {
			this.alias = alias;
		}

		@Override
		public String toConfigValue() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Override
		public boolean matchConfigValue(String in) {
			return toConfigValue().equalsIgnoreCase(in)
					|| alias != null && alias.equalsIgnoreCase(in);
		}
	}

	private final PushRecurseSubmodulesMode recurseSubmodules;

	private final PushDefault pushDefault;

	/**
	 * Creates a new instance.
	 *
	 * @param config
	 *            {@link Config} to fill the {@link PushConfig} from
	 * @since 6.1
	 */
	public PushConfig(Config config) {
		recurseSubmodules = config.getEnum(ConfigConstants.CONFIG_PUSH_SECTION,
				null, ConfigConstants.CONFIG_KEY_RECURSE_SUBMODULES,
				PushRecurseSubmodulesMode.NO);
		pushDefault = config.getEnum(ConfigConstants.CONFIG_PUSH_SECTION, null,
				ConfigConstants.CONFIG_KEY_DEFAULT, PushDefault.SIMPLE);
	}

	/**
	 * Retrieves the value of git config {@code push.recurseSubmodules}.
	 *
	 * @return the value
	 * @since 6.1
	 */
	public PushRecurseSubmodulesMode getRecurseSubmodules() {
		return recurseSubmodules;
	}

	/**
	 * Retrieves the value of git config {@code push.default}.
	 *
	 * @return the value
	 * @since 6.1
	 */
	public PushDefault getPushDefault() {
		return pushDefault;
	}
}
