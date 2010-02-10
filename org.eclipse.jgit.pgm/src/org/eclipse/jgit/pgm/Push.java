/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

@Command(common = true, usage = "Update remote repository from local refs")
class Push extends TextBuiltin {
	@Option(name = "--timeout", metaVar = "SECONDS", usage = "abort connection if no activity")
	int timeout = -1;

	@Argument(index = 0, metaVar = "uri-ish")
	private String remote = Constants.DEFAULT_REMOTE_NAME;

	@Argument(index = 1, metaVar = "refspec")
	private final List<RefSpec> refSpecs = new ArrayList<RefSpec>();

	@Option(name = "--all")
	void addAll(final boolean ignored) {
		refSpecs.add(Transport.REFSPEC_PUSH_ALL);
	}

	@Option(name = "--tags")
	void addTags(final boolean ignored) {
		refSpecs.add(Transport.REFSPEC_TAGS);
	}

	@Option(name = "--verbose", aliases = { "-v" })
	private boolean verbose = false;

	@Option(name = "--thin")
	private boolean thin = Transport.DEFAULT_PUSH_THIN;

	@Option(name = "--no-thin")
	void nothin(final boolean ignored) {
		thin = false;
	}

	@Option(name = "--force", aliases = { "-f" })
	private boolean force;

	@Option(name = "--receive-pack", metaVar = "path")
	private String receivePack;

	@Option(name = "--dry-run")
	private boolean dryRun;

	private boolean shownURI;

	@Override
	protected void run() throws Exception {
		if (force) {
			final List<RefSpec> orig = new ArrayList<RefSpec>(refSpecs);
			refSpecs.clear();
			for (final RefSpec spec : orig)
				refSpecs.add(spec.setForceUpdate(true));
		}

		final List<Transport> transports;
		transports = Transport.openAll(db, remote, Transport.Operation.PUSH);
		for (final Transport transport : transports) {
			if (0 <= timeout)
				transport.setTimeout(timeout);
			transport.setPushThin(thin);
			if (receivePack != null)
				transport.setOptionReceivePack(receivePack);
			transport.setDryRun(dryRun);

			final Collection<RemoteRefUpdate> toPush = transport
					.findRemoteRefUpdatesFor(refSpecs);

			final URIish uri = transport.getURI();
			final PushResult result;
			try {
				result = transport.push(new TextProgressMonitor(), toPush);
			} finally {
				transport.close();
			}
			printPushResult(uri, result);
		}
	}

	private void printPushResult(final URIish uri,
			final PushResult result) {
		shownURI = false;
		boolean everythingUpToDate = true;

		// at first, print up-to-date ones...
		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			if (rru.getStatus() == Status.UP_TO_DATE) {
				if (verbose)
					printRefUpdateResult(uri, result, rru);
			} else
				everythingUpToDate = false;
		}

		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			// ...then successful updates...
			if (rru.getStatus() == Status.OK)
				printRefUpdateResult(uri, result, rru);
		}

		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			// ...finally, others (problematic)
			if (rru.getStatus() != Status.OK
					&& rru.getStatus() != Status.UP_TO_DATE)
				printRefUpdateResult(uri, result, rru);
		}

		AbstractFetchCommand.showRemoteMessages(result.getMessages());
		if (everythingUpToDate)
			out.println("Everything up-to-date");
	}

	private void printRefUpdateResult(final URIish uri,
			final PushResult result, final RemoteRefUpdate rru) {
		if (!shownURI) {
			shownURI = true;
			out.format("To %s\n", uri);
		}

		final String remoteName = rru.getRemoteName();
		final String srcRef = rru.isDelete() ? null : rru.getSrcRef();

		switch (rru.getStatus()) {
		case OK:
			if (rru.isDelete())
				printUpdateLine('-', "[deleted]", null, remoteName, null);
			else {
				final Ref oldRef = result.getAdvertisedRef(remoteName);
				if (oldRef == null) {
					final String summary;
					if (remoteName.startsWith(Constants.R_TAGS))
						summary = "[new tag]";
					else
						summary = "[new branch]";
					printUpdateLine('*', summary, srcRef, remoteName, null);
				} else {
					boolean fastForward = rru.isFastForward();
					final char flag = fastForward ? ' ' : '+';
					final String summary = oldRef.getObjectId().abbreviate(db)
							.name()
							+ (fastForward ? ".." : "...")
							+ rru.getNewObjectId().abbreviate(db).name();
					final String message = fastForward ? null : "forced update";
					printUpdateLine(flag, summary, srcRef, remoteName, message);
				}
			}
			break;

		case NON_EXISTING:
			printUpdateLine('X', "[no match]", null, remoteName, null);
			break;

		case REJECTED_NODELETE:
			printUpdateLine('!', "[rejected]", null, remoteName,
					"remote side does not support deleting refs");
			break;

		case REJECTED_NONFASTFORWARD:
			printUpdateLine('!', "[rejected]", srcRef, remoteName,
					"non-fast forward");
			break;

		case REJECTED_REMOTE_CHANGED:
			final String message = "remote ref object changed - is not expected one "
					+ rru.getExpectedOldObjectId().abbreviate(db).name();
			printUpdateLine('!', "[rejected]", srcRef, remoteName, message);
			break;

		case REJECTED_OTHER_REASON:
			printUpdateLine('!', "[remote rejected]", srcRef, remoteName, rru
					.getMessage());
			break;

		case UP_TO_DATE:
			if (verbose)
				printUpdateLine('=', "[up to date]", srcRef, remoteName, null);
			break;

		case NOT_ATTEMPTED:
		case AWAITING_REPORT:
			printUpdateLine('?', "[unexpected push-process behavior]", srcRef,
					remoteName, rru.getMessage());
			break;
		}
	}

	private void printUpdateLine(final char flag, final String summary,
			final String srcRef, final String destRef, final String message) {
		out.format(" %c %-17s", flag, summary);

		if (srcRef != null)
			out.format(" %s ->", abbreviateRef(srcRef, true));
		out.format(" %s", abbreviateRef(destRef, true));

		if (message != null)
			out.format(" (%s)", message);

		out.println();
	}
}
