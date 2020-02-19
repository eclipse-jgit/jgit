/*
 * Copyright (C) 2013, Gunnar Wagenknecht
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;

import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * This class keeps git repository core parameters.
 */
public class CoreConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<CoreConfig> KEY = CoreConfig::new;

	/** Permissible values for {@code core.autocrlf}. */
	public enum AutoCRLF {
		/** Automatic CRLF-&gt;LF conversion is disabled. */
		FALSE,

		/** Automatic CRLF-&gt;LF conversion is enabled. */
		TRUE,

		/** CRLF-&gt;LF performed, but no LF-&gt;CRLF. */
		INPUT;
	}

	/**
	 * Permissible values for {@code core.eol}.
	 * <p>
	 * https://git-scm.com/docs/gitattributes
	 *
	 * @since 4.3
	 */
	public enum EOL {
		/** Check in with LF, check out with CRLF. */
		CRLF,

		/** Check in with LF, check out without conversion. */
		LF,

		/** Use the platform's native line ending. */
		NATIVE;
	}

	/**
	 * EOL stream conversion protocol.
	 *
	 * @since 4.3
	 */
	public enum EolStreamType {
		/** Convert to CRLF without binary detection. */
		TEXT_CRLF,

		/** Convert to LF without binary detection. */
		TEXT_LF,

		/** Convert to CRLF with binary detection. */
		AUTO_CRLF,

		/** Convert to LF with binary detection. */
		AUTO_LF,

		/** Do not convert. */
		DIRECT;
	}

	/**
	 * Permissible values for {@code core.checkstat}.
	 *
	 * @since 3.0
	 */
	public enum CheckStat {
		/**
		 * Only check the size and whole second part of time stamp when
		 * comparing the stat info in the dircache with actual file stat info.
		 */
		MINIMAL,

		/**
		 * Check as much of the dircache stat info as possible. Implementation
		 * limits may apply.
		 */
		DEFAULT
	}

	/**
	 * Permissible values for {@code core.logAllRefUpdates}.
	 *
	 * @since 5.6
	 */
	public enum LogRefUpdates {
		/** Don't create ref logs; default for bare repositories. */
		FALSE,

		/**
		 * Create ref logs for refs/heads/**, refs/remotes/**, refs/notes/**,
		 * and for HEAD. Default for non-bare repositories.
		 */
		TRUE,

		/** Create ref logs for all refs/** and for HEAD. */
		ALWAYS
	}

	private final int compression;

	private final int packIndexVersion;

	private final LogRefUpdates logAllRefUpdates;

	private final String excludesfile;

	private final String attributesfile;

	/**
	 * Options for symlink handling
	 *
	 * @since 3.3
	 */
	public enum SymLinks {
		/** Check out symbolic links as plain files . */
		FALSE,

		/** Check out symbolic links as links. */
		TRUE
	}

	/**
	 * Options for hiding files whose names start with a period.
	 *
	 * @since 3.5
	 */
	public enum HideDotFiles {
		/** Do not hide .files. */
		FALSE,

		/** Hide add .files. */
		TRUE,

		/** Hide only .git. */
		DOTGITONLY
	}

	private CoreConfig(Config rc) {
		compression = rc.getInt(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_COMPRESSION, DEFAULT_COMPRESSION);
		packIndexVersion = rc.getInt(ConfigConstants.CONFIG_PACK_SECTION,
				ConfigConstants.CONFIG_KEY_INDEXVERSION, 2);
		logAllRefUpdates = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
				LogRefUpdates.TRUE);
		excludesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EXCLUDESFILE);
		attributesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE);
	}

	/**
	 * Get the compression level to use when storing loose objects
	 *
	 * @return The compression level to use when storing loose objects
	 */
	public int getCompression() {
		return compression;
	}

	/**
	 * Get the preferred pack index file format; 0 for oldest possible.
	 *
	 * @return the preferred pack index file format; 0 for oldest possible.
	 */
	public int getPackIndexVersion() {
		return packIndexVersion;
	}

	/**
	 * Whether to log all refUpdates
	 *
	 * @return whether to log all refUpdates
	 * @deprecated since 5.6; default value depends on whether the repository is
	 *             bare. Use
	 *             {@link Config#getEnum(String, String, String, Enum)}
	 *             directly.
	 */
	@Deprecated
	public boolean isLogAllRefUpdates() {
		return !LogRefUpdates.FALSE.equals(logAllRefUpdates);
	}

	/**
	 * Get path of excludesfile
	 *
	 * @return path of excludesfile
	 */
	public String getExcludesFile() {
		return excludesfile;
	}

	/**
	 * Get path of attributesfile
	 *
	 * @return path of attributesfile
	 * @since 3.7
	 */
	public String getAttributesFile() {
		return attributesfile;
	}
}
