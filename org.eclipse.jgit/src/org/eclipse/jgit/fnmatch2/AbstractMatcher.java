/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.fnmatch2;

/**
 * Base class for default methods as {@link #toString()} and such.
 * <p>
 * This class is immutable and thread safe.
 * 
 * @since 3.5
 */
public abstract class AbstractMatcher implements IMatcher {

	final boolean dirOnly;
	final String pattern;

	/**
	 * @param pattern string to parse
	 * @param dirOnly true if this matcher should match only directories
	 */
	AbstractMatcher(String pattern, boolean dirOnly) {
		this.pattern = pattern;
		this.dirOnly = dirOnly;
	}

	@Override
	public String toString() {
		return pattern;
	}

	@Override
	public int hashCode() {
		return pattern.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AbstractMatcher)) {
			return false;
		}
		return pattern.equals(((AbstractMatcher) obj).pattern);
	}
}
