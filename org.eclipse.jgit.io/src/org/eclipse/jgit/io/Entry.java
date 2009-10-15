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

package org.eclipse.jgit.io;

import org.eclipse.jgit.io.lock.Lockable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Represents each entry in a storage system. For example, in a local filesystem
 * storage it would represent {@link java.io.File}. Here the storage system
 * mainly refers to where repository meta data such as git objects, ref logs,
 * packs are stored; for example a '.git' directory and all its contents in a
 * clone repo would correspond to an entry.
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public interface Entry
        extends Lockable {

  /**
   * Retrieves the name of the entry
   * @return Name of the entry
   */
  public String getName();

  /**
   * Retrieves the absolute path of the entry.
   * @return Absoluth path
   */
  public String getAbsolutePath();

  /**
   * Retrieves whether the entry represents a directory or not
   * @return True if represents a directory else false
   */
  public boolean isDirectory();

  /**
   * Signifies whether the entry is a new one or being read from the
   * persistent storage
   * @return True if being read form storage else false
   */
  public boolean isExists();

  /**
   * Does this operating system and JRE support the execute flag on entries?
   *
   * @return true if this implementation can provide reasonably accurate
   *         executable bit information; false otherwise.
   */
  public boolean isExecutableSupported();

  /**
   * Determine if the entry is executable (or not).
   * <p>
   * Not all platforms and JREs support executable flags on entries. If the
   * feature is unsupported this method will always return false.
   *
   * @return true if the entry is believed to be executable by the user.
   */
  public boolean isExecutable();

  /**
   * Make directories upto the entry represented by this instance, provided
   * that this instance itself is a directory.
   * @return True if directories were created.
   */
  public boolean mkdirs();

  /**
   * Set an entry to be executable by the user.
   * <p>
   * Not all platforms and JREs support executable flags on entries. If the
   * feature is unsupported this method will always return false and no
   * changes will be made to the entry specified.
   *
   * @param executable
   *            true to enable execution; false to disable it.
   * @return true if the change succeeded; false otherwise.
   */
  public boolean setExecutable(boolean executable);

  /**
   * Retrieves the URI of this entry. URI in this case acts as a primary key
   * to identify an entry.
   * @return URI to identify this entry instance
   */
  public URI getURI();

  /**
   * Retrieves the length of the entry if its predictable.
   * @return < 0 if the length is unpredictable else the length of the entry's
   *         content
   */
  public long length();

  /**
   * Retrieves the InputStream for reading the content of the entry
   * @return Input stream to read entry content
   * @throws IOException If no such entry exists or there is any other error
   */
  public InputStream getInputStream()
          throws IOException;

  /**
   * Retrieves a locked channeled output stream. When the output stream is closed
   * the channel is released automatically. If lock is not attained for this
   * entry, first it will be attempted to attain, and if attaining fails it will
   * abort returning an output stream. If lock was attained by this operation
   * then when the stream is closed it will also be released automatically.
   * @param overwrite False if to write in append mode else true
   * @param lock Whether to attain lock or not
   * @return Output stream to write content to
   * @throws IOException If no such entry exists in append mode or there is any
   *                     error in retrieving it or retrieving the lock.
   */
  public OutputStream getOutputStream(boolean overwrite,
                                      boolean lock)
          throws IOException;

  /**
   * Behaves in as if {@link Entry#getOutputStream(boolean, boolean)} is called
   * with lock param as false.
   * @param overwrite False if to write in append mode else true
   * @return Output stream to write content to
   * @throws IOException If no such entry exists in append mode or there is any
   *                     error in retrieving it.
   * @see Entry#getOutputStream(boolean, boolean) 
   */
  public OutputStream getOutputStream(boolean overwrite)
          throws IOException;

  /**
   * Retrieve all the child entries of this entries if its a directory.
   * @return If not a directory then a empty array else array of sub-entries.
   */
  public Entry[] getChildren();

  /**
   * Retrieve a specific child of an entry. It will basically match
   * {@link Entry#getName() name} of the children to find and that too only
   * the direct children.
   * @param name Name of the child to find
   * @return If child is not found then NULL or else the child specified by
   *         the name
   */
  public Entry getChild(String name);

  /**
   * Retrieve the parent entry of the current entry.
   * @return NULL if no parent or else the direct parent of the current entry
   */
  public Entry getParent();

  /**
   * Create this entry, e.g. a file (not a directory) in the underlying system
   * storage. If intention is to create a directory please use
   * {@link #mkdirs() this}.
   * @return True if created else false
   * @throws IOException If any I/O during creation
   */
  public boolean create()
          throws IOException;

  /**
   * Delete current entry
   * @return True if deleted successfully else false
   * @throws IOException If any error during writing
   */
  public boolean delete()
          throws IOException;

  /**
   * Retrieve the storage system this entry either is from or will be
   * persisted to.
   * @return Storage system of the entry, will never be NULL.
   */
  public StorageSystem getStorageSystem();
}
