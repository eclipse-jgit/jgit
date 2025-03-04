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
 * <p>
 * When writing or updating patterns, assume the contents are syntactically
 * correct. Patterns can be simple and need not cover all syntactical corner
 * cases, as long as they are sufficiently permissive.
 *
 * @since 6.10.1
 */
@SuppressWarnings({"ImmutableEnumChecker", "nls"})
public enum DiffDriver {
	/**
	 * Built-in diff driver for <a href=
	 * "https://learn.microsoft.com/en-us/cpp/cpp/cpp-language-reference">c++</a>
	 */
	cpp(List.of(
			/* Jump targets or access declarations */
			"^[ \\t]*[A-Za-z_][A-Za-z_0-9]*:\\s*($|/[/*])"), List.of(
			/* functions/methods, variables, and compounds at top level */
			"^((::\\s*)?[A-Za-z_].*)$")),
	/**
	 * Built-in diff driver for <a href=
	 * "https://devicetree-specification.readthedocs.io/en/stable/source-language.html">device
	 * tree files</a>
	 */
	dts(List.of(";", "="), List.of(
			/* lines beginning with a word optionally preceded by '&' or the root */
			"^[ \\t]*((/[ \\t]*\\{|&?[a-zA-Z_]).*)")),
	/**
	 * Built-in diff driver for <a href=
	 * "https://docs.oracle.com/javase/specs/jls/se21/html/index.html">java</a>
	 */
	java(List.of(
			"^[ \\t]*(catch|do|for|if|instanceof|new|return|switch|throw|while)"),
			List.of(
					/* Class, enum, interface, and record declarations */
					"^[ \\t]*(([a-z-]+[ \\t]+)*(class|enum|interface|record)[ \\t]+.*)$",
					/* Method definitions; note that constructor signatures are not */
					/* matched because they are indistinguishable from method calls. */
					"^[ \\t]*(([A-Za-z_<>&\\]\\[][?&<>.,A-Za-z_0-9]*[ \\t]+)+[A-Za-z_]"
							+ "[A-Za-z_0-9]*[ \\t]*\\([^;]*)$")),
	/**
	 * Built-in diff driver for
	 * <a href="https://docs.python.org/3/reference/index.html">python</a>
	 */
	python(List.of("^[ \\t]*((class|(async[ \\t]+)?def)[ \\t].*)$")),
	/**
	 * Built-in diff driver for
	 * <a href="https://doc.rust-lang.org/reference/introduction.html">rust</a>
	 */
	rust(List.of("^[\\t ]*((pub(\\([^\\)]+\\))?[\\t ]+)?"
			+ "((async|const|unsafe|extern([\\t ]+\"[^\"]+\"))[\\t ]+)?"
			+ "(struct|enum|union|mod|trait|fn|impl|macro_rules!)[< \\t]+[^;]*)$"));

	private final List<Pattern> negatePatterns;

	private final List<Pattern> matchPatterns;

	DiffDriver(List<String> negate, List<String> match, int flags) {
		if (negate != null) {
			this.negatePatterns = negate.stream()
					.map(r -> Pattern.compile(r, flags))
					.collect(Collectors.toList());
		} else {
			this.negatePatterns = null;
		}
		this.matchPatterns = match.stream().map(r -> Pattern.compile(r, flags))
				.collect(Collectors.toList());
	}

	DiffDriver(List<String> match) {
		this(null, match, 0);
	}

	DiffDriver(List<String> negate, List<String> match) {
		this(negate, match, 0);
	}

	/**
	 * Returns the list of patterns used to exclude certain lines from being
	 * considered as function names.
	 *
	 * @return the list of negate patterns
	 */
	public List<Pattern> getNegatePatterns() {
		return negatePatterns;
	}

	/**
	 * Returns the list of patterns used to match lines for potential function
	 * names.
	 *
	 * @return the list of match patterns
	 */
	public List<Pattern> getMatchPatterns() {
		return matchPatterns;
	}
}
