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

import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import org.eclipse.jgit.io.localfs.LocalFileSystem;

/**
 * Manager that registers different storage system for different {@link URI}
 * schemes and serves users with {@link Entry} or {@link StorageSystem} as URI
 * schemes request.
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public class StorageSystemManager {

  /**
   * Map to act as registrar for all registered schemes
   */
  private static Map<String, StorageSystem> storageSystemMap;

  /**
   * Initialize the registrar and and load the instance for default storage,
   * i.e. the local file system.
   */
  static {
    storageSystemMap = new Hashtable<String, StorageSystem>();
    try {
      register(LocalFileSystem.class);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Retrieves the storage system for a given scheme from the registrar.
   * @param scheme Scheme to retrieve; it should never be null
   * @return Storage system representing the scheme.
   * @throws IOException If scheme is null or scheme does not exist.
   */
  public static StorageSystem getStorageSystem(String scheme)
          throws IOException {
    if (scheme != null && storageSystemMap.containsKey(scheme)) {
      return storageSystemMap.get(scheme);
    }
    else {
      throw new IOException("Scheme ( " + scheme +
                            " ) not registered with manager!");
    }
  }

  /**
   * Load an storage system entry using its respective URI.
   * @param uri URI to retrieve the storage system entity for
   * @return The entry in the storage system for the requested URI
   * @throws IOException If scheme does not exist.
   * @throws NullPointerException If uri is null
   */
  public static Entry getEntry(URI uri)
          throws IOException,
                 NullPointerException {
    String scheme = uri.getScheme();
    return getStorageSystem(scheme).getEntry(uri);
  }

  /**
   * Registers a {@link StorageSystem} implementaiton against its schema into
   * the registrar using the implementions fully qualified class name and its
   * non-args constructor.
   * @param storageSystemClassName The class names representation is string.
   * @throws ClassNotFoundException If no class is found with the name specified
   * @throws InstantiationException If there is any exception during
   *                                initialization
   * @throws IllegalAccessException If the class dpes not have a public
   *                                non-args constructor
   * @throws ClassCastException If the class does implement {@link StorageSystem}
   */
  public static void register(String storageSystemClassName)
          throws ClassNotFoundException,
                 InstantiationException,
                 IllegalAccessException,
                 ClassCastException {
    register((Class<? extends StorageSystem>) Class.forName(
            storageSystemClassName));
  }

  /**
   * Registers a {@link StorageSystem} implementaiton against its schema into
   * the registrar using the implemention class name and its non-args constructor.
   * @param storageSystemClassName The class names representation is string.
   * @throws InstantiationException If there is any exception during
   *                                initialization
   * @throws IllegalAccessException If the class dpes not have a public
   *                                non-args constructor
   */
  public static void register(
          Class<? extends StorageSystem> storageSystemClass)
          throws InstantiationException,
                 IllegalAccessException {
    if (storageSystemClass != null) {
      StorageSystem system = storageSystemClass.newInstance();
      register(system);
    }
  }

  /**
   * Registers a {@link StorageSystem} instance into the reigstrar. It would be
   * particularly useful if system does not have a non-args constructor.
   * @param system System to register in the manager
   */
  public static void register(StorageSystem system) {
    if (system != null) {
      storageSystemMap.put(system.getURIScheme(), system);
    }
  }
}
