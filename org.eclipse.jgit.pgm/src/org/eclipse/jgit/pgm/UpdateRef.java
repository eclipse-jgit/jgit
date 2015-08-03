/*
 * Copyright (C) 2007-2008, Charles O'Farrell <charleso@charleso.org>
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

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_updateRef")
class UpdateRef extends TextBuiltin {
	@Option(name = "-m", usage = "usage_moveRenameABranch")
	private String reason;

	@Argument(index = 0)
	private String ref;

	@Argument(index = 1)
	private String newValue;

	@Override
	protected void run() throws Exception {
		// we want to move our ref, not detach it
		boolean detach = false;
		RefUpdate update = db.updateRef(ref, detach);

		// set the object, person, and message
		update.setNewObjectId(db.resolve(newValue + "^{commit}")); //$NON-NLS-1$
		update.setRefLogIdent(new PersonIdent(db));

		// the raw command assumes force
		update.setForceUpdate(true);

		// set the reflog message appropriately
		if (reason != null) {
			boolean appendStatus = false;
			update.setRefLogMessage(reason, appendStatus);
		}

		// do the update and check the result
		Result result = update.update();
		if (result != Result.FORCED) {
			throw die("Unexpected result " + result); //$NON-NLS-1$
		}
	}
}
