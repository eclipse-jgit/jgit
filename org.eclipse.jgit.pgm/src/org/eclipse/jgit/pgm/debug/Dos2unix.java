/*
 * Copyright (C) 2012, Robin Rosenberg
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

package org.eclipse.jgit.pgm.debug;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;

@Command(usage = "[--d2u | --u2d] (--d2u is default)")
class Dos2unix extends TextBuiltin {

	@Option(name = "--d2u", metaVar = "Convert CRLF to LF")
	boolean d2u;

	@Option(name = "--u2d", metaVar = "Convert LF to CRLF")
	boolean u2d;

	@Override
	protected void run() throws Exception {
		if (d2u && u2d)
			throw new IllegalArgumentException(
					"Cannot use both --d2u and --u2d options");
		if (!d2u && !u2d)
			d2u = true;

		DirCache cache = db.lockDirCache();
		try {
			DirCacheEditor editor = cache.editor();
			ObjectReader reader = db.newObjectReader();
			ObjectInserter inserter = db.newObjectInserter();
			TreeWalk tw = new TreeWalk(reader);
			tw.reset();
			tw.addTree(new DirCacheIterator(cache));
			final int DI = 0;
			tw.setRecursive(true);
			byte[] binDetectBuf = new byte[8000];
			while (tw.next()) {
				if (FileMode.REGULAR_FILE.equals(tw.getFileMode(DI))) {
					DirCacheIterator d = tw.getTree(DI, DirCacheIterator.class);
					ObjectLoader open = reader.open(d.getEntryObjectId());
					if (open.getSize() > 8000) {
						ObjectStream openStream = open.openStream();
						int read = openStream.read(binDetectBuf);
						openStream.close();
						if (RawText.isBinary(binDetectBuf, read))
							continue;
					}
					byte[] bytes = open.getBytes();
					if (RawText.isBinary(bytes))
						continue;
					int op = 0;
					int ip = 0;
					byte[] outbytes = new byte[bytes.length * 2];
					if (d2u) {
						while (ip < bytes.length) {
							byte b1 = bytes[ip++];
							if (b1 == '\r' && ip < bytes.length) {
								byte b2 = bytes[ip];
								if (b2 == '\n') {
									outbytes[op++] = '\n';
									ip++;
									continue;
								}
							}
							outbytes[op++] = b1;
						}
					} else {
						// Unix to DOS
						while (ip < bytes.length) {
							byte b1 = bytes[ip++];
							if (b1 == '\n') {
								if (ip < 2 || bytes[ip - 2] == '\r') {
									// crlf in input, keep
								} else {
									outbytes[op++] = '\r';
								}
							}
							outbytes[op++] = b1;
						}
					}
					if (ip == op)
						continue;
					final ObjectId newId = inserter.idFor(Constants.OBJ_BLOB,
							outbytes, 0, op);
					if (!reader.has(newId))
						inserter.insert(Constants.OBJ_BLOB, outbytes, 0, op);

					if (!newId.equals(d.getEntryObjectId())) {
						editor.add(new DirCacheEditor.PathEdit(d
								.getDirCacheEntry()) {
							@Override
							public void apply(DirCacheEntry ent) {
								ent.setObjectId(newId);
							}
						});
					}
				}
			}
			editor.commit();
			reader.release();
			cache = null;
		} finally {
			if (cache != null)
				cache.unlock();
		}
	}
}
