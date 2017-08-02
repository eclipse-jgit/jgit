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

import java.text.MessageFormat;
import java.util.function.Predicate;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * This class keeps git repository core parameters.
 */
public class CoreConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<CoreConfig> KEY =
			// Assume non-bare for backwards compatibility, so isLogAllRefUpdates
			// continues to default to true if unset.
			cfg -> new CoreConfig(cfg, false);

	/**
	 * Key for {@link Config#get(SectionParser)}.
	 * <p>
	 * Takes into account properties of the repository that are not captured in
	 * the {@link Config}, such as whether it has a working tree.
	 *
	 * @param repo
	 *            repository containing the config.
	 * @return section parser.
	 * @since 4.9
	 */
	public static Config.SectionParser<CoreConfig> key(Repository repo) {
		return cfg -> new CoreConfig(cfg, repo.isBare());
	}

	/** Permissible values for {@code core.autocrlf}. */
	public static enum AutoCRLF {
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
	public static enum EOL {
		/** checkin with LF, checkout with CRLF. */
		CRLF,

		/** checkin with LF, checkout without conversion. */
		LF,

		/** use the platform's native line ending. */
		NATIVE;
	}

	/**
	 * EOL stream conversion protocol
	 *
	 * @since 4.3
	 */
	public static enum EolStreamType {
		/** convert to CRLF without binary detection */
		TEXT_CRLF,

		/** convert to LF without binary detection */
		TEXT_LF,

		/** convert to CRLF with binary detection */
		AUTO_CRLF,

		/** convert to LF with binary detection */
		AUTO_LF,

		/** do not convert */
		DIRECT;
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

	private final LogAllRefUpdates logAllRefUpdates;

	private final String excludesfile;

	private final String attributesfile;

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

	/**
	 * Options for hiding files whose names start with a period
	 *
	 * @since 3.5
	 */
	public static enum HideDotFiles {
		/** Do not hide .files */
		FALSE,
		/** Hide add .files */
		TRUE,
		/** Hide only .git */
		DOTGITONLY
	}

	/**
	 * Options for enabling reflogs with {@code core.logAllRefUpdates}.
	 * <p>
	 * See also the documentation of {@code core.logAllRefUpdates} in {@code
	 * git-config(1)}.
	 *
	 * @since 4.9
	 */
	public static enum LogAllRefUpdates {
		/**
		 * Don't log all ref updates: only log updates for refs whose reflog
		 * already exists.
		 *
		 * @since 4.9
		 */
		FALSE(r -> false),

		/**
		 * Log all ref updates for a default set of refs.
		 * <p>
		 * Updates are logged only for the following refs:
		 * <ul>
		 * <li>{@code refs/heads/*}</li>
		 * <li>{@code refs/remotes/*}</li>
		 * <li>{@code refs/notes/*}</li>
		 * <li>{@code HEAD}</li>
		 *
		 * @since 4.9
		 */
		TRUE(r -> r.startsWith(Constants.R_HEADS)
				|| r.startsWith(Constants.R_REMOTES)
				|| r.startsWith(Constants.R_NOTES)
				|| r.equals(Constants.HEAD)),

		/**
		 * Log all ref updates for all refs.
		 *
		 * @since 4.9
		 */
		ALWAYS(r -> true);

		private final Predicate<String> shouldAutoCreateLog;

		private LogAllRefUpdates(Predicate<String> shouldAutoCreateLog) {
			this.shouldAutoCreateLog = shouldAutoCreateLog;
		}

		/**
		 * Check whether a log should be auto-created for a ref.
		 *
		 * @param refName
		 *            ref name to check.
		 * @return true if the log should be auto-created.
		 * @since 4.9
		 */
		public boolean shouldAutoCreateLog(String refName) {
			return shouldAutoCreateLog.test(refName);
		}
	}

	private CoreConfig(Config rc, boolean bare) {
		compression = rc.getInt(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_COMPRESSION, DEFAULT_COMPRESSION);
		packIndexVersion = rc.getInt(ConfigConstants.CONFIG_PACK_SECTION,
				ConfigConstants.CONFIG_KEY_INDEXVERSION, 2);
		logAllRefUpdates = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
				bare ? LogAllRefUpdates.FALSE : LogAllRefUpdates.TRUE);
		excludesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EXCLUDESFILE);
		attributesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE);
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
	 * Check whether all ref updates on some refs should be logged.
	 *
	 * @deprecated use {@code #getLogAllRefUpdates()}.
	 * @return whether to log all ref updates on at least some ref updates;
	 *         details about which ref updates are logged can be distinguished via
	 *         {@code #getLogAllRefUpdates()}.
	 */
	@Deprecated
	public boolean isLogAllRefUpdates() {
		switch (logAllRefUpdates) {
			case FALSE:
				return false;
			case TRUE:
			case ALWAYS:
				return true;
			default:
				throw new IllegalStateException(
						MessageFormat.format(
								JGitText.get().enumValueNotSupported0,
								logAllRefUpdates.name()));
		}
	}

	/**
	 * Get an enum value describing how to log all ref updates.
	 *
	 * @return whether to log all ref updates.
	 * @since 4.9
	 */
	public LogAllRefUpdates getLogAllRefUpdates() {
		return logAllRefUpdates;
	}

	/**
	 * @return path of excludesfile
	 */
	public String getExcludesFile() {
		return excludesfile;
	}

	/**
	 * @return path of attributesfile
	 * @since 3.7
	 */
	public String getAttributesFile() {
		return attributesfile;
	}
}
