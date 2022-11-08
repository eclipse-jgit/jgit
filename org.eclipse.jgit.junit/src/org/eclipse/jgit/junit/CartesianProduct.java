/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * @since 6.4
 */
public class CartesianProduct {

	/**
	 * Computes the cartesian product of the given sets
	 *
	 * @param sets
	 * @return the cartesian product of the given sets
	 */
	public static Set<Set<Object>> compute(Set<?>... sets) {
		if (sets == null) {
			return Collections.emptySet();
		}
		return Arrays.stream(sets)
				// ignore null and empty sets
				.filter(set -> set != null && set.size() > 0)
				// represent set elements as Set<Object>
				.map(set -> set.stream().map(Set::<Object> of)
						// Stream<Set<Set<Object>>>
						.collect(Collectors.toSet()))
				// summation of pairs of inner sets
				.reduce((set1, set2) -> set1.stream()
						// combinations of inner sets
						.flatMap(inner1 -> set2.stream()
								// merge two inner sets into one
								.map(inner2 -> Stream.of(inner1, inner2)
										.flatMap(Set::stream)
										.collect(Collectors.toCollection(
												LinkedHashSet::new))))
						// return Set<Set<Object>>
						.collect(Collectors.toCollection(LinkedHashSet::new)))
				// otherwise an empty set
				.orElse(Collections.emptySet());
	}

	/**
	 * @param setOfSets
	 * @return stream of Arguments
	 */
	public static Stream<Arguments> toArgumentStream(
			Set<Set<Object>> setOfSets) {
		return setOfSets.stream().map(Arguments::arguments);
	}

	/**
	 * Convert an array to a set.
	 *
	 * @param <T>
	 *            type of the array elements
	 * @param list
	 *            the array to convert
	 * @return the result set
	 */
	public static <T> Set<T> toSet(T[] list) {
		return new HashSet<>(Arrays.asList(list));
	}

	/**
	 * Convert a collection to a set.
	 *
	 * @param <T>
	 *            type of the collection elements
	 * @param collection
	 *            the collection to convert
	 * @return the result set
	 */
	public static <T> Set<T> toSet(Collection<T> collection) {
		return new HashSet<>(collection);
	}
}
