/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.util.RefList;

/**
 * Writes out refs to {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} file by
 * computing and applying all the missing traits for optimized reads.
 *
 * @since 7.7
 */
public abstract class PackedRefsWriter {
	/** Refs to be written. */
	protected Collection<Ref> refs;

	/** Traits that are applicable to the refs. */
	protected final EnumSet<PackedRefsTrait> traits;

	/**
	 * <p>
	 * Constructor for PackedRefsWriter.
	 * </p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 * @param traits
	 *            traits that are applicable to the refs. If any traits are
	 *            missing, they would be applied before writing, but the
	 *            specified traits are not re-computed/checked.
	 * @param refDir
	 *            refDir where the packed-refs file should be written.
	 *
	 * @throws IOException
	 *             if it is not able to peel any of the tags.
	 * @since 7.7
	 */
	public PackedRefsWriter(Collection<Ref> refs,
			EnumSet<PackedRefsTrait> traits, RefDirectory refDir)
			throws IOException {
		this(refs, traits);
		applyMissingTraits(refDir);
	}

	/**
	 * <p>
	 * Constructor for PackedRefsWriter.
	 * </p>
	 *
	 * <p>
	 * Stores the provided refs and traits without applying any missing traits.
	 * Intended for use by subclasses that manage trait application themselves.
	 * </p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 * @param traits
	 *            traits that are applicable to the refs.
	 */
	protected PackedRefsWriter(Collection<Ref> refs,
			EnumSet<PackedRefsTrait> traits) {
		this.refs = refs;
		this.traits = traits;
	}

	/**
	 * Rebuild the {@link org.eclipse.jgit.lib.Constants#INFO_REFS}.
	 * <p>
	 * This method rebuilds the contents of the
	 * {@link org.eclipse.jgit.lib.Constants#INFO_REFS} file to match the passed
	 * list of references.
	 *
	 * @throws java.io.IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writeInfoRefs() throws IOException {
		final StringWriter w = new StringWriter();
		final char[] tmp = new char[Constants.OBJECT_ID_STRING_LENGTH];
		for (Ref r : refs) {
			if (Constants.HEAD.equals(r.getName())) {
				// Historically HEAD has never been published through
				// the INFO_REFS file. This is a mistake, but its the
				// way things are.
				//
				continue;
			}

			ObjectId objectId = r.getObjectId();
			if (objectId == null) {
				// Symrefs to unborn branches aren't advertised in the info/refs
				// file.
				continue;
			}
			objectId.copyTo(tmp, w);
			w.write('\t');
			w.write(r.getName());
			w.write('\n');

			ObjectId peeledObjectId = r.getPeeledObjectId();
			if (peeledObjectId != null) {
				peeledObjectId.copyTo(tmp, w);
				w.write('\t');
				w.write(r.getName());
				w.write("^{}\n"); //$NON-NLS-1$
			}
		}
		writeFile(Constants.INFO_REFS, Constants.encode(w.toString()));
	}

	/**
	 * Rebuild the {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} file.
	 * <p>
	 * This method rebuilds the contents of the
	 * {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} file to match the
	 * passed list of references, including only those refs that have a storage
	 * type of {@link org.eclipse.jgit.lib.Ref.Storage#PACKED}.
	 *
	 * @throws java.io.IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writePackedRefs() throws IOException {
		final StringWriter w = new StringWriter();
		w.write(RefDirectory.PACKED_REFS_HEADER);
		for (PackedRefsTrait t : traits) {
			w.write(t.value());
		}
		w.write('\n');

		final char[] tmp = new char[Constants.OBJECT_ID_STRING_LENGTH];
		for (Ref r : refs) {
			if (r.getStorage() != Ref.Storage.PACKED)
				continue;

			ObjectId objectId = r.getObjectId();
			if (objectId == null) {
				// A packed ref cannot be a symref, let alone a symref
				// to an unborn branch.
				throw new NullPointerException();
			}
			objectId.copyTo(tmp, w);
			w.write(' ');
			w.write(r.getName());
			w.write('\n');

			ObjectId peeledObjectId = r.getPeeledObjectId();
			if (peeledObjectId != null) {
				w.write('^');
				peeledObjectId.copyTo(tmp, w);
				w.write('\n');
			}
		}
		writeFile(Constants.PACKED_REFS, Constants.encode(w.toString()));
	}

	/**
	 * Access the new refs after all the necessary traits were applied.
	 *
	 * @return refs with all the traits applied
	 */
	public RefList<Ref> asRefList() {
		RefList.Builder<Ref> builder = new RefList.Builder<>(this.refs.size());
		for (Ref each : refs) {
			builder.add(each);
		}
		return builder.toRefList();
	}

	/**
	 * Handles actual writing of ref files to the git repository, which may
	 * differ slightly depending on the destination and transport.
	 *
	 * @param file
	 *            path to ref file.
	 * @param content
	 *            byte content of file to be written.
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 */
	protected abstract void writeFile(String file, byte[] content)
			throws IOException;

	private void applyMissingTraits(RefDirectory refDir) throws IOException {
		if (!traits.contains(PackedRefsTrait.SORTED)) {
			this.refs = RefComparator.sort(refs);
			traits.add(PackedRefsTrait.SORTED);
		}

		if (!traits.contains(PackedRefsTrait.PEELED)) {
			Collection<Ref> peeledRefs = new ArrayList<>();
			for (Ref ref : refs) {
				if (!ref.isPeeled() && ref.getName().startsWith(R_TAGS)) {
					ref = refDir.peel(ref);
				}
				peeledRefs.add(ref);
			}
			this.refs = peeledRefs;
			this.traits.add(PackedRefsTrait.PEELED);
		}
	}
}
