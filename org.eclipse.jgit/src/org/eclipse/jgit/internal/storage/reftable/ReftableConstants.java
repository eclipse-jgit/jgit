/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

class ReftableConstants {
	static final byte[] FILE_HEADER_MAGIC = { 'R', 'E', 'F', 'T' };
	static final byte VERSION_1 = (byte) 1;

	static final int FILE_HEADER_LEN = 24;
	static final int FILE_FOOTER_LEN = 68;

	static final byte FILE_BLOCK_TYPE = 'R';
	static final byte REF_BLOCK_TYPE = 'r';
	static final byte OBJ_BLOCK_TYPE = 'o';
	static final byte LOG_BLOCK_TYPE = 'g';
	static final byte INDEX_BLOCK_TYPE = 'i';

	static final int VALUE_NONE = 0x0;
	static final int VALUE_1ID = 0x1;
	static final int VALUE_2ID = 0x2;
	static final int VALUE_SYMREF = 0x3;
	static final int VALUE_TYPE_MASK = 0x7;

	static final int LOG_NONE = 0x0;
	static final int LOG_DATA = 0x1;

	static final int MAX_BLOCK_SIZE = (1 << 24) - 1;
	static final int MAX_RESTARTS = 65535;

	static boolean isFileHeaderMagic(byte[] buf, int o, int n) {
		return (n - o) >= FILE_HEADER_MAGIC.length
				&& buf[o + 0] == FILE_HEADER_MAGIC[0]
				&& buf[o + 1] == FILE_HEADER_MAGIC[1]
				&& buf[o + 2] == FILE_HEADER_MAGIC[2]
				&& buf[o + 3] == FILE_HEADER_MAGIC[3];
	}

	static long reverseUpdateIndex(long time) {
		return 0xffffffffffffffffL - time;
	}

	private ReftableConstants() {
	}
}
