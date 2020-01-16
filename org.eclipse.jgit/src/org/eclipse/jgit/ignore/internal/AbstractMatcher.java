/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore.internal;

/**
 * Base class for default methods as {@link #toString()} and such.
 * <p>
 * This class is immutable and thread safe.
 */
public abstract class AbstractMatcher implements IMatcher {

	final boolean dirOnly;

	final String pattern;

	/**
	 * @param pattern
	 *            string to parse
	 * @param dirOnly
	 *            true if this matcher should match only directories
	 */
	AbstractMatcher(String pattern, boolean dirOnly) {
		this.pattern = pattern;
		this.dirOnly = dirOnly;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return pattern;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return pattern.hashCode();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof AbstractMatcher))
			return false;
		AbstractMatcher other = (AbstractMatcher) obj;
		return dirOnly == other.dirOnly && pattern.equals(other.pattern);
	}
}
