/*
 * Copyright (C) 2011, 2021 IBM Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.patch.PatchApplier.Result;

/**
 * Apply a patch to files and/or to the index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-apply.html"
 *      >Git documentation about apply</a>
 * @since 2.0
 */
public class ApplyCommand extends GitCommand<ApplyResult> {

	private InputStream in;

	/**
	 * Constructs the command.
	 *
	 * @param repo
	 *            the repository this command will be used on
	 */
	ApplyCommand(Repository repo) {
		super(repo);
		if (repo == null) {
			throw new NullPointerException(JGitText.get().repositoryIsRequired);
		}
	}

	/**
	 * Set patch
	 *
	 * @param in
	 *            the patch to apply
	 * @return this instance
	 */
	public ApplyCommand setPatch(InputStream in) {
		checkCallable();
		this.in = in;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Executes the {@code ApplyCommand} command with all the options and
	 * parameters collected by the setter methods (e.g.
	 * {@link #setPatch(InputStream)} of this class. Each instance of this class
	 * should only be used for one invocation of the command. Don't call this
	 * method twice on an instance.
	 */
	@Override
	public ApplyResult call() throws GitAPIException {
		checkCallable();
		setCallable(false);
		Patch patch = new Patch();
		try (InputStream inStream = in) {
			patch.parse(inStream);
			if (!patch.getErrors().isEmpty()) {
				throw new PatchFormatException(patch.getErrors());
			}
		} catch (IOException e) {
			throw new PatchApplyException(MessageFormat.format(
					JGitText.get().patchApplyException, e.getMessage()), e);
		}
		ApplyResult r = new ApplyResult();
		try {
			PatchApplier patchApplier = new PatchApplier(repo);
			Result applyResult = patchApplier.applyPatch(patch);
			if (!applyResult.getErrors().isEmpty()) {
				throw new PatchApplyException(
						MessageFormat.format(JGitText.get().patchApplyException,
						applyResult.getErrors()));
			}
			for (String p : applyResult.getPaths()) {
				r.addUpdatedFile(new File(repo.getWorkTree(), p));
			}
		} catch (IOException e) {
			throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException,
					e.getMessage(), e));
		}
		return r;
	}
}
