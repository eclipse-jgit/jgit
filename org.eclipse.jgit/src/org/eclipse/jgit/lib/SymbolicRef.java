/*
 * Copyright (C) 2010, Google Inc. and others
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
 * A reference that indirectly points at another
 * {@link org.eclipse.jgit.lib.Ref}.
 * <p>
 * A symbolic reference always derives its current value from the target
 * reference.
 */
public class SymbolicRef implements Ref {
	private final String name;

	private final Ref target;

	private final long updateIndex;

	/**
	 * Create a new ref pairing.
	 *
	 * @param refName
	 *            name of this ref.
	 * @param target
	 *            the ref we reference and derive our value from.
	 */
	public SymbolicRef(@NonNull String refName, @NonNull Ref target) {
		this.name = refName;
		this.target = target;
		this.updateIndex = UNDEFINED_UPDATE_INDEX;
	}

	/**
	 * Create a new ref pairing.
	 *
	 * @param refName
	 *            name of this ref.
	 * @param target
	 *            the ref we reference and derive our value from.
	 * @param updateIndex
	 *            index that increases with each update of the reference
	 * @since 5.3
	 */
	public SymbolicRef(@NonNull String refName, @NonNull Ref target,
			long updateIndex) {
		this.name = refName;
		this.target = target;
		this.updateIndex = updateIndex;
	}

	/** {@inheritDoc} */
	@Override
	@NonNull
	public String getName() {
		return name;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isSymbolic() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	@NonNull
	public Ref getLeaf() {
		Ref dst = getTarget();
		while (dst.isSymbolic())
			dst = dst.getTarget();
		return dst;
	}

	/** {@inheritDoc} */
	@Override
	@NonNull
	public Ref getTarget() {
		return target;
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public ObjectId getObjectId() {
		return getLeaf().getObjectId();
	}

	/** {@inheritDoc} */
	@Override
	@NonNull
	public Storage getStorage() {
		return Storage.LOOSE;
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public ObjectId getPeeledObjectId() {
		return getLeaf().getPeeledObjectId();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isPeeled() {
		return getLeaf().isPeeled();
	}

	/**
	 * {@inheritDoc}
	 * @since 5.3
	 */
	@Override
	public long getUpdateIndex() {
		if (updateIndex == UNDEFINED_UPDATE_INDEX) {
			throw new UnsupportedOperationException();
		}
		return updateIndex;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("SymbolicRef[");
		Ref cur = this;
		while (cur.isSymbolic()) {
			r.append(cur.getName());
			r.append(" -> ");
			cur = cur.getTarget();
		}
		r.append(cur.getName());
		r.append('=');
		r.append(ObjectId.toString(cur.getObjectId()));
		r.append("(");
		r.append(updateIndex); // Print value, even if -1
		r.append(")]");
		return r.toString();
	}
}
