/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Represent request and status of a remote ref update. Specification is
 * provided by client, while status is handled by {@link PushProcess} class,
 * being read-only for client.
 * <p>
 * Client can create instances of this class directly, basing on user
 * specification and advertised refs ({@link Connection} or through
 * {@link Transport} helper methods. Apply this specification on remote
 * repository using
 * {@link Transport#push(org.eclipse.jgit.lib.ProgressMonitor, java.util.Collection)}
 * method.
 * </p>
 *
 */
public class RemoteRefUpdate {
	/**
	 * Represent current status of a remote ref update.
	 */
	public static enum Status {
		/**
		 * Push process hasn't yet attempted to update this ref. This is the
		 * default status, prior to push process execution.
		 */
		NOT_ATTEMPTED,

		/**
		 * Remote ref was up to date, there was no need to update anything.
		 */
		UP_TO_DATE,

		/**
		 * Remote ref update was rejected, as it would cause non fast-forward
		 * update.
		 */
		REJECTED_NONFASTFORWARD,

		/**
		 * Remote ref update was rejected, because remote side doesn't
		 * support/allow deleting refs.
		 */
		REJECTED_NODELETE,

		/**
		 * Remote ref update was rejected, because old object id on remote
		 * repository wasn't the same as defined expected old object.
		 */
		REJECTED_REMOTE_CHANGED,

		/**
		 * Remote ref update was rejected for other reason, possibly described
		 * in {@link RemoteRefUpdate#getMessage()}.
		 */
		REJECTED_OTHER_REASON,

		/**
		 * Remote ref didn't exist. Can occur on delete request of a non
		 * existing ref.
		 */
		NON_EXISTING,

		/**
		 * Push process is awaiting update report from remote repository. This
		 * is a temporary state or state after critical error in push process.
		 */
		AWAITING_REPORT,

		/**
		 * Remote ref was successfully updated.
		 */
		OK;
	}

	private final ObjectId expectedOldObjectId;

	private final ObjectId newObjectId;

	private final String remoteName;

	private final TrackingRefUpdate trackingRefUpdate;

	private final String srcRef;

	private final boolean forceUpdate;

	private Status status;

	private boolean fastForward;

	private String message;

	private final Repository localDb;

	private RefUpdate localUpdate;

	/**
	 * Construct remote ref update request by providing an update specification.
	 * Object is created with default {@link Status#NOT_ATTEMPTED} status and no
	 * message.
	 *
	 * @param localDb
	 *            local repository to push from.
	 * @param srcRef
	 *            source revision - any string resolvable by
	 *            {@link Repository#resolve(String)}. This resolves to the new
	 *            object that the caller want remote ref to be after update. Use
	 *            null or {@link ObjectId#zeroId()} string for delete request.
	 * @param remoteName
	 *            full name of a remote ref to update, e.g. "refs/heads/master"
	 *            (no wildcard, no short name).
	 * @param forceUpdate
	 *            true when caller want remote ref to be updated regardless
	 *            whether it is fast-forward update (old object is ancestor of
	 *            new object).
	 * @param localName
	 *            optional full name of a local stored tracking branch, to
	 *            update after push, e.g. "refs/remotes/zawir/dirty" (no
	 *            wildcard, no short name); null if no local tracking branch
	 *            should be updated.
	 * @param expectedOldObjectId
	 *            optional object id that caller is expecting, requiring to be
	 *            advertised by remote side before update; update will take
	 *            place ONLY if remote side advertise exactly this expected id;
	 *            null if caller doesn't care what object id remote side
	 *            advertise. Use {@link ObjectId#zeroId()} when expecting no
	 *            remote ref with this name.
	 * @throws IOException
	 *             when I/O error occurred during creating
	 *             {@link TrackingRefUpdate} for local tracking branch or srcRef
	 *             can't be resolved to any object.
	 * @throws IllegalArgumentException
	 *             if some required parameter was null
	 */
	public RemoteRefUpdate(final Repository localDb, final String srcRef,
			final String remoteName, final boolean forceUpdate,
			final String localName, final ObjectId expectedOldObjectId)
			throws IOException {
		this(localDb, srcRef, srcRef != null ? localDb.resolve(srcRef)
				: ObjectId.zeroId(), remoteName, forceUpdate, localName,
				expectedOldObjectId);
	}

	/**
	 * Construct remote ref update request by providing an update specification.
	 * Object is created with default {@link Status#NOT_ATTEMPTED} status and no
	 * message.
	 *
	 * @param localDb
	 *            local repository to push from.
	 * @param srcRef
	 *            source revision. Use null to delete.
	 * @param remoteName
	 *            full name of a remote ref to update, e.g. "refs/heads/master"
	 *            (no wildcard, no short name).
	 * @param forceUpdate
	 *            true when caller want remote ref to be updated regardless
	 *            whether it is fast-forward update (old object is ancestor of
	 *            new object).
	 * @param localName
	 *            optional full name of a local stored tracking branch, to
	 *            update after push, e.g. "refs/remotes/zawir/dirty" (no
	 *            wildcard, no short name); null if no local tracking branch
	 *            should be updated.
	 * @param expectedOldObjectId
	 *            optional object id that caller is expecting, requiring to be
	 *            advertised by remote side before update; update will take
	 *            place ONLY if remote side advertise exactly this expected id;
	 *            null if caller doesn't care what object id remote side
	 *            advertise. Use {@link ObjectId#zeroId()} when expecting no
	 *            remote ref with this name.
	 * @throws IOException
	 *             when I/O error occurred during creating
	 *             {@link TrackingRefUpdate} for local tracking branch or srcRef
	 *             can't be resolved to any object.
	 * @throws IllegalArgumentException
	 *             if some required parameter was null
	 */
	public RemoteRefUpdate(final Repository localDb, final Ref srcRef,
			final String remoteName, final boolean forceUpdate,
			final String localName, final ObjectId expectedOldObjectId)
			throws IOException {
		this(localDb, srcRef != null ? srcRef.getName() : null,
				srcRef != null ? srcRef.getObjectId() : null, remoteName,
				forceUpdate, localName, expectedOldObjectId);
	}

	/**
	 * Construct remote ref update request by providing an update specification.
	 * Object is created with default {@link Status#NOT_ATTEMPTED} status and no
	 * message.
	 *
	 * @param localDb
	 *            local repository to push from.
	 * @param srcRef
	 *            source revision to label srcId with. If null srcId.name() will
	 *            be used instead.
	 * @param srcId
	 *            The new object that the caller wants remote ref to be after
	 *            update. Use null or {@link ObjectId#zeroId()} for delete
	 *            request.
	 * @param remoteName
	 *            full name of a remote ref to update, e.g. "refs/heads/master"
	 *            (no wildcard, no short name).
	 * @param forceUpdate
	 *            true when caller want remote ref to be updated regardless
	 *            whether it is fast-forward update (old object is ancestor of
	 *            new object).
	 * @param localName
	 *            optional full name of a local stored tracking branch, to
	 *            update after push, e.g. "refs/remotes/zawir/dirty" (no
	 *            wildcard, no short name); null if no local tracking branch
	 *            should be updated.
	 * @param expectedOldObjectId
	 *            optional object id that caller is expecting, requiring to be
	 *            advertised by remote side before update; update will take
	 *            place ONLY if remote side advertise exactly this expected id;
	 *            null if caller doesn't care what object id remote side
	 *            advertise. Use {@link ObjectId#zeroId()} when expecting no
	 *            remote ref with this name.
	 * @throws IOException
	 *             when I/O error occurred during creating
	 *             {@link TrackingRefUpdate} for local tracking branch or srcRef
	 *             can't be resolved to any object.
	 * @throws IllegalArgumentException
	 *             if some required parameter was null
	 */
	public RemoteRefUpdate(final Repository localDb, final String srcRef,
			final ObjectId srcId, final String remoteName,
			final boolean forceUpdate, final String localName,
			final ObjectId expectedOldObjectId) throws IOException {
		if (remoteName == null)
			throw new IllegalArgumentException(JGitText.get().remoteNameCantBeNull);
		if (srcId == null && srcRef != null)
			throw new IOException(MessageFormat.format(
					JGitText.get().sourceRefDoesntResolveToAnyObject, srcRef));

		if (srcRef != null)
			this.srcRef = srcRef;
		else if (srcId != null && !srcId.equals(ObjectId.zeroId()))
			this.srcRef = srcId.name();
		else
			this.srcRef = null;

		if (srcId != null)
			this.newObjectId = srcId;
		else
			this.newObjectId = ObjectId.zeroId();

		this.remoteName = remoteName;
		this.forceUpdate = forceUpdate;
		if (localName != null && localDb != null) {
			localUpdate = localDb.updateRef(localName);
			localUpdate.setForceUpdate(true);
			localUpdate.setRefLogMessage("push", true); //$NON-NLS-1$
			localUpdate.setNewObjectId(newObjectId);
			trackingRefUpdate = new TrackingRefUpdate(
					true,
					remoteName,
					localName,
					localUpdate.getOldObjectId() != null
						? localUpdate.getOldObjectId()
						: ObjectId.zeroId(),
					newObjectId);
		} else
			trackingRefUpdate = null;
		this.localDb = localDb;
		this.expectedOldObjectId = expectedOldObjectId;
		this.status = Status.NOT_ATTEMPTED;
	}

	/**
	 * Create a new instance of this object basing on existing instance for
	 * configuration. State (like {@link #getMessage()}, {@link #getStatus()})
	 * of base object is not shared. Expected old object id is set up from
	 * scratch, as this constructor may be used for 2-stage push: first one
	 * being dry run, second one being actual push.
	 *
	 * @param base
	 *            configuration base.
	 * @param newExpectedOldObjectId
	 *            new expected object id value.
	 * @throws IOException
	 *             when I/O error occurred during creating
	 *             {@link TrackingRefUpdate} for local tracking branch or srcRef
	 *             of base object no longer can be resolved to any object.
	 */
	public RemoteRefUpdate(final RemoteRefUpdate base,
			final ObjectId newExpectedOldObjectId) throws IOException {
		this(base.localDb, base.srcRef, base.remoteName, base.forceUpdate,
				(base.trackingRefUpdate == null ? null : base.trackingRefUpdate
						.getLocalName()), newExpectedOldObjectId);
	}

	/**
	 * @return expectedOldObjectId required to be advertised by remote side, as
	 *         set in constructor; may be null.
	 */
	public ObjectId getExpectedOldObjectId() {
		return expectedOldObjectId;
	}

	/**
	 * @return true if some object is required to be advertised by remote side,
	 *         as set in constructor; false otherwise.
	 */
	public boolean isExpectingOldObjectId() {
		return expectedOldObjectId != null;
	}

	/**
	 * @return newObjectId for remote ref, as set in constructor.
	 */
	public ObjectId getNewObjectId() {
		return newObjectId;
	}

	/**
	 * @return true if this update is deleting update; false otherwise.
	 */
	public boolean isDelete() {
		return ObjectId.zeroId().equals(newObjectId);
	}

	/**
	 * @return name of remote ref to update, as set in constructor.
	 */
	public String getRemoteName() {
		return remoteName;
	}

	/**
	 * @return local tracking branch update if localName was set in constructor.
	 */
	public TrackingRefUpdate getTrackingRefUpdate() {
		return trackingRefUpdate;
	}

	/**
	 * @return source revision as specified by user (in constructor), could be
	 *         any string parseable by {@link Repository#resolve(String)}; can
	 *         be null if specified that way in constructor - this stands for
	 *         delete request.
	 */
	public String getSrcRef() {
		return srcRef;
	}

	/**
	 * @return true if user specified a local tracking branch for remote update;
	 *         false otherwise.
	 */
	public boolean hasTrackingRefUpdate() {
		return trackingRefUpdate != null;
	}

	/**
	 * @return true if this update is forced regardless of old remote ref
	 *         object; false otherwise.
	 */
	public boolean isForceUpdate() {
		return forceUpdate;
	}

	/**
	 * @return status of remote ref update operation.
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Check whether update was fast-forward. Note that this result is
	 * meaningful only after successful update (when status is {@link Status#OK}).
	 *
	 * @return true if update was fast-forward; false otherwise.
	 */
	public boolean isFastForward() {
		return fastForward;
	}

	/**
	 * @return message describing reasons of status when needed/possible; may be
	 *         null.
	 */
	public String getMessage() {
		return message;
	}

	void setStatus(final Status status) {
		this.status = status;
	}

	void setFastForward(boolean fastForward) {
		this.fastForward = fastForward;
	}

	void setMessage(final String message) {
		this.message = message;
	}

	/**
	 * Update locally stored tracking branch with the new object.
	 *
	 * @param walk
	 *            walker used for checking update properties.
	 * @throws IOException
	 *             when I/O error occurred during update
	 */
	protected void updateTrackingRef(final RevWalk walk) throws IOException {
		if (isDelete())
			trackingRefUpdate.setResult(localUpdate.delete(walk));
		else
			trackingRefUpdate.setResult(localUpdate.update(walk));
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "RemoteRefUpdate[remoteName="
				+ remoteName
				+ ", "
				+ status
				+ ", "
				+ (expectedOldObjectId != null ? expectedOldObjectId.name()
						: "(null)") + "..."
				+ (newObjectId != null ? newObjectId.name() : "(null)")
				+ (fastForward ? ", fastForward" : "")
 + ", srcRef=" + srcRef
				+ (forceUpdate ? ", forceUpdate" : "") + ", message="
				+ (message != null ? "\"" + message + "\"" : "null") + "]";
	}
}
