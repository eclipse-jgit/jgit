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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * The standard "transfer", "fetch", "receive", and "uploadpack" configuration
 * parameters.
 */
public class TransferConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<TransferConfig> KEY = new SectionParser<TransferConfig>() {
		public TransferConfig parse(final Config cfg) {
			return new TransferConfig(cfg);
		}
	};

	private final boolean fsckObjects;
	private final boolean allowTipSha1InWant;
	private final String[] hideRefs;

	TransferConfig(final Repository db) {
		this(db.getConfig());
	}

	private TransferConfig(final Config rc) {
		fsckObjects = rc.getBoolean("receive", "fsckobjects", false); //$NON-NLS-1$ //$NON-NLS-2$
		allowTipSha1InWant = rc.getBoolean(
				"uploadpack", "allowtipsha1inwant", false); //$NON-NLS-1$ //$NON-NLS-2$
		hideRefs = rc.getStringList("uploadpack", null, "hiderefs"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * @return strictly verify received objects?
	 */
	public boolean isFsckObjects() {
		return fsckObjects;
	}

	/**
	 * @return allow clients to request non-advertised tip SHA-1s?
	 * @since 3.1
	 */
	public boolean isAllowTipSha1InWant() {
		return allowTipSha1InWant;
	}

	/**
	 * @return {@link RefFilter} respecting configured hidden refs.
	 * @since 3.1
	 */
	public RefFilter getRefFilter() {
		if (hideRefs.length == 0)
			return RefFilter.DEFAULT;

		return new RefFilter() {
			public Map<String, Ref> filter(Map<String, Ref> refs) {
				Map<String, Ref> result = new HashMap<String, Ref>();
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
}
