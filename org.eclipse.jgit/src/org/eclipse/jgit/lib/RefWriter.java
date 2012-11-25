/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.storage.file.RefDirectory;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Writes out refs to the {@link Constants#INFO_REFS} and
 * {@link Constants#PACKED_REFS} files.
 *
 * This class is abstract as the writing of the files must be handled by the
 * caller. This is because it is used by transport classes as well.
 */
public abstract class RefWriter {

	private final Collection<Ref> refs;

	/**
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(Collection<Ref> refs) {
		this.refs = RefComparator.sort(refs);
	}

	/**
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
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(RefList<Ref> refs) {
		this.refs = refs.asList();
	}

	/**
	 * Rebuild the {@link Constants#INFO_REFS}.
	 * <p>
	 * This method rebuilds the contents of the {@link Constants#INFO_REFS} file
	 * to match the passed list of references.
	 *
	 *
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writeInfoRefs() throws IOException {
		final StringWriter w = new StringWriter();
		final char[] tmp = new char[Constants.OBJECT_ID_STRING_LENGTH];
		for (final Ref r : refs) {
			if (Constants.HEAD.equals(r.getName())) {
				// Historically HEAD has never been published through
				// the INFO_REFS file. This is a mistake, but its the
				// way things are.
				//
				continue;
			}

			r.getObjectId().copyTo(tmp, w);
			w.write('\t');
			w.write(r.getName());
			w.write('\n');

			if (r.getPeeledObjectId() != null) {
				r.getPeeledObjectId().copyTo(tmp, w);
				w.write('\t');
				w.write(r.getName());
				w.write("^{}\n"); //$NON-NLS-1$
			}
		}
		writeFile(Constants.INFO_REFS, Constants.encode(w.toString()));
	}

	/**
	 * Rebuild the {@link Constants#PACKED_REFS} file.
	 * <p>
	 * This method rebuilds the contents of the {@link Constants#PACKED_REFS}
	 * file to match the passed list of references, including only those refs
	 * that have a storage type of {@link Ref.Storage#PACKED}.
	 *
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writePackedRefs() throws IOException {
		boolean peeled = false;
		for (final Ref r : refs) {
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
		for (final Ref r : refs) {
			if (r.getStorage() != Ref.Storage.PACKED)
				continue;

			r.getObjectId().copyTo(tmp, w);
			w.write(' ');
			w.write(r.getName());
			w.write('\n');

			if (r.getPeeledObjectId() != null) {
				w.write('^');
				r.getPeeledObjectId().copyTo(tmp, w);
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
	 * @throws IOException
	 */
	protected abstract void writeFile(String file, byte[] content)
			throws IOException;
}
