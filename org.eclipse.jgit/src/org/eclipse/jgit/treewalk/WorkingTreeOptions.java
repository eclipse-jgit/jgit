/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2012-2013, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.CheckStat;
import org.eclipse.jgit.lib.CoreConfig.EOL;
import org.eclipse.jgit.lib.CoreConfig.HideDotFiles;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;

/**
 * Options used by the {@link org.eclipse.jgit.treewalk.WorkingTreeIterator}.
 */
public class WorkingTreeOptions {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<WorkingTreeOptions> KEY =
			WorkingTreeOptions::new;

	private final boolean fileMode;

	private final AutoCRLF autoCRLF;

	private final EOL eol;

	private final CheckStat checkStat;

	private final SymLinks symlinks;

	private final HideDotFiles hideDotFiles;

	private final boolean dirNoGitLinks;

	private WorkingTreeOptions(Config rc) {
		fileMode = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		autoCRLF = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, AutoCRLF.FALSE);
		eol = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EOL, EOL.NATIVE);
		checkStat = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_CHECKSTAT, CheckStat.DEFAULT);
		symlinks = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, SymLinks.TRUE);
		hideDotFiles = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HIDEDOTFILES,
				HideDotFiles.DOTGITONLY);
		dirNoGitLinks = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_DIRNOGITLINKS,
				false);
	}

	/** @return true if the execute bit on working files should be trusted. */
	/**
	 * Whether the execute bit on working files should be trusted.
	 *
	 * @return {@code true} if the execute bit on working files should be
	 *         trusted.
	 */
	public boolean isFileMode() {
		return fileMode;
	}

	/**
	 * Get automatic CRLF conversion configuration.
	 *
	 * @return how automatic CRLF conversion has been configured.
	 */
	public AutoCRLF getAutoCRLF() {
		return autoCRLF;
	}

	/**
	 * Get how text line endings should be normalized.
	 *
	 * @return how text line endings should be normalized.
	 * @since 4.3
	 */
	public EOL getEOL() {
		return eol;
	}

	/**
	 * Get how stat data is compared.
	 *
	 * @return how stat data is compared.
	 * @since 3.0
	 */
	public CheckStat getCheckStat() {
		return checkStat;
	}

	/**
	 * Get how we handle symbolic links
	 *
	 * @return how we handle symbolic links
	 * @since 3.3
	 */
	public SymLinks getSymLinks() {
		return symlinks;
	}

	/**
	 * Get how we create '.'-files (on Windows)
	 *
	 * @return how we create '.'-files (on Windows)
	 * @since 3.5
	 */
	public HideDotFiles getHideDotFiles() {
		return hideDotFiles;
	}

	/**
	 * Whether or not we treat nested repos as directories.
	 *
	 * @return whether or not we treat nested repos as directories. If true,
	 *         folders containing .git entries will not be treated as gitlinks.
	 * @since 4.3
	 */
	public boolean isDirNoGitLinks() { return dirNoGitLinks; }
}
