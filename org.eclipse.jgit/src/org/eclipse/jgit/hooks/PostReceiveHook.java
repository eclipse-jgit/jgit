/*
 * Copyright (C) 2026, NVIDIA Corporation and others
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
import java.util.Collection;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ProcessResult;

/**
 * The <code>post-receive</code> hook implementation. This hook is run on the
 * receiving side after all ref updates of a push have been processed.
 * <p>
 * The hook receives one line per successfully updated ref on stdin, in the
 * format {@code <old-id> <new-id> <ref-name>}, separated by single spaces and
 * terminated by a newline, as with C git.
 *
 * @since 8.0
 */
public class PostReceiveHook extends GitHook<Void> {

	/** The post-receive hook name. */
	public static final String NAME = "post-receive"; //$NON-NLS-1$

	private String commands;

	/**
	 * Constructor for PostReceiveHook
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
	protected PostReceiveHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	@Override
	public Void call() throws IOException, AbortedByHookException {
		if (commands == null || commands.isEmpty()) {
			return null;
		}
		doRun();
		return null;
	}

	@Override
	public String getHookName() {
		return NAME;
	}

	@Override
	protected String getStdinArgs() {
		return commands;
	}

	/**
	 * Set the ref updates to pass to the hook. The hook is not run if this is
	 * empty, as C git does not run it when no ref was updated.
	 *
	 * @param cmds
	 *            the successfully executed ref updates
	 */
	public void setCommands(Collection<ReceiveCommand> cmds) {
		StringBuilder b = new StringBuilder();
		for (ReceiveCommand cmd : cmds) {
			b.append(cmd.getOldId().name());
			b.append(' ');
			b.append(cmd.getNewId().name());
			b.append(' ');
			b.append(cmd.getRefName());
			b.append('\n');
		}
		commands = b.toString();
	}

	/**
	 * Overwrites the default implementation to never throw an
	 * {@link AbortedByHookException}, as the refs have already been updated
	 * and the exit code of the post-receive hook has no effect.
	 */
	@Override
	protected void handleError(String message, ProcessResult result)
			throws AbortedByHookException {
		// Do nothing as the exit code of the post-receive hook has no effect.
	}
}
