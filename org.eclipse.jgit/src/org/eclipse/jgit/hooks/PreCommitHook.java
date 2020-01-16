/*
 * Copyright (C) 2015 Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.hooks;

import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Repository;

/**
 * The <code>pre-commit</code> hook implementation. This hook is run before the
 * commit and can reject the commit.
 *
 * @since 4.0
 */
public class PreCommitHook extends GitHook<Void> {

	/** The pre-commit hook name. */
	public static final String NAME = "pre-commit"; //$NON-NLS-1$

	/**
	 * Constructor for PreCommitHook
	 * <p>
	 * This constructor will use the default error stream.
	 * </p>
	 *
	 * @param repo
	 *            The repository
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 */
	protected PreCommitHook(Repository repo, PrintStream outputStream) {
		super(repo, outputStream);
	}

	/**
	 * Constructor for PreCommitHook
	 *
	 * @param repo
	 *            The repository
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 * @param errorStream
	 *            The error stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.err}.
	 * @since 5.6
	 */
	protected PreCommitHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	/** {@inheritDoc} */
	@Override
	public Void call() throws IOException, AbortedByHookException {
		doRun();
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public String getHookName() {
		return NAME;
	}

}
