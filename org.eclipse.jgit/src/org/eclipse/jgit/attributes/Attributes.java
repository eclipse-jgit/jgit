/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr>
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
package org.eclipse.jgit.attributes;

import java.util.Set;

import org.eclipse.jgit.attributes.Attribute.State;

/**
 * Util class to handle attributes.
 *
 * @author <a href="mailto:arthur.daussy@obeo.fr">Arthur Daussy</a>
 *
 */
public class Attributes {

	private static final String IDENT_ATTR_NAME = "ident"; //$NON-NLS-1$

	private static Attribute IDENT_SET = new Attribute(IDENT_ATTR_NAME,
			State.SET);

	private static Attribute IDENT_UNSET = new Attribute(IDENT_ATTR_NAME,
			State.UNSET);

	Attributes() {
	}

	/**
	 * Checks that the list contains the ident attribute with a
	 * {@link State#SET} value.
	 *
	 * @param attributes
	 *            list of attributes to checks (can be <code>null</code>).
	 * @return <code>true</code> if the list contains the ident attribute with a
	 *         {@link State#SET} value, <code>false</code> otherwise.
	 * @since 3.6
	 */
	public static boolean hasIdentSet(Set<Attribute> attributes) {
		return attributes != null && attributes.contains(IDENT_SET);
	}

	/**
	 * Returns <code>true</code> if the list contains an "ident" attribute.
	 *
	 * @param attributes
	 * @return <code>true</code> if the list constains an "ident" attribute,
	 *         <code>false</code> otherwise.
	 * @since 3.6
	 */
	public static boolean hasIdent(Set<Attribute> attributes) {
		if (attributes == null)
			return false;
		return attributes.contains(IDENT_UNSET)
				|| attributes.contains(IDENT_SET);
	}

}
