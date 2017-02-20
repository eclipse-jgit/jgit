/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/**
 * Used to delete one or several tags.
 *
 * The result of {@link #call()} is a list with the (full) names of the deleted
 * tags.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
 *      >Git documentation about Tag</a>
 */
public class DeleteTagCommand extends GitCommand<List<String>> {

	private final Set<String> tags = new HashSet<>();

	/**
	 * @param repo
	 */
	protected DeleteTagCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @return the list with the full names of the deleted tags
	 */
	@Override
	public List<String> call() throws GitAPIException {
		checkCallable();
		List<String> result = new ArrayList<>();
		if (tags.isEmpty())
			return result;
		try {
			setCallable(false);
			for (String tagName : tags) {
				if (tagName == null)
					continue;
				Ref currentRef = repo.findRef(tagName);
				if (currentRef == null)
					continue;
				String fullName = currentRef.getName();
				RefUpdate update = repo.updateRef(fullName);
				update.setForceUpdate(true);
				Result deleteResult = update.delete();

				boolean ok = true;
				switch (deleteResult) {
				case IO_FAILURE:
				case LOCK_FAILURE:
				case REJECTED:
					ok = false;
					break;
				default:
					break;
				}

				if (ok) {
					result.add(fullName);
				} else
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().deleteTagUnexpectedResult,
							deleteResult.name()));
			}
			return result;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * @param tags
	 *            the names of the tags to delete; if not set, this will do
	 *            nothing; invalid tag names will simply be ignored
	 * @return this instance
	 */
	public DeleteTagCommand setTags(String... tags) {
		checkCallable();
		this.tags.clear();
		for (String tagName : tags)
			this.tags.add(tagName);
		return this;
	}
}
