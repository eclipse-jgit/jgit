/*
 * Copyright (C) 2013 Anders Martinsson
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

package org.eclipse.jgit.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Anders Martinsson
 * @author anders.martinsson@tieto.com
 * @param <K>
 * @param <V>
 *
 */
public class SizeLimitedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

	private int maxSize = 100;

	protected boolean removeEldestEntry(Map.Entry eldest) {
		return size() > maxSize;
	}

	/**
	 * Sets the hash size limit. Removes oldest elements until new size limit is
	 * reached if needed.
	 *
	 * @param maxSize
	 *            The new size limit.
	 *
	 */
	public void setSizeLimit(int maxSize) {
		K key;
		while (size() > maxSize && size() > 0) {
			key = keySet().iterator().next();
			remove(key);
		}
		this.maxSize = maxSize;
	}

	/**
	 * @return the current size limit of this HashMap
	 */
	public int getSizeLimit() {
		return maxSize;
	}
}
