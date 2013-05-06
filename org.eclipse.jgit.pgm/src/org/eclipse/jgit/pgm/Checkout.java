/*
 * Copyright (C) 2010, 2012 Chris Aniszczyk <caniszczyk@gmail.com>
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

package org.eclipse.jgit.pgm;

import java.text.MessageFormat;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_checkout")
class Checkout extends TextBuiltin {

	@Option(name = "-b", usage = "usage_createBranchAndCheckout")
	private boolean createBranch = false;

	@Option(name = "--force", aliases = { "-f" }, usage = "usage_forceCheckout")
	private boolean force = false;

	@Argument(required = true, metaVar = "metaVar_name", usage = "usage_checkout")
	private String name;

	@Override
	protected void run() throws Exception {
		if (createBranch) {
			final ObjectId head = db.resolve(Constants.HEAD);
			if (head == null)
				throw die(CLIText.get().onBranchToBeBorn);
		}

		CheckoutCommand command = new Git(db).checkout();
		command.setCreateBranch(createBranch);
		command.setName(name);
		command.setForce(force);
		try {
			String oldBranch = db.getBranch();
			Ref ref = command.call();
			if (ref == null)
				return;
			if (Repository.shortenRefName(ref.getName()).equals(oldBranch)) {
				outw.println(MessageFormat.format(
						CLIText.get().alreadyOnBranch,
						name));
				return;
			}
			if (createBranch)
				outw.println(MessageFormat.format(
						CLIText.get().switchedToNewBranch,
						Repository.shortenRefName(ref.getName())));
			else
				outw.println(MessageFormat.format(
						CLIText.get().switchedToBranch,
						Repository.shortenRefName(ref.getName())));
		} catch (RefNotFoundException e) {
			outw.println(MessageFormat.format(
					CLIText.get().pathspecDidNotMatch,
					name));
		} catch (RefAlreadyExistsException e) {
			throw die(MessageFormat.format(CLIText.get().branchAlreadyExists,
					name));
		}
	}
}
