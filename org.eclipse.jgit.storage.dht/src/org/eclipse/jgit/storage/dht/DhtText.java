/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/** Translation bundle for the DHT storage provider. */
public class DhtText extends TranslationBundle {
	/** @return an instance of this translation bundle. */
	public static DhtText get() {
		return NLS.getBundleFor(DhtText.class);
	}

	/***/ public String cannotInsertObject;
	/***/ public String corruptChunk;
	/***/ public String corruptCompressedObject;
	/***/ public String cycleInDeltaChain;
	/***/ public String databaseRequired;
	/***/ public String expectedObjectSizeDuringCopyAsIs;
	/***/ public String invalidCachedPackInfo;
	/***/ public String invalidChunkKey;
	/***/ public String invalidChunkMeta;
	/***/ public String invalidObjectIndexKey;
	/***/ public String invalidObjectInfo;
	/***/ public String invalidRefData;
	/***/ public String missingChunk;
	/***/ public String missingLongOffsetBase;
	/***/ public String nameRequired;
	/***/ public String noSavedTypeForBase;
	/***/ public String notTimeUnit;
	/***/ public String objectListSelectingName;
	/***/ public String objectListCountingFrom;
	/***/ public String objectTypeUnknown;
	/***/ public String packParserInvalidPointer;
	/***/ public String packParserRollbackFailed;
	/***/ public String recordingObjects;
	/***/ public String repositoryAlreadyExists;
	/***/ public String repositoryMustBeBare;
	/***/ public String shortCompressedObject;
	/***/ public String timeoutChunkMeta;
	/***/ public String timeoutLocatingRepository;
	/***/ public String tooManyObjectsInPack;
	/***/ public String unsupportedChunkIndex;
	/***/ public String unsupportedObjectTypeInChunk;
	/***/ public String wrongChunkPositionInCachedPack;
}
