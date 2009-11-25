/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;

@Command(common = true, usage = "Update remote refs from another repository")
class Fetch extends AbstractFetchCommand {
	@Option(name = "--timeout", metaVar = "SECONDS", usage = "abort connection if no activity")
	int timeout = -1;

	@Option(name = "--fsck", usage = "perform fsck style checks on receive")
	private Boolean fsck;

	@Option(name = "--no-fsck")
	void nofsck(final boolean ignored) {
		fsck = Boolean.FALSE;
	}

	@Option(name = "--prune", usage = "prune stale tracking refs")
	private Boolean prune;

	@Option(name = "--dry-run")
	private boolean dryRun;

	@Option(name = "--thin", usage = "fetch thin pack")
	private Boolean thin;

	@Option(name = "--no-thin")
	void nothin(final boolean ignored) {
		thin = Boolean.FALSE;
	}

	@Argument(index = 0, metaVar = "uri-ish")
	private String remote = Constants.DEFAULT_REMOTE_NAME;

	@Argument(index = 1, metaVar = "refspec")
	private List<RefSpec> toget;

	@Override
	protected void run() throws Exception {
		final Transport tn = Transport.open(db, remote);
		if (fsck != null)
			tn.setCheckFetchedObjects(fsck.booleanValue());
		if (prune != null)
			tn.setRemoveDeletedRefs(prune.booleanValue());
		tn.setDryRun(dryRun);
		if (thin != null)
			tn.setFetchThin(thin.booleanValue());
		if (0 <= timeout)
			tn.setTimeout(timeout);
		final FetchResult r;
		try {
			r = tn.fetch(new TextProgressMonitor(), toget);
			if (r.getTrackingRefUpdates().isEmpty())
				return;
		} finally {
			tn.close();
		}
		showFetchResult(tn, r);
	}
}
