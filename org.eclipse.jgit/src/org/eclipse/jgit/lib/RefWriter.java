/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Writes out refs to the {@link org.eclipse.jgit.lib.Constants#INFO_REFS} and
 * {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} files.
 *
 * This class is abstract as the writing of the files must be handled by the
 * caller. This is because it is used by transport classes as well.
 *
 * @deprecated since 7.7, use {@link PackedRefsWriter} instead.
 */
@Deprecated(since = "7.7")
public abstract class RefWriter extends PackedRefsWriter {

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	@Deprecated(since = "7.7")
	public RefWriter(Collection<Ref> refs) {
		super(RefComparator.sort(refs), EnumSet.of(PackedRefsTrait.SORTED));
		if (containsAnyPeeledRef(this.refs)) {
			this.traits.add(PackedRefsTrait.PEELED);
		}
	}

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	@Deprecated(since = "7.7")
	public RefWriter(Map<String, Ref> refs) {
		super(
			refs instanceof RefMap
				? refs.values()
				: RefComparator.sort(refs.values()),
			EnumSet.of(PackedRefsTrait.SORTED)
		);
		if (containsAnyPeeledRef(this.refs)) {
			this.traits.add(PackedRefsTrait.PEELED);
		}
	}

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	@Deprecated(since = "7.7")
	public RefWriter(RefList<Ref> refs) {
		super(refs.asList(), EnumSet.of(PackedRefsTrait.SORTED));
		if (containsAnyPeeledRef(this.refs)) {
			this.traits.add(PackedRefsTrait.PEELED);
		}
	}

	private boolean containsAnyPeeledRef(Collection<Ref> refs) {
		for (Ref r : refs) {
			if (r.getStorage().isPacked() && r.isPeeled()) {
				return true;
			}
		}
		return false;
	}
}
