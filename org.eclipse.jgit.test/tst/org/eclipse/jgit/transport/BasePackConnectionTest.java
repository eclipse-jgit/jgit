/*
 * Copyright (C) 2020, Lee Worrall and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Test;

public class BasePackConnectionTest {

	@Test
	public void foo() throws URISyntaxException, TransportException {
		TransportLocal tl = new TransportLocal(new URIish("dummy"), new java.io.File("")) {
			@Override
			public FetchConnection openFetch() throws TransportException {
				URIish uri = null;
				try {
					uri = new URIish("foo");
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				NoRemoteRepositoryException e = new NoRemoteRepositoryException(uri, "REPO: not found");
				e.initCause(new java.io.EOFException("Short read of block"));
				throw e;
			}
		};
		class BpcTest extends BasePackPushConnection {
			BpcTest(PackTransport packTransport) {
				super(packTransport);
				foo();
			}
			public boolean entryPoint() throws TransportException {
				return readAdvertisedRefs();
			}
			void foo() {
				class Pli extends PacketLineIn {
					public Pli() {
						super(InputStream.nullInputStream());
					}

					@Override
					public String readString() throws IOException {
						throw new EOFException("Short read of block");
					}
				}
				pckIn = new Pli();
			}

		}
		BpcTest bpc = new BpcTest(tl);
		bpc.entryPoint();
	}

	@Test
	public void testUpdateWithSymRefsAdds() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new HashMap<>();
		symRefs.put("HEAD", "refs/heads/main");

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertThat(refMap, hasKey("HEAD"));
		final Ref headRef = refMap.get("HEAD");
		assertThat(headRef, instanceOf(SymbolicRef.class));
		final SymbolicRef headSymRef = (SymbolicRef) headRef;
		assertEquals("HEAD", headSymRef.getName());
		assertSame(mainRef, headSymRef.getTarget());
	}

	@Test
	public void testUpdateWithSymRefsReplaces() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("HEAD", new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "HEAD",
				mainRef.getObjectId()));
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new HashMap<>();
		symRefs.put("HEAD", "refs/heads/main");

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertThat(refMap, hasKey("HEAD"));
		final Ref headRef = refMap.get("HEAD");
		assertThat(headRef, instanceOf(SymbolicRef.class));
		final SymbolicRef headSymRef = (SymbolicRef) headRef;
		assertEquals("HEAD", headSymRef.getName());
		assertSame(mainRef, headSymRef.getTarget());
	}

	@Test
	public void testUpdateWithSymRefsWithIndirectsAdds() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new LinkedHashMap<>(); // Ordered
		symRefs.put("refs/heads/sym3", "refs/heads/sym2"); // Forward reference
		symRefs.put("refs/heads/sym1", "refs/heads/main");
		symRefs.put("refs/heads/sym2", "refs/heads/sym1"); // Backward reference

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertThat(refMap, hasKey("refs/heads/sym1"));
		final Ref sym1Ref = refMap.get("refs/heads/sym1");
		assertThat(sym1Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym1SymRef = (SymbolicRef) sym1Ref;
		assertEquals("refs/heads/sym1", sym1SymRef.getName());
		assertSame(mainRef, sym1SymRef.getTarget());

		assertThat(refMap, hasKey("refs/heads/sym2"));
		final Ref sym2Ref = refMap.get("refs/heads/sym2");
		assertThat(sym2Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym2SymRef = (SymbolicRef) sym2Ref;
		assertEquals("refs/heads/sym2", sym2SymRef.getName());
		assertSame(sym1SymRef, sym2SymRef.getTarget());

		assertThat(refMap, hasKey("refs/heads/sym3"));
		final Ref sym3Ref = refMap.get("refs/heads/sym3");
		assertThat(sym3Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym3SymRef = (SymbolicRef) sym3Ref;
		assertEquals("refs/heads/sym3", sym3SymRef.getName());
		assertSame(sym2SymRef, sym3SymRef.getTarget());
	}

	@Test
	public void testUpdateWithSymRefsWithIndirectsReplaces() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("refs/heads/sym1", new ObjectIdRef.Unpeeled(
				Ref.Storage.LOOSE, "refs/heads/sym1", mainRef.getObjectId()));
		refMap.put("refs/heads/sym2", new ObjectIdRef.Unpeeled(
				Ref.Storage.LOOSE, "refs/heads/sym2", mainRef.getObjectId()));
		refMap.put("refs/heads/sym3", new ObjectIdRef.Unpeeled(
				Ref.Storage.LOOSE, "refs/heads/sym3", mainRef.getObjectId()));
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new LinkedHashMap<>(); // Ordered
		symRefs.put("refs/heads/sym3", "refs/heads/sym2"); // Forward reference
		symRefs.put("refs/heads/sym1", "refs/heads/main");
		symRefs.put("refs/heads/sym2", "refs/heads/sym1"); // Backward reference

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertThat(refMap, hasKey("refs/heads/sym1"));
		final Ref sym1Ref = refMap.get("refs/heads/sym1");
		assertThat(sym1Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym1SymRef = (SymbolicRef) sym1Ref;
		assertEquals("refs/heads/sym1", sym1SymRef.getName());
		assertSame(mainRef, sym1SymRef.getTarget());

		assertThat(refMap, hasKey("refs/heads/sym2"));
		final Ref sym2Ref = refMap.get("refs/heads/sym2");
		assertThat(sym2Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym2SymRef = (SymbolicRef) sym2Ref;
		assertEquals("refs/heads/sym2", sym2SymRef.getName());
		assertSame(sym1SymRef, sym2SymRef.getTarget());

		assertThat(refMap, hasKey("refs/heads/sym3"));
		final Ref sym3Ref = refMap.get("refs/heads/sym3");
		assertThat(sym3Ref, instanceOf(SymbolicRef.class));
		final SymbolicRef sym3SymRef = (SymbolicRef) sym3Ref;
		assertEquals("refs/heads/sym3", sym3SymRef.getName());
		assertSame(sym2SymRef, sym3SymRef.getTarget());
	}

	@Test
	public void testUpdateWithSymRefsIgnoresSelfReference() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new LinkedHashMap<>();
		symRefs.put("refs/heads/sym1", "refs/heads/sym1");

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertEquals(2, refMap.size());
		assertThat(refMap, not(hasKey("refs/heads/sym1")));
	}

	@Test
	public void testUpdateWithSymRefsIgnoreCircularReference() {
		final Ref mainRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE,
				"refs/heads/main", ObjectId.fromString(
						"0000000000000000000000000000000000000001"));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(mainRef.getName(), mainRef);
		refMap.put("refs/heads/other",
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/other",
						ObjectId.fromString(
								"0000000000000000000000000000000000000002")));

		final Map<String, String> symRefs = new LinkedHashMap<>();
		symRefs.put("refs/heads/sym2", "refs/heads/sym1");
		symRefs.put("refs/heads/sym1", "refs/heads/sym2");

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertEquals(2, refMap.size());
		assertThat(refMap, not(hasKey("refs/heads/sym1")));
		assertThat(refMap, not(hasKey("refs/heads/sym2")));
	}

	@Test
	public void testUpdateWithSymRefsFillInHead() {
		final String oidName = "0000000000000000000000000000000000000001";
		final Ref advertised = new ObjectIdRef.PeeledNonTag(Ref.Storage.NETWORK,
				Constants.HEAD, ObjectId.fromString(oidName));

		final Map<String, Ref> refMap = new HashMap<>();
		refMap.put(advertised.getName(), advertised);

		final Map<String, String> symRefs = new HashMap<>();
		symRefs.put("HEAD", "refs/heads/main");

		BasePackConnection.updateWithSymRefs(refMap, symRefs);

		assertThat(refMap, hasKey("HEAD"));
		assertThat(refMap, hasKey("refs/heads/main"));
		final Ref headRef = refMap.get("HEAD");
		final Ref mainRef = refMap.get("refs/heads/main");
		assertThat(headRef, instanceOf(SymbolicRef.class));
		final SymbolicRef headSymRef = (SymbolicRef) headRef;
		assertEquals(Constants.HEAD, headSymRef.getName());
		assertSame(mainRef, headSymRef.getTarget());
		assertEquals(oidName, headRef.getObjectId().name());
		assertEquals(oidName, mainRef.getObjectId().name());
	}
}