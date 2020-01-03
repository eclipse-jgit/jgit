/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for the Ketch implementation.
 */
public class KetchText extends TranslationBundle {
	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return instance of this translation bundle.
	 */
	public static KetchText get() {
		return NLS.getBundleFor(KetchText.class);
	}

	// @formatter:off
	/***/ public String accepted;
	/***/ public String cannotFetchFromLocalReplica;
	/***/ public String failed;
	/***/ public String invalidFollowerUri;
	/***/ public String leaderFailedToStore;
	/***/ public String localReplicaRequired;
	/***/ public String mismatchedTxnNamespace;
	/***/ public String outsideTxnNamespace;
	/***/ public String proposingUpdates;
	/***/ public String queuedProposalFailedToApply;
	/***/ public String starting;
	/***/ public String unsupportedVoterCount;
	/***/ public String waitingForQueue;
}
