/*
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Specification of annotated tag behavior during fetch.
 */
public enum TagOpt {
	/**
	 * Automatically follow tags if we fetch the thing they point at.
	 * <p>
	 * This is the default behavior and tries to balance the benefit of having
	 * an annotated tag against the cost of possibly objects that are only on
	 * branches we care nothing about. Annotated tags are fetched only if we can
	 * prove that we already have (or will have when the fetch completes) the
	 * object the annotated tag peels (dereferences) to.
	 */
	AUTO_FOLLOW(""), //$NON-NLS-1$

	/**
	 * Never fetch tags, even if we have the thing it points at.
	 * <p>
	 * This option must be requested by the user and always avoids fetching
	 * annotated tags. It is most useful if the location you are fetching from
	 * publishes annotated tags, but you are not interested in the tags and only
	 * want their branches.
	 */
	NO_TAGS("--no-tags"), //$NON-NLS-1$

	/**
	 * Always fetch tags, even if we do not have the thing it points at.
	 * <p>
	 * Unlike {@link #AUTO_FOLLOW} the tag is always obtained. This may cause
	 * hundreds of megabytes of objects to be fetched if the receiving
	 * repository does not yet have the necessary dependencies.
	 */
	FETCH_TAGS("--tags"); //$NON-NLS-1$

	private final String option;

	private TagOpt(String o) {
		option = o;
	}

	/**
	 * Get the command line/configuration file text for this value.
	 *
	 * @return text that appears in the configuration file to activate this.
	 */
	public String option() {
		return option;
	}

	/**
	 * Convert a command line/configuration file text into a value instance.
	 *
	 * @param o
	 *            the configuration file text value.
	 * @return the option that matches the passed parameter.
	 */
	public static TagOpt fromOption(String o) {
		if (o == null || o.length() == 0)
			return AUTO_FOLLOW;
		for (TagOpt tagopt : values()) {
			if (tagopt.option().equals(o))
				return tagopt;
		}
		throw new IllegalArgumentException(MessageFormat.format(JGitText.get().invalidTagOption, o));
	}
}
