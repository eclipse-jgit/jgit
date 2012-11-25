/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

/** A {@link Ref} that points directly at an {@link ObjectId}. */
public abstract class ObjectIdRef implements Ref {
	/** Any reference whose peeled value is not yet known. */
	public static class Unpeeled extends ObjectIdRef {
		/**
		 * Create a new ref pairing.
		 *
		 * @param st
		 *            method used to store this ref.
		 * @param name
		 *            name of this ref.
		 * @param id
		 *            current value of the ref. May be null to indicate a ref
		 *            that does not exist yet.
		 */
		public Unpeeled(Storage st, String name, ObjectId id) {
			super(st, name, id);
		}

		public ObjectId getPeeledObjectId() {
			return null;
		}

		public boolean isPeeled() {
			return false;
		}
	}

	/** An annotated tag whose peeled object has been cached. */
	public static class PeeledTag extends ObjectIdRef {
		private final ObjectId peeledObjectId;

		/**
		 * Create a new ref pairing.
		 *
		 * @param st
		 *            method used to store this ref.
		 * @param name
		 *            name of this ref.
		 * @param id
		 *            current value of the ref.
		 * @param p
		 *            the first non-tag object that tag {@code id} points to.
		 */
		public PeeledTag(Storage st, String name, ObjectId id, ObjectId p) {
			super(st, name, id);
			peeledObjectId = p;
		}

		public ObjectId getPeeledObjectId() {
			return peeledObjectId;
		}

		public boolean isPeeled() {
			return true;
		}
	}

	/** A reference to a non-tag object coming from a cached source. */
	public static class PeeledNonTag extends ObjectIdRef {
		/**
		 * Create a new ref pairing.
		 *
		 * @param st
		 *            method used to store this ref.
		 * @param name
		 *            name of this ref.
		 * @param id
		 *            current value of the ref. May be null to indicate a ref
		 *            that does not exist yet.
		 */
		public PeeledNonTag(Storage st, String name, ObjectId id) {
			super(st, name, id);
		}

		public ObjectId getPeeledObjectId() {
			return null;
		}

		public boolean isPeeled() {
			return true;
		}
	}

	private final String name;

	private final Storage storage;

	private final ObjectId objectId;

	/**
	 * Create a new ref pairing.
	 *
	 * @param st
	 *            method used to store this ref.
	 * @param name
	 *            name of this ref.
	 * @param id
	 *            current value of the ref. May be null to indicate a ref that
	 *            does not exist yet.
	 */
	protected ObjectIdRef(Storage st, String name, ObjectId id) {
		this.name = name;
		this.storage = st;
		this.objectId = id;
	}

	public String getName() {
		return name;
	}

	public boolean isSymbolic() {
		return false;
	}

	public Ref getLeaf() {
		return this;
	}

	public Ref getTarget() {
		return this;
	}

	public ObjectId getObjectId() {
		return objectId;
	}

	public Storage getStorage() {
		return storage;
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Ref["); //$NON-NLS-1$
		r.append(getName());
		r.append('=');
		r.append(ObjectId.toString(getObjectId()));
		r.append(']');
		return r.toString();
	}
}
