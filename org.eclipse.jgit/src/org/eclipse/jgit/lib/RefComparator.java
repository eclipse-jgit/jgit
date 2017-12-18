/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2010, Google Inc.
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
	public int compare(final Ref o1, final Ref o2) {
		return compareTo(o1, o2);
	}

	/**
	 * Sorts the collection of refs, returning a new collection.
	 *
	 * @param refs
	 *            collection to be sorted
	 * @return sorted collection of refs
	 */
	public static Collection<Ref> sort(final Collection<Ref> refs) {
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
	public static int compareTo(final Ref o1, final Ref o2) {
		return o1.getName().compareTo(o2.getName());
	}
}
