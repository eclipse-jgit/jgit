/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

/** A {@link Ref} that points directly at an {@link ObjectId}. */
public class ObjectIdRef extends Ref {
	private final Storage storage;

	private final ObjectId objectId;

	private final ObjectId peeledObjectId;

	private final boolean peeled;

	/**
	 * Create a new ref pairing.
	 *
	 * @param st
	 *            method used to store this ref.
	 * @param refName
	 *            name of this ref.
	 * @param id
	 *            current value of the ref. May be null to indicate a ref that
	 *            does not exist yet.
	 */
	public ObjectIdRef(Storage st, String refName, ObjectId id) {
		this(st, refName, id, null, false);
	}

	/**
	 * Create a new ref pairing.
	 *
	 * @param st
	 *            method used to store this ref.
	 * @param refName
	 *            name of this ref.
	 * @param id
	 *            current value of the ref. May be null to indicate a ref that
	 *            does not exist yet.
	 * @param peel
	 *            peeled value of the ref's tag. May be null if this is not a
	 *            tag or the peeled value is not known.
	 * @param peeled
	 *            true if peel represents a the peeled value of the object
	 */
	public ObjectIdRef(Storage st, String refName, ObjectId id, ObjectId peel,
			boolean peeled) {
		super(refName);
		this.storage = st;
		this.objectId = id;
		this.peeledObjectId = peel;
		this.peeled = peeled;
	}

	/**
	 * Cached value of this ref.
	 *
	 * @return the value of this ref at the last time we read it.
	 */
	public ObjectId getObjectId() {
		return objectId;
	}

	/**
	 * Cached value of <code>ref^{}</code> (the ref peeled to commit).
	 *
	 * @return if this ref is an annotated tag the id of the commit (or tree or
	 *         blob) that the annotated tag refers to; null if this ref does not
	 *         refer to an annotated tag.
	 */
	public ObjectId getPeeledObjectId() {
		if (!peeled)
			return null;
		return peeledObjectId;
	}

	/**
	 * @return whether the Ref represents a peeled tag
	 */
	public boolean isPeeled() {
		return peeled;
	}

	/**
	 * How was this ref obtained?
	 * <p>
	 * The current storage model of a Ref may influence how the ref must be
	 * updated or deleted from the repository.
	 *
	 * @return type of ref.
	 */
	public Storage getStorage() {
		return storage;
	}

	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Ref[");
		r.append(getName());
		r.append('=');
		r.append(ObjectId.toString(getObjectId()));
		r.append(']');
		return r.toString();
	}
}
