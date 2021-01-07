/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.ServiceUnavailableException;
import org.eclipse.jgit.api.errors.WrongObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.GpgSignatureVerifierFactory;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A command to verify GPG signatures on tags or commits.
 *
 * @since 5.11
 */
public class VerifySignatureCommand extends GitCommand<Map<String, VerificationResult>> {

	/**
	 * Describes what kind of objects shall be handled by a
	 * {@link VerifySignatureCommand}.
	 */
	public enum VerifyMode {
		/**
		 * Handle any object type, ignore anything that is not a commit or tag.
		 */
		ANY,
		/**
		 * Handle only commits; throw a {@link WrongObjectTypeException} for
		 * anything else.
		 */
		COMMITS,
		/**
		 * Handle only tags; throw a {@link WrongObjectTypeException} for
		 * anything else.
		 */
		TAGS
	}

	private final Set<String> namesToCheck = new HashSet<>();

	private VerifyMode mode = VerifyMode.ANY;

	private GpgSignatureVerifier verifier;

	private GpgConfig config;

	private boolean ownVerifier;

	/**
	 * Creates a new {@link VerifySignatureCommand} for the given {@link Repository}.
	 *
	 * @param repo
	 *            to operate on
	 */
	public VerifySignatureCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Add a name of an object (SHA-1, ref name; anything that can be
	 * {@link Repository#resolve(String) resolved}) to the command to have its
	 * signature verified.
	 *
	 * @param name
	 *            to add
	 * @return {@code this}
	 */
	public VerifySignatureCommand addName(String name) {
		checkCallable();
		namesToCheck.add(name);
		return this;
	}

	/**
	 * Add names of objects (SHA-1, ref name; anything that can be
	 * {@link Repository#resolve(String) resolved}) to the command to have their
	 * signatures verified.
	 *
	 * @param names
	 *            to add; duplicates will be ignored
	 * @return {@code this}
	 */
	public VerifySignatureCommand addNames(String... names) {
		checkCallable();
		namesToCheck.addAll(Arrays.asList(names));
		return this;
	}

	/**
	 * Add names of objects (SHA-1, ref name; anything that can be
	 * {@link Repository#resolve(String) resolved}) to the command to have their
	 * signatures verified.
	 *
	 * @param names
	 *            to add; duplicates will be ignored
	 * @return {@code this}
	 */
	public VerifySignatureCommand addNames(Collection<String> names) {
		checkCallable();
		namesToCheck.addAll(names);
		return this;
	}

	/**
	 * Sets the mode of operation for this command.
	 *
	 * @param mode
	 *            the {@link VerifyMode} to set
	 * @return {@code this}
	 */
	public VerifySignatureCommand setMode(@NonNull VerifyMode mode) {
		checkCallable();
		this.mode = mode;
		return this;
	}

	/**
	 * Sets the {@link GpgSignatureVerifier} to use.
	 *
	 * @param verifier
	 *            the {@link GpgSignatureVerifier} to use, or {@code null} to
	 *            use the default verifier
	 * @return {@code this}
	 */
	public VerifySignatureCommand setVerifier(GpgSignatureVerifier verifier) {
		checkCallable();
		this.verifier = verifier;
		return this;
	}

	/**
	 * Sets an external {@link GpgConfig} to use. Whether it will be used it at
	 * the discretion of the {@link #setVerifier(GpgSignatureVerifier)}.
	 *
	 * @param config
	 *            to set; if {@code null}, the config will be loaded from the
	 *            git config of the repository
	 * @return {@code this}
	 * @since 5.11
	 */
	public VerifySignatureCommand setGpgConfig(GpgConfig config) {
		checkCallable();
		this.config = config;
		return this;
	}

	/**
	 * Retrieves the currently set {@link GpgSignatureVerifier}. Can be used
	 * after a successful {@link #call()} to get the verifier that was used.
	 *
	 * @return the {@link GpgSignatureVerifier}
	 */
	public GpgSignatureVerifier getVerifier() {
		return verifier;
	}

	/**
	 * {@link Repository#resolve(String) Resolves} all names added to the
	 * command to git objects and verifies their signature. Non-existing objects
	 * are ignored.
	 * <p>
	 * Depending on the {@link #setMode(VerifyMode)}, only tags or commits or
	 * any kind of objects are allowed.
	 * </p>
	 * <p>
	 * Unsigned objects are silently skipped.
	 * </p>
	 *
	 * @return a map of the given names to the corresponding
	 *         {@link VerificationResult}, excluding ignored or skipped objects.
	 * @throws ServiceUnavailableException
	 *             if no {@link GpgSignatureVerifier} was set and no
	 *             {@link GpgSignatureVerifierFactory} is available
	 * @throws WrongObjectTypeException
	 *             if a name resolves to an object of a type not allowed by the
	 *             {@link #setMode(VerifyMode)} mode
	 */
	@Override
	@NonNull
	public Map<String, VerificationResult> call()
			throws ServiceUnavailableException, WrongObjectTypeException {
		checkCallable();
		setCallable(false);
		Map<String, VerificationResult> result = new HashMap<>();
		if (verifier == null) {
			GpgSignatureVerifierFactory factory = GpgSignatureVerifierFactory
					.getDefault();
			if (factory == null) {
				throw new ServiceUnavailableException(
						JGitText.get().signatureVerificationUnavailable);
			}
			verifier = factory.getVerifier();
			ownVerifier = true;
		}
		if (config == null) {
			config = new GpgConfig(repo.getConfig());
		}
		try (RevWalk walk = new RevWalk(repo)) {
			for (String toCheck : namesToCheck) {
				ObjectId id = repo.resolve(toCheck);
				if (id != null && !ObjectId.zeroId().equals(id)) {
					RevObject object;
					try {
						object = walk.parseAny(id);
					} catch (MissingObjectException e) {
						continue;
					}
					VerificationResult verification = verifyOne(object);
					if (verification != null) {
						result.put(toCheck, verification);
					}
				}
			}
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().signatureVerificationError, e);
		} finally {
			if (ownVerifier) {
				verifier.clear();
			}
		}
		return result;
	}

	private VerificationResult verifyOne(RevObject object)
			throws WrongObjectTypeException, IOException {
		int type = object.getType();
		if (VerifyMode.TAGS.equals(mode) && type != Constants.OBJ_TAG) {
			throw new WrongObjectTypeException(object, Constants.OBJ_TAG);
		} else if (VerifyMode.COMMITS.equals(mode)
				&& type != Constants.OBJ_COMMIT) {
			throw new WrongObjectTypeException(object, Constants.OBJ_COMMIT);
		}
		if (type == Constants.OBJ_COMMIT || type == Constants.OBJ_TAG) {
			try {
				GpgSignatureVerifier.SignatureVerification verification = verifier
						.verifySignature(object, config);
				if (verification == null) {
					// Not signed
					return null;
				}
				// Create new result
				return new Result(object, verification, null);
			} catch (JGitInternalException e) {
				return new Result(object, null, e);
			}
		}
		return null;
	}

	private static class Result implements VerificationResult {

		private final Throwable throwable;

		private final SignatureVerification verification;

		private final RevObject object;

		public Result(RevObject object, SignatureVerification verification,
				Throwable throwable) {
			this.object = object;
			this.verification = verification;
			this.throwable = throwable;
		}

		@Override
		public Throwable getException() {
			return throwable;
		}

		@Override
		public SignatureVerification getVerification() {
			return verification;
		}

		@Override
		public RevObject getObject() {
			return object;
		}

	}
}
