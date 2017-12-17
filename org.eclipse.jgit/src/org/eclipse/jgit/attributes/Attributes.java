/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>,
 * Copyright (C) 2017, Obeo (mathieu.cartaud@obeo.fr)
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.lib.Constants;

/**
 * Represents a set of attributes for a path
 * <p>
 *
 * @since 4.2
 */
public final class Attributes {
	private final Map<String, Attribute> map = new LinkedHashMap<>();

	/**
	 * Creates a new instance
	 *
	 * @param attributes
	 *            a {@link org.eclipse.jgit.attributes.Attribute}
	 */
	public Attributes(Attribute... attributes) {
		if (attributes != null) {
			for (Attribute a : attributes) {
				put(a);
			}
		}
	}

	/**
	 * Whether the set of attributes is empty
	 *
	 * @return true if the set does not contain any attributes
	 */
	public boolean isEmpty() {
		return map.isEmpty();
	}

	/**
	 * Get the attribute with the given key
	 *
	 * @param key
	 *            a {@link java.lang.String} object.
	 * @return the attribute or null
	 */
	public Attribute get(String key) {
		return map.get(key);
	}

	/**
	 * Get all attributes
	 *
	 * @return all attributes
	 */
	public Collection<Attribute> getAll() {
		return new ArrayList<>(map.values());
	}

	/**
	 * Put an attribute
	 *
	 * @param a
	 *            an {@link org.eclipse.jgit.attributes.Attribute}
	 */
	public void put(Attribute a) {
		map.put(a.getKey(), a);
	}

	/**
	 * Remove attribute with given key
	 *
	 * @param key
	 *            an attribute name
	 */
	public void remove(String key) {
		map.remove(key);
	}

	/**
	 * Whether there is an attribute with this key
	 *
	 * @param key
	 *            key of an attribute
	 * @return true if the {@link org.eclipse.jgit.attributes.Attributes}
	 *         contains this key
	 */
	public boolean containsKey(String key) {
		return map.containsKey(key);
	}

	/**
	 * Return the state.
	 *
	 * @param key
	 *            key of an attribute
	 * @return the state (never returns <code>null</code>)
	 */
	public Attribute.State getState(String key) {
		Attribute a = map.get(key);
		return a != null ? a.getState() : Attribute.State.UNSPECIFIED;
	}

	/**
	 * Whether the attribute is set
	 *
	 * @param key
	 *            a {@link java.lang.String} object.
	 * @return true if the key is
	 *         {@link org.eclipse.jgit.attributes.Attribute.State#SET}, false in
	 *         all other cases
	 */
	public boolean isSet(String key) {
		return (getState(key) == State.SET);
	}

	/**
	 * Whether the attribute is unset
	 *
	 * @param key
	 *            a {@link java.lang.String} object.
	 * @return true if the key is
	 *         {@link org.eclipse.jgit.attributes.Attribute.State#UNSET}, false
	 *         in all other cases
	 */
	public boolean isUnset(String key) {
		return (getState(key) == State.UNSET);
	}

	/**
	 * Whether the attribute with the given key is unspecified
	 *
	 * @param key
	 *            a {@link java.lang.String} object.
	 * @return true if the key is
	 *         {@link org.eclipse.jgit.attributes.Attribute.State#UNSPECIFIED},
	 *         false in all other cases
	 */
	public boolean isUnspecified(String key) {
		return (getState(key) == State.UNSPECIFIED);
	}

	/**
	 * Is this a custom attribute
	 *
	 * @param key
	 *            a {@link java.lang.String} object.
	 * @return true if the key is
	 *         {@link org.eclipse.jgit.attributes.Attribute.State#CUSTOM}, false
	 *         in all other cases see {@link #getValue(String)} for the value of
	 *         the key
	 */
	public boolean isCustom(String key) {
		return (getState(key) == State.CUSTOM);
	}

	/**
	 * Get attribute value
	 *
	 * @param key
	 *            an attribute key
	 * @return the attribute value (may be <code>null</code>)
	 */
	public String getValue(String key) {
		Attribute a = map.get(key);
		return a != null ? a.getValue() : null;
	}

	/**
	 * Test if the given attributes implies to handle the related entry as a
	 * binary file (i.e. if the entry has an -merge or a merge=binary attribute)
	 * or if it can be content merged.
	 *
	 * @return <code>true</code> if the entry can be content merged,
	 *         <code>false</code> otherwise
	 * @since 4.9
	 */
	public boolean canBeContentMerged() {
		if (isUnset(Constants.ATTR_MERGE)) {
			return false;
		} else if (isCustom(Constants.ATTR_MERGE)
				&& getValue(Constants.ATTR_MERGE)
						.equals(Constants.ATTR_BUILTIN_BINARY_MERGER)) {
			return false;
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(getClass().getSimpleName());
		buf.append("["); //$NON-NLS-1$
		buf.append(" "); //$NON-NLS-1$
		for (Attribute a : map.values()) {
			buf.append(a.toString());
			buf.append(" "); //$NON-NLS-1$
		}
		buf.append("]"); //$NON-NLS-1$
		return buf.toString();
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return map.hashCode();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Attributes))
			return false;
		Attributes other = (Attributes) obj;
		return this.map.equals(other.map);
	}

}
