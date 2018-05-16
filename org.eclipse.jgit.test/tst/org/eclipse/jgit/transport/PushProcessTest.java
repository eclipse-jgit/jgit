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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

public class PushProcessTest extends SampleDataRepositoryTestCase {
	private PushProcess process;

	private MockTransport transport;

	private HashSet<RemoteRefUpdate> refUpdates;

	private HashSet<Ref> advertisedRefs;

	private Status connectionUpdateStatus;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		transport = new MockTransport(db, new URIish());
		refUpdates = new HashSet<>();
		advertisedRefs = new HashSet<>();
		connectionUpdateStatus = Status.OK;
	}

	/**
	 * Test for fast-forward remote update.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateFastForward() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		testOneUpdateStatus(rru, ref, Status.OK, Boolean.TRUE);
	}

	/**
	 * Test for non fast-forward remote update, when remote object is not known
	 * to local repository.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateNonFastForwardUnknownObject() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("0000000000000000000000000000000000000001"));
		testOneUpdateStatus(rru, ref, Status.REJECTED_NONFASTFORWARD, null);
	}

	/**
	 * Test for non fast-forward remote update, when remote object is known to
	 * local repository, but it is not an ancestor of new object.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateNonFastForward() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"ac7e7e44c1885efb472ad54a78327d66bfc4ecef",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		testOneUpdateStatus(rru, ref, Status.REJECTED_NONFASTFORWARD, null);
	}

	/**
	 * Test for non fast-forward remote update, when force update flag is set.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateNonFastForwardForced() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"ac7e7e44c1885efb472ad54a78327d66bfc4ecef",
				"refs/heads/master", true, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		testOneUpdateStatus(rru, ref, Status.OK, Boolean.FALSE);
	}

	/**
	 * Test for remote ref creation.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateCreateRef() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"ac7e7e44c1885efb472ad54a78327d66bfc4ecef",
				"refs/heads/master", false, null, null);
		testOneUpdateStatus(rru, null, Status.OK, Boolean.TRUE);
	}

	/**
	 * Test for remote ref deletion.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateDelete() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db, (String) null,
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		testOneUpdateStatus(rru, ref, Status.OK, Boolean.TRUE);
	}

	/**
	 * Test for remote ref deletion (try), when that ref doesn't exist on remote
	 * repo.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateDeleteNonExisting() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db, (String) null,
				"refs/heads/master", false, null, null);
		testOneUpdateStatus(rru, null, Status.NON_EXISTING, null);
	}

	/**
	 * Test for remote ref update, when it is already up to date.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateUpToDate() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		testOneUpdateStatus(rru, ref, Status.UP_TO_DATE, null);
	}

	/**
	 * Test for remote ref update with expected remote object.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateExpectedRemote() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, ObjectId
						.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		testOneUpdateStatus(rru, ref, Status.OK, Boolean.TRUE);
	}

	/**
	 * Test for remote ref update with expected old object set, when old object
	 * is not that expected one.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateUnexpectedRemote() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, ObjectId
						.fromString("0000000000000000000000000000000000000001"));
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		testOneUpdateStatus(rru, ref, Status.REJECTED_REMOTE_CHANGED, null);
	}

	/**
	 * Test for remote ref update with expected old object set, when old object
	 * is not that expected one and force update flag is set (which should have
	 * lower priority) - shouldn't change behavior.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateUnexpectedRemoteVsForce() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", true, null, ObjectId
						.fromString("0000000000000000000000000000000000000001"));
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		testOneUpdateStatus(rru, ref, Status.REJECTED_REMOTE_CHANGED, null);
	}

	/**
	 * Test for remote ref update, when connection rejects update.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateRejectedByConnection() throws IOException {
		connectionUpdateStatus = Status.REJECTED_OTHER_REASON;
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		testOneUpdateStatus(rru, ref, Status.REJECTED_OTHER_REASON, null);
	}

	/**
	 * Test for remote refs updates with mixed cases that shouldn't depend on
	 * each other.
	 *
	 * @throws IOException
	 */
	@Test
	public void testUpdateMixedCases() throws IOException {
		final RemoteRefUpdate rruOk = new RemoteRefUpdate(db, (String) null,
				"refs/heads/master", false, null, null);
		final Ref refToChange = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		final RemoteRefUpdate rruReject = new RemoteRefUpdate(db,
				(String) null, "refs/heads/nonexisting", false, null, null);
		refUpdates.add(rruOk);
		refUpdates.add(rruReject);
		advertisedRefs.add(refToChange);
		executePush();
		assertEquals(Status.OK, rruOk.getStatus());
		assertTrue(rruOk.isFastForward());
		assertEquals(Status.NON_EXISTING, rruReject.getStatus());
	}

	/**
	 * Test for local tracking ref update.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTrackingRefUpdateEnabled() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, "refs/remotes/test/master", null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		refUpdates.add(rru);
		advertisedRefs.add(ref);
		final PushResult result = executePush();
		final TrackingRefUpdate tru = result
				.getTrackingRefUpdate("refs/remotes/test/master");
		assertNotNull(tru);
		assertEquals("refs/remotes/test/master", tru.getLocalName());
		assertEquals(Result.NEW, tru.getResult());
	}

	/**
	 * Test for local tracking ref update disabled.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTrackingRefUpdateDisabled() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		refUpdates.add(rru);
		advertisedRefs.add(ref);
		final PushResult result = executePush();
		assertTrue(result.getTrackingRefUpdates().isEmpty());
	}

	/**
	 * Test for local tracking ref update when remote update has failed.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTrackingRefUpdateOnReject() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"ac7e7e44c1885efb472ad54a78327d66bfc4ecef",
				"refs/heads/master", false, null, null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("2c349335b7f797072cf729c4f3bb0914ecb6dec9"));
		final PushResult result = testOneUpdateStatus(rru, ref,
				Status.REJECTED_NONFASTFORWARD, null);
		assertTrue(result.getTrackingRefUpdates().isEmpty());
	}

	/**
	 * Test for push operation result - that contains expected elements.
	 *
	 * @throws IOException
	 */
	@Test
	public void testPushResult() throws IOException {
		final RemoteRefUpdate rru = new RemoteRefUpdate(db,
				"2c349335b7f797072cf729c4f3bb0914ecb6dec9",
				"refs/heads/master", false, "refs/remotes/test/master", null);
		final Ref ref = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master",
				ObjectId.fromString("ac7e7e44c1885efb472ad54a78327d66bfc4ecef"));
		refUpdates.add(rru);
		advertisedRefs.add(ref);
		final PushResult result = executePush();
		assertEquals(1, result.getTrackingRefUpdates().size());
		assertEquals(1, result.getAdvertisedRefs().size());
		assertEquals(1, result.getRemoteUpdates().size());
		assertNotNull(result.getTrackingRefUpdate("refs/remotes/test/master"));
		assertNotNull(result.getAdvertisedRef("refs/heads/master"));
		assertNotNull(result.getRemoteUpdate("refs/heads/master"));
	}

	private PushResult testOneUpdateStatus(final RemoteRefUpdate rru,
			final Ref advertisedRef, final Status expectedStatus,
			Boolean fastForward) throws NotSupportedException,
			TransportException {
		refUpdates.add(rru);
		if (advertisedRef != null)
			advertisedRefs.add(advertisedRef);
		final PushResult result = executePush();
		assertEquals(expectedStatus, rru.getStatus());
		if (fastForward != null)
			assertEquals(fastForward, Boolean.valueOf(rru.isFastForward()));
		return result;
	}

	private PushResult executePush() throws NotSupportedException,
			TransportException {
		process = new PushProcess(transport, refUpdates);
		return process.execute(new TextProgressMonitor());
	}

	private class MockTransport extends Transport {
		MockTransport(Repository local, URIish uri) {
			super(local, uri);
		}

		@Override
		public FetchConnection openFetch() throws NotSupportedException,
				TransportException {
			throw new NotSupportedException("mock");
		}

		@Override
		public PushConnection openPush() throws NotSupportedException,
				TransportException {
			return new MockPushConnection();
		}

		@Override
		public void close() {
			// nothing here
		}
	}

	private class MockPushConnection extends BaseConnection implements
			PushConnection {
		MockPushConnection() {
			final Map<String, Ref> refsMap = new HashMap<>();
			for (Ref r : advertisedRefs)
				refsMap.put(r.getName(), r);
			available(refsMap);
		}

		@Override
		public void close() {
			// nothing here
		}

		@Override
		public void push(ProgressMonitor monitor,
				Map<String, RemoteRefUpdate> refsToUpdate, OutputStream out)
				throws TransportException {
			push(monitor, refsToUpdate);
		}

		@Override
		public void push(ProgressMonitor monitor,
				Map<String, RemoteRefUpdate> refsToUpdate)
				throws TransportException {
			for (RemoteRefUpdate rru : refsToUpdate.values()) {
				assertEquals(Status.NOT_ATTEMPTED, rru.getStatus());
				rru.setStatus(connectionUpdateStatus);
			}
		}
	}
}
