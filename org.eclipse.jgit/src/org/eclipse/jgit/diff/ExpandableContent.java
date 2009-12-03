/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.diff;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents content as a sequence of bytes. After creation more content
 * can be added to the end of this content by specifying a region in a RawText
 * containing the content to be added.
 * @todo enhance documentation
 */
public class ExpandableContent {
	class ContentChunk {
		byte[] content;
		int from;
		int to;
		public ContentChunk(byte[] content, int from, int to) {
			this.content = content;
			this.from = from;
			this.to = to;
		}
	}

	// The list of content chunks which make up the content this object represents
	private List<ContentChunk> chunks=new LinkedList<ContentChunk>();

	/**
	 * Adds more content
	 *
	 * @param text the text containing the content to be added. Not the complete text is added (see below)
	 * @param startLine the first line which is added to the content
	 * @param endLine the last line which is added
	 */
	public void add(RawText text, int startLine, int endLine) {

		int from = text.lines.get(startLine+1);
		int to = text.lines.get(endLine+1);
		chunks.add(new ContentChunk(text.content, from, to));
	}

	/**
	 * Adds more content
	 *
	 * @param text the text containing the content to be added. The complete text is added
	 */
	public void add(RawText text) {
		add(text, 0, text.size());
	}

	public String toString() {
		StringBuilder ret=new StringBuilder();
		for (ContentChunk c:chunks) {
			ret.append(new String(c.content, c.from, c.to-c.from));
		}
		return(ret.toString());
	}
}
