/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Collection;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Hook invoked by {@link org.eclipse.jgit.transport.UploadPack} before during
 * critical phases.
 * <p>
 * If any hook function throws
 * {@link org.eclipse.jgit.transport.ServiceMayNotContinueException} then
 * processing stops immediately and the exception is thrown up the call stack.
 * Most phases of UploadPack will try to report the exception's message text to
 * the end-user over the client's protocol connection.
 */
public interface PreUploadHook {
	/** A simple no-op hook. */
	PreUploadHook NULL = new PreUploadHook() {
		@Override
		public void onBeginNegotiateRound(UploadPack up,
				Collection<? extends ObjectId> wants, int cntOffered)
				throws ServiceMayNotContinueException {
			// Do nothing.
		}

		@Override
		public void onEndNegotiateRound(UploadPack up,
				Collection<? extends ObjectId> wants, int cntCommon,
				int cntNotFound, boolean ready)
				throws ServiceMayNotContinueException {
			// Do nothing.
		}

		@Override
		public void onSendPack(UploadPack up,
				Collection<? extends ObjectId> wants,
				Collection<? extends ObjectId> haves)
				throws ServiceMayNotContinueException {
			// Do nothing.
		}
	};

	/**
	 * Invoked before negotiation round is started.
	 *
	 * @param up
	 *            the upload pack instance handling the connection.
	 * @param wants
	 *            the list of wanted objects.
	 * @param cntOffered
	 *            number of objects the client has offered.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	void onBeginNegotiateRound(UploadPack up,
			Collection<? extends ObjectId> wants, int cntOffered)
			throws ServiceMayNotContinueException;

	/**
	 * Invoked after a negotiation round is completed.
	 *
	 * @param up
	 *            the upload pack instance handling the connection.
	 * @param wants
	 *            the list of wanted objects.
	 * @param cntCommon
	 *            number of objects this round found to be common. In a smart
	 *            HTTP transaction this includes the objects that were
	 *            previously found to be common.
	 * @param cntNotFound
	 *            number of objects in this round the local repository does not
	 *            have, but that were offered as potential common bases.
	 * @param ready
	 *            true if a pack is ready to be sent (the commit graph was
	 *            successfully cut).
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	void onEndNegotiateRound(UploadPack up,
			Collection<? extends ObjectId> wants, int cntCommon,
			int cntNotFound, boolean ready)
			throws ServiceMayNotContinueException;

	/**
	 * Invoked just before a pack will be sent to the client.
	 *
	 * @param up
	 *            the upload pack instance handling the connection.
	 * @param wants
	 *            the list of wanted objects. These may be RevObject or
	 *            RevCommit if the processed parsed them. Implementors should
	 *            not rely on the values being parsed.
	 * @param haves
	 *            the list of common objects. Empty on an initial clone request.
	 *            These may be RevObject or RevCommit if the processed parsed
	 *            them. Implementors should not rely on the values being parsed.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	void onSendPack(UploadPack up, Collection<? extends ObjectId> wants,
			Collection<? extends ObjectId> haves)
			throws ServiceMayNotContinueException;
}
