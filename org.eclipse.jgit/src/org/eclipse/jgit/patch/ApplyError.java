/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com>
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
package org.eclipse.jgit.patch;

import java.io.IOException;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;

/** An error which occurred when applying a patch */
public class ApplyError {

	private FileHeader fileHeader;

	private ChangeType changeType;

	private char hunkContorlChar;

	private HunkHeader hunkHeader;

	private IOException ioException;

	/**
	 * @param fh
	 *            file header
	 * @param t
	 *            change type
	 * @param c
	 *            hunk control char
	 */
	public ApplyError(FileHeader fh, ChangeType t, char c) {
		this.fileHeader = fh;
		this.changeType = t;
		this.hunkContorlChar = c;
	}

	/**
	 * @param fh
	 *            file header
	 * @param t
	 *            change type
	 */
	public ApplyError(FileHeader fh, ChangeType t) {
		this(fh, t, (char) 0);

	}

	/**
	 * @param hh
	 *            hunk header
	 * @param c
	 *            hunk control char
	 */
	public ApplyError(HunkHeader hh, char c) {
		this(hh.getFileHeader(), ChangeType.MODIFY, c);
		this.hunkHeader = hh;
	}

	/**
	 * @param e
	 *            IO exception
	 */
	public ApplyError(IOException e) {
		this.ioException = e;
	}

	/**
	 * @return file header
	 */
	public FileHeader getFileHeader() {
		return fileHeader;
	}

	/**
	 * @return hunk header
	 */
	public HunkHeader getHunkHeader() {
		return hunkHeader;
	}

	/**
	 * @return change type
	 */
	public ChangeType getChangeType() {
		return changeType;
	}

	/**
	 * @return hunk control char
	 */
	public char getHunkControlChar() {
		return hunkContorlChar;
	}

	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		if (ioException != null) {
			r.append(ioException.getMessage());
		} else {
			r.append(getChangeType().name());
			if (getHunkControlChar() != 0) {
				r.append(","); //$NON-NLS-1$
				r.append(getHunkControlChar());
			}
			r.append(": at hunk "); //$NON-NLS-1$
			r.append(getHunkHeader());
			r.append("  in "); //$NON-NLS-1$
			r.append(getFileHeader());
		}
		return r.toString();
	}
}