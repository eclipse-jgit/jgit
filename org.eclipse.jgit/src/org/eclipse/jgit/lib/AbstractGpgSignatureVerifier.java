/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Distribution License v. 1.0 which is available at
* https://www.eclipse.org/org/documents/edl-v10.php.
*
* SPDX-License-Identifier: BSD-3-Clause
*/
package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Provides a base implementation of
 * {@link GpgSignatureVerifier#verifySignature(RevObject, GpgConfig)}.
 *
 * @since 6.9
 */
public abstract class AbstractGpgSignatureVerifier
		implements GpgSignatureVerifier {

	@Override
	public SignatureVerification verifySignature(RevObject object,
			GpgConfig config) throws IOException {
		if (object instanceof RevCommit) {
			RevCommit commit = (RevCommit) object;
			byte[] signatureData = commit.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = commit.getRawBuffer();
			// Now remove the GPG signature
			byte[] header = { 'g', 'p', 'g', 's', 'i', 'g' };
			int start = RawParseUtils.headerStart(header, raw, 0);
			if (start < 0) {
				return null;
			}
			int end = RawParseUtils.nextLfSkippingSplitLines(raw, start);
			// start is at the beginning of the header's content
			start -= header.length + 1;
			// end is on the terminating LF; we need to skip that, too
			if (end < raw.length) {
				end++;
			}
			byte[] data = new byte[raw.length - (end - start)];
			System.arraycopy(raw, 0, data, 0, start);
			System.arraycopy(raw, end, data, start, raw.length - end);
			return verify(config, data, signatureData);
		} else if (object instanceof RevTag) {
			RevTag tag = (RevTag) object;
			byte[] signatureData = tag.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = tag.getRawBuffer();
			// The signature is just tacked onto the end of the message, which
			// is last in the buffer.
			byte[] data = Arrays.copyOfRange(raw, 0,
					raw.length - signatureData.length);
			return verify(config, data, signatureData);
		}
		return null;
	}
}
