/*
 * Copyright (C) 2011, Stefan Lay <stefan.lay@.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.diff;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.io.NullOutputStream;

/**
 * A DiffFormatter used to calculate the patch-id of the diff.
 */
public class PatchIdDiffFormatter extends DiffFormatter {

	private final MessageDigest digest;

	/**
	 * Initialize a formatter to compute a patch id.
	 */
	public PatchIdDiffFormatter() {
		super(new DigestOutputStream(NullOutputStream.INSTANCE,
				Constants.newMessageDigest()));
		digest = ((DigestOutputStream) getOutputStream()).getMessageDigest();
	}

	/**
	 * Should be called after having called one of the format methods
	 *
	 * @return the patch id calculated for the provided diff.
	 */
	public ObjectId getCalulatedPatchId() {
		return ObjectId.fromRaw(digest.digest());
	}

	/** {@inheritDoc} */
	@Override
	protected void writeHunkHeader(int aStartLine, int aEndLine,
			int bStartLine, int bEndLine) throws IOException {
		// The hunk header is not taken into account for patch id calculation
	}

	/** {@inheritDoc} */
	@Override
	protected void formatIndexLine(OutputStream o, DiffEntry ent)
			throws IOException {
		// The index line is not taken into account for patch id calculation
	}
}
