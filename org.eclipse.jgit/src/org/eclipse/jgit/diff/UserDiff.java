/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Built-in drivers for various languages, sorted by name. These drivers will be
 * used to determine function names for a hunk.
 *
 * When writing or updating patterns, assume the contents are syntactically
 * correct. Patterns can be simple and need not cover all syntactical corner
 * cases, as long as they are sufficiently permissive.
 */
public class UserDiff {
	private static final Driver[] BUILTIN_DRIVERS = { new Driver("cpp", List.of(
			/* Jump targets or access declarations */
			"^[ \\t]*[A-Za-z_][A-Za-z_0-9]*:\\s*($|/[/*])"), List.of(
			/* functions/methods, variables, and compounds at top level */
			"^((::\\s*)?[A-Za-z_].*)$")),
			new Driver("dts", List.of(";", "="), List.of(
					/* lines beginning with a word optionally preceded by '&' or the root */
					"^[ \\t]*((/[ \\t]*\\{|&?[a-zA-Z_]).*)")),
			new Driver("java",
					List.of("^[ \\t]*(catch|do|for|if|instanceof|new|return|switch|throw|while)"),
					List.of(
							/* Class, enum, interface, and record declarations */
							"^[ \\t]*(([a-z-]+[ \\t]+)*(class|enum|interface|record)[ \\t]+.*)$",
							/* Method definitions; note that constructor signatures are not */
							/* matched because they are indistinguishable from method calls. */
							"^[ \\t]*(([A-Za-z_<>&\\]\\[][?&<>.,A-Za-z_0-9]*[ \\t]+)+[A-Za-z_][A-Za-z_0-9]*[ \\t]*\\([^;]*)$")),
			new Driver("python",
					List.of("^[ \\t]*((class|(async[ \\t]+)?def)[ \\t].*)$")),
			new Driver("rust",
					List.of("^[\\t ]*((pub(\\([^\\)]+\\))?[\\t ]+)?((async|const|unsafe|extern([\\t ]+\"[^\"]+\"))[\\t ]+)?(struct|enum|union|mod|trait|fn|impl|macro_rules!)[< \\t]+[^;]*)$")) };

	/**
	 * Retrieves a driver by its name.
	 *
	 * @param name
	 * 		the name of the driver
	 * @return the driver with the specified name, or null if no such driver
	 * 		exists
	 */
	public static Driver getDriverByName(String name) {
		if (name == null) {
			return null;
		}
		for (Driver driver : UserDiff.BUILTIN_DRIVERS) {
			if (driver.name.equals(name)) {
				return driver;
			}
		}
		return null;
	}

	/**
	 * Represents a diff driver with patterns for matching and negating lines.
	 */
	public static class Driver {
		String name;

		List<Pattern> negatePatterns;

		List<Pattern> matchPatterns;

		/**
		 * Create a new Driver.
		 *
		 * @param name
		 * 		the name of the driver
		 * @param negate
		 * 		the list of patterns to negate
		 * @param match
		 * 		the list of patterns to match
		 * @param flags
		 * 		the flags for pattern compilation
		 */
		public Driver(String name, List<String> negate, List<String> match,
				int flags) {
			this.name = name;
			if (negate != null) {
				this.negatePatterns = negate.stream()
						.map(r -> Pattern.compile(r, flags))
						.collect(Collectors.toList());
			}
			this.matchPatterns = match.stream()
					.map(r -> Pattern.compile(r, flags))
					.collect(Collectors.toList());
		}

		/**
		 * Create a new Driver with match patterns only and no negate patterns.
		 *
		 * @param name
		 * 		the name of the driver
		 * @param match
		 * 		the list of patterns to match
		 */
		public Driver(String name, List<String> match) {
			this(name, null, match, 0);
		}

		/**
		 * Create a new Driver with negate and match patterns.
		 *
		 * @param name
		 * 		the name of the driver
		 * @param negate
		 * 		the list of patterns to negate
		 * @param match
		 * 		the list of patterns to match
		 */
		public Driver(String name, List<String> negate, List<String> match) {
			this(name, negate, match, 0);
		}
	}
}
