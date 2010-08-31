/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

/**
 * Origin object representing the origin of a part of the file, usually there
 * should be one origin for each commit and path
 *
 *
 */
public class Origin {

	/**
	 *
	 */
	final protected RevCommit commit;


	/**
	 *
	 */
	final protected String filename;

	/**
	 *
	 */
	final protected Repository repository;

	/**
	 * creates a new Commit origin for a given commit and path
	 *
	 * @param repository
	 *            git repository for this origin
	 * @param commit
	 *            the commit object for this origin
	 * @param filename
	 *            the path of the file in this commit
	 */
	public Origin(Repository repository, RevCommit commit, String filename) {
		super();
		this.repository = repository;
		this.commit = commit;
		this.filename = filename;
	}

	/**
	 * get the ObjectId of the file, used for identifying identical versions
	 *
	 * @return object id
	 */
	public ObjectId getObjectId() {
		try {
			RevTree revTree = commit.getTree();
			TreeEntry blobEntry = repository.mapTree(revTree).findBlobMember(
					filename);
			if (blobEntry == null) {
				return ObjectId.zeroId();
			}
			return blobEntry.getId();
		} catch (Exception e) {
			throw new RuntimeException("Error retrieving data for origin "
					+ this, e);
		}
	}

	/**
	 * get the file contents at this commit
	 *
	 * @return an array of strings containing the file lines
	 */
	public byte[] getBytes() {
		try {
			RevTree revTree = commit.getTree();
			TreeEntry blobEntry = repository.mapTree(revTree).findBlobMember(
					filename);
			if (blobEntry == null) {
				// does not exist yet
				return new byte[0];
			}
			ObjectLoader objectLoader = repository.open(blobEntry.getId());
			return objectLoader.getBytes();
		} catch (Exception e) {
			throw new RuntimeException("Error retrieving data for origin "
					+ this);
		}
	}

	/**
	 * @return the associated commit
	 */
	public RevCommit getCommit() {
		return commit;
	}

	/**
	 * @return the repository of this Origin
	 */
	public Repository getRepository() {
		return repository;
	}


	@Override
	public String toString() {
		return filename + " --> " + commit;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((commit == null) ? 0 : commit.getId().hashCode());
		result = prime * result
				+ ((filename == null) ? 0 : filename.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Origin other = (Origin) obj;
		if (commit == null) {
			if (other.commit != null)
				return false;
		} else if (!AnyObjectId.equals(commit, other.commit))
			return false;
		if (filename == null) {
			if (other.filename != null)
				return false;
		} else if (!filename.equals(other.filename))
			return false;
		return true;
	}

}