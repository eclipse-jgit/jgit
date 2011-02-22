/*
 * Copyright (C) 2011, Google Inc.
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
package org.eclipse.jgit.storage.dht.spi.jdbc;

import java.sql.Driver;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtTimeoutException;
import org.eclipse.jgit.storage.dht.Timeout;

/**
 * Simple JDBC connection pool to the database server.
 */
public class ClientPool {
	/** Constructs a {@link ClientPool}. */
	public static class Builder {
		private Driver driver;

		private String url;

		private Properties properties = new Properties();

		private Timeout connectionTimeout = Timeout.seconds(5);

		private int maxOpen = 10;

		private int maxIdle = 5;

		/**
		 * @param driver
		 * @return {@code this}
		 */
		public Builder setDriver(Driver driver) {
			this.driver = driver;
			return this;
		}

		/**
		 * @param clazz
		 * @return {@code this}
		 * @throws DhtException
		 */
		public Builder setDriver(Class<? extends Driver> clazz)
				throws DhtException {
			try {
				return setDriver(clazz.newInstance());
			} catch (InstantiationException err) {
				throw new DhtException(err);
			} catch (IllegalAccessException err) {
				throw new DhtException(err);
			}
		}

		/**
		 * @param className
		 * @return {@code this}
		 * @throws DhtException
		 */
		@SuppressWarnings("unchecked")
		public Builder setDriver(String className) throws DhtException {
			try {
				ClassLoader cl = Thread.currentThread().getContextClassLoader();
				Class<?> clazz = Class.forName(className, true, cl);
				return setDriver((Class<? extends Driver>) clazz);
			} catch (ClassNotFoundException e) {
				throw new DhtException(e);
			}
		}

		/**
		 * @param url
		 * @return {@code this}
		 */
		public Builder setURL(String url) {
			this.url = url;
			return this;
		}

		/** @return current properties map. */
		public Properties getProperties() {
			return properties;
		}

		/**
		 * @param user
		 * @return {@code this}
		 */
		public Builder setUser(String user) {
			return setProperty("user", user);
		}

		/**
		 * @param password
		 * @return {@code this}
		 */
		public Builder setPassword(String password) {
			return setProperty("password", password);
		}

		/**
		 * @param key
		 * @param val
		 * @return {@code this}
		 */
		public Builder setProperty(String key, Object val) {
			properties.put(key, val);
			return this;
		}

		/**
		 * @param map
		 * @return {@code this}
		 */
		public Builder setProperties(Map<String, ?> map) {
			properties.clear();
			properties.putAll(map);
			return this;
		}

		/**
		 * @param open
		 * @return {@code this}
		 */
		public Builder setMaxOpen(int open) {
			maxOpen = open;
			return this;
		}

		/**
		 * @param idle
		 * @return {@code this}
		 */
		public Builder setMaxIdle(int idle) {
			maxIdle = idle;
			return this;
		}

		/**
		 * @param timeout
		 * @return {@code this}
		 */
		public Builder setConnectionTimeout(Timeout timeout) {
			this.connectionTimeout = timeout;
			return this;
		}

		/**
		 * @return a connection pool using this builder's settings.
		 * @throws DhtException
		 *             the connection pool cannot open a connection.
		 */
		public ClientPool build() throws DhtException {
			return new ClientPool(this);
		}
	}

	private final Driver driver;

	private final String url;

	private final Properties properties;

	private final Timeout connectionTimeout;

	private final Semaphore clients;

	private final ArrayBlockingQueue<Client> idle;

	private ClientPool(Builder info) throws DhtException {
		driver = info.driver;
		url = info.url;
		properties = new Properties(info.properties);
		connectionTimeout = info.connectionTimeout;
		clients = new Semaphore(Math.max(1, info.maxOpen));
		idle = new ArrayBlockingQueue<Client>(Math.max(1, info.maxIdle));

		// Open an initial idle connection. This ensures that for H2
		// in-memory databases the database exists so long as the
		// pool is valid, which supports unit testing.
		idle.add(newClient());
	}

	/** Close all currently open connections in the pool. */
	public void shutdown() {
		while (!idle.isEmpty()) {
			Client conn = idle.poll();
			if (conn != null) {
				try {
					conn.close();
				} catch (DhtException err) {
					// Ignore close errors during shutdown.
				}
			}
		}
	}

	/**
	 * Obtain a connection from the pool.
	 *
	 * @return the connection.
	 * @throws DhtException
	 *             a connection cannot be opened to the database.
	 */
	public Client get() throws DhtException {
		try {
			long time = connectionTimeout.getTime();
			TimeUnit unit = connectionTimeout.getUnit();
			if (!clients.tryAcquire(time, unit)) {
				throw new DhtTimeoutException(MessageFormat.format(
						JdbcText.get().connectionTimeout, connectionTimeout));
			}
		} catch (InterruptedException interrupt) {
			throw new DhtTimeoutException(interrupt);
		}

		Client conn = idle.poll();
		if (conn == null)
			conn = newClient();
		return conn;
	}

	/**
	 * Release a connection back to the pool.
	 *
	 * @param conn
	 *            the connection to release.
	 */
	public void release(Client conn) {
		try {
			if (conn.hasPending())
				conn.clear();
			if (conn.hasPending() || !idle.offer(conn)) {
				try {
					conn.close();
				} catch (DhtException err) {
					// Ignore close errors when releasing extra connections.
				}
			}
		} finally {
			clients.release();
		}
	}

	private Client newClient() throws DhtException {
		try {
			return new Client(driver.connect(url, properties));
		} catch (SQLException err) {
			throw new DhtException(JdbcText.get().connectionFailed, err);
		}
	}
}
