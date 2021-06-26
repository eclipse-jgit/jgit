/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.internal.JGitText;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;

/**
 * Thrown when the search for reuse phase times out.
 *
 * @since 5.13
 */
public class SearchForReuseTimeout extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a search for reuse timeout error.
	 *
	 * @param timeout
	 *            time exceeded during the search for reuse phase.
	 */
	public SearchForReuseTimeout(Duration timeout) {
		super(MessageFormat.format(JGitText.get().searchForReuseTimeout,
				Long.valueOf(timeout.getSeconds())));
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}