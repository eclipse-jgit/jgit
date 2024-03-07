package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.lib.Constants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helper static class used to determine effective paths from a list of
 * pathFilters.
 */
public class PathsCalculator {

	/**
	 * Between two sets of paths, return paths with the greatest shared scope.
	 * <p>
	 * i.e. OR(set("a", "b/c"), set("a/b", "b")) -> set("a", "b").
	 * <p>
	 * i.e. OR(set("a", "b/c", "f"), set("a/b", "b")) -> set("a", "b", "f").
	 *
	 * @param filters
	 *            a list of TreeFilter
	 * @return a set of paths, or empty if no path exist in any TreeFilter
	 * @since 6.8
	 */
	public static Optional<Set<byte[]>> OR(TreeFilter[] filters) {
		Set<String> allPaths = new HashSet<>();

		for (TreeFilter f : filters) {
			if (f.getPathsBestEffort().isPresent()) {
				Set<String> simplifiedSet = decodePaths(
						f.getPathsBestEffort().get());
				if (!simplifiedSet.isEmpty()) {
					allPaths.addAll(simplifiedSet);
				}
			}
		}

		return Optional.of(encodePaths(filterByGreatestScopes(allPaths)));
	}

	/**
	 * Between two sets of paths, return paths with the smallest shared scope.
	 * <p>
	 * i.e. AND(set("a", "b/c"), set("a/b", "b")) -> set("a/b", "b/c").
	 * <p>
	 * i.e. AND(set("a", "b/c", "f"), set("a/b", "b")) -> set().
	 *
	 * @param filters
	 *            a list of TreeFilter
	 * @return a set of paths, or empty if a path is not covered by all
	 *         PathFilter.
	 * @since 6.8
	 */
	public static Optional<Set<byte[]>> AND(TreeFilter[] filters) {
		ArrayList<Set<String>> listPathSet = new ArrayList<>();

		for (TreeFilter f : filters) {
			if (f.getPathsBestEffort().isEmpty()) {
				return Optional.empty();
			}
			Set<String> simplifiedSet = filterByGreatestScopes(
					decodePaths(f.getPathsBestEffort().get()));
			if (simplifiedSet.isEmpty()) {
				return Optional.empty();
			}
			listPathSet.add(simplifiedSet);
		}

		Set<String> effectivePaths = listPathSet.get(0);
		for (int i = 1; i < listPathSet.size(); i++) {
			Set<String> pathSet = listPathSet.get(i);
			effectivePaths = ANDPathsSet(effectivePaths, pathSet);
			if (effectivePaths.isEmpty()) {
				return Optional.empty();
			}
		}

		return Optional.of(encodePaths(effectivePaths));
	}

	private static Set<String> ANDPathsSet(Set<String> setA, Set<String> setB) {
		// two sets of paths don't share any scope if one is empty
		if (setA.isEmpty() || setB.isEmpty()) {
			return Collections.emptySet();
		}

		Set<String> unassociated = new HashSet<>();
		unassociated.addAll(setA);
		unassociated.addAll(setB);

		Set<String> result = new HashSet<>();

		for (String path : setA) {
			path = trimSlashes(path);
			int lastSlash = path.length();
			while (0 < lastSlash) {
				String scope = path.substring(0, lastSlash);
				if (setB.contains(scope)) {
					unassociated.remove(scope);
					unassociated.remove(path);
					result.add(path);
				}
				lastSlash = path.lastIndexOf('/', lastSlash - 1);
			}
		}

		for (String path : setB) {
			path = trimSlashes(path);
			int lastSlash = path.length();
			while (0 < lastSlash) {
				String scope = path.substring(0, lastSlash);
				if (setA.contains(scope)) {
					unassociated.remove(scope);
					unassociated.remove(path);
					result.add(path);
				}
				lastSlash = path.lastIndexOf('/', lastSlash - 1);
			}
		}

		if (!unassociated.isEmpty()) {
			// at least one path is not in scope of the opposite set
			return Collections.emptySet();
		}

		return result;
	}

	private static String trimSlashes(String s) {
		s = s.trim();
		if (s.startsWith("/")) {
			s = s.substring(1);
		}
		if (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static Set<String> filterByGreatestScopes(Set<String> paths) {
		Set<String> result = new HashSet<>();
		for (String path : paths) {
			path = trimSlashes(path);
			String greatestScope = path;
			int lastSlash = path.lastIndexOf('/');
			while (0 < lastSlash) {
				String scope = path.substring(0, lastSlash);
				if (paths.contains(scope))
					greatestScope = scope;
				lastSlash = path.lastIndexOf('/', lastSlash - 1);
			}
			result.add(greatestScope);
		}
		return result;
	}

	/**
	 * Convert a set of file paths from byte arrays to strings.
	 *
	 * @param paths
	 *            a set of paths.
	 * @return a set of paths in string form, or empty.
	 * @since 6.8
	 */
	public static Set<String> decodePaths(Set<byte[]> paths) {
		return paths.stream()
				.map(buffer -> UTF_8.decode(ByteBuffer.wrap(buffer)).toString())
				.collect(Collectors.toSet());
	}

	/**
	 * Convert a set of file paths from strings to byte arrays.
	 *
	 * @param paths
	 *            a set of paths.
	 * @return a set of paths in byte arrays form, or empty.
	 * @since 6.8
	 */
	public static Set<byte[]> encodePaths(Set<String> paths) {
		return paths.stream().map(Constants::encode)
				.collect(Collectors.toSet());
	}
}
