/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

import java.util.Comparator;
import java.util.List;

/**
 * @author keunhong
 *
 * @param <T>
 */
public abstract class MinFinder<T> {
	/**
	 * Lists to find minimum in
	 */
	protected List<List<T>> lists;

	/**
	 * Number of elements
	 */
	protected int size;

	/**
	 * Comparator for finding minimum
	 */
	protected Comparator<T> comparator;

	/**
	 * @param lists
	 * @param comparator
	 * @param size
	 */
	public MinFinder(List<List<T>> lists, Comparator<T> comparator, int size) {
		this.comparator = comparator;
		this.size = size;
		update(lists, size);
	}

	/**
	 * @return minimum
	 */
	public abstract T peek();

	/**
	 * @return minimum and remove from list
	 */
	public abstract T pop();

	/**
	 * Updates lists
	 *
	 * @param lists
	 */
	public void update(List<List<T>> lists) {
		update(lists, 10 * lists.size());
	}

	/**
	 * Updates lists
	 *
	 * @param lists
	 * @param size
	 */
	public void update(List<List<T>> lists, int size) {
		this.lists = lists;
		this.size = size;
	}

	/**
	 * Clears the min finder
	 */
	public void clear() {
		this.lists.clear();
		this.size = 0;
	}
}
