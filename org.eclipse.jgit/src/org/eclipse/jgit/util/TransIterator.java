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

	private D next;

	/**
	 * @param source
	 *            input collection. Do not modify while using this iterator.
	 * @param transformation
	 *            function to convert an S element to a D element. Return "null"
	 *            for errors. They will be ignored by the iterator.
	 */
	public TransIterator(Collection<S> source, Function<S, D> transformation) {
		this.sourceIt = source.iterator();
		this.transformation = transformation;
		readNext();
	}

	private void readNext() {
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
		return next != null;
	}

	@Override
	public D next() {
		D result = next;
		readNext();
		return result;
	}
}
