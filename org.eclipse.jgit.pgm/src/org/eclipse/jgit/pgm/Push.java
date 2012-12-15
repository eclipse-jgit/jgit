/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

import static java.lang.Character.valueOf;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_UpdateRemoteRepositoryFromLocalRefs")
class Push extends TextBuiltin {
	@Option(name = "--timeout", metaVar = "metaVar_seconds", usage = "usage_abortConnectionIfNoActivity")
	int timeout = -1;

	@Argument(index = 0, metaVar = "metaVar_uriish")
	private String remote = Constants.DEFAULT_REMOTE_NAME;

	@Argument(index = 1, metaVar = "metaVar_refspec")
	private final List<RefSpec> refSpecs = new ArrayList<RefSpec>();

	@Option(name = "--all")
	private boolean all;

	@Option(name = "--tags")
	private boolean tags;

	@Option(name = "--verbose", aliases = { "-v" })
	private boolean verbose = false;

	@Option(name = "--thin")
	private boolean thin = Transport.DEFAULT_PUSH_THIN;

	@Option(name = "--no-thin")
	void nothin(@SuppressWarnings("unused") final boolean ignored) {
		thin = false;
	}

	@Option(name = "--force", aliases = { "-f" })
	private boolean force;

	@Option(name = "--receive-pack", metaVar = "metaVar_path")
	private String receivePack;

	@Option(name = "--dry-run")
	private boolean dryRun;

	private boolean shownURI;

	@Override
	protected void run() throws Exception {
		Git git = new Git(db);
		PushCommand push = git.push();
		push.setDryRun(dryRun);
		push.setForce(force);
		push.setProgressMonitor(new TextProgressMonitor());
		push.setReceivePack(receivePack);
		push.setRefSpecs(refSpecs);
		if (all)
			push.setPushAll();
		if (tags)
			push.setPushTags();
		push.setRemote(remote);
		push.setThin(thin);
		push.setTimeout(timeout);
		Iterable<PushResult> results = push.call();
		for (PushResult result : results) {
			ObjectReader reader = db.newObjectReader();
			try {
				printPushResult(reader, result.getURI(), result);
			} finally {
				reader.release();
			}
		}
	}

	private void printPushResult(final ObjectReader reader, final URIish uri,
			final PushResult result) throws IOException {
		shownURI = false;
		boolean everythingUpToDate = true;

		// at first, print up-to-date ones...
		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			if (rru.getStatus() == Status.UP_TO_DATE) {
				if (verbose)
					printRefUpdateResult(reader, uri, result, rru);
			} else
				everythingUpToDate = false;
		}

		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			// ...then successful updates...
			if (rru.getStatus() == Status.OK)
				printRefUpdateResult(reader, uri, result, rru);
		}

		for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
			// ...finally, others (problematic)
			if (rru.getStatus() != Status.OK
					&& rru.getStatus() != Status.UP_TO_DATE)
				printRefUpdateResult(reader, uri, result, rru);
		}

		AbstractFetchCommand.showRemoteMessages(result.getMessages());
		if (everythingUpToDate)
			outw.println(CLIText.get().everythingUpToDate);
	}

	private void printRefUpdateResult(final ObjectReader reader,
			final URIish uri, final PushResult result, final RemoteRefUpdate rru)
			throws IOException {
		if (!shownURI) {
			shownURI = true;
			outw.println(MessageFormat.format(CLIText.get().pushTo, uri));
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
					final String summary = safeAbbreviate(reader, oldRef
							.getObjectId())
							+ (fastForward ? ".." : "...") //$NON-NLS-1$ //$NON-NLS-2$
							+ safeAbbreviate(reader, rru.getNewObjectId());
					final String message = fastForward ? null : CLIText.get().forcedUpdate;
					printUpdateLine(flag, summary, srcRef, remoteName, message);
				}
			}
			break;

		case NON_EXISTING:
			printUpdateLine('X', "[no match]", null, remoteName, null);
			break;

		case REJECTED_NODELETE:
			printUpdateLine('!', "[rejected]", null, remoteName,
					CLIText.get().remoteSideDoesNotSupportDeletingRefs);
			break;

		case REJECTED_NONFASTFORWARD:
			printUpdateLine('!', "[rejected]", srcRef, remoteName,
					CLIText.get().nonFastForward);
			break;

		case REJECTED_REMOTE_CHANGED:
			final String message = MessageFormat.format(
					CLIText.get().remoteRefObjectChangedIsNotExpectedOne,
					safeAbbreviate(reader, rru.getExpectedOldObjectId()));
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

	private static String safeAbbreviate(ObjectReader reader, ObjectId id) {
		try {
			return reader.abbreviate(id).name();
		} catch (IOException cannotAbbreviate) {
			return id.name();
		}
	}

	private void printUpdateLine(final char flag, final String summary,
			final String srcRef, final String destRef, final String message)
			throws IOException {
		outw.format(" %c %-17s", valueOf(flag), summary); //$NON-NLS-1$

		if (srcRef != null)
			outw.format(" %s ->", abbreviateRef(srcRef, true)); //$NON-NLS-1$
		outw.format(" %s", abbreviateRef(destRef, true)); //$NON-NLS-1$

		if (message != null)
			outw.format(" (%s)", message); //$NON-NLS-1$

		outw.println();
	}
}
