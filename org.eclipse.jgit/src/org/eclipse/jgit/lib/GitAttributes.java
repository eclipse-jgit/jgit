/*
 * Copyright (C) 2017, Obeo (mathieu.cartaud@obeo.fr)
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
package org.eclipse.jgit.lib;

import org.eclipse.jgit.attributes.Attributes;

/**
 * Constants and helper methods to use with the .gitattributes files.
 *
 * @since 4.9
 */
public class GitAttributes {

	/** Merge attribute. */
	public static final String MERGE = "merge"; //$NON-NLS-1$

	/** Binary value for custom merger. */
	public static final String BUILTIN_BINARY_MERGER = "binary"; //$NON-NLS-1$

	/**
	 * Test if the given attributes implies to handle to related entry as a
	 * binary file (i.e. if the entry has an -merge or a merge=binary
	 * attribute).
	 *
	 * @param attributes
	 *            the attributes defined for this entry
	 * @return <code>true</code> if the entry must be handled like a binary one,
	 *         <code>false</code> otherwise
	 */
	public static boolean isBinary(Attributes attributes) {
		if (attributes != null) {
			if (attributes.isUnset(GitAttributes.MERGE)) {
				return true;
			} else if (attributes.isCustom(GitAttributes.MERGE)
					&& attributes.getValue(GitAttributes.MERGE).equals(
							GitAttributes.BUILTIN_BINARY_MERGER)) {
				return true;
			}
		}
		return false;
	}

}
