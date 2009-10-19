/*
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

/**
 * Pairing of a name and the {@link ObjectId} it currently has.
 * <p>
 * A ref in Git is (more or less) a variable that holds a single object
 * identifier. The object identifier can be any valid Git object (blob, tree,
 * commit, annotated tag, ...).
 * <p>
 * The ref name has the attributes of the ref that was asked for as well as
 * the ref it was resolved to for symbolic refs plus the object id it points
 * to and (for tags) the peeled target object id, i.e. the tag resolved
 * recursively until a non-tag object is referenced.
 */
public class Ref {
	/** Location where a {@link Ref} is stored. */
	public static enum Storage {
		/**
		 * The ref does not exist yet, updating it may create it.
		 * <p>
		 * Creation is likely to choose {@link #LOOSE} storage.
		 */
		NEW(true, false),

		/**
		 * The ref is stored in a file by itself.
		 * <p>
		 * Updating this ref affects only this ref.
		 */
		LOOSE(true, false),

		/**
		 * The ref is stored in the <code>packed-refs</code> file, with
		 * others.
		 * <p>
		 * Updating this ref requires rewriting the file, with perhaps many
		 * other refs being included at the same time.
		 */
		PACKED(false, true),

		/**
		 * The ref is both {@link #LOOSE} and {@link #PACKED}.
		 * <p>
		 * Updating this ref requires only updating the loose file, but deletion
		 * requires updating both the loose file and the packed refs file.
		 */
		LOOSE_PACKED(true, true),

		/**
		 * The ref came from a network advertisement and storage is unknown.
		 * <p>
		 * This ref cannot be updated without Git-aware support on the remote
		 * side, as Git-aware code consolidate the remote refs and reported them
		 * to this process.
		 */
		NETWORK(false, false);

		private final boolean loose;

		private final boolean packed;

		private Storage(final boolean l, final boolean p) {
			loose = l;
			packed = p;
		}

		/**
		 * @return true if this storage has a loose file.
		 */
		public boolean isLoose() {
			return loose;
		}

		/**
		 * @return true if this storage is inside the packed file.
		 */
		public boolean isPacked() {
			return packed;
		}
	}

	private final Storage storage;

	private final String name;

	private ObjectId objectId;

	private ObjectId peeledObjectId;

	private final String origName;

	private final boolean peeled;

	/**
	 * Create a new ref pairing.
	 *
	 * @param st
	 *            method used to store this ref.
	 * @param origName
	 *            The name used to resolve this ref
	 * @param refName
	 *            name of this ref.
	 * @param id
	 *            current value of the ref. May be null to indicate a ref that
	 *            does not exist yet.
	 */
	public Ref(final Storage st, final String origName, final String refName, final ObjectId id) {
		this(st, origName, refName, id, null, false);
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
	 */
	public Ref(final Storage st, final String refName, final ObjectId id) {
		this(st, refName, refName, id, null, false);
	}

	/**
	 * Create a new ref pairing.
	 *
	 * @param st
	 *            method used to store this ref.
	 * @param origName
	 *            The name used to resolve this ref
	 * @param refName
	 *            name of this ref.
	 * @param id
	 *            current value of the ref. May be null to indicate a ref that
	 *            does not exist yet.
	 * @param peel
	 *            peeled value of the ref's tag. May be null if this is not a
	 *            tag or not yet peeled (in which case the next parameter should be null)
	 * @param peeled
	 * 			  true if peel represents a the peeled value of the object
	 */
	public Ref(final Storage st, final String origName, final String refName, final ObjectId id,
			final ObjectId peel, final boolean peeled) {
		storage = st;
		this.origName = origName;
		name = refName;
		objectId = id;
		peeledObjectId = peel;
		this.peeled = peeled;
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
	 * 			  true if peel represents a the peeled value of the object
	 */
	public Ref(final Storage st, final String refName, final ObjectId id,
			final ObjectId peel, boolean peeled) {
		this(st, refName, refName, id, peel, peeled);
	}

	/**
	 * What this ref is called within the repository.
	 *
	 * @return name of this ref.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the originally resolved name
	 */
	public String getOrigName() {
		return origName;
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
		String o = "";
		if (!origName.equals(name))
			o = "(" + origName + ")";
		return "Ref[" + o + name + "=" + ObjectId.toString(getObjectId()) + "]";
	}

	void setPeeledObjectId(final ObjectId id) {
		peeledObjectId = id;
	}
}
