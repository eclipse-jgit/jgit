/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.AlternateRepositoryDatabase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/** Send a complete list of current refs, including peeled values for tags. */
class InfoRefsServlet extends RepositoryServlet {
	private static final long serialVersionUID = 1L;

	private static final String ENCODING = "UTF-8";

	@Override
	public void doGet(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		serve(req, rsp, true);
	}

	@Override
	protected void doHead(final HttpServletRequest req,
			final HttpServletResponse rsp) throws ServletException, IOException {
		serve(req, rsp, false);
	}

	private void serve(final HttpServletRequest req,
			final HttpServletResponse rsp, final boolean sendBody)
			throws IOException {
		final RefAdvertiser adv = compute(req);
		final byte[] raw = adv.toByteArray();
		rsp.setContentType("text/plain");
		rsp.setCharacterEncoding(ENCODING);
		send(raw, req, rsp, sendBody);
	}

	private RefAdvertiser compute(final HttpServletRequest req) {
		final Repository db = getRepository(req);
		final RefAdvertiser adv = new RefAdvertiser(new RevWalk(db));
		adv.send(db.getAllRefs().values());
		return adv;
	}

	private static class RefAdvertiser {
		private final RevWalk walk;

		private final RevFlag ADVERTISED;

		private final StringBuilder outBuffer = new StringBuilder();

		private final char[] tmpId = new char[2 * Constants.OBJECT_ID_LENGTH];

		private final Set<String> capablities = new LinkedHashSet<String>();

		private boolean first = true;

		RefAdvertiser(final RevWalk protoWalk) {
			walk = protoWalk;
			ADVERTISED = protoWalk.newFlag("ADVERTISED");
		}

		void advertiseCapability(String name) {
			capablities.add(name);
		}

		void send(final Collection<Ref> refs) {
			for (final Ref r : RefComparator.sort(refs)) {
				final RevObject obj = parseAnyOrNull(r.getObjectId());
				if (obj != null) {
					advertiseAny(obj, r.getOrigName());
					if (obj instanceof RevTag)
						advertiseTag((RevTag) obj, r.getOrigName() + "^{}");
				}
			}
		}

		void advertiseHave(AnyObjectId id) {
			RevObject obj = parseAnyOrNull(id);
			if (obj != null) {
				advertiseAnyOnce(obj, ".have");
				if (obj instanceof RevTag)
					advertiseAnyOnce(((RevTag) obj).getObject(), ".have");
			}
		}

		void includeAdditionalHaves() {
			additionalHaves(walk.getRepository().getObjectDatabase());
		}

		byte[] toByteArray() throws UnsupportedEncodingException {
			return outBuffer.toString().getBytes(ENCODING);
		}

		private void additionalHaves(final ObjectDatabase db) {
			if (db instanceof AlternateRepositoryDatabase)
				additionalHaves(((AlternateRepositoryDatabase) db)
						.getRepository());
			for (ObjectDatabase alt : db.getAlternates())
				additionalHaves(alt);
		}

		private void additionalHaves(final Repository alt) {
			for (final Ref r : alt.getAllRefs().values())
				advertiseHave(r.getObjectId());
		}

		boolean isEmpty() {
			return first;
		}

		private RevObject parseAnyOrNull(final AnyObjectId id) {
			if (id == null)
				return null;
			try {
				return walk.parseAny(id);
			} catch (IOException e) {
				return null;
			}
		}

		private void advertiseAnyOnce(final RevObject obj, final String refName) {
			if (!obj.has(ADVERTISED))
				advertiseAny(obj, refName);
		}

		private void advertiseAny(final RevObject obj, final String refName) {
			obj.add(ADVERTISED);
			advertiseId(obj, refName);
		}

		private void advertiseTag(final RevTag tag, final String refName) {
			RevObject o = tag;
			do {
				// Fully unwrap here so later on we have these already parsed.
				final RevObject target = ((RevTag) o).getObject();
				try {
					walk.parseHeaders(target);
				} catch (IOException err) {
					return;
				}
				target.add(ADVERTISED);
				o = target;
			} while (o instanceof RevTag);
			advertiseAny(tag.getObject(), refName);
		}

		void advertiseId(final AnyObjectId id, final String refName) {
			id.copyTo(tmpId, outBuffer);
			outBuffer.append(' ');
			outBuffer.append(refName);
			if (first) {
				first = false;
				if (!capablities.isEmpty()) {
					outBuffer.append('\0');
					for (final String capName : capablities) {
						outBuffer.append(' ');
						outBuffer.append(capName);
					}
					outBuffer.append(' ');
				}
			}
			outBuffer.append('\n');
		}
	}
}
