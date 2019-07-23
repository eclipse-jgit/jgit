/*
 * Copyright (c) 2019, 2026 Brian Riehman <briehman@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.mailmap;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Supports mapping to canonical identities according to a set of mailmap
 * entries.
 *
 * @since 8.0
 */
public class Mailmap {

	/**
	 * Map of old-email/old-name key to {@link MailmapEntry} to facilitate
	 * performance.
	 */
	private final Map<String, MailmapEntry> entries;


	/**
	 * Create an entry mailmap.
	 */
	public Mailmap() {
		this.entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * Produce a mailmap based upon the provided entries.
	 *
	 * @param initialEntries
	 *            the canonical mappings defining the mailmap
	 */
	public Mailmap(List<MailmapEntry> initialEntries) {
		this();
		initialEntries.forEach(me -> entries.put(me.getMapKeyForLookup(), me));
	}

	/**
	 * Map the given identity to a proper canonical entry, if defined in the
	 * mailmap. Matching in the mailmap is case insensitive, and more specific
	 * entries (name + email) take precedence over less specific ones (email
	 * only).
	 *
	 * @param ident
	 *            the identity to map
	 * @return a modified identity if matched against a mailmap entry, else the
	 *         original {@code ident}
	 */
	public PersonIdent map(@Nullable PersonIdent ident) {
		if (ident == null) {
			return null;
		}
		MailmapEntry matchingEntry = entries
				.get(ident.getEmailAddress() + ' ' + ident.getName());
		if (matchingEntry == null) {
			matchingEntry = entries.get(ident.getEmailAddress());
		}
		return matchingEntry == null ? ident : matchingEntry.map(ident);
	}

	/**
	 * Add the entries from the given {@code mailmap}. New entries will override
	 * existing entries that have the same map key (case insensitively).
	 *
	 * @param mailmap
	 *            the mailmap whose entries will be added to the current mailmap
	 */
	public void append(Mailmap mailmap) {
		entries.putAll(mailmap.entries);
	}
}
