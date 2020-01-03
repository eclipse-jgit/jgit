/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.util.StringUtils;

/** A line in a Git {@link Config} file. */
class ConfigLine {
	/** The text content before entry. */
	String prefix;

	/** The section name for the entry. */
	String section;

	/** Subsection name. */
	String subsection;

	/** The key name. */
	String name;

	/** The value. */
	String value;

	/** The text content after entry. */
	String suffix;

	/** The source from which this line was included from. */
	String includedFrom;

	ConfigLine forValue(String newValue) {
		final ConfigLine e = new ConfigLine();
		e.prefix = prefix;
		e.section = section;
		e.subsection = subsection;
		e.name = name;
		e.value = newValue;
		e.suffix = suffix;
		e.includedFrom = includedFrom;
		return e;
	}

	boolean match(final String aSection, final String aSubsection,
			final String aKey) {
		return eqIgnoreCase(section, aSection)
				&& eqSameCase(subsection, aSubsection)
				&& eqIgnoreCase(name, aKey);
	}

	boolean match(String aSection, String aSubsection) {
		return eqIgnoreCase(section, aSection)
				&& eqSameCase(subsection, aSubsection);
	}

	private static boolean eqIgnoreCase(String a, String b) {
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
		return StringUtils.equalsIgnoreCase(a, b);
	}

	private static boolean eqSameCase(String a, String b) {
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		if (section == null)
			return "<empty>";
		StringBuilder b = new StringBuilder(section);
		if (subsection != null)
			b.append(".").append(subsection);
		if (name != null)
			b.append(".").append(name);
		if (value != null)
			b.append("=").append(value);
		return b.toString();
	}
}
