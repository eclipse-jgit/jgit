/*
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
	private static int defaultStreamFileThreshold = 50 * 1024 * 1024;

	/**
	 * Returns the default threshold beyond which objects should not be read into
	 * memory completely but should be streamed. Streaming will keep the memory
	 * usage low in case of large objects, but may slow down operations
	 * significantly. Certain functionality may not be able to use streaming but
	 * will throw an Exception instead.
	 *
	 * @return the default stream file threshold
	 */
	public static int getDefaultStreamFileThreshold() {
		return defaultStreamFileThreshold;
	}

	/**
	 * @param threshold
	 */
	public static void setDefaultStreamFileThreshold(int threshold) {
		defaultStreamFileThreshold = threshold;
	}

	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<CoreConfig> KEY = new SectionParser<CoreConfig>() {
		public CoreConfig parse(final Config cfg) {
			return new CoreConfig(cfg);
		}
	};

	/** Permissible values for {@code core.autocrlf}. */
	public static enum AutoCRLF {
		/** Automatic CRLF->LF conversion is disabled. */
		FALSE,

		/** Automatic CRLF->LF conversion is enabled. */
		TRUE,

		/** CRLF->LF performed, but no LF->CRLF. */
		INPUT;
	}

	/**
	 * Permissible values for {@code core.checkstat}
	 *
	 * @since 3.0
	 */
	public static enum CheckStat {
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

	private final int compression;

	private final int packIndexVersion;

	private final boolean logAllRefUpdates;

	private final String excludesfile;

	private final long streamFileThreshold;

	/**
	 * Options for symlink handling
	 *
	 * @since 3.3
	 */
	public static enum SymLinks {
		/** Checkout symbolic links as plain files */
		FALSE,
		/** Checkout symbolic links as links */
		TRUE
	}

	private CoreConfig(final Config rc) {
		compression = rc.getInt(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_COMPRESSION, DEFAULT_COMPRESSION);
		packIndexVersion = rc.getInt(ConfigConstants.CONFIG_PACK_SECTION,
				ConfigConstants.CONFIG_KEY_INDEXVERSION, 2);
		logAllRefUpdates = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
		                                 ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
		excludesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
		                            ConfigConstants.CONFIG_KEY_EXCLUDESFILE);
		streamFileThreshold = rc.getLong(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_STREAM_FILE_TRESHOLD,
				defaultStreamFileThreshold);
	}

	/**
	 * @return The compression level to use when storing loose objects
	 */
	public int getCompression() {
		return compression;
	}

	/**
	 * @return the preferred pack index file format; 0 for oldest possible.
	 */
	public int getPackIndexVersion() {
		return packIndexVersion;
	}

	/**
	 * @return whether to log all refUpdates
	 */
	public boolean isLogAllRefUpdates() {
		return logAllRefUpdates;
	}

	/**
	 * @return path of excludesfile
	 */
	public String getExcludesFile() {
		return excludesfile;
	}

	/**
	 * @see #defaultStreamFileThreshold
	 *
	 * @return the size threshold beyond which objects must be streamed
	 */
	public long getStreamFileThreshold() {
		return streamFileThreshold;
	}
}
