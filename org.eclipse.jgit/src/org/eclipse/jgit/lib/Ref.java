/*
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Pairing of a name and the {@link org.eclipse.jgit.lib.ObjectId} it currently
 * has.
 * <p>
 * A ref in Git is (more or less) a variable that holds a single object
 * identifier. The object identifier can be any valid Git object (blob, tree,
 * commit, annotated tag, ...).
 * <p>
 * The ref name has the attributes of the ref that was asked for as well as the
 * ref it was resolved to for symbolic refs plus the object id it points to and
 * (for tags) the peeled target object id, i.e. the tag resolved recursively
 * until a non-tag object is referenced.
 */
public interface Ref {
	/** Location where a {@link Ref} is stored. */
	enum Storage {
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
		 * The ref is stored in the <code>packed-refs</code> file, with others.
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

		private Storage(boolean l, boolean p) {
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

	/**
	 * Update index value when a reference doesn't have one
	 *
	 * @since 5.4
	 */
	long UNDEFINED_UPDATE_INDEX = -1L;

	/**
	 * What this ref is called within the repository.
	 *
	 * @return name of this ref.
	 */
	@NonNull
	String getName();

	/**
	 * Test if this reference is a symbolic reference.
	 * <p>
	 * A symbolic reference does not have its own
	 * {@link org.eclipse.jgit.lib.ObjectId} value, but instead points to
	 * another {@code Ref} in the same database and always uses that other
	 * reference's value as its own.
	 *
	 * @return true if this is a symbolic reference; false if this reference
	 *         contains its own ObjectId.
	 */
	boolean isSymbolic();

	/**
	 * Traverse target references until {@link #isSymbolic()} is false.
	 * <p>
	 * If {@link #isSymbolic()} is false, returns {@code this}.
	 * <p>
	 * If {@link #isSymbolic()} is true, this method recursively traverses
	 * {@link #getTarget()} until {@link #isSymbolic()} returns false.
	 * <p>
	 * This method is effectively
	 *
	 * <pre>
	 * return isSymbolic() ? getTarget().getLeaf() : this;
	 * </pre>
	 *
	 * @return the reference that actually stores the ObjectId value.
	 */
	@NonNull
	Ref getLeaf();

	/**
	 * Get the reference this reference points to, or {@code this}.
	 * <p>
	 * If {@link #isSymbolic()} is true this method returns the reference it
	 * directly names, which might not be the leaf reference, but could be
	 * another symbolic reference.
	 * <p>
	 * If this is a leaf level reference that contains its own ObjectId,this
	 * method returns {@code this}.
	 *
	 * @return the target reference, or {@code this}.
	 */
	@NonNull
	Ref getTarget();

	/**
	 * Cached value of this ref.
	 *
	 * @return the value of this ref at the last time we read it. May be
	 *         {@code null} to indicate a ref that does not exist yet or a
	 *         symbolic ref pointing to an unborn branch.
	 */
	@Nullable
	ObjectId getObjectId();

	/**
	 * Cached value of <code>ref^{}</code> (the ref peeled to commit).
	 *
	 * @return if this ref is an annotated tag the id of the commit (or tree or
	 *         blob) that the annotated tag refers to; {@code null} if this ref
	 *         does not refer to an annotated tag.
	 */
	@Nullable
	ObjectId getPeeledObjectId();

	/**
	 * Whether the Ref represents a peeled tag.
	 *
	 * @return whether the Ref represents a peeled tag.
	 */
	boolean isPeeled();

	/**
	 * How was this ref obtained?
	 * <p>
	 * The current storage model of a Ref may influence how the ref must be
	 * updated or deleted from the repository.
	 *
	 * @return type of ref.
	 */
	@NonNull
	Storage getStorage();

	/**
	 * Indicator of the relative order between updates of a specific reference
	 * name. A number that increases when a reference is updated.
	 * <p>
	 * With symbolic references, the update index refers to updates of the
	 * symbolic reference itself. For example, if HEAD points to
	 * refs/heads/master, then the update index for exactRef("HEAD") will only
	 * increase when HEAD changes to point to another ref, regardless of how
	 * many times refs/heads/master is updated.
	 * <p>
	 * Should not be used unless the {@code RefDatabase} that instantiated the
	 * ref supports versioning (see {@link RefDatabase#hasVersioning()})
	 *
	 * @return the update index (i.e. version) of this reference.
	 * @throws UnsupportedOperationException
	 *             if the creator of the instance (e.g. {@link RefDatabase})
	 *             doesn't support versioning and doesn't override this method
	 * @since 5.3
	 */
	default long getUpdateIndex() {
		throw new UnsupportedOperationException();
	}
}
