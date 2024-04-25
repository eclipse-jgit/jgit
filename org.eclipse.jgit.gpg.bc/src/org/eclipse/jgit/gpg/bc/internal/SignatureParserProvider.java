/*
 * Copyright (C) 2024, GerritForge Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class SignatureParserProvider {

	private static Map<String, SignatureParser> signatureParserByPrefixMap = new HashMap<>();

	static {
		GpgSignatureParser.register();
		CmsSignatureParser.register();
	}

	static void registerSignatureParser(String signaturePrefix, SignatureParser signatureParser) {
		SignatureParser previousParser = signatureParserByPrefixMap.put(signaturePrefix.trim(), signatureParser);
		if (previousParser != null) {
			previousParser.clear();
		}
	}

	@Nullable
	static SignatureParser get(byte[] signatureData) {
		String signatureString = new String(signatureData, StandardCharsets.UTF_8);
		int firstLineEnd = signatureString.indexOf('\n');
		if (firstLineEnd <= 0) {
			return null;
		}
		String prefix = signatureString.substring(0, firstLineEnd).trim();
		return signatureParserByPrefixMap.get(prefix);
	}

	public static void clear() {
		signatureParserByPrefixMap.values().forEach(SignatureParser::clear);
	}
}
