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
 * The <code>post-commit</code> hook implementation. This hook is run after the
 * commit was successfully executed.
 *
 * @since 4.5
 */
public class PostCommitHook extends GitHook<Void> {

	/** The post-commit hook name. */
	public static final String NAME = "post-commit"; //$NON-NLS-1$

	/**
	 * Constructor for PostCommitHook
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
	protected PostCommitHook(Repository repo, PrintStream outputStream) {
		super(repo, outputStream);
	}

	/**
	 * Constructor for PostCommitHook
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
	protected PostCommitHook(Repository repo, PrintStream outputStream,
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
