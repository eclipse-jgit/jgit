/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2012-2013, Robin Rosenberg
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
package org.eclipse.jgit.treewalk;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.CheckStat;
import org.eclipse.jgit.lib.CoreConfig.HideDotFiles;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;

/** Options used by the {@link WorkingTreeIterator}. */
public class WorkingTreeOptions {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<WorkingTreeOptions> KEY = new SectionParser<WorkingTreeOptions>() {
		public WorkingTreeOptions parse(final Config cfg) {
			return new WorkingTreeOptions(cfg);
		}
	};

	private final boolean fileMode;

	private final AutoCRLF autoCRLF;

	private final CheckStat checkStat;

	private final SymLinks symlinks;

	private final HideDotFiles hideDotFiles;

	private WorkingTreeOptions(final Config rc) {
		fileMode = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		autoCRLF = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, AutoCRLF.FALSE);
		checkStat = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_CHECKSTAT, CheckStat.DEFAULT);
		symlinks = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, SymLinks.TRUE);
		hideDotFiles = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HIDEDOTFILES,
				HideDotFiles.DOTGITONLY);
	}

	/** @return true if the execute bit on working files should be trusted. */
	public boolean isFileMode() {
		return fileMode;
	}

	/** @return how automatic CRLF conversion has been configured. */
	public AutoCRLF getAutoCRLF() {
		return autoCRLF;
	}

	/**
	 * @return how stat data is compared
	 * @since 2.3
	 */
	public CheckStat getCheckStat() {
		return checkStat;
	}

	/** @return how we handle symbolic links */
	public SymLinks getSymLinks() {
		return symlinks;
	}

	/** @return how we create '.'-files */
	public HideDotFiles getHideDotFiles() {
		return hideDotFiles;
	}
}
