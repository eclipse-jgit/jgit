/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Properties;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

/**
 * Optimize storage of references. For a RefDirectory database, this packs all
 * non-symbolic, loose refs into packed-refs. For Reftable, all the data is
 * compacted into a single table.
 *
 * @since 7.1
 */
public class PackRefsCommand extends GitCommand<String> {
	private ProgressMonitor monitor;

	private boolean all;

	/**
	 * Creates a new {@link PackRefsCommand} instance with default values.
	 *
	 * @param repo
	 * 		the repository this command will be used on
	 */
	public PackRefsCommand(Repository repo) {
		super(repo);
		this.monitor = NullProgressMonitor.INSTANCE;
	}

	/**
	 * Set progress monitor
	 *
	 * @param monitor
	 * 		a progress monitor
	 * @return this instance
	 */
	public PackRefsCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Specify whether to pack all the references.
	 *
	 * @param all
	 * 		if <code>true</code> all the loose refs will be packed
	 * @return this instance
	 */
	public PackRefsCommand setAll(boolean all) {
		this.all = all;
		return this;
	}

	@Override
	public String call() throws GitAPIException {
		checkCallable();
		try {
			repo.getRefDatabase().packRefs(monitor,
					new RefDatabase.PackRefsOptions(all));
			return JGitText.get().packRefsSuccessful;
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().packRefsFailed, e);
		}
	}
}
