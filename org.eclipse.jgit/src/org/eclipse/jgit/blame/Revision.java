/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
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

import java.io.IOException;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.IO;

/**
 * Class containing all the lines present in a file revision.
 */
public class Revision {

	private String path;

	private int number;

	private RevCommit commit;

	private ObjectId blob;

	private Line[] lines;

	private int index;

	/**
	 * Create revision
	 *
	 * @param path
	 * @param size
	 */
	public Revision(String path, int size) {
		this.path = path;
		this.lines = new Line[size];
	}

	@Override
	public int hashCode() {
		return commit.hashCode() ^ blob.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof Revision) {
			Revision other = (Revision) obj;
			return this.number == other.number && this.path.equals(other.path);
		} else
			return false;
	}

	@Override
	public String toString() {
		return this.path + "#" + this.number;
	}

	/**
	 * Get path of revision
	 *
	 * @return file path
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return commit
	 */
	public RevCommit getCommit() {
		return this.commit;
	}

	/**
	 * @param commit
	 * @return this revision
	 */
	public Revision setCommit(RevCommit commit) {
		this.commit = commit;
		return this;
	}

	/**
	 * @param blob
	 * @return this revision
	 */
	public Revision setBlob(ObjectId blob) {
		this.blob = blob;
		return this;
	}

	/**
	 * Get blob
	 *
	 * @return blob id
	 */
	public ObjectId getBlob() {
		return this.blob;
	}

	/**
	 * Set revision number
	 *
	 * @param number
	 * @return revision
	 */
	public Revision setNumber(int number) {
		this.number = number;
		return this;
	}

	/**
	 * Get revision number
	 *
	 * @return number
	 */
	public int getNumber() {
		return this.number;
	}

	/**
	 * Add line
	 *
	 * @param line
	 * @return this revision
	 */
	public Revision addLine(Line line) {
		if (line != null) {
			line.setEnd(number);
			lines[index] = line.setNumber(index);
			index++;
		}
		return this;
	}

	/**
	 * Get number of lines in revison
	 *
	 * @return line count
	 */
	public int getLineCount() {
		return this.lines.length;
	}

	/**
	 * Get line at number
	 *
	 * @param lineNumber
	 * @return line
	 */
	public Line getLine(int lineNumber) {
		return lineNumber >= 0 && lineNumber < this.lines.length ? this.lines[lineNumber]
				: null;
	}

	/**
	 * Get lines
	 *
	 * @return array of lines
	 */
	public Line[] getLines() {
		return this.lines;
	}

	/**
	 * Get content of line at specified number
	 *
	 * @param repository
	 * @param lineNumber
	 * @return line
	 * @throws IOException
	 */
	public String getLineContent(Repository repository, int lineNumber)
			throws IOException {
		Line line = getLine(lineNumber);
		if (line == null)
			return null;
		ObjectLoader loader = repository.open(blob, Constants.OBJ_BLOB);
		byte[] contents;
		if (loader.isLarge())
			contents = IO.readWholeStream(loader.openStream(),
					(int) loader.getSize()).array();
		else
			contents = loader.getCachedBytes();
		return new RawText(contents).getString(lineNumber);
	}

}
