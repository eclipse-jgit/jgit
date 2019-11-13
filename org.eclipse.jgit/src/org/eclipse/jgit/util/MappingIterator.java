/*
 * Copyright (c) 2019, Google LLC and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Iterator of type <D> over a collection of type <S>. The conversion from <S>
 * to <D> happens on demand.
 * <p>
 * Use this iterator when the conversion is expensive and probably won't be
 * needed for all elements.
 *
 * @param <S>
 *            Source type, type of the input collection
 * @param <D>
 *            Destination type (effective type of the iterator)
 * @since 5.6
 */
public class MappingIterator<S, D> implements Iterator<D> {

	private Iterator<S> sourceIt;

	private Function<S, D> transformation;

	private D next;

	private boolean nextIsSet;

	/**
	 * @param source
	 *            input collection. Do not modify while using this iterator.
	 * @param transformation
	 *            function to convert an S element to a D element. Return "null"
	 *            for errors. They will be ignored by the iterator.
	 */
	public MappingIterator(Collection<S> source, Function<S, D> transformation) {
		this.sourceIt = source.iterator();
		this.transformation = transformation;
	}

	private void readNext() {
		nextIsSet = true;

		if (!sourceIt.hasNext()) {
			next = null;
			return;
		}

		this.next = transformation.apply(sourceIt.next());
		while (next == null && sourceIt.hasNext()) {
			this.next = transformation.apply(sourceIt.next());
		}
	}

	@Override
	public boolean hasNext() {
		if (!nextIsSet) {
			readNext();
		}
		return next != null;
	}

	@Override
	public D next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		nextIsSet = false;
		return next;
	}
}
