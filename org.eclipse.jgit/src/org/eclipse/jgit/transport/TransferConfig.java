/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.util.StringUtils.equalsIgnoreCase;
import static org.eclipse.jgit.util.StringUtils.toLowerCase;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.file.LazyObjectIdSetFile;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.SystemReader;

/**
 * The standard "transfer", "fetch", "receive", and "uploadpack" configuration
 * parameters.
 */
public class TransferConfig {
	private static final String FSCK = "fsck"; //$NON-NLS-1$

	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<TransferConfig> KEY =
			TransferConfig::new;

	/**
	 * A git configuration value for how to handle a fsck failure of a particular kind.
	 * Used in e.g. fsck.missingEmail.
	 * @since 4.9
	 */
	public enum FsckMode {
		/**
		 * Treat it as an error (the default).
		 */
		ERROR,
		/**
		 * Issue a warning (in fact, jgit treats this like IGNORE, but git itself does warn).
		 */
		WARN,
		/**
		 * Ignore the error.
		 */
		IGNORE;
	}

	private final boolean fetchFsck;
	private final boolean receiveFsck;
	private final String fsckSkipList;
	private final EnumSet<ObjectChecker.ErrorType> ignore;
	private final boolean allowInvalidPersonIdent;
	private final boolean safeForWindows;
	private final boolean safeForMacOS;
	private final boolean allowTipSha1InWant;
	private final boolean allowReachableSha1InWant;
	final String[] hideRefs;

	TransferConfig(final Repository db) {
		this(db.getConfig());
	}

	TransferConfig(final Config rc) {
		boolean fsck = rc.getBoolean("transfer", "fsckobjects", false); //$NON-NLS-1$ //$NON-NLS-2$
		fetchFsck = rc.getBoolean("fetch", "fsckobjects", fsck); //$NON-NLS-1$ //$NON-NLS-2$
		receiveFsck = rc.getBoolean("receive", "fsckobjects", fsck); //$NON-NLS-1$ //$NON-NLS-2$
		fsckSkipList = rc.getString(FSCK, null, "skipList"); //$NON-NLS-1$
		allowInvalidPersonIdent = rc.getBoolean(FSCK, "allowInvalidPersonIdent", false); //$NON-NLS-1$
		safeForWindows = rc.getBoolean(FSCK, "safeForWindows", //$NON-NLS-1$
						SystemReader.getInstance().isWindows());
		safeForMacOS = rc.getBoolean(FSCK, "safeForMacOS", //$NON-NLS-1$
						SystemReader.getInstance().isMacOS());

		ignore = EnumSet.noneOf(ObjectChecker.ErrorType.class);
		EnumSet<ObjectChecker.ErrorType> set = EnumSet
				.noneOf(ObjectChecker.ErrorType.class);
		for (String key : rc.getNames(FSCK)) {
			if (equalsIgnoreCase(key, "skipList") //$NON-NLS-1$
					|| equalsIgnoreCase(key, "allowLeadingZeroFileMode") //$NON-NLS-1$
					|| equalsIgnoreCase(key, "allowInvalidPersonIdent") //$NON-NLS-1$
					|| equalsIgnoreCase(key, "safeForWindows") //$NON-NLS-1$
					|| equalsIgnoreCase(key, "safeForMacOS")) { //$NON-NLS-1$
				continue;
			}

			ObjectChecker.ErrorType id = FsckKeyNameHolder.parse(key);
			if (id != null) {
				switch (rc.getEnum(FSCK, null, key, FsckMode.ERROR)) {
				case ERROR:
					ignore.remove(id);
					break;
				case WARN:
				case IGNORE:
					ignore.add(id);
					break;
				}
				set.add(id);
			}
		}
		if (!set.contains(ObjectChecker.ErrorType.ZERO_PADDED_FILEMODE)
				&& rc.getBoolean(FSCK, "allowLeadingZeroFileMode", false)) { //$NON-NLS-1$
			ignore.add(ObjectChecker.ErrorType.ZERO_PADDED_FILEMODE);
		}

		allowTipSha1InWant = rc.getBoolean(
				"uploadpack", "allowtipsha1inwant", false); //$NON-NLS-1$ //$NON-NLS-2$
		allowReachableSha1InWant = rc.getBoolean(
				"uploadpack", "allowreachablesha1inwant", false); //$NON-NLS-1$ //$NON-NLS-2$
		hideRefs = rc.getStringList("uploadpack", null, "hiderefs"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Create checker to verify fetched objects
	 *
	 * @return checker to verify fetched objects, or null if checking is not
	 *         enabled in the repository configuration.
	 * @since 3.6
	 */
	@Nullable
	public ObjectChecker newObjectChecker() {
		return newObjectChecker(fetchFsck);
	}

	/**
	 * Create checker to verify objects pushed into this repository
	 *
	 * @return checker to verify objects pushed into this repository, or null if
	 *         checking is not enabled in the repository configuration.
	 * @since 4.2
	 */
	@Nullable
	public ObjectChecker newReceiveObjectChecker() {
		return newObjectChecker(receiveFsck);
	}

	private ObjectChecker newObjectChecker(boolean check) {
		if (!check) {
			return null;
		}
		return new ObjectChecker()
			.setIgnore(ignore)
			.setAllowInvalidPersonIdent(allowInvalidPersonIdent)
			.setSafeForWindows(safeForWindows)
			.setSafeForMacOS(safeForMacOS)
			.setSkipList(skipList());
	}

	private ObjectIdSet skipList() {
		if (fsckSkipList != null && !fsckSkipList.isEmpty()) {
			return new LazyObjectIdSetFile(new File(fsckSkipList));
		}
		return null;
	}

	/**
	 * Whether to allow clients to request non-advertised tip SHA-1s
	 *
	 * @return allow clients to request non-advertised tip SHA-1s?
	 * @since 3.1
	 */
	public boolean isAllowTipSha1InWant() {
		return allowTipSha1InWant;
	}

	/**
	 * Whether to allow clients to request non-tip SHA-1s
	 *
	 * @return allow clients to request non-tip SHA-1s?
	 * @since 4.1
	 */
	public boolean isAllowReachableSha1InWant() {
		return allowReachableSha1InWant;
	}

	/**
	 * Get {@link org.eclipse.jgit.transport.RefFilter} respecting configured
	 * hidden refs.
	 *
	 * @return {@link org.eclipse.jgit.transport.RefFilter} respecting
	 *         configured hidden refs.
	 * @since 3.1
	 */
	public RefFilter getRefFilter() {
		if (hideRefs.length == 0)
			return RefFilter.DEFAULT;

		return new RefFilter() {
			@Override
			public Map<String, Ref> filter(Map<String, Ref> refs) {
				Map<String, Ref> result = new HashMap<>();
				for (Map.Entry<String, Ref> e : refs.entrySet()) {
					boolean add = true;
					for (String hide : hideRefs) {
						if (e.getKey().equals(hide) || prefixMatch(hide, e.getKey())) {
							add = false;
							break;
						}
					}
					if (add)
						result.put(e.getKey(), e.getValue());
				}
				return result;
			}

			private boolean prefixMatch(String p, String s) {
				return p.charAt(p.length() - 1) == '/' && s.startsWith(p);
			}
		};
	}

	static class FsckKeyNameHolder {
		private static final Map<String, ObjectChecker.ErrorType> errors;

		static {
			errors = new HashMap<>();
			for (ObjectChecker.ErrorType m : ObjectChecker.ErrorType.values()) {
				errors.put(keyNameFor(m.name()), m);
			}
		}

		static ObjectChecker.@Nullable ErrorType parse(String key) {
			return errors.get(toLowerCase(key));
		}

		private static String keyNameFor(String name) {
			StringBuilder r = new StringBuilder(name.length());
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if (c != '_') {
					r.append(c);
				}
			}
			return toLowerCase(r.toString());
		}

		private FsckKeyNameHolder() {
		}
	}
}
