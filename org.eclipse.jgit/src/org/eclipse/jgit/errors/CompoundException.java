/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;

/**
 * An exception detailing multiple reasons for failure.
 */
public class CompoundException extends Exception {
	private static final long serialVersionUID = 1L;

	private static String format(Collection<Throwable> causes) {
		final StringBuilder msg = new StringBuilder();
		msg.append(JGitText.get().failureDueToOneOfTheFollowing);
		for (Throwable c : causes) {
			msg.append("  "); //$NON-NLS-1$
			msg.append(c.getMessage());
			msg.append("\n"); //$NON-NLS-1$
		}
		return msg.toString();
	}

	private final List<Throwable> causeList;

	/**
	 * Constructs an exception detailing many potential reasons for failure.
	 *
	 * @param why
	 *            Two or more exceptions that may have been the problem.
	 */
	public CompoundException(Collection<Throwable> why) {
		super(format(why));
		causeList = Collections.unmodifiableList(new ArrayList<>(why));
	}

	/**
	 * Get the complete list of reasons why this failure happened.
	 *
	 * @return unmodifiable collection of all possible reasons.
	 */
	public List<Throwable> getAllCauses() {
		return causeList;
	}
}
