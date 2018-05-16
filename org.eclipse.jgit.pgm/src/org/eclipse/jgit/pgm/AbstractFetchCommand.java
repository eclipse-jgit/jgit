/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.Option;

abstract class AbstractFetchCommand extends TextBuiltin {
	@Option(name = "--verbose", aliases = { "-v" }, usage = "usage_beMoreVerbose")
	private boolean verbose;

	/**
	 * Show fetch result.
	 *
	 * @param r
	 *            a {@link org.eclipse.jgit.transport.FetchResult} object.
	 * @throws java.io.IOException
	 *             if any.
	 */
	protected void showFetchResult(FetchResult r) throws IOException {
		try (ObjectReader reader = db.newObjectReader()) {
			boolean shownURI = false;
			for (TrackingRefUpdate u : r.getTrackingRefUpdates()) {
				if (!verbose && u.getResult() == RefUpdate.Result.NO_CHANGE)
					continue;

				final char type = shortTypeOf(u.getResult());
				final String longType = longTypeOf(reader, u);
				final String src = abbreviateRef(u.getRemoteName(), false);
				final String dst = abbreviateRef(u.getLocalName(), true);

				if (!shownURI) {
					outw.println(MessageFormat.format(CLIText.get().fromURI,
							r.getURI()));
					shownURI = true;
				}

				outw.format(" %c %-17s %-10s -> %s", valueOf(type), longType, //$NON-NLS-1$
						src, dst);
				outw.println();
			}
		}
		showRemoteMessages(errw, r.getMessages());
		for (FetchResult submoduleResult : r.submoduleResults().values()) {
			showFetchResult(submoduleResult);
		}
	}

	static void showRemoteMessages(ThrowingPrintWriter writer, String pkt) throws IOException {
		while (0 < pkt.length()) {
			final int lf = pkt.indexOf('\n');
			final int cr = pkt.indexOf('\r');
			final int s;
			if (0 <= lf && 0 <= cr)
				s = Math.min(lf, cr);
			else if (0 <= lf)
				s = lf;
			else if (0 <= cr)
				s = cr;
			else {
				writer.print(MessageFormat.format(CLIText.get().remoteMessage,
						pkt));
				writer.println();
				break;
			}

			if (pkt.charAt(s) == '\r') {
				writer.print(MessageFormat.format(CLIText.get().remoteMessage,
						pkt.substring(0, s)));
				writer.print('\r');
			} else {
				writer.print(MessageFormat.format(CLIText.get().remoteMessage,
						pkt.substring(0, s)));
				writer.println();
			}

			pkt = pkt.substring(s + 1);
		}
		writer.flush();
	}

	private static String longTypeOf(ObjectReader reader,
			final TrackingRefUpdate u) {
		final RefUpdate.Result r = u.getResult();
		if (r == RefUpdate.Result.LOCK_FAILURE)
			return "[lock fail]"; //$NON-NLS-1$
		if (r == RefUpdate.Result.IO_FAILURE)
			return "[i/o error]"; //$NON-NLS-1$
		if (r == RefUpdate.Result.REJECTED)
			return "[rejected]"; //$NON-NLS-1$
		if (ObjectId.zeroId().equals(u.getNewObjectId()))
			return "[deleted]"; //$NON-NLS-1$

		if (r == RefUpdate.Result.NEW) {
			if (u.getRemoteName().startsWith(Constants.R_HEADS))
				return "[new branch]"; //$NON-NLS-1$
			else if (u.getLocalName().startsWith(Constants.R_TAGS))
				return "[new tag]"; //$NON-NLS-1$
			return "[new]"; //$NON-NLS-1$
		}

		if (r == RefUpdate.Result.FORCED) {
			final String aOld = safeAbbreviate(reader, u.getOldObjectId());
			final String aNew = safeAbbreviate(reader, u.getNewObjectId());
			return aOld + "..." + aNew; //$NON-NLS-1$
		}

		if (r == RefUpdate.Result.FAST_FORWARD) {
			final String aOld = safeAbbreviate(reader, u.getOldObjectId());
			final String aNew = safeAbbreviate(reader, u.getNewObjectId());
			return aOld + ".." + aNew; //$NON-NLS-1$
		}

		if (r == RefUpdate.Result.NO_CHANGE)
			return "[up to date]"; //$NON-NLS-1$
		return "[" + r.name() + "]"; //$NON-NLS-1$//$NON-NLS-2$
	}

	private static String safeAbbreviate(ObjectReader reader, ObjectId id) {
		try {
			return reader.abbreviate(id).name();
		} catch (IOException cannotAbbreviate) {
			return id.name();
		}
	}

	private static char shortTypeOf(RefUpdate.Result r) {
		if (r == RefUpdate.Result.LOCK_FAILURE)
			return '!';
		if (r == RefUpdate.Result.IO_FAILURE)
			return '!';
		if (r == RefUpdate.Result.NEW)
			return '*';
		if (r == RefUpdate.Result.FORCED)
			return '+';
		if (r == RefUpdate.Result.FAST_FORWARD)
			return ' ';
		if (r == RefUpdate.Result.REJECTED)
			return '!';
		if (r == RefUpdate.Result.NO_CHANGE)
			return '=';
		return ' ';
	}
}
