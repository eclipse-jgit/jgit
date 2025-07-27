/*
 * Copyright (C) 2013, CloudBees, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.TypedConfigGetter.UNSET_INT;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.fnmatch.Matcher;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbrevConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevFlagSet;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Given a commit, show the most recent tag that is reachable from a commit.
 *
 * @since 3.2
 */
public class DescribeCommand extends GitCommand<String> {
	private final RevWalk w;

	/**
	 * Commit to describe.
	 */
	private RevCommit target;

	/**
	 * How many tags we'll consider as candidates.
	 * This can only go up to the number of flags JGit can support in a walk,
	 * which is 24.
	 */
	private int maxCandidates = 10;

	/**
	 * Whether to always use long output format or not.
	 */
	private boolean longDesc;

	/**
	 * Pattern matchers to be applied to tags under consideration.
	 */
	private List<FileNameMatcher> matchers = new ArrayList<>();

	/**
	 * Pattern matchers to be applied to tags for exclusion.
	 */
	private List<FileNameMatcher> excludeMatchers = new ArrayList<>();

	/**
	 * Whether to use all refs in the refs/ namespace
	 */
	private boolean useAll;

	/**
	 * Whether to use all tags (incl. lightweight) or not.
	 */
	private boolean useTags;

	/**
	 * Whether to show a uniquely abbreviated commit hash as a fallback or not.
	 */
	private boolean always;

	/**
	 * The prefix length to use when abbreviating a commit hash.
	 */
	private int abbrev = UNSET_INT;

	/**
	 * Constructor for DescribeCommand.
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected DescribeCommand(Repository repo) {
		super(repo);
		w = new RevWalk(repo);
		w.setRetainBody(false);
	}

	/**
	 * Sets the commit to be described.
	 *
	 * @param target
	 * 		A non-null object ID to be described.
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the supplied commit does not exist.
	 * @throws IncorrectObjectTypeException
	 *             the supplied id is not a commit or an annotated tag.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public DescribeCommand setTarget(ObjectId target) throws IOException {
		this.target = w.parseCommit(target);
		return this;
	}

	/**
	 * Sets the commit to be described.
	 *
	 * @param rev
	 *            Commit ID, tag, branch, ref, etc. See
	 *            {@link org.eclipse.jgit.lib.Repository#resolve(String)} for
	 *            allowed syntax.
	 * @return {@code this}
	 * @throws IncorrectObjectTypeException
	 *             the supplied id is not a commit or an annotated tag.
	 * @throws org.eclipse.jgit.api.errors.RefNotFoundException
	 *             the given rev didn't resolve to any object.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public DescribeCommand setTarget(String rev) throws IOException,
			RefNotFoundException {
		ObjectId id = repo.resolve(rev);
		if (id == null)
			throw new RefNotFoundException(MessageFormat.format(JGitText.get().refNotResolved, rev));
		return setTarget(id);
	}

	/**
	 * Determine whether always to use the long format or not. When set to
	 * <code>true</code> the long format is used even the commit matches a tag.
	 *
	 * @param longDesc
	 *            <code>true</code> if always the long format should be used.
	 * @return {@code this}
	 * @see <a
	 *      href="https://www.kernel.org/pub/software/scm/git/docs/git-describe.html"
	 *      >Git documentation about describe</a>
	 * @since 4.0
	 */
	public DescribeCommand setLong(boolean longDesc) {
		this.longDesc = longDesc;
		return this;
	}

	/**
	 * Instead of using only the annotated tags, use any ref found in refs/
	 * namespace. This option enables matching any known branch,
	 * remote-tracking branch, or lightweight tag.
	 *
	 * @param all
	 *            <code>true</code> enables matching any ref found in refs/
	 *            like setting option --all in c git
	 * @return {@code this}
	 * @since 5.10
	 */
	public DescribeCommand setAll(boolean all) {
		this.useAll = all;
		return this;
	}

	/**
	 * Instead of using only the annotated tags, use any tag found in refs/tags
	 * namespace. This option enables matching lightweight (non-annotated) tags
	 * or not.
	 *
	 * @param tags
	 *            <code>true</code> enables matching lightweight (non-annotated)
	 *            tags like setting option --tags in c git
	 * @return {@code this}
	 * @since 5.0
	 */
	public DescribeCommand setTags(boolean tags) {
		this.useTags = tags;
		return this;
	}

	/**
	 * Always describe the commit by eventually falling back to a uniquely
	 * abbreviated commit hash if no other name matches.
	 *
	 * @param always
	 *            <code>true</code> enables falling back to a uniquely
	 *            abbreviated commit hash
	 * @return {@code this}
	 * @since 5.4
	 */
	public DescribeCommand setAlways(boolean always) {
		this.always = always;
		return this;
	}

	/**
	 * Sets the prefix length to use when abbreviating an object SHA-1.
	 *
	 * @param abbrev
	 *            minimum length of the abbreviated string. Must be in the range
	 *            [{@value AbbrevConfig#MIN_ABBREV},
	 *            {@value Constants#OBJECT_ID_STRING_LENGTH}].
	 * @return {@code this}
	 * @since 6.1
	 */
	public DescribeCommand setAbbrev(int abbrev) {
		if (abbrev == 0) {
			this.abbrev = 0;
		} else {
			this.abbrev = AbbrevConfig.capAbbrev(abbrev);
		}
		return this;
	}

	private String longDescription(Ref tag, int depth, ObjectId tip)
			throws IOException {
		if (abbrev == 0) {
			return formatRefName(tag.getName());
		}
		return String.format("%s-%d-g%s", formatRefName(tag.getName()), //$NON-NLS-1$
				Integer.valueOf(depth),
				w.getObjectReader().abbreviate(tip, abbrev).name());
	}

	/**
	 * Sets one or more {@code glob(7)} patterns that tags must match to be
	 * considered. If multiple patterns are provided, tags only need match one
	 * of them.
	 *
	 * @param patterns
	 *            the {@code glob(7)} pattern or patterns
	 * @return {@code this}
	 * @throws org.eclipse.jgit.errors.InvalidPatternException
	 *             if the pattern passed in was invalid.
	 * @see <a href=
	 *      "https://www.kernel.org/pub/software/scm/git/docs/git-describe.html"
	 *      >Git documentation about describe</a>
	 * @since 4.9
	 */
	public DescribeCommand setMatch(String... patterns) throws InvalidPatternException {
		for (String p : patterns) {
			matchers.add(new FileNameMatcher(new Matcher(p, null)));
		}
		return this;
	}

	/**
	 * Sets one or more {@code glob(7)} patterns that tags must not match to be
	 * considered. If multiple patterns are provided, they will all be applied.
	 *
	 * @param patterns
	 *            the {@code glob(7)} pattern or patterns
	 * @return {@code this}
	 * @throws org.eclipse.jgit.errors.InvalidPatternException
	 *             if the pattern passed in was invalid.
	 * @see <a href=
	 *      "https://www.kernel.org/pub/software/scm/git/docs/git-describe.html"
	 *      >Git documentation about describe</a>
	 * @since 7.2
	 */
	public DescribeCommand setExclude(String... patterns) throws InvalidPatternException {
		for (String p : patterns) {
			excludeMatchers.add(new FileNameMatcher(new Matcher(p, null)));
		}
		return this;
	}

	private final Comparator<Ref> TAG_TIE_BREAKER = new Comparator<>() {

		@Override
		public int compare(Ref o1, Ref o2) {
			try {
				return tagDate(o2).compareTo(tagDate(o1));
			} catch (IOException e) {
				return 0;
			}
		}

		private Instant tagDate(Ref tag) throws IOException {
			RevTag t = w.parseTag(tag.getObjectId());
			w.parseBody(t);
			return t.getTaggerIdent().getWhenAsInstant();
		}
	};

	private Optional<Ref> getBestMatch(List<Ref> tags) {
		if (tags == null || tags.isEmpty()) {
			return Optional.empty();
		} else if (matchers.isEmpty() && excludeMatchers.isEmpty()) {
			Collections.sort(tags, TAG_TIE_BREAKER);
			return Optional.of(tags.get(0));
		}

		Stream<Ref> matchingTags;
		if (!matchers.isEmpty()) {
			// Find the first tag that matches in the stream of all tags
			// filtered by matchers ordered by tie break order
			matchingTags = Stream.empty();
			for (FileNameMatcher matcher : matchers) {
				Stream<Ref> m = tags.stream().filter( //
						tag -> {
							matcher.append(formatRefName(tag.getName()));
							boolean result = matcher.isMatch();
							matcher.reset();
							return result;
						});
				matchingTags = Stream.of(matchingTags, m).flatMap(i -> i);
			}
		} else {
			// If there are no matchers, there are only excluders
			// Assume all tags match for now before applying excluders
			matchingTags = tags.stream();
		}

		for (FileNameMatcher matcher : excludeMatchers) {
			matchingTags = matchingTags.filter( //
					tag -> {
						matcher.append(formatRefName(tag.getName()));
						boolean result = matcher.isMatch();
						matcher.reset();
						return !result;
					});
		}
		return matchingTags.sorted(TAG_TIE_BREAKER).findFirst();
	}

	private ObjectId getObjectIdFromRef(Ref r) throws JGitInternalException {
		try {
			ObjectId key = repo.getRefDatabase().peel(r).getPeeledObjectId();
			if (key == null) {
				key = r.getObjectId();
			}
			return key;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Describes the specified commit. Target defaults to HEAD if no commit was
	 * set explicitly.
	 */
	@Override
	public String call() throws GitAPIException {
		try {
			checkCallable();
			if (target == null) {
				setTarget(Constants.HEAD);
			}
			if (abbrev == UNSET_INT) {
				abbrev = AbbrevConfig.parseFromConfig(repo).get();
			}

			Collection<Ref> tagList = repo.getRefDatabase()
					.getRefsByPrefix(useAll ? R_REFS : R_TAGS);
			Map<ObjectId, List<Ref>> tags = tagList.stream()
					.filter(this::filterLightweightTags)
					.collect(Collectors.groupingBy(this::getObjectIdFromRef));

			// combined flags of all the candidate instances
			final RevFlagSet allFlags = new RevFlagSet();

			/**
			 * Tracks the depth of each tag as we find them.
			 */
			class Candidate {
				final Ref tag;
				final RevFlag flag;

				/**
				 * This field counts number of commits that are reachable from
				 * the tip but not reachable from the tag.
				 */
				int depth;

				Candidate(RevCommit commit, Ref tag) {
					this.tag = tag;
					this.flag = w.newFlag(tag.getName());
					// we'll mark all the nodes reachable from this tag accordingly
					allFlags.add(flag);
					w.carry(flag);
					commit.add(flag);
					// As of this writing, JGit carries a flag from a child to its parents
					// right before RevWalk.next() returns, so all the flags that are added
					// must be manually carried to its parents. If that gets fixed,
					// this will be unnecessary.
					commit.carry(flag);
				}

				/**
				 * Does this tag contain the given commit?
				 */
				boolean reaches(RevCommit c) {
					return c.has(flag);
				}

				String describe(ObjectId tip) throws IOException {
					return longDescription(tag, depth, tip);
				}

			}
			List<Candidate> candidates = new ArrayList<>();    // all the candidates we find

			// is the target already pointing to a suitable tag? if so, we are done!
			Optional<Ref> bestMatch = getBestMatch(tags.get(target));
			if (bestMatch.isPresent()) {
				return longDesc ? longDescription(bestMatch.get(), 0, target) :
						formatRefName(bestMatch.get().getName());
			}

			w.markStart(target);

			int seen = 0;   // commit seen thus far
			RevCommit c;
			while ((c = w.next()) != null) {
				if (!c.hasAny(allFlags)) {
					// if a tag already dominates this commit,
					// then there's no point in picking a tag on this commit
					// since the one that dominates it is always more preferable
					bestMatch = getBestMatch(tags.get(c));
					if (bestMatch.isPresent()) {
						Candidate cd = new Candidate(c, bestMatch.get());
						candidates.add(cd);
						cd.depth = seen;
					}
				}

				// if the newly discovered commit isn't reachable from a tag that we've seen
				// it counts toward the total depth.
				for (Candidate cd : candidates) {
					if (!cd.reaches(c))
						cd.depth++;
				}

				// if we have search going for enough tags, we will start
				// closing down. JGit can only give us a finite number of bits,
				// so we can't track all tags even if we wanted to.
				if (candidates.size() >= maxCandidates)
					break;

				// TODO: if all the commits in the queue of RevWalk has allFlags
				// there's no point in continuing search as we'll not discover any more
				// tags. But RevWalk doesn't expose this.
				seen++;
			}

			// at this point we aren't adding any more tags to our search,
			// but we still need to count all the depths correctly.
			while ((c = w.next()) != null) {
				if (c.hasAll(allFlags)) {
					// no point in visiting further from here, so cut the search here
					for (RevCommit p : c.getParents())
						p.add(RevFlag.SEEN);
				} else {
					for (Candidate cd : candidates) {
						if (!cd.reaches(c))
							cd.depth++;
					}
				}
			}

			// if all the nodes are dominated by all the tags, the walk stops
			if (candidates.isEmpty()) {
				return always
						? w.getObjectReader()
								.abbreviate(target,
										AbbrevConfig.capAbbrev(abbrev))
								.name()
						: null;
			}

			Candidate best = Collections.min(candidates,
					(Candidate o1, Candidate o2) -> o1.depth - o2.depth);

			return best.describe(target);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
			w.close();
		}
	}

	/**
	 * Removes the refs/ or refs/tags prefix from tag names
	 * @param name the name of the tag
	 * @return the tag name with its prefix removed
	 */
	private String formatRefName(String name) {
		return name.startsWith(R_TAGS) ? name.substring(R_TAGS.length()) :
				name.substring(R_REFS.length());
	}

	/**
	 * Whether we use lightweight tags or not for describe Candidates
	 *
	 * @param ref
	 *            reference under inspection
	 * @return true if it should be used for describe or not regarding
	 *         {@link org.eclipse.jgit.api.DescribeCommand#useTags}
	 */
	@SuppressWarnings("null")
	private boolean filterLightweightTags(Ref ref) {
		ObjectId id = ref.getObjectId();
		try {
			return this.useAll || this.useTags || (id != null && (w.parseTag(id) != null));
		} catch (IOException e) {
			return false;
		}
	}
}
