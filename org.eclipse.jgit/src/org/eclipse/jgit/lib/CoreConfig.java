/*
 * Copyright (C) 2013, Gunnar Wagenknecht
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class keeps git repository core parameters.
 */
public class CoreConfig {
	private static final Logger LOG = LoggerFactory.getLogger(CoreConfig.class);
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

	/**
	 * Default value of commit graph enable option: {@value}
	 *
	 * @since 6.5
	 */
	public static final boolean DEFAULT_COMMIT_GRAPH_ENABLE = false;

	/**
	 * Permissible values for {@code core.trustPackedRefsStat}.
	 *
	 * @since 6.1.1
	 * @deprecated use {@link TrustStat} instead
	 */
	@Deprecated(since = "7.2", forRemoval = true)
	public enum TrustPackedRefsStat {
		/** Do not trust file attributes of the packed-refs file. */
		NEVER,

		/** Trust file attributes of the packed-refs file. */
		ALWAYS,

		/**
		 * Open and close the packed-refs file to refresh its file attributes
		 * and then trust it.
		 */
		AFTER_OPEN,

		/**
		 * {@code core.trustPackedRefsStat} defaults to this when it is not set
		 */
		UNSET
	}

	/**
	 * Permissible values for {@code core.trustLooseRefStat}.
	 *
	 * @since 6.9
	 * @deprecated use {@link TrustStat} instead
	 */
	@Deprecated(since = "7.2", forRemoval = true)
	public enum TrustLooseRefStat {

		/** Trust file attributes of the loose ref. */
		ALWAYS,

		/**
		 * Open and close parent directories of the loose ref file until the
		 * repository root to refresh its file attributes and then trust it.
		 */
		AFTER_OPEN,
	}

	/**
	 * Values for {@code core.trustXXX} options.
	 *
	 * @since 7.2
	 */
	public enum TrustStat {
		/** Do not trust file attributes of a File. */
		NEVER,

		/** Always trust file attributes of a File. */
		ALWAYS,

		/** Open and close the File to refresh its file attributes
		 * and then trust it. */
		AFTER_OPEN,

		/**
		 * Used for specific options to inherit value from value set for
		 * core.trustStat.
		 */
		INHERIT
	}

	private final int compression;

	private final int packIndexVersion;

	private final String excludesfile;

	private final String attributesfile;

	private final boolean commitGraph;

	private final TrustStat trustStat;

	private final TrustStat trustPackedRefsStat;

	private final TrustStat trustLooseRefStat;

	private final TrustStat trustPackStat;

	private final TrustStat trustLooseObjectStat;

	private final TrustStat trustTablesListStat;

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

	/**
	 * Create a new core configuration from the passed configuration.
	 *
	 * @param rc
	 *            git configuration
	 */
	CoreConfig(Config rc) {
		compression = rc.getInt(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_COMPRESSION, DEFAULT_COMPRESSION);
		packIndexVersion = rc.getInt(ConfigConstants.CONFIG_PACK_SECTION,
				ConfigConstants.CONFIG_KEY_INDEXVERSION, 2);
		excludesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EXCLUDESFILE);
		attributesfile = rc.getString(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE);
		commitGraph = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_COMMIT_GRAPH,
				DEFAULT_COMMIT_GRAPH_ENABLE);

		trustStat = parseTrustStat(rc);
		trustPackedRefsStat = parseTrustPackedRefsStat(rc);
		trustLooseRefStat = parseTrustLooseRefStat(rc);
		trustPackStat = parseTrustPackFileStat(rc);
		trustLooseObjectStat = parseTrustLooseObjectFileStat(rc);
		trustTablesListStat = parseTablesListStat(rc);
	}

	private static TrustStat parseTrustStat(Config rc) {
		Boolean tfs = rc.getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT);
		TrustStat ts = rc.getEnum(TrustStat.values(),
				ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUST_STAT);
		if (tfs != null) {
			if (ts == null) {
				LOG.warn(JGitText.get().deprecatedTrustFolderStat);
				return tfs.booleanValue() ? TrustStat.ALWAYS : TrustStat.NEVER;
			}
			LOG.warn(JGitText.get().precedenceTrustConfig);
		}
		if (ts == null) {
			ts = TrustStat.ALWAYS;
		} else if (ts == TrustStat.INHERIT) {
			LOG.warn(JGitText.get().invalidTrustStat);
			ts = TrustStat.ALWAYS;
		}
		return ts;
	}

	private TrustStat parseTrustPackedRefsStat(Config rc) {
		return inheritParseTrustStat(rc,
				ConfigConstants.CONFIG_KEY_TRUST_PACKED_REFS_STAT);
	}

	private TrustStat parseTrustLooseRefStat(Config rc) {
		return inheritParseTrustStat(rc,
				ConfigConstants.CONFIG_KEY_TRUST_LOOSE_REF_STAT);
	}

	private TrustStat parseTrustPackFileStat(Config rc) {
		return inheritParseTrustStat(rc,
				ConfigConstants.CONFIG_KEY_TRUST_PACK_STAT);
	}

	private TrustStat parseTrustLooseObjectFileStat(Config rc) {
		return inheritParseTrustStat(rc,
				ConfigConstants.CONFIG_KEY_TRUST_LOOSE_OBJECT_STAT);
	}

	private TrustStat inheritParseTrustStat(Config rc, String key) {
		TrustStat t = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null, key,
				TrustStat.INHERIT);
		return t == TrustStat.INHERIT ? trustStat : t;
	}

	private TrustStat parseTablesListStat(Config rc) {
		TrustStat t = rc.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUST_TABLESLIST_STAT,
				TrustStat.INHERIT);
		return t == TrustStat.INHERIT ? trustStat : t;
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

	/**
	 * Whether to read the commit-graph file (if it exists) to parse the graph
	 * structure of commits. Default to
	 * {@value org.eclipse.jgit.lib.CoreConfig#DEFAULT_COMMIT_GRAPH_ENABLE}.
	 *
	 * @return whether to read the commit-graph file
	 * @since 6.5
	 */
	public boolean enableCommitGraph() {
		return commitGraph;
	}

	/**
	 * Get how far we can trust file attributes of packed-refs file which is
	 * used to store {@link org.eclipse.jgit.lib.Ref}s in
	 * {@link org.eclipse.jgit.internal.storage.file.RefDirectory}.
	 *
	 * @return how far we can trust file attributes of packed-refs file.
	 *
	 * @since 7.2
	 */
	public TrustStat getTrustPackedRefsStat() {
		return trustPackedRefsStat;
	}

	/**
	 * Get how far we can trust file attributes of loose ref files which are
	 * used to store {@link org.eclipse.jgit.lib.Ref}s in
	 * {@link org.eclipse.jgit.internal.storage.file.RefDirectory}.
	 *
	 * @return how far we can trust file attributes of loose ref files.
	 *
	 * @since 7.2
	 */
	public TrustStat getTrustLooseRefStat() {
		return trustLooseRefStat;
	}

	/**
	 * Get how far we can trust file attributes of packed-refs file which is
	 * used to store {@link org.eclipse.jgit.lib.Ref}s in
	 * {@link org.eclipse.jgit.internal.storage.file.RefDirectory}.
	 *
	 * @return how far we can trust file attributes of packed-refs file.
	 *
	 * @since 7.2
	 */
	public TrustStat getTrustPackStat() {
		return trustPackStat;
	}

	/**
	 * Get how far we can trust file attributes of loose ref files which are
	 * used to store {@link org.eclipse.jgit.lib.Ref}s in
	 * {@link org.eclipse.jgit.internal.storage.file.RefDirectory}.
	 *
	 * @return how far we can trust file attributes of loose ref files.
	 *
	 * @since 7.2
	 */
	public TrustStat getTrustLooseObjectStat() {
		return trustLooseObjectStat;
	}

	/**
	 * Get how far we can trust file attributes of the "tables.list" file which
	 * is used to store the list of filenames of the files storing
	 * {@link org.eclipse.jgit.internal.storage.reftable.Reftable}s in
	 * {@link org.eclipse.jgit.internal.storage.file.FileReftableDatabase}.
	 *
	 * @return how far we can trust file attributes of the "tables.list" file.
	 *
	 * @since 7.2
	 */
	public TrustStat getTrustTablesListStat() {
		return trustTablesListStat;
	}
}
