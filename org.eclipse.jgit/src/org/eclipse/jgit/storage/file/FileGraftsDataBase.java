/*
 * Copyright (C) 2010, Robin Rosenberg
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
package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GraftsDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

class FileGraftsDataBase implements GraftsDatabase {
	private final File graftsFile;
	private FileSnapshot snapShot;

	private Map<AnyObjectId, List<ObjectId>> grafts;

	FileGraftsDataBase(File graftsFile) {
		this.graftsFile = graftsFile;
	}

	boolean exists() {
		return graftsFile.exists();
	}

	boolean isOutdated() {
		if (snapShot == null && graftsFile.exists())
			return true;
		if (snapShot != null && !graftsFile.exists()) {
			snapShot = null;
			return true;
		}
		return snapShot == null || snapShot.isModified(graftsFile);
	}

	public Map<AnyObjectId, List<ObjectId>> getGrafts() throws IOException {
		if (isOutdated())
			grafts = loadGrafts();
		return grafts;
	}

	Map<AnyObjectId, List<ObjectId>> loadGrafts()
		throws IOException {
		try {
			if (!graftsFile.exists()) {
				snapShot = null;
				return null;
			}
			HashMap<AnyObjectId, List<ObjectId>> ret = new HashMap<AnyObjectId, List<ObjectId>>();
			snapShot = FileSnapshot.save(graftsFile);
			byte[] graftData = IO.readFully(graftsFile);
			int p = 0;
			int line = 1;
			while (p < graftData.length) {
				ObjectId child = ObjectId.fromString(graftData, p);
				int endOfLine = RawParseUtils.nextLF(graftData, p);
				int parentCount = (endOfLine - p)
						/ Constants.OBJECT_ID_STRING_LENGTH - 1;
				p += Constants.OBJECT_ID_STRING_LENGTH;
				List<ObjectId> parents;
				if (parentCount == 0)
					parents = Collections.<ObjectId> emptyList();
				else if (parentCount == 1) {
					if (graftData[p] != ' ')
						throw new IOException("Invlid graft file at line "
								+ line + " in " + graftsFile);
					ObjectId parent = ObjectId.fromString(graftData, p + 1);
					p += 1 + Constants.OBJECT_ID_STRING_LENGTH;
					if (graftData[p] != '\n')
						throw new IOException("Invlid graft file at line "
								+ line + " in " + graftsFile);
					parents = Collections.<ObjectId> singletonList(parent);
				} else {
					parents = new ArrayList<ObjectId>(parentCount);
					while (p < endOfLine - 1) {
						if (graftData[p] != ' ')
							throw new IOException(
									"Invlid graft file at line " + line
											+ " in " + graftsFile);
						ObjectId parent = ObjectId.fromString(graftData,
								p + 1);
						p += 1 + Constants.OBJECT_ID_STRING_LENGTH;
						parents.add(parent);
					}
					if (graftData[p] != '\n')
						throw new IOException("Invlid graft file at line "
								+ line + " in " + graftsFile);
				}
				p += 1; // LF
				ret.put(child, parents);
				line++;
			}
			return ret;
		} catch (FileNotFoundException e) {
			// That's fine
			return null;
		}
	}
}