/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the result of a {@link org.eclipse.jgit.api.CheckoutCommand}
 */
public class CheckoutResult {

	/**
	 * The {@link Status#ERROR} result;
	 */
	public static final CheckoutResult ERROR_RESULT = new CheckoutResult(
			Status.ERROR, null);

	/**
	 * The {@link Status#NOT_TRIED} result;
	 */
	public static final CheckoutResult NOT_TRIED_RESULT = new CheckoutResult(
			Status.NOT_TRIED, null);

	/**
	 * The status
	 */
	public enum Status {
		/**
		 * The call() method has not yet been executed
		 */
		NOT_TRIED,
		/**
		 * Checkout completed normally
		 */
		OK,
		/**
		 * Checkout has not completed because of checkout conflicts
		 */
		CONFLICTS,
		/**
		 * Checkout has completed, but some files could not be deleted
		 */
		NONDELETED,
		/**
		 * An Exception occurred during checkout
		 */
		ERROR;
	}

	private final Status myStatus;

	private final List<String> conflictList;

	private final List<String> undeletedList;

	private final List<String> modifiedList;

	private final List<String> removedList;

	/**
	 * Create a new fail result. If status is {@link Status#CONFLICTS},
	 * <code>fileList</code> is a list of conflicting files, if status is
	 * {@link Status#NONDELETED}, <code>fileList</code> is a list of not deleted
	 * files. All other values ignore <code>fileList</code>. To create a result
	 * for {@link Status#OK}, see {@link #CheckoutResult(List, List)}.
	 *
	 * @param status
	 *            the failure status
	 * @param fileList
	 *            the list of files to store, status has to be either
	 *            {@link Status#CONFLICTS} or {@link Status#NONDELETED}.
	 */
	CheckoutResult(Status status, List<String> fileList) {
		this(status, fileList, null, null);
	}

	/**
	 * Create a new fail result. If status is {@link Status#CONFLICTS},
	 * <code>fileList</code> is a list of conflicting files, if status is
	 * {@link Status#NONDELETED}, <code>fileList</code> is a list of not deleted
	 * files. All other values ignore <code>fileList</code>. To create a result
	 * for {@link Status#OK}, see {@link #CheckoutResult(List, List)}.
	 *
	 * @param status
	 *            the failure status
	 * @param fileList
	 *            the list of files to store, status has to be either
	 *            {@link Status#CONFLICTS} or {@link Status#NONDELETED}.
	 * @param modified
	 *            the modified files
	 * @param removed
	 *            the removed files.
	 */
	CheckoutResult(Status status, List<String> fileList, List<String> modified,
			List<String> removed) {
		myStatus = status;
		if (status == Status.CONFLICTS)
			this.conflictList = fileList;
		else
			this.conflictList = new ArrayList<>(0);
		if (status == Status.NONDELETED)
			this.undeletedList = fileList;
		else
			this.undeletedList = new ArrayList<>(0);

		this.modifiedList = modified;
		this.removedList = removed;
	}

	/**
	 * Create a new OK result with modified and removed files.
	 *
	 * @param modified
	 *            the modified files
	 * @param removed
	 *            the removed files.
	 */
	CheckoutResult(List<String> modified, List<String> removed) {
		myStatus = Status.OK;

		this.conflictList = new ArrayList<>(0);
		this.undeletedList = new ArrayList<>(0);

		this.modifiedList = modified;
		this.removedList = removed;
	}

	/**
	 * Get status
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return myStatus;
	}

	/**
	 * Get list of file that created a checkout conflict
	 *
	 * @return the list of files that created a checkout conflict, or an empty
	 *         list if {@link #getStatus()} is not
	 *         {@link org.eclipse.jgit.api.CheckoutResult.Status#CONFLICTS};
	 */
	public List<String> getConflictList() {
		return conflictList;
	}

	/**
	 * Get the list of files that could not be deleted during checkout
	 *
	 * @return the list of files that could not be deleted during checkout, or
	 *         an empty list if {@link #getStatus()} is not
	 *         {@link org.eclipse.jgit.api.CheckoutResult.Status#NONDELETED};
	 */
	public List<String> getUndeletedList() {
		return undeletedList;
	}

	/**
	 * Get the list of files that where modified during checkout
	 *
	 * @return the list of files that where modified during checkout, or an
	 *         empty list if {@link #getStatus()} is not
	 *         {@link org.eclipse.jgit.api.CheckoutResult.Status#OK}
	 */
	public List<String> getModifiedList() {
		return modifiedList;
	}

	/**
	 * Get the list of files that where removed during checkout
	 *
	 * @return the list of files that where removed during checkout, or an empty
	 *         list if {@link #getStatus()} is not
	 *         {@link org.eclipse.jgit.api.CheckoutResult.Status#OK}
	 */
	public List<String> getRemovedList() {
		return removedList;
	}
}
