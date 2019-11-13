package org.eclipse.jgit.util;

import java.util.Collection;
import java.util.Iterator;
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
 */
public class TransIterator<S, D> implements Iterator<D> {

	private Iterator<S> sourceIt;

	private Function<S, D> transformation;

	/**
	 * @param source
	 *            input collection. Do not modify while using this iterator.
	 * @param transformation
	 *            function to convert an S element to a D element
	 */
	public TransIterator(Collection<S> source, Function<S, D> transformation) {
		this.sourceIt = source.iterator();
		this.transformation = transformation;
	}

	@Override
	public boolean hasNext() {
		return sourceIt.hasNext();
	}

	@Override
	public D next() {
		return transformation.apply(sourceIt.next());
	}
}
