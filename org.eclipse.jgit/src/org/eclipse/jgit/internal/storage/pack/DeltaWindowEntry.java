/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

final class DeltaWindowEntry {
	DeltaWindowEntry prev;
	DeltaWindowEntry next;
	ObjectToPack object;

	/** Complete contents of this object. Lazily loaded. */
	byte[] buffer;

	/** Index of this object's content, to encode other deltas. Lazily loaded. */
	DeltaIndex index;

	final void set(ObjectToPack object) {
		this.object = object;
		this.index = null;
		this.buffer = null;
	}

	/** @return current delta chain depth of this object. */
	final int depth() {
		return object.getDeltaDepth();
	}

	/** @return type of the object in this window entry. */
	final int type() {
		return object.getType();
	}

	/** @return estimated unpacked size of the object, in bytes . */
	final int size() {
		return object.getWeight();
	}

	/** @return true if there is no object stored in this entry. */
	final boolean empty() {
		return object == null;
	}

	/** Remove this entry from the window chain. */
	private final void unlink() {
		prev.next = next;
		next.prev = prev;
	}

	final void makeNext(DeltaWindowEntry e) {
		e.unlink();
		e.next = next;
		e.prev = this;
		next.prev = e;
		next = e;
	}

	static DeltaWindowEntry createWindow(int cnt) {
		// C Git increases the window size supplied by the user by 1.
		// We don't know why it does this, but if the user asks for
		// window=10, it actually processes with window=11. Because
		// the window size has the largest direct impact on the final
		// pack file size, we match this odd behavior here to give us
		// a better chance of producing a similar sized pack as C Git.
		//
		// We would prefer to directly honor the user's request since
		// PackWriter has a minimum of 2 for the window size, but then
		// users might complain that JGit is creating a bigger pack file.
		DeltaWindowEntry res = new DeltaWindowEntry();
		DeltaWindowEntry p = res;
		for (int i = 0; i < cnt; i++) {
			DeltaWindowEntry e = new DeltaWindowEntry();
			e.prev = p;
			p.next = e;
			p = e;
		}
		p.next = res;
		res.prev = p;
		return res;
	}
}
