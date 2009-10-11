/*
 * Copyright (C) 2009, Imran M Yousuf <imyousuf@smartitengineering.com>
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

package org.eclipse.jgit.io.localfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import org.eclipse.jgit.io.Entry;
import org.eclipse.jgit.io.StorageSystem;
import org.eclipse.jgit.io.StorageSystemManager;

/**
 * Entry implementation for local file system. This class should not be
 * initialized directly unless its a {@link LocalFileSystem}. SPI users should
 * use {@link StorageSystemManager#getEntry(java.net.URI)} to get the first
 * {@link Entry} and then traverse from there onwards.
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public class LocalFileEntry
        implements Entry {

  private File localFile;
  private StorageSystem storageSystem;

  /**
   * Contructs an entry based of on the local file system storage and a file
   * that will be represented by this entry.
   * @param localFile File represented by this entry
   * @param storageSystem Storage system of the entry
   * @throws IllegalArgumentException If either argument is NULL
   */
  protected LocalFileEntry(File localFile,
                           StorageSystem storageSystem)
          throws IllegalArgumentException {
    setLocalFile(localFile);
    setStorageSystem(storageSystem);
  }

  /**
   * Sets the storage system instance for this entry.
   * @param storageSystem Storage system
   * @throws IllegalArgumentException IF storageSystem is null
   */
  protected void setStorageSystem(StorageSystem storageSystem)
          throws IllegalArgumentException {
    if (storageSystem == null) {
      throw new IllegalArgumentException("Storage system can't be NULL!");
    }
    this.storageSystem = storageSystem;
  }

  /**
   * Retrieves the file being adapted by this entry.
   * @return File being adapted
   */
  public File getLocalFile() {
    return localFile;
  }

  /**
   * Sets the file which is to be used as adapt from this instance by this
   * implementation
   * @param localFile Local file being adapted by this instance
   * @throws IllegalArgumentException If localFile is null
   */
  protected void setLocalFile(File localFile)
          throws IllegalArgumentException {
    if (localFile == null) {
      throw new IllegalArgumentException(
              "Local file to be set can't be NULL");
    }
    this.localFile = localFile;
  }

  public String getName() {
    return getLocalFile().getName();
  }

  public String getAbsolutePath() {
    return getLocalFile().getAbsolutePath();
  }

  public boolean isDirectory() {
    return getLocalFile().isDirectory();
  }

  public boolean isExists() {
    return getLocalFile().exists();
  }

  public boolean mkdirs() {
    return getLocalFile().mkdirs();
  }

  public URI getURI() {
    return getLocalFile().toURI();
  }

  public InputStream getInputStream()
          throws IOException {
    if (getLocalFile().exists()) {
      try {
        return new FileInputStream(getLocalFile());
      }
      catch (FileNotFoundException ex) {
        throw ex;
      }
    }
    else {
      throw new FileNotFoundException("File does not exists!");
    }
  }

  public OutputStream getOutputStream(boolean overwrite)
          throws IOException {
    try {
      if (!isExists()) {

        return new FileOutputStream(getLocalFile());
      }
      else {
        if (overwrite) {
          return new FileOutputStream(getLocalFile());
        }
        else {
          return new FileOutputStream(getLocalFile(), true);
        }
      }
    }
    catch (FileNotFoundException ex) {
      throw ex;
    }
  }

  public Entry[] getChildren() {
    File[] children = getLocalFile().listFiles();
    if (children == null || children.length == 0) {
      return new Entry[0];
    }
    else {
      Entry[] entries = new Entry[children.length];
      for (int i = 0; i < children.length; ++i) {
        entries[i] = getStorageSystem().getEntry(children[i].toURI());
      }
      return entries;
    }
  }

  public Entry getChild(String name) {
    if (name == null || name.length() == 0) {
      return null;
    }
    Entry[] children = getChildren();
    for (Entry entry : children) {
      if (name.equals(entry.getName())) {
        return entry;
      }
    }
    return null;
  }

  public Entry getParent() {
    File parent = getLocalFile().getParentFile();
    if (parent == null) {
      return null;
    }
    else {
      return getStorageSystem().getEntry(parent.toURI());
    }
  }

  public StorageSystem getStorageSystem() {
    return storageSystem;
  }

  public long length() {
    return getLocalFile().length();
  }
}
