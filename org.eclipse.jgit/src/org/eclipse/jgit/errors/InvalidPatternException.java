/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de>
 * Copyright (C) 2009, Vasyl' Vavrychuk <vvavrychuk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

/**
 * Thrown when a pattern passed in an argument was wrong.
 */
public class InvalidPatternException extends Exception {
	private static final long serialVersionUID = 1L;

	private final String pattern;

	/**
	 * Constructor for InvalidPatternException
	 *
	 * @param message
	 *            explains what was wrong with the pattern.
	 * @param pattern
	 *            the invalid pattern.
	 */
	public InvalidPatternException(String message, String pattern) {
		super(message);
		this.pattern = pattern;
	}

	/**
	 * Constructor for InvalidPatternException
	 *
	 * @param message
	 *            explains what was wrong with the pattern.
	 * @param pattern
	 *            the invalid pattern.
	 * @param cause
	 *            the cause.
	 * @since 4.10
	 */
	public InvalidPatternException(String message, String pattern,
			Throwable cause) {
		this(message, pattern);
		initCause(cause);
	}

	/**
	 * Get the invalid pattern
	 *
	 * @return the invalid pattern.
	 */
	public String getPattern() {
		return pattern;
	}

}
