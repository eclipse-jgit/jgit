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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SYMREF;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RefMap;

/** Support for the start of {@link UploadPack} and {@link ReceivePack}. */
public abstract class RefAdvertiser {
	/** Advertiser which frames lines in a {@link PacketLineOut} format. */
	public static class PacketLineOutRefAdvertiser extends RefAdvertiser {
		private final CharsetEncoder utf8 = UTF_8.newEncoder();
		private final PacketLineOut pckOut;

		private byte[] binArr = new byte[256];
		private ByteBuffer binBuf = ByteBuffer.wrap(binArr);

		private char[] chArr = new char[256];
		private CharBuffer chBuf = CharBuffer.wrap(chArr);

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
		public void advertiseId(AnyObjectId id, String refName)
				throws IOException {
			id.copyTo(binArr, 0);
			binArr[OBJECT_ID_STRING_LENGTH] = ' ';
			binBuf.position(OBJECT_ID_STRING_LENGTH + 1);
			append(refName);
			if (first) {
				first = false;
				if (!capablities.isEmpty()) {
					append('\0');
					for (String cap : capablities) {
						append(' ');
						append(cap);
					}
				}
			}
			append('\n');
			pckOut.writePacket(binArr, 0, binBuf.position());
		}

		private void append(String str) throws CharacterCodingException {
			int n = str.length();
			if (n > chArr.length) {
				chArr = new char[n + 256];
				chBuf = CharBuffer.wrap(chArr);
			}
			str.getChars(0, n, chArr, 0);
			chBuf.position(0).limit(n);
			utf8.reset();
			for (;;) {
				CoderResult cr = utf8.encode(chBuf, binBuf, true);
				if (cr.isOverflow()) {
					grow();
				} else if (cr.isUnderflow()) {
					break;
				} else {
					cr.throwException();
				}
			}
		}

		private void append(int b) {
			if (!binBuf.hasRemaining()) {
				grow();
			}
			binBuf.put((byte) b);
		}

		private void grow() {
			int cnt = binBuf.position();
			byte[] tmp = new byte[binArr.length << 1];
			System.arraycopy(binArr, 0, tmp, 0, cnt);
			binArr = tmp;
			binBuf = ByteBuffer.wrap(binArr);
			binBuf.position(cnt);
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

	private final StringBuilder tmpLine = new StringBuilder(100);

	private final char[] tmpId = new char[Constants.OBJECT_ID_STRING_LENGTH];

	final Set<String> capablities = new LinkedHashSet<>();

	private final Set<ObjectId> sent = new HashSet<>();

	private Repository repository;

	private boolean derefTags;

	boolean first = true;

	/**
	 * Initialize this advertiser with a repository for peeling tags.
	 *
	 * @param src
	 *            the repository to read from.
	 */
	public void init(Repository src) {
		repository = src;
	}

	/**
	 * Toggle tag peeling.
	 * <p>
	 * <p>
	 * This method must be invoked prior to any of the following:
	 * <ul>
	 * <li>{@link #send(Map)}
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
	 * Add one protocol capability with a value ({@code "name=value"}).
	 *
	 * @param name
	 *            name of the capability.
	 * @param value
	 *            value. If null the capability will not be added.
	 * @since 4.0
	 */
	public void advertiseCapability(String name, String value) {
		if (value != null) {
			capablities.add(name + '=' + value);
		}
	}

	/**
	 * Add a symbolic ref to capabilities.
	 * <p>
	 * This method must be invoked prior to any of the following:
	 * <ul>
	 * <li>{@link #send(Map)}
	 * <li>{@link #advertiseHave(AnyObjectId)}
	 * </ul>
	 *
	 * @param from
	 *            The symbolic ref, e.g. "HEAD"
	 * @param to
	 *            The real ref it points to, e.g. "refs/heads/master"
	 *
	 * @since 3.6
	 */
	public void addSymref(String from, String to) {
		advertiseCapability(OPTION_SYMREF, from + ':' + to);
	}

	/**
	 * Format an advertisement for the supplied refs.
	 *
	 * @param refs
	 *            zero or more refs to format for the client. The collection is
	 *            sorted before display if necessary, and therefore may appear
	 *            in any order.
	 * @return set of ObjectIds that were advertised to the client.
	 * @throws IOException
	 *             the underlying output stream failed to write out an
	 *             advertisement record.
	 */
	public Set<ObjectId> send(Map<String, Ref> refs) throws IOException {
		for (Ref ref : getSortedRefs(refs)) {
			if (ref.getObjectId() == null)
				continue;

			advertiseAny(ref.getObjectId(), ref.getName());

			if (!derefTags)
				continue;

			if (!ref.isPeeled()) {
				if (repository == null)
					continue;
				ref = repository.peel(ref);
			}

			if (ref.getPeeledObjectId() != null)
				advertiseAny(ref.getPeeledObjectId(), ref.getName() + "^{}"); //$NON-NLS-1$
		}
		return sent;
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
		advertiseAnyOnce(id, ".have"); //$NON-NLS-1$
	}

	/** @return true if no advertisements have been sent yet. */
	public boolean isEmpty() {
		return first;
	}

	private void advertiseAnyOnce(AnyObjectId obj, final String refName)
			throws IOException {
		if (!sent.contains(obj))
			advertiseAny(obj, refName);
	}

	private void advertiseAny(AnyObjectId obj, final String refName)
			throws IOException {
		sent.add(obj.toObjectId());
		advertiseId(obj, refName);
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
