/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2009, Google, Inc.
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

	ConfigLine forValue(final String newValue) {
		final ConfigLine e = new ConfigLine();
		e.prefix = prefix;
		e.section = section;
		e.subsection = subsection;
		e.name = name;
		e.value = newValue;
		e.suffix = suffix;
		return e;
	}

	boolean match(final String aSection, final String aSubsection,
			final String aKey) {
		return eqIgnoreCase(section, aSection)
				&& eqSameCase(subsection, aSubsection)
				&& eqIgnoreCase(name, aKey);
	}

	boolean match(final String aSection, final String aSubsection) {
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
