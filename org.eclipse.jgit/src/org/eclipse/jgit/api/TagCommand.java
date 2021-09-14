/*
 * Copyright (C) 2010, 2020 Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidTagNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.ServiceUnavailableException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.GpgObjectSigner;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Create/update an annotated tag object or a simple unannotated tag
 * <p>
 * Examples (<code>git</code> is a {@link org.eclipse.jgit.api.Git} instance):
 * <p>
 * Create a new tag for the current commit:
 *
 * <pre>
 * git.tag().setName(&quot;v1.0&quot;).setMessage(&quot;First stable release&quot;).call();
 * </pre>
 * <p>
 *
 * <p>
 * Create a new unannotated tag for the current commit:
 *
 * <pre>
 * git.tag().setName(&quot;v1.0&quot;).setAnnotated(false).call();
 * </pre>
 * <p>
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
 *      >Git documentation about Tag</a>
 */
public class TagCommand extends CredentialsAwareCommand<TagCommand, Ref> {

	private RevObject id;

	private String name;

	private String message;

	private PersonIdent tagger;

	private Boolean signed;

	private boolean forceUpdate;

	private Boolean annotated;

	private String signingKey;

	private GpgConfig gpgConfig;

	private GpgObjectSigner gpgSigner;

	/**
	 * <p>Constructor for TagCommand.</p>
	 *
	 * @param repo a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected TagCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code tag} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @since 2.0
	 */
	@Override
	public Ref call() throws GitAPIException, ConcurrentRefUpdateException,
			InvalidTagNameException, NoHeadException {
		checkCallable();

		RepositoryState state = repo.getRepositoryState();
		processOptions(state);

		try (RevWalk revWalk = new RevWalk(repo)) {
			// if no id is set, we should attempt to use HEAD
			if (id == null) {
				ObjectId objectId = repo.resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
				if (objectId == null)
					throw new NoHeadException(
							JGitText.get().tagOnRepoWithoutHEADCurrentlyNotSupported);

				id = revWalk.parseCommit(objectId);
			}

			if (!isAnnotated()) {
				return updateTagRef(id, revWalk, name,
						"SimpleTag[" + name + " : " + id //$NON-NLS-1$ //$NON-NLS-2$
								+ "]"); //$NON-NLS-1$
			}

			// create the tag object
			TagBuilder newTag = new TagBuilder();
			newTag.setTag(name);
			newTag.setMessage(message);
			newTag.setTagger(tagger);
			newTag.setObjectId(id);

			if (gpgSigner != null) {
				gpgSigner.signObject(newTag, signingKey, tagger,
						credentialsProvider, gpgConfig);
			}

			// write the tag object
			try (ObjectInserter inserter = repo.newObjectInserter()) {
				ObjectId tagId = inserter.insert(newTag);
				inserter.flush();

				String tag = newTag.getTag();
				return updateTagRef(tagId, revWalk, tag, newTag.toString());

			}

		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfTagCommand,
					e);
		}
	}

	private Ref updateTagRef(ObjectId tagId, RevWalk revWalk,
			String tagName, String newTagToString) throws IOException,
			ConcurrentRefUpdateException, RefAlreadyExistsException {
		String refName = Constants.R_TAGS + tagName;
		RefUpdate tagRef = repo.updateRef(refName);
		tagRef.setNewObjectId(tagId);
		tagRef.setForceUpdate(forceUpdate);
		tagRef.setRefLogMessage("tagged " + name, false); //$NON-NLS-1$
		Result updateResult = tagRef.update(revWalk);
		switch (updateResult) {
		case NEW:
		case FORCED:
			return repo.exactRef(refName);
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(
					JGitText.get().couldNotLockHEAD, tagRef.getRef(),
					updateResult);
		case NO_CHANGE:
			if (forceUpdate) {
				return repo.exactRef(refName);
			}
			throw new RefAlreadyExistsException(MessageFormat
					.format(JGitText.get().tagAlreadyExists, newTagToString),
					updateResult);
		case REJECTED:
			throw new RefAlreadyExistsException(MessageFormat.format(
					JGitText.get().tagAlreadyExists, newTagToString),
					updateResult);
		default:
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().updatingRefFailed, refName, newTagToString,
					updateResult));
		}
	}

	/**
	 * Sets default values for not explicitly specified options. Then validates
	 * that all required data has been provided.
	 *
	 * @param state
	 *            the state of the repository we are working on
	 *
	 * @throws InvalidTagNameException
	 *             if the tag name is null or invalid
	 * @throws ServiceUnavailableException
	 *             if the tag should be signed but no signer can be found
	 * @throws UnsupportedSigningFormatException
	 *             if the tag should be signed but {@code gpg.format} is not
	 *             {@link GpgFormat#OPENPGP}
	 */
	private void processOptions(RepositoryState state)
			throws InvalidTagNameException, ServiceUnavailableException,
			UnsupportedSigningFormatException {
		if (name == null
				|| !Repository.isValidRefName(Constants.R_TAGS + name)) {
			throw new InvalidTagNameException(
					MessageFormat.format(JGitText.get().tagNameInvalid,
							name == null ? "<null>" : name)); //$NON-NLS-1$
		}
		if (!isAnnotated()) {
			if ((message != null && !message.isEmpty()) || tagger != null) {
				throw new JGitInternalException(JGitText
						.get().messageAndTaggerNotAllowedInUnannotatedTags);
			}
		} else {
			if (tagger == null) {
				tagger = new PersonIdent(repo);
			}
			// Figure out whether to sign.
			if (!(Boolean.FALSE.equals(signed) && signingKey == null)) {
				if (gpgConfig == null) {
					gpgConfig = new GpgConfig(repo.getConfig());
				}
				boolean doSign = isSigned() || gpgConfig.isSignAllTags();
				if (!Boolean.TRUE.equals(annotated) && !doSign) {
					doSign = gpgConfig.isSignAnnotated();
				}
				if (doSign) {
					if (signingKey == null) {
						signingKey = gpgConfig.getSigningKey();
					}
					if (gpgSigner == null) {
						GpgSigner signer = GpgSigner.getDefault();
						if (!(signer instanceof GpgObjectSigner)) {
							throw new ServiceUnavailableException(
									JGitText.get().signingServiceUnavailable);
						}
						gpgSigner = (GpgObjectSigner) signer;
					}
					// The message of a signed tag must end in a newline because
					// the signature will be appended.
					if (message != null && !message.isEmpty()
							&& !message.endsWith("\n")) { //$NON-NLS-1$
						message += '\n';
					}
				}
			}
		}
	}

	/**
	 * Set the tag <code>name</code>.
	 *
	 * @param name
	 *            the tag name used for the {@code tag}
	 * @return {@code this}
	 */
	public TagCommand setName(String name) {
		checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * Get the tag <code>name</code>.
	 *
	 * @return the tag name used for the <code>tag</code>
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the tag <code>message</code>.
	 *
	 * @return the tag message used for the <code>tag</code>
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the tag <code>message</code>.
	 *
	 * @param message
	 *            the tag message used for the {@code tag}
	 * @return {@code this}
	 */
	public TagCommand setMessage(String message) {
		checkCallable();
		this.message = message;
		return this;
	}

	/**
	 * Whether {@link #setSigned(boolean) setSigned(true)} has been called or
	 * whether a {@link #setSigningKey(String) signing key ID} has been set;
	 * i.e., whether -s or -u was specified explicitly.
	 *
	 * @return whether the tag is signed
	 */
	public boolean isSigned() {
		return Boolean.TRUE.equals(signed) || signingKey != null;
	}

	/**
	 * If set to true the Tag command creates a signed tag object. This
	 * corresponds to the parameter -s (--sign or --no-sign) on the command
	 * line.
	 * <p>
	 * If {@code true}, the tag will be a signed annotated tag.
	 * </p>
	 *
	 * @param signed
	 *            whether to sign
	 * @return {@code this}
	 */
	public TagCommand setSigned(boolean signed) {
		checkCallable();
		this.signed = Boolean.valueOf(signed);
		return this;
	}

	/**
	 * Sets the {@link GpgSigner} to use if the commit is to be signed.
	 *
	 * @param signer
	 *            to use; if {@code null}, the default signer will be used
	 * @return {@code this}
	 * @since 5.11
	 */
	public TagCommand setGpgSigner(GpgObjectSigner signer) {
		checkCallable();
		this.gpgSigner = signer;
		return this;
	}

	/**
	 * Sets an external {@link GpgConfig} to use. Whether it will be used is at
	 * the discretion of the {@link #setGpgSigner(GpgObjectSigner)}.
	 *
	 * @param config
	 *            to set; if {@code null}, the config will be loaded from the
	 *            git config of the repository
	 * @return {@code this}
	 * @since 5.11
	 */
	public TagCommand setGpgConfig(GpgConfig config) {
		checkCallable();
		this.gpgConfig = config;
		return this;
	}

	/**
	 * Sets the tagger of the tag. If the tagger is null, a PersonIdent will be
	 * created from the info in the repository.
	 *
	 * @param tagger
	 *            a {@link org.eclipse.jgit.lib.PersonIdent} object.
	 * @return {@code this}
	 */
	public TagCommand setTagger(PersonIdent tagger) {
		checkCallable();
		this.tagger = tagger;
		return this;
	}

	/**
	 * Get the <code>tagger</code> who created the tag.
	 *
	 * @return the tagger of the tag
	 */
	public PersonIdent getTagger() {
		return tagger;
	}

	/**
	 * Get the tag's object id
	 *
	 * @return the object id of the tag
	 */
	public RevObject getObjectId() {
		return id;
	}

	/**
	 * Sets the object id of the tag. If the object id is {@code null}, the
	 * commit pointed to from HEAD will be used.
	 *
	 * @param id
	 *            a {@link org.eclipse.jgit.revwalk.RevObject} object.
	 * @return {@code this}
	 */
	public TagCommand setObjectId(RevObject id) {
		checkCallable();
		this.id = id;
		return this;
	}

	/**
	 * Whether this is a forced update
	 *
	 * @return is this a force update
	 */
	public boolean isForceUpdate() {
		return forceUpdate;
	}

	/**
	 * If set to true the Tag command may replace an existing tag object. This
	 * corresponds to the parameter -f on the command line.
	 *
	 * @param forceUpdate
	 *            whether this is a forced update
	 * @return {@code this}
	 */
	public TagCommand setForceUpdate(boolean forceUpdate) {
		checkCallable();
		this.forceUpdate = forceUpdate;
		return this;
	}

	/**
	 * Configure this tag to be created as an annotated tag
	 *
	 * @param annotated
	 *            whether this shall be an annotated tag
	 * @return {@code this}
	 * @since 3.0
	 */
	public TagCommand setAnnotated(boolean annotated) {
		checkCallable();
		this.annotated = Boolean.valueOf(annotated);
		return this;
	}

	/**
	 * Whether this will create an annotated tag.
	 *
	 * @return true if this command will create an annotated tag (default is
	 *         true)
	 * @since 3.0
	 */
	public boolean isAnnotated() {
		boolean setExplicitly = Boolean.TRUE.equals(annotated) || isSigned();
		if (setExplicitly) {
			return true;
		}
		// Annotated at default (not set explicitly)
		return annotated == null;
	}

	/**
	 * Sets the signing key.
	 * <p>
	 * Per spec of {@code user.signingKey}: this will be sent to the GPG program
	 * as is, i.e. can be anything supported by the GPG program.
	 * </p>
	 * <p>
	 * Note, if none was set or {@code null} is specified a default will be
	 * obtained from the configuration.
	 * </p>
	 * <p>
	 * If set to a non-{@code null} value, the tag will be a signed annotated
	 * tag.
	 * </p>
	 *
	 * @param signingKey
	 *            signing key; {@code null} allowed
	 * @return {@code this}
	 * @since 5.11
	 */
	public TagCommand setSigningKey(String signingKey) {
		checkCallable();
		this.signingKey = signingKey;
		return this;
	}

	/**
	 * Retrieves the signing key ID.
	 *
	 * @return the key ID set, or {@code null} if none is set
	 * @since 5.11
	 */
	public String getSigningKey() {
		return signingKey;
	}
}
