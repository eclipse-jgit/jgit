/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	final void makeNext(DeltaWindowEntry e) {
		// Disconnect e from the chain.
		e.prev.next = e.next;
		e.next.prev = e.prev;

		// Insert e after this.
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
