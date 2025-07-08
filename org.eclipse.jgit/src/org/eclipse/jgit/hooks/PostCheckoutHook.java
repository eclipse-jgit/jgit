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
import java.util.Objects;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * The <code>post-checkout</code> hook implementation. This hook is run after
 * the checkout was successfully executed.
 *
 * @since 7.4
 */
public class PostCheckoutHook extends GitHook<Void> {

	/** The post-commit hook name. */
	public static final String NAME = "post-checkout"; //$NON-NLS-1$

	/**
	 * The ref of the previous HEAD.
	 */
	private Ref prevHead;

	/**
	 * The ref of the new HEAD.
	 */
	private Ref newHead;

	/**
	 * Indicating if the checkout command is a branch checkout. If changing
	 * branches this flag is 1. If checking our files (retrieving files from the
	 * index) this flag is 0.
	 */
	private boolean isCheckoutBranch;

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

	@Override
	protected String[] getParameters() {
		return new String[] {
				Objects.requireNonNullElse(prevHead.getObjectId(),
						ObjectId.zeroId()).name(),
				Objects.requireNonNullElse(newHead.getObjectId(),
						ObjectId.zeroId()).name(),
				String.valueOf(isCheckoutBranch ? 1 : 0) };
	}

	/**
	 * @param prevHead
	 *            The Ref of the previous HEAD
	 * @return {@code this} for convenience.
	 */
	public PostCheckoutHook setPreviousHead(Ref prevHead) {
		this.prevHead = prevHead;
		return this;
	}

	/**
	 * @param newHead
	 *            The Ref of the new HEAD
	 * @return {@code this} for convenience.
	 */
	public PostCheckoutHook setNewHead(Ref newHead) {
		this.newHead = newHead;
		return this;
	}

	/**
	 * @param isBranchCheckout
	 *            Indicates if the checkout command is a branch checkout. This
	 *            flag is 1, if a branch checkout is executed. This flag is 0,
	 *            if a files are retrieved from the index
	 * @return {@code this} for convenience.
	 */
	public PostCheckoutHook setIsBranchCheckout(boolean isBranchCheckout) {
		this.isCheckoutBranch = isBranchCheckout;
		return this;
	}

}
