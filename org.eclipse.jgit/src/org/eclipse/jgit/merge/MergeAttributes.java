/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Provides {@link Attributes} for the trees during content-merge of a three way
 * merge
 *
 * @since 7.8
 */
public interface MergeAttributes {

	/**
	 * Attributes used when the {@link TreeWalk} has no
	 * {@link AttributesNodeProvider}
	 */
	static MergeAttributes NO_ATTRIBUTES = new MergeAttributes() {

		private static final Attributes EMPTY_ATTRIBUTES = new Attributes();

		@Override
		public Attributes base() {
			return EMPTY_ATTRIBUTES;
		}

		@Override
		public Attributes ours() {
			return EMPTY_ATTRIBUTES;
		}

		@Override
		public Attributes theirs() {
			return EMPTY_ATTRIBUTES;
		}
	};

	/**
	 * Retrieve the {@link Attributes} for the common base between ours and
	 * theirs
	 *
	 * @return {@link Attributes} for the common base between ours and theirs
	 */
	Attributes base();

	/**
	 * Retrieve the {@link Attributes} for the 'ours' side of the merge
	 *
	 * @return {@link Attributes} for the 'ours' side of the merge
	 */
	Attributes ours();

	/**
	 * Retrieve the {@link Attributes} for the 'theirs' side of the merge
	 *
	 * @return {@link Attributes} for the 'theirs' side of the merge
	 */
	Attributes theirs();

}