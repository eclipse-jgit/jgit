/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.Serializable;
import java.util.function.Consumer;

public interface ClusterMessageService {

	void connect();

	<T> void createConsumer(DestinationType type, String channel, Class<T> clazz, Consumer<T> listener);

	void broadcast(DestinationType type, String channel, Serializable object);

	boolean isSystemClustered();

	void close();

	enum DestinationType {
		PubSub, LoadBalancer
	}
}
