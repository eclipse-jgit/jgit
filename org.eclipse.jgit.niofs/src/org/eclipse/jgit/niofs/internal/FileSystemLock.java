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

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File system Lock. To instantiate a new Lock use {@link FileSystemLockManager}
 */
public class FileSystemLock {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemLock.class);

	ReentrantLock lock = new ReentrantLock(true);
	private FileLock physicalLock;
	private Path lockFile;
	private FileChannel fileChannel;
	private long lastAccessMilliseconds;
	private long lastAccessThresholdMilliseconds;
	private final URI repoURI;
	private String lockName;

	protected FileSystemLock(File directory, String lockName, TimeUnit lastAccessTimeUnit, long lastAccessThreshold) {
		this.lockName = lockName;
		repoURI = directory.toURI();
		this.lockFile = createLockInfra(repoURI);
		this.lastAccessThresholdMilliseconds = lastAccessTimeUnit.toMillis(lastAccessThreshold);
	}

	void registerAccess() {
		lastAccessMilliseconds = System.currentTimeMillis();
	}

	public void lock() {
		registerAccess();
		lock.lock();

		if (needToCreatePhysicalLock()) {
			physicalLockOnFS();
		}
	}

	public void unlock() {
		registerAccess();
		if (lock.isLocked()) {
			if (releasePhysicalLock()) {
				physicalUnLockOnFS();
			}
			lock.unlock();
		}
	}

	public boolean hasBeenInUse() {
		if (recentlyAccessed()) {
			return true;
		}
		return lock.isLocked();
	}

	private boolean recentlyAccessed() {
		return (System.currentTimeMillis() - lastAccessMilliseconds) < lastAccessThresholdMilliseconds;
	}

	private boolean needToCreatePhysicalLock() {
		return ((physicalLock == null || !physicalLock.isValid()) && lock.getHoldCount() == 1);
	}

	private boolean releasePhysicalLock() {
		return physicalLock != null && physicalLock.isValid() && lock.isLocked() && lock.getHoldCount() == 1;
	}

	void physicalLockOnFS() {
		try {
			File file = lockFile.toFile();
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			fileChannel = raf.getChannel();
			physicalLock = fileChannel.lock();
			fileChannel.position(0);
			fileChannel.write(ByteBuffer.wrap("locked".getBytes()));
		} catch (Exception e) {
			LOGGER.error("Error during lock of FS [" + repoURI.toString() + " -- " + this.getLockName() + "]", e);
			throw new RuntimeException(e);
		}
	}

	void physicalUnLockOnFS() {
		try {
			physicalLock.release();
			fileChannel.close();
			fileChannel = null;
			physicalLock = null;
		} catch (Exception e) {
			LOGGER.error("Error during unlock of FS [" + repoURI.toString() + " -- " + this.getLockName() + "]", e);
			throw new RuntimeException(e);
		}
	}

	Path createLockInfra(URI uri) {
		Path lockFile = null;
		try {
			Path repo = Paths.get(uri);
			lockFile = repo.resolve(getLockName());
			Files.createFile(lockFile);
		} catch (FileAlreadyExistsException ignored) {
		} catch (Exception e) {
			LOGGER.error("Error building lock infra [" + toString() + "]", e);
			throw new RuntimeException(e);
		}
		return lockFile;
	}

	protected String getLockName() {
		return this.lockName;
	}
}
