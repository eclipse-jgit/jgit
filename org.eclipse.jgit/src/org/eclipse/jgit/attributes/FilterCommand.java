/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An abstraction for JGit's builtin implementations for hooks and filters.
 * Instead of spawning an external processes to start a filter/hook and to pump
 * data from/to stdin/stdout these builtin commmands may be used. They are
 * constructed by {@link org.eclipse.jgit.attributes.FilterCommandFactory}.
 *
 * @since 4.6
 */
public abstract class FilterCommand {
	/**
	 * The {@link InputStream} this command should read from
	 */
	protected InputStream in;

	/**
	 * The {@link OutputStream} this command should write to
	 */
	protected OutputStream out;

	/**
	 * Constructor for FilterCommand
	 * <p>
	 * FilterCommand implementors are required to manage the in and out streams
	 * (close on success and/or exception).
	 *
	 * @param in
	 *            The {@link java.io.InputStream} this command should read from
	 * @param out
	 *            The {@link java.io.OutputStream} this command should write to
	 */
	public FilterCommand(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	/**
	 * Execute the command. The command is supposed to read data from
	 * {@link #in} and to write the result to {@link #out}. It returns the
	 * number of bytes it read from {@link #in}. It should be called in a loop
	 * until it returns -1 signaling that the {@link java.io.InputStream} is
	 * completely processed.
	 * <p>
	 * On successful completion (return -1) or on Exception, the streams
	 * {@link #in} and {@link #out} are closed by the implementation.
	 *
	 * @return the number of bytes read from the {@link java.io.InputStream} or
	 *         -1. -1 means that the {@link java.io.InputStream} is completely
	 *         processed.
	 * @throws java.io.IOException
	 *             when {@link java.io.IOException} occurred while reading from
	 *             {@link #in} or writing to {@link #out}
	 */
	public abstract int run() throws IOException;
}
