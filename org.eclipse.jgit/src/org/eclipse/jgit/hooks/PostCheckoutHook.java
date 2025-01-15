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
import org.eclipse.jgit.util.ProcessResult;

/**
 * The <code>post-checkout</code> hook implementation. This hook is run after
 * the checkout was successfully executed.
 *
 * @since 7.2
 */
public class PostCheckoutHook extends GitHook<Void> {

	/** The post-commit hook name. */
	public static final String NAME = "post-checkout"; //$NON-NLS-1$

	/**
	 * Constructor for PostCheckoutHook
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
	protected PostCheckoutHook(Repository repo, PrintStream outputStream) {
		super(repo, outputStream);
	}

	/**
	 * Constructor for PostCheckoutHook
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
	protected PostCheckoutHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	@Override
	public Void call() throws IOException, AbortedByHookException {
		doRun();
		return null;
	}

	@Override
	public String getHookName() {
		return NAME;
	}

	/**
	 * Overwrites the default implementation to never throw an
	 * {@link AbortedByHookException}, as the checkout has already been done and
	 * the exit code of the post-checkout hook has no effect.
	 */
	@Override
	protected void handleError(String message, ProcessResult result)
			throws AbortedByHookException {
		// Do nothing as the exit code of the post-checkout hook has no effect.
	}

}
