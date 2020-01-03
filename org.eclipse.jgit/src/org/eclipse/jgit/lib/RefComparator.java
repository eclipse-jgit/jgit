/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Util for sorting (or comparing) Ref instances by name.
 * <p>
 * Useful for command line tools or writing out refs to file.
 */
public class RefComparator implements Comparator<Ref> {

	/** Singleton instance of RefComparator */
	public static final RefComparator INSTANCE = new RefComparator();

	/** {@inheritDoc} */
	@Override
	public int compare(Ref o1, Ref o2) {
		return compareTo(o1, o2);
	}

	/**
	 * Sorts the collection of refs, returning a new collection.
	 *
	 * @param refs
	 *            collection to be sorted
	 * @return sorted collection of refs
	 */
	public static Collection<Ref> sort(Collection<Ref> refs) {
		final List<Ref> r = new ArrayList<>(refs);
		Collections.sort(r, INSTANCE);
		return r;
	}

	/**
	 * Compare a reference to a name.
	 *
	 * @param o1
	 *            the reference instance.
	 * @param o2
	 *            the name to compare to.
	 * @return standard Comparator result of &lt; 0, 0, &gt; 0.
	 */
	public static int compareTo(Ref o1, String o2) {
		return o1.getName().compareTo(o2);
	}

	/**
	 * Compare two references by name.
	 *
	 * @param o1
	 *            the reference instance.
	 * @param o2
	 *            the other reference instance.
	 * @return standard Comparator result of &lt; 0, 0, &gt; 0.
	 */
	public static int compareTo(Ref o1, Ref o2) {
		return o1.getName().compareTo(o2.getName());
	}
}
