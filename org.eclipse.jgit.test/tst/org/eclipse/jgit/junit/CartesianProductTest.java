package org.eclipse.jgit.junit;

import static org.eclipse.jgit.junit.CartesianProduct.compute;
import static org.eclipse.jgit.junit.CartesianProduct.toArgumentStream;
import static org.eclipse.jgit.junit.CartesianProduct.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CartesianProductTest {

	public enum Evaluation {
		Good, Bad
	}

	@Test
	@SuppressWarnings("boxing")
	void testSimpleCartesian() {
		Set<Set<Object>> params = compute( //
				toSet(new Integer[] { 1, 2 }), //
				toSet(new String[] { "X", "Y" }), //
				toSet(Evaluation.values()));
		Set<String> result = new HashSet<>();
		for (Set<Object> p : params) {
			result.add(p.toString());
		}
		assertEquals(8, result.size());
		assertTrue(result.contains("[1, X, Bad]"));
		assertTrue(result.contains("[1, X, Good]"));
		assertTrue(result.contains("[1, Y, Bad]"));
		assertTrue(result.contains("[1, Y, Good]"));
		assertTrue(result.contains("[2, X, Bad]"));
		assertTrue(result.contains("[2, X, Good]"));
		assertTrue(result.contains("[2, Y, Bad]"));
		assertTrue(result.contains("[2, Y, Good]"));

		Stream<Arguments> argsStream = toArgumentStream(params);
		Collection<Object[]> objects = argsStream.map(Arguments::get)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		for (Object o : objects) {
			System.out.println(o);
		}
	}

	@SuppressWarnings("boxing")
	private static Stream<Arguments> params() {
		return toArgumentStream(compute( //
				toSet(new Integer[] { 1, 2 }), //
				toSet(new String[] { "X", "Y" }), //
				toSet(Evaluation.values())));
	}

	@ParameterizedTest
	@MethodSource("params")
	void testUsingCartesianParams(Integer i, String s, Evaluation e) {
		System.out.println(
				String.format("integer: %d, String: %s, Enum: %s", i, s, e));
	}
}
