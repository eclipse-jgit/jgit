/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.jgit.lib.ObjectId;

class FetchHeadRecord {
	ObjectId newValue;

	boolean notForMerge;

	String sourceName;

	URIish sourceURI;

	void write(final Writer pw) throws IOException {
		final String type;
		final String name;
		if (sourceName.startsWith(R_HEADS)) {
			type = "branch";
			name = sourceName.substring(R_HEADS.length());
		} else if (sourceName.startsWith(R_TAGS)) {
			type = "tag";
			name = sourceName.substring(R_TAGS.length());
		} else if (sourceName.startsWith(R_REMOTES)) {
			type = "remote branch";
			name = sourceName.substring(R_REMOTES.length());
		} else {
			type = "";
			name = sourceName;
		}

		pw.write(newValue.name());
		pw.write('\t');
		if (notForMerge)
			pw.write("not-for-merge");
		pw.write('\t');
		pw.write(type);
		pw.write(" '");
		pw.write(name);
		pw.write("' of ");
		pw.write(sourceURI.toString());
		pw.write("\n");
	}
}
