/*
 * Copyright (C) 2008, 2013 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Objects;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;

/**
 * Describes how refs in one repository copy into another repository.
 * <p>
 * A ref specification provides matching support and limited rules to rewrite a
 * reference in one repository to another reference in another repository.
 */
public class RefSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Suffix for wildcard ref spec component, that indicate matching all refs
	 * with specified prefix.
	 */
	public static final String WILDCARD_SUFFIX = "/*"; //$NON-NLS-1$

	/**
	 * Check whether provided string is a wildcard ref spec component.
	 *
	 * @param s
	 *            ref spec component - string to test. Can be null.
	 * @return true if provided string is a wildcard ref spec component.
	 */
	public static boolean isWildcard(String s) {
		return s != null && s.contains("*"); //$NON-NLS-1$
	}

	/** Does this specification ask for forced updated (rewind/reset)? */
	private boolean force;

	/** Is this specification actually a wildcard match? */
	private boolean wildcard;

	/** Is this the special ":" RefSpec? */
	private boolean matching;

	/** Is this a negative refspec*/
	private boolean negative;

	/**
	 * How strict to be about wildcards.
	 *
	 * @since 4.5
	 */
	public enum WildcardMode {
		/**
		 * Reject refspecs with an asterisk on the source side and not the
		 * destination side or vice versa. This is the mode used by FetchCommand
		 * and PushCommand to create a one-to-one mapping between source and
		 * destination refs.
		 */
		REQUIRE_MATCH,
		/**
		 * Allow refspecs with an asterisk on only one side. This can create a
		 * many-to-one mapping between source and destination refs, so
		 * expandFromSource and expandFromDestination are not usable in this
		 * mode.
		 */
		ALLOW_MISMATCH
	}

	/** Whether a wildcard is allowed on one side but not the other. */
	private WildcardMode allowMismatchedWildcards;

	/** Name of the ref(s) we would copy from. */
	private String srcName;

	/** Name of the ref(s) we would copy into. */
	private String dstName;

	/**
	 * Construct an empty RefSpec.
	 * <p>
	 * A newly created empty RefSpec is not suitable for use in most
	 * applications, as at least one field must be set to match a source name.
	 */
	public RefSpec() {
		matching = false;
		force = false;
		wildcard = false;
		srcName = Constants.HEAD;
		dstName = null;
		negative =false;
		allowMismatchedWildcards = WildcardMode.REQUIRE_MATCH;
	}

	/**
	 * Parse a ref specification for use during transport operations.
	 * <p>
	 * Specifications are typically one of the following forms:
	 * <ul>
	 * <li><code>refs/heads/master</code></li>
	 * <li><code>refs/heads/master:refs/remotes/origin/master</code></li>
	 * <li><code>refs/heads/*:refs/remotes/origin/*</code></li>
	 * <li><code>+refs/heads/master</code></li>
	 * <li><code>+refs/heads/master:refs/remotes/origin/master</code></li>
	 * <li><code>+refs/heads/*:refs/remotes/origin/*</code></li>
	 * <li><code>+refs/pull/&#42;/head:refs/remotes/origin/pr/*</code></li>
	 * <li><code>:refs/heads/master</code></li>
	 * </ul>
	 *
	 * If the wildcard mode allows mismatches, then these ref specs are also
	 * valid:
	 * <ul>
	 * <li><code>refs/heads/*</code></li>
	 * <li><code>refs/heads/*:refs/heads/master</code></li>
	 * </ul>
	 *
	 * @param spec
	 *            string describing the specification.
	 * @param mode
	 *            whether to allow a wildcard on one side without a wildcard on
	 *            the other.
	 * @throws java.lang.IllegalArgumentException
	 *             the specification is invalid.
	 * @since 4.5
	 */
	public RefSpec(String spec, WildcardMode mode) {
		this.allowMismatchedWildcards = mode;
		String s = spec;

		if (s.startsWith("^+") || s.startsWith("+^")) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().invalidRefSpec, spec));
		}

		if (s.startsWith("+")) { //$NON-NLS-1$
			force = true;
			s = s.substring(1);
		}

		if(s.startsWith("^")) {
			negative = true;
			s = s.substring(1);
		}

		boolean matchPushSpec = false;
		final int c = s.lastIndexOf(':');
		if (c == 0) {
			s = s.substring(1);
			if (s.isEmpty()) {
				matchPushSpec = true;
				wildcard = true;
				srcName = Constants.R_HEADS + '*';
				dstName = srcName;
			} else {
				if (isWildcard(s)) {
					wildcard = true;
					if (mode == WildcardMode.REQUIRE_MATCH) {
						throw new IllegalArgumentException(MessageFormat
								.format(JGitText.get().invalidWildcards, spec));
					}
				}
				dstName = checkValid(s);
			}
		} else if (c > 0) {
			String src = s.substring(0, c);
			String dst = c == s.length() - 1 ? null : s.substring(c + 1);
			if (isWildcard(src) && isWildcard(dst)) {
				// Both contain wildcard
				wildcard = true;
			} else if (isWildcard(src) || isWildcard(dst)) {
				wildcard = true;
				if (mode == WildcardMode.REQUIRE_MATCH)
					throw new IllegalArgumentException(MessageFormat
							.format(JGitText.get().invalidWildcards, spec));
			}
			srcName = checkValid(src);
			dstName = checkValid(dst);
		} else {
			if (isWildcard(s)) {
				if (mode == WildcardMode.REQUIRE_MATCH) {
					throw new IllegalArgumentException(MessageFormat
							.format(JGitText.get().invalidWildcards, spec));
				}
				wildcard = true;
			}
			srcName = checkValid(s);
		}
		matching = matchPushSpec;
	}

	/**
	 * Parse a ref specification for use during transport operations.
	 * <p>
	 * Specifications are typically one of the following forms:
	 * <ul>
	 * <li><code>refs/heads/master</code></li>
	 * <li><code>refs/heads/master:refs/remotes/origin/master</code></li>
	 * <li><code>refs/heads/*:refs/remotes/origin/*</code></li>
	 * <li><code>+refs/heads/master</code></li>
	 * <li><code>+refs/heads/master:refs/remotes/origin/master</code></li>
	 * <li><code>+refs/heads/*:refs/remotes/origin/*</code></li>
	 * <li><code>+refs/pull/&#42;/head:refs/remotes/origin/pr/*</code></li>
	 * <li><code>:refs/heads/master</code></li>
	 * </ul>
	 *
	 * @param spec
	 *            string describing the specification.
	 * @throws java.lang.IllegalArgumentException
	 *             the specification is invalid.
	 */
	public RefSpec(String spec) {
		this(spec, WildcardMode.REQUIRE_MATCH);
	}

	private RefSpec(RefSpec p) {
		matching = false;
		force = p.isForceUpdate();
		wildcard = p.isWildcard();
		negative = p.isNegative();
		srcName = p.getSource();
		dstName = p.getDestination();
		allowMismatchedWildcards = p.allowMismatchedWildcards;
	}

	/**
	 * Tells whether this {@link RefSpec} is the special "matching" RefSpec ":"
	 * for pushing.
	 *
	 * @return whether this is a "matching" RefSpec
	 * @since 6.1
	 */
	public boolean isMatching() {
		return matching;
	}

	/**
	 * Check if this specification wants to forcefully update the destination.
	 *
	 * @return true if this specification asks for updates without merge tests.
	 */
	public boolean isForceUpdate() {
		return force;
	}

	/**
	 * Create a new RefSpec with a different force update setting.
	 *
	 * @param forceUpdate
	 *            new value for force update in the returned instance.
	 * @return a new RefSpec with force update as specified.
	 */
	public RefSpec setForceUpdate(boolean forceUpdate) {
		final RefSpec r = new RefSpec(this);
		r.matching = matching;
		r.force = forceUpdate;
		return r;
	}

	/**
	 * Check if this specification is actually a wildcard pattern.
	 * <p>
	 * If this is a wildcard pattern then the source and destination names
	 * returned by {@link #getSource()} and {@link #getDestination()} will not
	 * be actual ref names, but instead will be patterns.
	 *
	 * @return true if this specification could match more than one ref.
	 */
	public boolean isWildcard() {
		return wildcard;
	}

	/**
	 * Check if this specification is a negative one.
	 * <p>
	 * If this is a
	 *
	 * @return true if this specification is negative.
	 */
	public boolean isNegative() {
		return negative;
	}

	/**
	 * Get the source ref description.
	 * <p>
	 * During a fetch this is the name of the ref on the remote repository we
	 * are fetching from. During a push this is the name of the ref on the local
	 * repository we are pushing out from.
	 *
	 * @return name (or wildcard pattern) to match the source ref.
	 */
	public String getSource() {
		return srcName;
	}

	/**
	 * Create a new RefSpec with a different source name setting.
	 *
	 * @param source
	 *            new value for source in the returned instance.
	 * @return a new RefSpec with source as specified.
	 * @throws java.lang.IllegalStateException
	 *             There is already a destination configured, and the wildcard
	 *             status of the existing destination disagrees with the
	 *             wildcard status of the new source.
	 */
	public RefSpec setSource(String source) {
		final RefSpec r = new RefSpec(this);
		r.srcName = checkValid(source);
		if (isWildcard(r.srcName) && r.dstName == null)
			throw new IllegalStateException(JGitText.get().destinationIsNotAWildcard);
		if (isWildcard(r.srcName) != isWildcard(r.dstName))
			throw new IllegalStateException(JGitText.get().sourceDestinationMustMatch);
		return r;
	}

	/**
	 * Get the destination ref description.
	 * <p>
	 * During a fetch this is the local tracking branch that will be updated
	 * with the new ObjectId after fetching is complete. During a push this is
	 * the remote ref that will be updated by the remote's receive-pack process.
	 * <p>
	 * If null during a fetch no tracking branch should be updated and the
	 * ObjectId should be stored transiently in order to prepare a merge.
	 * <p>
	 * If null during a push, use {@link #getSource()} instead.
	 *
	 * @return name (or wildcard) pattern to match the destination ref.
	 */
	public String getDestination() {
		return dstName;
	}

	/**
	 * Create a new RefSpec with a different destination name setting.
	 *
	 * @param destination
	 *            new value for destination in the returned instance.
	 * @return a new RefSpec with destination as specified.
	 * @throws java.lang.IllegalStateException
	 *             There is already a source configured, and the wildcard status
	 *             of the existing source disagrees with the wildcard status of
	 *             the new destination.
	 */
	public RefSpec setDestination(String destination) {
		final RefSpec r = new RefSpec(this);
		r.dstName = checkValid(destination);
		if (isWildcard(r.dstName) && r.srcName == null)
			throw new IllegalStateException(JGitText.get().sourceIsNotAWildcard);
		if (isWildcard(r.srcName) != isWildcard(r.dstName))
			throw new IllegalStateException(JGitText.get().sourceDestinationMustMatch);
		return r;
	}

	/**
	 * Create a new RefSpec with a different source/destination name setting.
	 *
	 * @param source
	 *            new value for source in the returned instance.
	 * @param destination
	 *            new value for destination in the returned instance.
	 * @return a new RefSpec with destination as specified.
	 * @throws java.lang.IllegalArgumentException
	 *             The wildcard status of the new source disagrees with the
	 *             wildcard status of the new destination.
	 */
	public RefSpec setSourceDestination(String source, String destination) {
		if (isWildcard(source) != isWildcard(destination))
			throw new IllegalStateException(JGitText.get().sourceDestinationMustMatch);
		final RefSpec r = new RefSpec(this);
		r.wildcard = isWildcard(source);
		r.srcName = source;
		r.dstName = destination;
		return r;
	}

	/**
	 * Does this specification's source description match the ref name?
	 *
	 * @param r
	 *            ref name that should be tested.
	 * @return true if the names match; false otherwise.
	 */
	public boolean matchSource(String r) {
		return match(r, getSource());
	}

	/**
	 * Does this specification's source description match the ref?
	 *
	 * @param r
	 *            ref whose name should be tested.
	 * @return true if the names match; false otherwise.
	 */
	public boolean matchSource(Ref r) {
		return match(r.getName(), getSource());
	}

	/**
	 * Does this specification's destination description match the ref name?
	 *
	 * @param r
	 *            ref name that should be tested.
	 * @return true if the names match; false otherwise.
	 */
	public boolean matchDestination(String r) {
		return match(r, getDestination());
	}

	/**
	 * Does this specification's destination description match the ref?
	 *
	 * @param r
	 *            ref whose name should be tested.
	 * @return true if the names match; false otherwise.
	 */
	public boolean matchDestination(Ref r) {
		return match(r.getName(), getDestination());
	}

	/**
	 * Expand this specification to exactly match a ref name.
	 * <p>
	 * Callers must first verify the passed ref name matches this specification,
	 * otherwise expansion results may be unpredictable.
	 *
	 * @param r
	 *            a ref name that matched our source specification. Could be a
	 *            wildcard also.
	 * @return a new specification expanded from provided ref name. Result
	 *         specification is wildcard if and only if provided ref name is
	 *         wildcard.
	 * @throws java.lang.IllegalStateException
	 *             when the RefSpec was constructed with wildcard mode that
	 *             doesn't require matching wildcards.
	 */
	public RefSpec expandFromSource(String r) {
		if (allowMismatchedWildcards != WildcardMode.REQUIRE_MATCH) {
			throw new IllegalStateException(
					JGitText.get().invalidExpandWildcard);
		}
		return isWildcard() ? new RefSpec(this).expandFromSourceImp(r) : this;
	}

	private RefSpec expandFromSourceImp(String name) {
		final String psrc = srcName, pdst = dstName;
		wildcard = false;
		srcName = name;
		dstName = expandWildcard(name, psrc, pdst);
		return this;
	}

	/**
	 * Expand this specification to exactly match a ref.
	 * <p>
	 * Callers must first verify the passed ref matches this specification,
	 * otherwise expansion results may be unpredictable.
	 *
	 * @param r
	 *            a ref that matched our source specification. Could be a
	 *            wildcard also.
	 * @return a new specification expanded from provided ref name. Result
	 *         specification is wildcard if and only if provided ref name is
	 *         wildcard.
	 * @throws java.lang.IllegalStateException
	 *             when the RefSpec was constructed with wildcard mode that
	 *             doesn't require matching wildcards.
	 */
	public RefSpec expandFromSource(Ref r) {
		return expandFromSource(r.getName());
	}

	/**
	 * Expand this specification to exactly match a ref name.
	 * <p>
	 * Callers must first verify the passed ref name matches this specification,
	 * otherwise expansion results may be unpredictable.
	 *
	 * @param r
	 *            a ref name that matched our destination specification. Could
	 *            be a wildcard also.
	 * @return a new specification expanded from provided ref name. Result
	 *         specification is wildcard if and only if provided ref name is
	 *         wildcard.
	 * @throws java.lang.IllegalStateException
	 *             when the RefSpec was constructed with wildcard mode that
	 *             doesn't require matching wildcards.
	 */
	public RefSpec expandFromDestination(String r) {
		if (allowMismatchedWildcards != WildcardMode.REQUIRE_MATCH) {
			throw new IllegalStateException(
					JGitText.get().invalidExpandWildcard);
		}
		return isWildcard() ? new RefSpec(this).expandFromDstImp(r) : this;
	}

	private RefSpec expandFromDstImp(String name) {
		final String psrc = srcName, pdst = dstName;
		wildcard = false;
		srcName = expandWildcard(name, pdst, psrc);
		dstName = name;
		return this;
	}

	/**
	 * Expand this specification to exactly match a ref.
	 * <p>
	 * Callers must first verify the passed ref matches this specification,
	 * otherwise expansion results may be unpredictable.
	 *
	 * @param r
	 *            a ref that matched our destination specification.
	 * @return a new specification expanded from provided ref name. Result
	 *         specification is wildcard if and only if provided ref name is
	 *         wildcard.
	 * @throws java.lang.IllegalStateException
	 *             when the RefSpec was constructed with wildcard mode that
	 *             doesn't require matching wildcards.
	 */
	public RefSpec expandFromDestination(Ref r) {
		return expandFromDestination(r.getName());
	}

	private boolean match(String name, String s) {
		if (s == null)
			return false;
		if (isWildcard(s)) {
			int wildcardIndex = s.indexOf('*');
			String prefix = s.substring(0, wildcardIndex);
			String suffix = s.substring(wildcardIndex + 1);
			return name.length() > prefix.length() + suffix.length()
					&& name.startsWith(prefix) && name.endsWith(suffix);
		}
		return name.equals(s);
	}

	private static String expandWildcard(String name, String patternA,
			String patternB) {
		int a = patternA.indexOf('*');
		int trailingA = patternA.length() - (a + 1);
		int b = patternB.indexOf('*');
		String match = name.substring(a, name.length() - trailingA);
		return patternB.substring(0, b) + match + patternB.substring(b + 1);
	}

	private static String checkValid(String spec) {
		if (spec != null && !isValid(spec))
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidRefSpec, spec));
		return spec;
	}

	private static boolean isValid(String s) {
		if (s.startsWith("/")) //$NON-NLS-1$
			return false;
		if (s.contains("//")) //$NON-NLS-1$
			return false;
		if (s.endsWith("/")) //$NON-NLS-1$
			return false;
		int i = s.indexOf('*');
		if (i != -1) {
			if (s.indexOf('*', i + 1) > i)
				return false;
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		int hc = 0;
		if (getSource() != null)
			hc = hc * 31 + getSource().hashCode();
		if (getDestination() != null)
			hc = hc * 31 + getDestination().hashCode();
		return hc;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof RefSpec))
			return false;
		final RefSpec b = (RefSpec) obj;
		if (isForceUpdate() != b.isForceUpdate()) {
			return false;
		}
		if(isNegative() != b.isNegative()) {
			return false;
		}
		if (isMatching()) {
			return b.isMatching();
		} else if (b.isMatching()) {
			return false;
		}
		return isWildcard() == b.isWildcard()
				&& Objects.equals(getSource(), b.getSource())
				&& Objects.equals(getDestination(), b.getDestination());
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		if (isForceUpdate()) {
			r.append('+');
		}
		if(isNegative()) {
			r.append('^');
		}
		if (isMatching()) {
			r.append(':');
		} else {
			if (getSource() != null) {
				r.append(getSource());
			}
			if (getDestination() != null) {
				r.append(':');
				r.append(getDestination());
			}
		}
		return r.toString();
	}
}
