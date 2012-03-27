/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2012, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com>
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

import static org.eclipse.jgit.util.StringUtils.compareIgnoreCase;
import static org.eclipse.jgit.util.StringUtils.compareWithCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ConfigSnapshot {
	final List<ConfigLine> entryList;
	final Map<Object, Object> cache;
	final ConfigSnapshot baseState;
	volatile List<ConfigLine> sorted;

	ConfigSnapshot(List<ConfigLine> entries, ConfigSnapshot base) {
		entryList = entries;
		cache = new ConcurrentHashMap<Object, Object>(16, 0.75f, 1);
		baseState = base;
	}

	String[] get(String section, String subsection, String name) {
		List<ConfigLine> s = sorted();
		int idx = find(s, section, subsection, name);
		if (idx < 0)
			return null;
		int end = end(s, idx, section, subsection, name);
		String[] r = new String[end - idx];
		for (int i = 0; idx < end;)
			r[i++] = s.get(idx++).value;
		return r;
	}

	private int find(List<ConfigLine> s, String s1, String s2, String name) {
		int low = 0;
		int high = s.size();
		while (low < high) {
			int mid = (low + high) >>> 1;
			ConfigLine e = s.get(mid);
			int cmp = compare2(
					s1, s2, name,
					e.section, e.subsection, e.name);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0)
				return first(s, mid, s1, s2, name);
			else
				low = mid + 1;
		}
		return -(low + 1);
	}

	private int first(List<ConfigLine> s, int i, String s1, String s2, String n) {
		while (0 < i) {
			if (s.get(i - 1).match(s1, s2, n))
				i--;
			else
				return i;
		}
		return i;
	}

	private int end(List<ConfigLine> s, int i, String s1, String s2, String n) {
		while (i < s.size()) {
			if (s.get(i).match(s1, s2, n))
				i++;
			else
				return i;
		}
		return i;
	}

	private List<ConfigLine> sorted() {
		List<ConfigLine> r = sorted;
		if (r == null)
			sorted = r = sort(entryList);
		return r;
	}

	private static List<ConfigLine> sort(List<ConfigLine> in) {
		List<ConfigLine> sorted = new ArrayList<ConfigLine>(in.size());
		for (ConfigLine line : in) {
			if (line.section != null && line.name != null)
				sorted.add(line);
		}
		Collections.sort(sorted, new LineComparator());
		return sorted;
	}

	private static int compare2(
			String aSection, String aSubsection, String aName,
			String bSection, String bSubsection, String bName) {
		int c = compareIgnoreCase(aSection, bSection);
		if (c != 0)
			return c;

		if (aSubsection == null && bSubsection != null)
			return -1;
		if (aSubsection != null && bSubsection == null)
			return 1;
		if (aSubsection != null) {
			c = compareWithCase(aSubsection, bSubsection);
			if (c != 0)
				return c;
		}

		return compareIgnoreCase(aName, bName);
	}

	private static class LineComparator implements Comparator<ConfigLine> {
		public int compare(ConfigLine a, ConfigLine b) {
			return compare2(
					a.section, a.subsection, a.name,
					b.section, b.subsection, b.name);
		}
	}
}
