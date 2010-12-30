/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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
package org.eclipse.jgit.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the result of a {@link CheckoutCommand}
 *
 */
public class CheckoutResult {

	/**
	 * The {@link Status#OK} result;
	 */
	public static final CheckoutResult OK_RESULT = new CheckoutResult(
			Status.OK, null);

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

	CheckoutResult(Status status, List<String> fileList) {
		myStatus = status;
		if (status == Status.CONFLICTS)
			this.conflictList = fileList;
		else
			this.conflictList = new ArrayList<String>(0);
		if (status == Status.NONDELETED)
			this.undeletedList = fileList;
		else
			this.undeletedList = new ArrayList<String>(0);

	}

	/**
	 * @return the status
	 */
	public Status getStatus() {
		return myStatus;
	}

	/**
	 * @return the list of files that created a checkout conflict, or an empty
	 *         list if {@link #getStatus()} is not {@link Status#CONFLICTS};
	 */
	public List<String> getConflictList() {
		return conflictList;
	}

	/**
	 * @return the list of files that could not be deleted during checkout, or
	 *         an empty list if {@link #getStatus()} is not
	 *         {@link Status#NONDELETED};
	 */
	public List<String> getUndeletedList() {
		return undeletedList;
	}

}
