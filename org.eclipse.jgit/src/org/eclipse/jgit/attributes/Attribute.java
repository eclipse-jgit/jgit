/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

/**
 * Represents an attribute.
 * <p>
 * According to the man page, an attribute can have the following states:
 * <ul>
 * <li>Set - represented by
 * {@link org.eclipse.jgit.attributes.Attribute.State#SET}</li>
 * <li>Unset - represented by
 * {@link org.eclipse.jgit.attributes.Attribute.State#UNSET}</li>
 * <li>Set to a value - represented by
 * {@link org.eclipse.jgit.attributes.Attribute.State#CUSTOM}</li>
 * <li>Unspecified - used to revert an attribute . This is crucial in order to
 * mark an attribute as unspecified in the attributes map and thus preventing
 * following (with lower priority) nodes from setting the attribute to a value
 * at all</li>
 * </ul>
 *
 * @since 3.7
 */
public final class Attribute {

	/**
	 * The attribute value state
	 * see also https://www.kernel.org/pub/software/scm/git/docs/gitattributes.html
	 */
	public static enum State {
		/** the attribute is set */
		SET,

		/** the attribute is unset */
		UNSET,

		/**
		 * the attribute appears as if it would not be defined at all
		 *
		 * @since 4.2
		 */
		UNSPECIFIED,

		/** the attribute is set to a custom value */
		CUSTOM
	}

	private final String key;
	private final State state;
	private final String value;

	/**
	 * Creates a new instance
	 *
	 * @param key
	 *            the attribute key. Should not be <code>null</code>.
	 * @param state
	 *            the attribute state. It should be either
	 *            {@link org.eclipse.jgit.attributes.Attribute.State#SET} or
	 *            {@link org.eclipse.jgit.attributes.Attribute.State#UNSET}. In
	 *            order to create a custom value attribute prefer the use of
	 *            {@link #Attribute(String, String)} constructor.
	 */
	public Attribute(String key, State state) {
		this(key, state, null);
	}

	private Attribute(String key, State state, String value) {
		if (key == null)
			throw new NullPointerException(
					"The key of an attribute should not be null"); //$NON-NLS-1$
		if (state == null)
			throw new NullPointerException(
					"The state of an attribute should not be null"); //$NON-NLS-1$

		this.key = key;
		this.state = state;
		this.value = value;
	}

	/**
	 * Creates a new instance.
	 *
	 * @param key
	 *            the attribute key. Should not be <code>null</code>.
	 * @param value
	 *            the custom attribute value
	 */
	public Attribute(String key, String value) {
		this(key, State.CUSTOM, value);
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Attribute))
			return false;
		Attribute other = (Attribute) obj;
		if (!key.equals(other.key))
			return false;
		if (state != other.state)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	/**
	 * Get key
	 *
	 * @return the attribute key (never returns <code>null</code>)
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Return the state.
	 *
	 * @return the state (never returns <code>null</code>)
	 */
	public State getState() {
		return state;
	}

	/**
	 * Get value
	 *
	 * @return the attribute value (may be <code>null</code>)
	 */
	public String getValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + key.hashCode();
		result = prime * result + state.hashCode();
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		switch (state) {
		case SET:
			return key;
		case UNSET:
			return "-" + key; //$NON-NLS-1$
		case UNSPECIFIED:
			return "!" + key; //$NON-NLS-1$
		case CUSTOM:
		default:
			return key + "=" + value; //$NON-NLS-1$
		}
	}
}
