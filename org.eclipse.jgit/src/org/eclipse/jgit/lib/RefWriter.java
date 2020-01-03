/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Writes out refs to the {@link org.eclipse.jgit.lib.Constants#INFO_REFS} and
 * {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} files.
 *
 * This class is abstract as the writing of the files must be handled by the
 * caller. This is because it is used by transport classes as well.
 */
public abstract class RefWriter {

	private final Collection<Ref> refs;

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(Collection<Ref> refs) {
		this.refs = RefComparator.sort(refs);
	}

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(Map<String, Ref> refs) {
		if (refs instanceof RefMap)
			this.refs = refs.values();
		else
			this.refs = RefComparator.sort(refs.values());
	}

	/**
	 * <p>Constructor for RefWriter.</p>
	 *
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(RefList<Ref> refs) {
		this.refs = refs.asList();
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
		boolean peeled = false;
		for (Ref r : refs) {
			if (r.getStorage().isPacked() && r.isPeeled()) {
				peeled = true;
				break;
			}
		}

		final StringWriter w = new StringWriter();
		if (peeled) {
			w.write(RefDirectory.PACKED_REFS_HEADER);
			if (peeled)
				w.write(RefDirectory.PACKED_REFS_PEELED);
			w.write('\n');
		}

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
	 * Handles actual writing of ref files to the git repository, which may
	 * differ slightly depending on the destination and transport.
	 *
	 * @param file
	 *            path to ref file.
	 * @param content
	 *            byte content of file to be written.
	 * @throws java.io.IOException
	 */
	protected abstract void writeFile(String file, byte[] content)
			throws IOException;
}
