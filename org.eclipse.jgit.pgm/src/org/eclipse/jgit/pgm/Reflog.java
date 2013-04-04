/*
 * Copyright (C) 2012, Tomasz Zarna <tomasz.zarna@tasktop.com>
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

import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ReflogCommand;
import org.eclipse.jgit.internal.storage.file.ReflogEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;

@Command(common = true, usage = "usage_manageReflogInformation")
class Reflog extends TextBuiltin {

	@Argument(metaVar = "metaVar_ref")
	private String ref;

	@Override
	protected void run() throws Exception {
		ReflogCommand cmd = new Git(db).reflog();
		if (ref != null)
			cmd.setRef(ref);
		Collection<ReflogEntry> entries = cmd.call();
		int i = 0;
		for (ReflogEntry entry : entries) {
			outw.println(toString(entry, i++));
		}
	}

	private String toString(ReflogEntry entry, int i) {
		final StringBuilder s = new StringBuilder();
		s.append(entry.getNewId().abbreviate(7).name());
		s.append(" "); //$NON-NLS-1$
		s.append(ref == null ? Constants.HEAD : Repository.shortenRefName(ref));
		s.append("@{" + i + "}:"); //$NON-NLS-1$ //$NON-NLS-2$
		s.append(" "); //$NON-NLS-1$
		s.append(entry.getComment());
		return s.toString();
	}
}
