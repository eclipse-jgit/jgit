/*
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

	private TagOpt(final String o) {
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
	public static TagOpt fromOption(final String o) {
		if (o == null || o.length() == 0)
			return AUTO_FOLLOW;
		for (final TagOpt tagopt : values()) {
			if (tagopt.option().equals(o))
				return tagopt;
		}
		throw new IllegalArgumentException(MessageFormat.format(JGitText.get().invalidTagOption, o));
	}
}
