/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

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
import org.eclipse.jgit.util.RefMap;

/** Support for the start of {@link UploadPack} and {@link ReceivePack}. */
public abstract class RefAdvertiser {
	/** Advertiser which frames lines in a {@link PacketLineOut} format. */
	public static class PacketLineOutRefAdvertiser extends RefAdvertiser {
		private final PacketLineOut pckOut;

		/**
		 * Create a new advertiser for the supplied stream.
		 *
		 * @param out
		 *            the output stream.
		 */
		public PacketLineOutRefAdvertiser(PacketLineOut out) {
			pckOut = out;
		}

		@Override
		protected void writeOne(final CharSequence line) throws IOException {
			pckOut.writeString(line.toString());
		}

		@Override
		protected void end() throws IOException {
			pckOut.end();
		}
	}

	private RevWalk walk;

	private RevFlag ADVERTISED;

	private final StringBuilder tmpLine = new StringBuilder(100);

	private final char[] tmpId = new char[Constants.OBJECT_ID_STRING_LENGTH];

	private final Set<String> capablities = new LinkedHashSet<String>();

	private boolean derefTags;

	private boolean first = true;

	/**
	 * Initialize a new advertisement formatter.
	 *
	 * @param protoWalk
	 *            the RevWalk used to parse objects that are advertised.
	 * @param advertisedFlag
	 *            flag marked on any advertised objects parsed out of the
	 *            {@code protoWalk}'s object pool, permitting the caller to
	 *            later quickly determine if an object was advertised (or not).
	 */
	public void init(final RevWalk protoWalk, final RevFlag advertisedFlag) {
		walk = protoWalk;
		ADVERTISED = advertisedFlag;
	}

	/**
	 * Toggle tag peeling.
	 * <p>
	 * <p>
	 * This method must be invoked prior to any of the following:
	 * <ul>
	 * <li>{@link #send(Map)}
	 * <li>{@link #advertiseHave(AnyObjectId)}
	 * <li>{@link #includeAdditionalHaves()}
	 * </ul>
	 *
	 * @param deref
	 *            true to show the dereferenced value of a tag as the special
	 *            ref <code>$tag^{}</code> ; false to omit it from the output.
	 */
	public void setDerefTags(final boolean deref) {
		derefTags = deref;
	}

	/**
	 * Add one protocol capability to the initial advertisement.
	 * <p>
	 * This method must be invoked prior to any of the following:
	 * <ul>
	 * <li>{@link #send(Map)}
	 * <li>{@link #advertiseHave(AnyObjectId)}
	 * <li>{@link #includeAdditionalHaves()}
	 * </ul>
	 *
	 * @param name
	 *            the name of a single protocol capability supported by the
	 *            caller. The set of capabilities are sent to the client in the
	 *            advertisement, allowing the client to later selectively enable
	 *            features it recognizes.
	 */
	public void advertiseCapability(String name) {
		capablities.add(name);
	}

	/**
	 * Format an advertisement for the supplied refs.
	 *
	 * @param refs
	 *            zero or more refs to format for the client. The collection is
	 *            sorted before display if necessary, and therefore may appear
	 *            in any order.
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	public void send(final Map<String, Ref> refs) throws IOException {
		for (final Ref r : getSortedRefs(refs)) {
			final RevObject obj = parseAnyOrNull(r.getObjectId());
			if (obj != null) {
				advertiseAny(obj, r.getName());
				if (derefTags && obj instanceof RevTag)
					advertiseTag((RevTag) obj, r.getName() + "^{}");
			}
		}
	}

	private Iterable<Ref> getSortedRefs(Map<String, Ref> all) {
		if (all instanceof RefMap
				|| (all instanceof SortedMap && ((SortedMap) all).comparator() == null))
			return all.values();
		return RefComparator.sort(all.values());
	}

	/**
	 * Advertise one object is available using the magic {@code .have}.
	 * <p>
	 * The magic {@code .have} advertisement is not available for fetching by a
	 * client, but can be used by a client when considering a delta base
	 * candidate before transferring data in a push. Within the record created
	 * by this method the ref name is simply the invalid string {@code .have}.
	 *
	 * @param id
	 *            identity of the object that is assumed to exist.
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	public void advertiseHave(AnyObjectId id) throws IOException {
		RevObject obj = parseAnyOrNull(id);
		if (obj != null) {
			advertiseAnyOnce(obj, ".have");
			if (obj instanceof RevTag)
				advertiseAnyOnce(((RevTag) obj).getObject(), ".have");
		}
	}

	/**
	 * Include references of alternate repositories as {@code .have} lines.
	 *
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	public void includeAdditionalHaves() throws IOException {
		additionalHaves(walk.getRepository().getObjectDatabase());
	}

	private void additionalHaves(final ObjectDatabase db) throws IOException {
		if (db instanceof AlternateRepositoryDatabase)
			additionalHaves(((AlternateRepositoryDatabase) db).getRepository());
		for (ObjectDatabase alt : db.getAlternates())
			additionalHaves(alt);
	}

	private void additionalHaves(final Repository alt) throws IOException {
		for (final Ref r : alt.getAllRefs().values())
			advertiseHave(r.getObjectId());
	}

	/** @return true if no advertisements have been sent yet. */
	public boolean isEmpty() {
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

	private void advertiseAnyOnce(final RevObject obj, final String refName)
			throws IOException {
		if (!obj.has(ADVERTISED))
			advertiseAny(obj, refName);
	}

	private void advertiseAny(final RevObject obj, final String refName)
			throws IOException {
		obj.add(ADVERTISED);
		advertiseId(obj, refName);
	}

	private void advertiseTag(final RevTag tag, final String refName)
			throws IOException {
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

	/**
	 * Advertise one object under a specific name.
	 * <p>
	 * If the advertised object is a tag, this method does not advertise the
	 * peeled version of it.
	 *
	 * @param id
	 *            the object to advertise.
	 * @param refName
	 *            name of the reference to advertise the object as, can be any
	 *            string not including the NUL byte.
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	public void advertiseId(final AnyObjectId id, final String refName)
			throws IOException {
		tmpLine.setLength(0);
		id.copyTo(tmpId, tmpLine);
		tmpLine.append(' ');
		tmpLine.append(refName);
		if (first) {
			first = false;
			if (!capablities.isEmpty()) {
				tmpLine.append('\0');
				for (final String capName : capablities) {
					tmpLine.append(' ');
					tmpLine.append(capName);
				}
				tmpLine.append(' ');
			}
		}
		tmpLine.append('\n');
		writeOne(tmpLine);
	}

	/**
	 * Write a single advertisement line.
	 *
	 * @param line
	 *            the advertisement line to be written. The line always ends
	 *            with LF. Never null or the empty string.
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	protected abstract void writeOne(CharSequence line) throws IOException;

	/**
	 * Mark the end of the advertisements.
	 *
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	protected abstract void end() throws IOException;
}
