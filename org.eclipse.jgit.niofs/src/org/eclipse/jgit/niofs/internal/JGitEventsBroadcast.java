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

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.eclipse.jgit.niofs.cluster.ClusterMessageService;

public class JGitEventsBroadcast {

	public static final String DEFAULT_TOPIC = "default-niogit-topic";

	private String nodeId = UUID.randomUUID().toString();
	private Consumer<WatchEventsWrapper> eventsPublisher;
	private final ClusterMessageService clusterMessageService;

	public JGitEventsBroadcast(ClusterMessageService clusterMessageService,
			Consumer<WatchEventsWrapper> eventsPublisher) {
		this.clusterMessageService = clusterMessageService;
		this.eventsPublisher = eventsPublisher;
		setupJMSConnection();
	}

	private void setupJMSConnection() {
		clusterMessageService.connect();
	}

	public void createWatchService(String topicName) {
		clusterMessageService.createConsumer(ClusterMessageService.DestinationType.PubSub, getChannelName(topicName),
				WatchEventsWrapper.class, (we) -> {
					if (!we.getNodeId().equals(nodeId)) {
						eventsPublisher.accept(we);
					}
				});
	}

	public synchronized void broadcast(String fsName, Path watchable, List<WatchEvent<?>> events) {
		clusterMessageService.broadcast(ClusterMessageService.DestinationType.PubSub, getChannelName(fsName),
				new WatchEventsWrapper(nodeId, fsName, watchable, events));
	}

	private String getChannelName(String fsName) {
		String channelName = DEFAULT_TOPIC;
		if (fsName.contains("/")) {
			channelName = fsName.substring(0, fsName.indexOf("/"));
		}
		return channelName;
	}

	public void close() {
		clusterMessageService.close();
	}
}
