/*
 * Copyright (C) 2010, 2013 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.PackRefsCommand;

/**
 * Abstraction of name to {@link org.eclipse.jgit.lib.ObjectId} mapping.
 * <p>
 * A reference database stores a mapping of reference names to
 * {@link org.eclipse.jgit.lib.ObjectId}. Every
 * {@link org.eclipse.jgit.lib.Repository} has a single reference database,
 * mapping names to the tips of the object graph contained by the
 * {@link org.eclipse.jgit.lib.ObjectDatabase}.
 */
public abstract class RefDatabase {
	/**
	 * Order of prefixes to search when using non-absolute references.
	 * <p>
	 * {@link #findRef(String)} takes this search space into consideration when
	 * locating a reference by name. The first entry in the path is always
	 * {@code ""}, ensuring that absolute references are resolved without
	 * further mangling.
	 */
	protected static final String[] SEARCH_PATH = { "", //$NON-NLS-1$
			Constants.R_REFS, //
			Constants.R_TAGS, //
			Constants.R_HEADS, //
			Constants.R_REMOTES //
	};

	/**
	 * Maximum number of times a {@link SymbolicRef} can be traversed.
	 * <p>
	 * If the reference is nested deeper than this depth, the implementation
	 * should either fail, or at least claim the reference does not exist.
	 *
	 * @since 4.2
	 */
	public static final int MAX_SYMBOLIC_REF_DEPTH = 5;

	/**
	 * Magic value for {@link #getRefsByPrefix(String)} to return all
	 * references.
	 */
	public static final String ALL = "";//$NON-NLS-1$

	/**
	 * The names of additional refs
	 *
	 * @since 6.5
	 */
	protected static final String[] additionalRefsNames = new String[] {
			Constants.MERGE_HEAD, Constants.FETCH_HEAD, Constants.ORIG_HEAD,
			Constants.CHERRY_PICK_HEAD, Constants.REVERT_HEAD };

	/**
	 * Initialize a new reference database at this location.
	 *
	 * @throws java.io.IOException
	 *             the database could not be created.
	 */
	public abstract void create() throws IOException;

	/**
	 * Close any resources held by this database.
	 */
	public abstract void close();

	/**
	 * With versioning, each reference has a version number that increases on
	 * update. See {@link Ref#getUpdateIndex()}.
	 *
	 * @implSpec This method returns false by default. Implementations
	 *           supporting versioning must override it to return true.
	 * @return true if the implementation assigns update indices to references.
	 * @since 5.3
	 */
	public boolean hasVersioning() {
		return false;
	}

	/**
	 * Determine if a proposed reference name overlaps with an existing one.
	 * <p>
	 * Reference names use '/' as a component separator, and may be stored in a
	 * hierarchical storage such as a directory on the local filesystem.
	 * <p>
	 * If the reference "refs/heads/foo" exists then "refs/heads/foo/bar" must
	 * not exist, as a reference cannot have a value and also be a container for
	 * other references at the same time.
	 * <p>
	 * If the reference "refs/heads/foo/bar" exists than the reference
	 * "refs/heads/foo" cannot exist, for the same reason.
	 *
	 * @param name
	 *            proposed name.
	 * @return true if the name overlaps with an existing reference; false if
	 *         using this name right now would be safe.
	 * @throws java.io.IOException
	 *             the database could not be read to check for conflicts.
	 * @see #getConflictingNames(String)
	 */
	public abstract boolean isNameConflicting(String name) throws IOException;

	/**
	 * Determine if a proposed reference cannot coexist with existing ones. If
	 * the passed name already exists, it's not considered a conflict.
	 *
	 * @param name
	 *            proposed name to check for conflicts against
	 * @return a collection of full names of existing refs which would conflict
	 *         with the passed ref name; empty collection when there are no
	 *         conflicts
	 * @throws java.io.IOException
	 *             if an error occurred
	 * @since 2.3
	 * @see #isNameConflicting(String)
	 */
	@NonNull
	public Collection<String> getConflictingNames(String name)
			throws IOException {
		Map<String, Ref> allRefs = getRefs(ALL);
		// Cannot be nested within an existing reference.
		int lastSlash = name.lastIndexOf('/');
		while (0 < lastSlash) {
			String needle = name.substring(0, lastSlash);
			if (allRefs.containsKey(needle))
				return Collections.singletonList(needle);
			lastSlash = name.lastIndexOf('/', lastSlash - 1);
		}

		List<String> conflicting = new ArrayList<>();
		// Cannot be the container of an existing reference.
		String prefix = name + '/';
		for (String existing : allRefs.keySet())
			if (existing.startsWith(prefix))
				conflicting.add(existing);

		return Collections.unmodifiableList(conflicting);
	}

	/**
	 * Create a new update command to create, modify or delete a reference.
	 *
	 * @param name
	 *            the name of the reference.
	 * @param detach
	 *            if {@code true} and {@code name} is currently a
	 *            {@link org.eclipse.jgit.lib.SymbolicRef}, the update will
	 *            replace it with an {@link org.eclipse.jgit.lib.ObjectIdRef}.
	 *            Otherwise, the update will recursively traverse
	 *            {@link org.eclipse.jgit.lib.SymbolicRef}s and operate on the
	 *            leaf {@link org.eclipse.jgit.lib.ObjectIdRef}.
	 * @return a new update for the requested name; never null.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 */
	@NonNull
	public abstract RefUpdate newUpdate(String name, boolean detach)
			throws IOException;

	/**
	 * Create a new update command to rename a reference.
	 *
	 * @param fromName
	 *            name of reference to rename from
	 * @param toName
	 *            name of reference to rename to
	 * @return an update command that knows how to rename a branch to another.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 */
	@NonNull
	public abstract RefRename newRename(String fromName, String toName)
			throws IOException;

	/**
	 * Create a new batch update to attempt on this database.
	 * <p>
	 * The default implementation performs a sequential update of each command.
	 *
	 * @return a new batch update object.
	 */
	@NonNull
	public BatchRefUpdate newBatchUpdate() {
		return new BatchRefUpdate(this);
	}

	/**
	 * Whether the database is capable of performing batch updates as atomic
	 * transactions.
	 * <p>
	 * If true, by default {@link org.eclipse.jgit.lib.BatchRefUpdate} instances
	 * will perform updates atomically, meaning either all updates will succeed,
	 * or all updates will fail. It is still possible to turn off this behavior
	 * on a per-batch basis by calling {@code update.setAtomic(false)}.
	 * <p>
	 * If false, {@link org.eclipse.jgit.lib.BatchRefUpdate} instances will
	 * never perform updates atomically, and calling
	 * {@code update.setAtomic(true)} will cause the entire batch to fail with
	 * {@code REJECTED_OTHER_REASON}.
	 * <p>
	 * This definition of atomicity is stronger than what is provided by
	 * {@link org.eclipse.jgit.transport.ReceivePack}. {@code ReceivePack} will
	 * attempt to reject all commands if it knows in advance some commands may
	 * fail, even if the storage layer does not support atomic transactions.
	 * Here, atomicity applies even in the case of unforeseeable errors.
	 *
	 * @return whether transactions are atomic by default.
	 * @since 3.6
	 */
	public boolean performsAtomicTransactions() {
		return false;
	}

	/**
	 * Read a single reference.
	 * <p>
	 * Aside from taking advantage of {@link #SEARCH_PATH}, this method may be
	 * able to more quickly resolve a single reference name than obtaining the
	 * complete namespace by {@code getRefs(ALL).get(name)}.
	 * <p>
	 * To read a specific reference without using @{link #SEARCH_PATH}, see
	 * {@link #exactRef(String)}.
	 *
	 * @param name
	 *            the name of the reference. May be a short name which must be
	 *            searched for using the standard {@link #SEARCH_PATH}.
	 * @return the reference (if it exists); else {@code null}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.3
	 */
	@Nullable
	public final Ref findRef(String name) throws IOException {
		String[] names = new String[SEARCH_PATH.length];
		for (int i = 0; i < SEARCH_PATH.length; i++) {
			names[i] = SEARCH_PATH[i] + name;
		}
		return firstExactRef(names);
	}

	/**
	 * Read a single reference.
	 * <p>
	 * Unlike {@link #findRef}, this method expects an unshortened reference
	 * name and does not search using the standard {@link #SEARCH_PATH}.
	 *
	 * @param name
	 *             the unabbreviated name of the reference.
	 * @return the reference (if it exists); else {@code null}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 4.1
	 */
	@Nullable
	public abstract Ref exactRef(String name) throws IOException;

	/**
	 * Read the specified references.
	 * <p>
	 * This method expects a list of unshortened reference names and returns
	 * a map from reference names to refs.  Any named references that do not
	 * exist will not be included in the returned map.
	 *
	 * @param refs
	 *             the unabbreviated names of references to look up.
	 * @return modifiable map describing any refs that exist among the ref
	 *         ref names supplied. The map can be an unsorted map.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 4.1
	 */
	@NonNull
	public Map<String, Ref> exactRef(String... refs) throws IOException {
		Map<String, Ref> result = new HashMap<>(refs.length);
		for (String name : refs) {
			Ref ref = exactRef(name);
			if (ref != null) {
				result.put(name, ref);
			}
		}
		return result;
	}

	/**
	 * Find the first named reference.
	 * <p>
	 * This method expects a list of unshortened reference names and returns
	 * the first that exists.
	 *
	 * @param refs
	 *             the unabbreviated names of references to look up.
	 * @return the first named reference that exists (if any); else {@code null}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 4.1
	 */
	@Nullable
	public Ref firstExactRef(String... refs) throws IOException {
		for (String name : refs) {
			Ref ref = exactRef(name);
			if (ref != null) {
				return ref;
			}
		}
		return null;
	}

	/**
	 * Returns all refs.
	 * <p>
	 * This includes {@code HEAD}, branches under {@code ref/heads/}, tags under
	 * {@code refs/tags/}, etc. It does not include pseudo-refs like
	 * {@code FETCH_HEAD}; for those, see {@link #getAdditionalRefs}.
	 * <p>
	 * Symbolic references to a non-existent ref (for example, {@code HEAD}
	 * pointing to a branch yet to be born) are not included.
	 * <p>
	 * Callers interested in only a portion of the ref hierarchy can call
	 * {@link #getRefsByPrefix} instead.
	 *
	 * @return immutable list of all refs.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.0
	 */
	@NonNull
	public List<Ref> getRefs() throws IOException {
		return getRefsByPrefix(ALL);
	}

	/**
	 * Get the reflog reader
	 *
	 * @param refName
	 *            a {@link java.lang.String} object.
	 * @return a {@link org.eclipse.jgit.lib.ReflogReader} for the supplied
	 *         refname, or {@code null} if the named ref does not exist.
	 * @throws java.io.IOException
	 *             the ref could not be accessed.
	 * @since 7.2
	 */
	@Nullable
	public ReflogReader getReflogReader(String refName) throws IOException {
		Ref ref = exactRef(refName);
		if (ref == null) {
			return null;
		}
		return getReflogReader(ref);
	}

	/**
	 * Get the reflog reader.
	 *
	 * @param ref
	 *            a Ref
	 * @return a {@link org.eclipse.jgit.lib.ReflogReader} for the supplied ref.
	 * @throws IOException
	 *             if an IO error occurred
	 * @since 7.2
	 */
	@NonNull
	public abstract ReflogReader getReflogReader(@NonNull Ref ref)
			throws IOException;

	/**
	 * Get a section of the reference namespace.
	 *
	 * @param prefix
	 *            prefix to search the namespace with; must end with {@code /}.
	 *            If the empty string ({@link #ALL}), obtain a complete snapshot
	 *            of all references.
	 * @return modifiable map that is a complete snapshot of the current
	 *         reference namespace, with {@code prefix} removed from the start
	 *         of each key. The map can be an unsorted map.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @deprecated use {@link #getRefsByPrefix} instead
	 */
	@NonNull
	@Deprecated
	public abstract Map<String, Ref> getRefs(String prefix) throws IOException;

	/**
	 * Returns refs whose names start with a given prefix.
	 * <p>
	 * The default implementation uses {@link #getRefs(String)}. Implementors of
	 * {@link RefDatabase} should override this method directly if a better
	 * implementation is possible.
	 *
	 * @param prefix
	 *            string that names of refs should start with; may be empty (to
	 *            return all refs).
	 * @return immutable list of refs whose names start with {@code prefix}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.0
	 */
	@NonNull
	public List<Ref> getRefsByPrefix(String prefix) throws IOException {
		Map<String, Ref> coarseRefs;
		int lastSlash = prefix.lastIndexOf('/');
		if (lastSlash == -1) {
			coarseRefs = getRefs(ALL);
		} else {
			coarseRefs = getRefs(prefix.substring(0, lastSlash + 1));
		}

		List<Ref> result;
		if (lastSlash + 1 == prefix.length()) {
			result = coarseRefs.values().stream().collect(toList());
		} else {
			String p = prefix.substring(lastSlash + 1);
			result = coarseRefs.entrySet().stream()
					.filter(e -> e.getKey().startsWith(p))
					.map(e -> e.getValue())
					.collect(toList());
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Returns refs whose names start with a given prefix excluding all refs
	 * that start with one of the given prefixes.
	 *
	 * <p>
	 * The default implementation is not efficient. Implementors of
	 * {@link RefDatabase} should override this method directly if a better
	 * implementation is possible.
	 *
	 * @param include
	 *            string that names of refs should start with; may be empty.
	 * @param excludes
	 *            strings that names of refs can't start with; may be empty.
	 * @return immutable list of refs whose names start with {@code prefix} and
	 *         none of the strings in {@code excludes}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.11
	 */
	@NonNull
	public List<Ref> getRefsByPrefixWithExclusions(String include, Set<String> excludes)
			throws IOException {
		Stream<Ref> refs = getRefs(include).values().stream();
		for(String exclude: excludes) {
			refs = refs.filter(r -> !r.getName().startsWith(exclude));
		}
		return Collections.unmodifiableList(refs.collect(Collectors.toList()));
	}

	/**
	 * Returns refs whose names start with one of the given prefixes.
	 * <p>
	 * The default implementation uses {@link #getRefsByPrefix(String)}.
	 * Implementors of {@link RefDatabase} should override this method directly
	 * if a better implementation is possible.
	 *
	 * @param prefixes
	 *            strings that names of refs should start with.
	 * @return immutable list of refs whose names start with one of
	 *         {@code prefixes}. Refs can be unsorted and may contain duplicates
	 *         if the prefixes overlap.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.2
	 */
	@NonNull
	public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
		List<Ref> result = new ArrayList<>();
		for (String prefix : prefixes) {
			result.addAll(getRefsByPrefix(prefix));
		}
		return Collections.unmodifiableList(result);
	}


	/**
	 * Returns all refs that resolve directly to the given {@link ObjectId}.
	 * Includes peeled {@link ObjectId}s. This is the inverse lookup of
	 * {@link #exactRef(String...)}.
	 *
	 * <p>
	 * The default implementation uses a linear scan. Implementors of
	 * {@link RefDatabase} should override this method directly if a better
	 * implementation is possible.
	 *
	 * @param id
	 *            {@link ObjectId} to resolve
	 * @return a {@link Set} of {@link Ref}s whose tips point to the provided
	 *         id.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.4
	 */
	@NonNull
	public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
		return getRefs().stream().filter(r -> id.equals(r.getObjectId())
				|| id.equals(r.getPeeledObjectId())).collect(toSet());
	}

	/**
	 * If the ref database does not support fast inverse queries, it may be
	 * advantageous to build a complete SHA1 to ref map in advance for multiple
	 * uses. To let applications decide on this decision, this function
	 * indicates whether the inverse map is available.
	 *
	 * @return whether this RefDatabase supports fast inverse ref queries.
	 * @throws IOException
	 *             on I/O problems.
	 * @since 5.6
	 */
	public boolean hasFastTipsWithSha1() throws IOException {
		return false;
	}

	/**
	 * Check if any refs exist in the ref database.
	 * <p>
	 * This uses the same definition of refs as {@link #getRefs()}. In
	 * particular, returns {@code false} in a new repository with no refs under
	 * {@code refs/} and {@code HEAD} pointing to a branch yet to be born, and
	 * returns {@code true} in a repository with no refs under {@code refs/} and
	 * a detached {@code HEAD} pointing to history.
	 *
	 * @return true if the database has refs.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 * @since 5.0
	 */
	public boolean hasRefs() throws IOException {
		return !getRefs().isEmpty();
	}

	/**
	 * Get the additional reference-like entities from the repository.
	 * <p>
	 * The result list includes non-ref items such as MERGE_HEAD and
	 * FETCH_RESULT cast to be refs. The names of these refs are not returned by
	 * <code>getRefs()</code> but are accepted by {@link #findRef(String)}
	 * and {@link #exactRef(String)}.
	 *
	 * @return a list of additional refs
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 */
	@NonNull
	public abstract List<Ref> getAdditionalRefs() throws IOException;

	/**
	 * Peel a possibly unpeeled reference by traversing the annotated tags.
	 * <p>
	 * If the reference cannot be peeled (as it does not refer to an annotated
	 * tag) the peeled id stays null, but
	 * {@link org.eclipse.jgit.lib.Ref#isPeeled()} will be true.
	 * <p>
	 * Implementors should check {@link org.eclipse.jgit.lib.Ref#isPeeled()}
	 * before performing any additional work effort.
	 *
	 * @param ref
	 *            The reference to peel
	 * @return {@code ref} if {@code ref.isPeeled()} is true; otherwise a new
	 *         Ref object representing the same data as Ref, but isPeeled() will
	 *         be true and getPeeledObjectId() will contain the peeled object
	 *         (or {@code null}).
	 * @throws java.io.IOException
	 *             the reference space or object space cannot be accessed.
	 */
	@NonNull
	public abstract Ref peel(Ref ref) throws IOException;

	/**
	 * Triggers a refresh of all internal data structures.
	 * <p>
	 * In case the RefDatabase implementation has internal caches this method
	 * will trigger that all these caches are cleared.
	 * <p>
	 * Implementors should overwrite this method if they use any kind of caches.
	 */
	public void refresh() {
		// nothing
	}

	/**
	 * Try to find the specified name in the ref map using {@link #SEARCH_PATH}.
	 *
	 * @param map
	 *            map of refs to search within. Names should be fully qualified,
	 *            e.g. "refs/heads/master".
	 * @param name
	 *            short name of ref to find, e.g. "master" to find
	 *            "refs/heads/master" in map.
	 * @return The first ref matching the name, or {@code null} if not found.
	 * @since 3.4
	 */
	@Nullable
	public static Ref findRef(Map<String, Ref> map, String name) {
		for (String prefix : SEARCH_PATH) {
			String fullname = prefix + name;
			Ref ref = map.get(fullname);
			if (ref != null)
				return ref;
		}
		return null;
	}

	/**
	 * Optimize pack ref storage.
	 *
	 * @param pm
	 *            a progress monitor
	 *
	 * @param packRefs
	 *            {@link PackRefsCommand} to control ref packing behavior
	 *
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 * @since 7.1
	 */
	public void packRefs(ProgressMonitor pm, PackRefsCommand packRefs)
			throws IOException {
		// nothing
	}
}
