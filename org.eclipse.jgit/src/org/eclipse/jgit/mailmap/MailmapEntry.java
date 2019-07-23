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

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * A mailmap mapping from an old name and/or email address to a proper name
 * and/or email address.
 *
 * The old email address is the member which is non-null. The other attributes
 * are nullable, but at least one is non-null in order to define a valid entry.
 *
 * @since 8.0
 */
public class MailmapEntry {

	private final @NonNull String oldEmail;

	private final String oldName;

	private final String newEmail;

	private final String newName;

	MailmapEntry(String oldName, @NonNull String oldEmail, String newName,
			String newEmail) {
		this.oldName = oldName;
		this.newName = newName;
		this.oldEmail = oldEmail;
		this.newEmail = newEmail;
	}

	/**
	 * Indicates that the given mailmap entry matches the provided user identity
	 *
	 * @param ident
	 *            the identity to match against
	 * @return true if the entry matches else false
	 */
	public boolean matches(PersonIdent ident) {
		return oldEmail.equalsIgnoreCase(ident.getEmailAddress())
				&& oldName == null || oldName.equalsIgnoreCase(ident.getName());
	}

	/**
	 * Map a given person identity to the updates version according to this
	 * entry. This may alter the name or email address, but the rest of the
	 * object should remain unchanged.
	 *
	 * @param original
	 *            the original identity to update
	 * @return a new identity updated to match this mailmap entry
	 */
	public PersonIdent map(PersonIdent original) {
		if (!matches(original)) {
			throw new IllegalStateException(
					String.format("Entry [%s] does not match this Person [%s]", //$NON-NLS-1$
							this, original));
		}
		String name = newName == null ? original.getName() : newName;
		String email = newEmail == null ? original.getEmailAddress() : newEmail;
		return new PersonIdent(name, email, original.getWhenAsInstant(),
				original.getZoneId());
	}

	/**
	 * @return a key which the oldEmail and the oldName (if present)
	 *         concatenated.
	 */
	public String getMapKeyForLookup() {
		return oldName == null ? oldEmail : oldEmail + ' ' + oldName;
	}
}
