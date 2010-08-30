/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;
import static org.eclipse.jgit.util.RawParseUtils.parseBase10;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.objectcheckers.CommitChecker;
import org.eclipse.jgit.lib.objectcheckers.IObjectChecker;
import org.eclipse.jgit.lib.objectcheckers.TagChecker;
import org.eclipse.jgit.lib.objectcheckers.TreeChecker;
import org.eclipse.jgit.util.MutableInteger;

/**
 * Verifies that an object is formatted correctly.
 * <p>
 * Verifications made by this class only check that the fields of an object are
 * formatted correctly. The ObjectId checksum of the object is not verified, and
 * connectivity links between objects are also not verified. Its assumed that
 * the caller can provide both of these validations on its own.
 * <p>
 * Instances of this class are not thread safe, but they may be reused to
 * perform multiple object validations.
 */
public class ObjectChecker {
	/** Header "tree " */
	public static final byte[] tree = Constants.encodeASCII("tree ");

	/** Header "parent " */
	public static final byte[] parent = Constants.encodeASCII("parent ");

	/** Header "author " */
	public static final byte[] author = Constants.encodeASCII("author ");

	/** Header "committer " */
	public static final byte[] committer = Constants.encodeASCII("committer ");

	/** Header "encoding " */
	public static final byte[] encoding = Constants.encodeASCII("encoding ");

	/** Header "object " */
	public static final byte[] object = Constants.encodeASCII("object ");

	/** Header "type " */
	public static final byte[] type = Constants.encodeASCII("type ");

	/** Header "tag " */
	public static final byte[] tag = Constants.encodeASCII("tag ");

	/** Header "tagger " */
	public static final byte[] tagger = Constants.encodeASCII("tagger ");

	Map<Integer, IObjectChecker> map;

    public ObjectChecker() {
        map = new HashMap<Integer, IObjectChecker>(4);
        map.put(OBJ_COMMIT,new CommitChecker());
        map.put(OBJ_TAG,new TagChecker());
        map.put(OBJ_TREE,new TreeChecker());
        map.put(OBJ_BLOB, null); // No verification for blobs, we just want to recognise it as a valid type
    }

	/**
	 * Check an object for parsing errors.
	 *
	 * @param objType
	 *            type of the object. Must be a valid object type code in
	 *            {@link Constants}.
	 * @param raw
	 *            the raw data which comprises the object. This should be in the
	 *            canonical format (that is the format used to generate the
	 *            ObjectId of the object). The array is never modified.
	 * @throws CorruptObjectException
	 *             if an error is identified.
	 */
	public void check(final int objType, final byte[] raw) throws CorruptObjectException {
        IObjectChecker objectChecker = checkerFor(objType);
        if (objectChecker!=null) {
            objectChecker.check(raw);
        }
	}

    public IObjectChecker checkerFor(int objType) throws CorruptObjectException {
        if (!map.containsKey(objType)) {
            throw new CorruptObjectException(MessageFormat.format(JGitText.get().corruptObjectInvalidType2, objType));
        }
        return map.get(objType);
    }

}
