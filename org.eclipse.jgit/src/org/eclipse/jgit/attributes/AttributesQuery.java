/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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

package org.eclipse.jgit.attributes;

import org.eclipse.jgit.util.RawParseUtils;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class AttributesQuery {

	private final String basePath;

	private final AttributesQuery parent;

	private final Attributes attributes;

	/**
	 * @param attributes
	 * @param basePath
	 * @param parent
	 */
	public AttributesQuery(Attributes attributes, String basePath,
			AttributesQuery parent) {
        basePath = RawParseUtils.pathAddTrailingSlash(basePath);
        basePath = RawParseUtils.pathTrimLeadingSlash(basePath);
		this.attributes = attributes;
		this.basePath = basePath;
		this.parent = parent;
	}

	/**
	 * @param path
	 * @param collector
	 */
	public void collect(String path, AttributesCollector collector) {
		collect(path, collector, new HashSet<String>());
	}

	private boolean collect(String path, AttributesCollector collector,
			Set<String> keysToIgnore) {
		path = RawParseUtils.pathTrimLeadingSlash(path);

		if (path.startsWith(basePath)) {
			final String relativePath = path.substring(basePath.length());
			if (!attributes.collect(relativePath, collector, keysToIgnore))
				return false;
		}

		if (parent != null && !parent.collect(path, collector, keysToIgnore))
			return false;

		return true;
	}
}