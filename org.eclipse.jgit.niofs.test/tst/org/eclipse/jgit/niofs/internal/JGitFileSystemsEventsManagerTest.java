/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class JGitFileSystemsEventsManagerTest {

	JGitFileSystemsEventsManager manager;
	JGitEventsBroadcast jGitEventsBroadcastMock = mock(JGitEventsBroadcast.class);

	@Before
	public void setup() {
		manager = new JGitFileSystemsEventsManager() {
			@Override
			void setupJGitEventsBroadcast() {
				jGitEventsBroadcast = jGitEventsBroadcastMock;
			}

			@Override
			JGitFileSystemWatchServices createFSWatchServicesManager() {
				return mock(JGitFileSystemWatchServices.class);
			}
		};
	}

	@Test
	public void doNotSetupClusterTest() {
		JGitFileSystemsEventsManager another = new JGitFileSystemsEventsManager();
		assertNull(another.getjGitEventsBroadcast());
	}

	@Test
	public void setupClusterTest() {
		assertNotNull(manager.getjGitEventsBroadcast());
	}

	@Test
	public void createWatchService() throws IOException {
		manager = new JGitFileSystemsEventsManager() {
			@Override
			void setupJGitEventsBroadcast() {
				jGitEventsBroadcast = jGitEventsBroadcastMock;
			}
		};

		WatchService fs = manager.newWatchService("fs");

		assertNotNull(fs);
		assertTrue(manager.getFsWatchServices().containsKey("fs"));
		verify(jGitEventsBroadcastMock).createWatchService("fs");
	}

	@Test
	public void shouldNotPublishEventsForANotWatchedFS() throws IOException {
		WatchService fsDora = manager.newWatchService("fsDora");
		WatchService fsBento = manager.newWatchService("fsBento");

		List<WatchEvent<?>> elist = Arrays.asList(mock(WatchEvent.class), mock(WatchEvent.class));

		manager.publishEvents("another", mock(Path.class), elist);

		verify(jGitEventsBroadcastMock, never()).broadcast(any(), any(), any());
	}

	@Test
	public void publishEventsShouldBeWatched() throws IOException {
		WatchService fsDoraWS = manager.newWatchService("fsDora");
		WatchService fsBento = manager.newWatchService("fsBento");

		JGitFileSystemWatchServices fsDoraWServices = manager.getFsWatchServices().get("fsDora");
		JGitFileSystemWatchServices fsBentoWServices = manager.getFsWatchServices().get("fsBento");

		List<WatchEvent<?>> elist = Arrays.asList(mock(WatchEvent.class), mock(WatchEvent.class));

		manager.publishEvents("fsDora", mock(Path.class), elist);

		verify(fsDoraWServices).publishEvents(any(), eq(elist));
		verify(jGitEventsBroadcastMock).broadcast(eq("fsDora"), any(), eq(elist));
		verify(fsBentoWServices, never()).publishEvents(any(), eq(elist));
	}

	@Test
	public void publishEventsWithoutBroadcast() throws IOException {
		manager.newWatchService("fsDora");
		manager.newWatchService("fsBento");

		JGitFileSystemWatchServices fsDoraWServices = manager.getFsWatchServices().get("fsDora");
		JGitFileSystemWatchServices fsBentoWServices = manager.getFsWatchServices().get("fsBento");

		List<WatchEvent<?>> elist = Arrays.asList(mock(WatchEvent.class), mock(WatchEvent.class));

		manager.publishEvents("fsDora", mock(Path.class), elist, false);

		verify(fsDoraWServices).publishEvents(any(), eq(elist));
		verify(jGitEventsBroadcastMock, never()).broadcast(eq("fsDora"), any(), eq(elist));
		verify(fsBentoWServices, never()).publishEvents(any(), eq(elist));
	}

	@Test
	public void watchServicesEvents() throws IOException {

		manager = new JGitFileSystemsEventsManager() {
			@Override
			void setupJGitEventsBroadcast() {
				jGitEventsBroadcast = jGitEventsBroadcastMock;
			}
		};

		WatchService fsDora1 = manager.newWatchService("fsDora");
		WatchService fsDora2 = manager.newWatchService("fsDora");

		List<WatchEvent<?>> list3events = Arrays.asList(mock(WatchEvent.class), mock(WatchEvent.class),
				mock(WatchEvent.class));

		List<WatchEvent<?>> list2events = Arrays.asList(mock(WatchEvent.class), mock(WatchEvent.class));

		manager.publishEvents("fsDora", mock(Path.class), list3events, false);

		List<WatchEvent<?>> watchEvents = fsDora1.poll().pollEvents();
		assertEquals(3, watchEvents.size());
		watchEvents = fsDora2.poll().pollEvents();
		assertEquals(3, watchEvents.size());

		manager.publishEvents("fsDora", mock(Path.class), list3events, false);
		manager.publishEvents("fsDora", mock(Path.class), list2events, false);

		watchEvents = fsDora2.poll().pollEvents();
		assertEquals(3, watchEvents.size());

		watchEvents = fsDora2.poll().pollEvents();
		assertEquals(2, watchEvents.size());

		watchEvents = fsDora1.poll().pollEvents();
		assertEquals(3, watchEvents.size());

		watchEvents = fsDora1.poll().pollEvents();
		assertEquals(2, watchEvents.size());
	}

	@Test
	public void closeTest() throws IOException {
		manager.newWatchService("fsDora");
		manager.newWatchService("fsBento");

		JGitFileSystemWatchServices fsDoraWServices = manager.getFsWatchServices().get("fsDora");
		JGitFileSystemWatchServices fsBentoWServices = manager.getFsWatchServices().get("fsBento");

		manager.close("fsDora");

		verify(fsDoraWServices).close();
		verify(fsBentoWServices, never()).close();
	}

	@Test
	public void testShutdown() throws IOException {
		manager.newWatchService("fsPetra");
		manager.newWatchService("fsEureka");

		JGitFileSystemWatchServices fsPetraWatchService = manager.getFsWatchServices().get("fsPetra");
		JGitFileSystemWatchServices fsEurekaWatchService = manager.getFsWatchServices().get("fsEureka");

		manager.shutdown();

		verify(fsPetraWatchService).close();
		verify(fsEurekaWatchService).close();
		verify(jGitEventsBroadcastMock).close();
	}
}