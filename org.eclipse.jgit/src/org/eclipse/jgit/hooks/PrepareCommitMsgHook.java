/*
 * Copyright (c) 2026 Vector Informatik GmbH and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.hooks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * The <code>prepare-commit-msg</code> hook implementation. This hook is run
 * after the default commit message has been prepared and before the editor is
 * started (or, in JGit, before the commit-msg hook). It can modify the commit
 * message file and can abort the commit by exiting non-zero.
 * <p>
 * The hook receives two arguments: the path to the COMMIT_EDITMSG file relative
 * to the work tree and the source of the message (see {@link MessageSource};
 * omitted if there is none).
 *
 * @since 8.0
 */
public class PrepareCommitMsgHook extends GitHook<String> {

	/**
	 * Constant indicating the name of the prepare-commit-msg hook.
	 */
	public static final String NAME = "prepare-commit-msg"; //$NON-NLS-1$

	/**
	 * The source of the commit message passed to the hook.
	 */
	public enum MessageSource {
		/** The message was given explicitly. */
		MESSAGE("message"), //$NON-NLS-1$
		/** The commit concludes a merge. */
		MERGE("merge"), //$NON-NLS-1$
		/** The message stems from a squash. */
		SQUASH("squash"); //$NON-NLS-1$

		private final String value;

		private MessageSource(String value) {
			this.value = value;
		}

		/**
		 * @return the argument value passed to the hook script
		 */
		String getValue() {
			return value;
		}
	}

	private String commitMessage;

	private MessageSource messageSource;

	/**
	 * Constructor for PrepareCommitMsgHook
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
	protected PrepareCommitMsgHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	@Override
	public String call() throws IOException, AbortedByHookException {
		if (commitMessage == null) {
			throw new IllegalStateException();
		}
		if (getCommitEditMessageFilePath() != null) {
			getRepository().writeCommitEditMsg(commitMessage);
			doRun();
			commitMessage = getRepository().readCommitEditMsg();
		}
		return commitMessage;
	}

	@Override
	public String getHookName() {
		return NAME;
	}

	@Override
	protected String[] getParameters() {
		List<String> params = new ArrayList<>(2);
		params.add(getCommitEditMessageFilePath());
		if (messageSource != null) {
			params.add(messageSource.getValue());
		}
		return params.toArray(new String[0]);
	}

	private String getCommitEditMessageFilePath() {
		File gitDir = getRepository().getDirectory();
		if (gitDir == null) {
			return null;
		}
		return Repository.stripWorkDir(getRepository().getWorkTree(),
				new File(gitDir, Constants.COMMIT_EDITMSG));
	}

	/**
	 * It is mandatory to call this method with a non-null value before actually
	 * calling the hook.
	 *
	 * @param commitMessage
	 *            The commit message before the hook has run.
	 * @return {@code this} for convenience.
	 */
	public PrepareCommitMsgHook setCommitMessage(String commitMessage) {
		this.commitMessage = commitMessage;
		return this;
	}

	/**
	 * Set the source of the commit message.
	 *
	 * @param messageSource
	 *            the source of the message; may be {@code null}
	 * @return {@code this} for convenience.
	 */
	public PrepareCommitMsgHook setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
		return this;
	}
}
