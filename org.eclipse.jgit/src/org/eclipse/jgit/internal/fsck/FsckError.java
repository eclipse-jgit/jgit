/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

		@Nullable
		final ObjectChecker.ErrorType errorType;

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
