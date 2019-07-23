/*
 * Copyright (c) 2019 Brian Riehman <briehman@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.mailmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Supports mapping to canonical identities according to a set of mailmap
 * entries. Entries win if their old part is identical or more specific (name +
 * e-mail vs. e-mail only).
 *
 * @since 7.7
 */
public class Mailmap {

	/**
	 * Map of old-email/old-name key to {@link MailmapEntry} to facilitate
	 * performance.
	 */
	private final Map<String, MailmapEntry> entriesMap;


	/**
	 * Create an entry mailmap.
	 */
	public Mailmap() {
		this.entriesMap = new HashMap<>();
	}

	/**
	 * Produce a mailmap based upon the provided entries.
	 *
	 * @param entries
	 *            the canonical mappings defining the mailmap
	 */
	public Mailmap(List<MailmapEntry> entries) {
		this.entriesMap = new HashMap<>(entries.size());
		entries.stream()
				.forEach(me -> entriesMap.put(me.getMapKeyForLookup(), me));
	}

	/**
	 * Map the given identity to a proper canonical entry, if defined in the
	 * mailmap. Otherwise, return the provided identity.
	 *
	 * @param ident
	 *            the identity to map
	 * @return a modified identity if matched against a mailmap entry, else the
	 *         original entry
	 */
	public PersonIdent map(@Nullable PersonIdent ident) {
		if (ident == null) {
			return null;
		}
		MailmapEntry matchingEntry = entriesMap
				.get(ident.getEmailAddress() + " " + ident.getName()); //$NON-NLS-1$
		if (matchingEntry == null) {
			matchingEntry = entriesMap.get(ident.getEmailAddress());
		}
		return matchingEntry == null ? ident : matchingEntry.map(ident);
	}

	/**
	 * Append the provided mailmap entries to the end of the current mailmap. If
	 * new lines have identical or more specific old parts, those newer lines
	 * override an earlier line. If the earlier line's old part is more
	 * specific, the earlier line wins.
	 *
	 * @param mailmap
	 *            the mailmap whose entries will be added to the current mailmap
	 */
	public void append(Mailmap mailmap) {
		entriesMap.putAll(mailmap.entriesMap);
	}
}
