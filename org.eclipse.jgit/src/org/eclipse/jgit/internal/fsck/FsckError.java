/*
 * Copyright (C) 2017, Google Inc.
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
package org.eclipse.jgit.internal.fsck;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.CorruptPackIndexException.ErrorType;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Holds all fsck errors of a git repository.
 */
public class FsckError {
	/** Represents a corrupt object. */
	public static class CorruptObject {
		final ObjectId id;

		final int type;

		final @Nullable ObjectChecker.ErrorType errorType;

		/**
		 * @param id
		 *            the object identifier.
		 * @param type
		 *            type of the object.
		 * @param errorType
		 *            kind of error
		 */
		public CorruptObject(ObjectId id, int type,
				@Nullable ObjectChecker.ErrorType errorType) {
			this.id = id;
			this.type = type;
			this.errorType = errorType;
		}

		/** @return identifier of the object. */
		public ObjectId getId() {
			return id;
		}

		/** @return type of the object. */
		public int getType() {
			return type;
		}

		/** @return error type of the corruption. */
		@Nullable
		public ObjectChecker.ErrorType getErrorType() {
			return errorType;
		}
	}

	/** Represents a corrupt pack index file. */
	public static class CorruptIndex {
		String fileName;

		CorruptPackIndexException.ErrorType errorType;

		/**
		 * @param fileName
		 *            the file name of the pack index.
		 * @param errorType
		 *            the type of error as reported in
		 *            {@link CorruptPackIndexException}.
		 */
		public CorruptIndex(String fileName, ErrorType errorType) {
			this.fileName = fileName;
			this.errorType = errorType;
		}

		/** @return the file name of the index file. */
		public String getFileName() {
			return fileName;
		}

		/** @return the error type of the corruption. */
		public ErrorType getErrorType() {
			return errorType;
		}
	}

	private final Set<CorruptObject> corruptObjects = new HashSet<>();

	private final Set<ObjectId> missingObjects = new HashSet<>();

	private final Set<CorruptIndex> corruptIndices = new HashSet<>();

	private final Set<String> nonCommitHeads = new HashSet<>();

	/**
	 * Get corrupt objects from all pack files
	 *
	 * @return corrupt objects from all pack files
	 */
	public Set<CorruptObject> getCorruptObjects() {
		return corruptObjects;
	}

	/**
	 * Get missing objects that should present in pack files
	 *
	 * @return missing objects that should present in pack files
	 */
	public Set<ObjectId> getMissingObjects() {
		return missingObjects;
	}

	/**
	 * Get corrupt index files associated with the packs
	 *
	 * @return corrupt index files associated with the packs
	 */
	public Set<CorruptIndex> getCorruptIndices() {
		return corruptIndices;
	}

	/**
	 * Get refs/heads/* which point to non-commit object
	 *
	 * @return refs/heads/* which point to non-commit object
	 */
	public Set<String> getNonCommitHeads() {
		return nonCommitHeads;
	}
}
