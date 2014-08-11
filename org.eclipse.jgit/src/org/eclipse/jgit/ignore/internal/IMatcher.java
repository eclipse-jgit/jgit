/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.ignore.internal;

/**
 * Generic string matcher
 *
 * @since 3.5
 */
public interface IMatcher {

	/**
	 * Matches entire given string
	 * 
	 * @param path
	 *            string which is not null, but might be empty
	 * @param assumeDirectory
	 *            true to assume this path as directory (even if it doesn't end
	 *            with a slash)
	 * @return true if this matcher pattern matches given string
	 */
	boolean matches(String path, boolean assumeDirectory);

	/**
	 * Matches only part of given string
	 *
	 * @param segment
	 *            string which is not null, but might be empty
	 * @param startIncl
	 *            start index, inclusive
	 * @param endExcl
	 *            end index, exclusive
	 * @param assumeDirectory
	 *            true to assume this path as directory (even if it doesn't end
	 *            with a slash)
	 * @return true if this matcher pattern matches given string
	 */
	boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory);
}
