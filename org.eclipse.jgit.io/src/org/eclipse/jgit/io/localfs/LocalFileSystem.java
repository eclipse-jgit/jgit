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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.eclipse.jgit.io.Entry;
import org.eclipse.jgit.io.StorageSystem;

/**
 * Implementation of storage system for the local file system.
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public class LocalFileSystem
        implements StorageSystem {

  public static final String PROTOCOL_FILE = "file";
  public static final Platform platform;
  public static Method canExecute;
  public static Method setExecute;
  public static String cygpath;

  static {
    platform = Platform.detect();
  }

  public String getURIScheme() {
    return PROTOCOL_FILE;
  }

  public Entry getEntry(URI uri) {
    if (uri == null) {
      return null;
    }
    else {
      return new LocalFileEntry(new File(uri), this);
    }
  }

  public Entry getWorkingDirectory() {
    String curDir = System.getProperty("user.dir");
    return new LocalFileEntry(new File(curDir), this);
  }

  public Entry resolve(Entry entry,
                       String path) {
    if (!(entry instanceof LocalFileEntry)) {
      return null;
    }
    LocalFileEntry fileEntry = (LocalFileEntry) entry;
    File localFile = fileEntry.getLocalFile();
    if (platform.equals(Platform.WIN32_CYGWIN)) {
      try {
        final Process p;

        p = Runtime.getRuntime().exec(
                new String[] {cygpath, "--windows", "--absolute", path},
                null, localFile);
        p.getOutputStream().close();

        final BufferedReader lineRead = new BufferedReader(
                new InputStreamReader(p.getInputStream(), "UTF-8"));
        String r = null;
        try {
          r = lineRead.readLine();
        }
        finally {
          lineRead.close();
        }

        for (;;) {
          try {
            if (p.waitFor() == 0 && r != null && r.length() > 0) {
              return new LocalFileEntry(new File(r), this);
            }
            break;
          }
          catch (InterruptedException ie) {
            // Stop bothering me, I have a zombie to reap.
          }
        }
      }
      catch (IOException ioe) {
        // Fall through and use the default return.
      }

    }
    final File abspn = new File(path);
    if (abspn.isAbsolute()) {
      return new LocalFileEntry(abspn, this);
    }
    return new LocalFileEntry(new File(localFile, path), this);
  }

  public Entry getHomeDirectory() {
    if (platform.equals(Platform.WIN32_CYGWIN)) {
      final String home = AccessController.doPrivileged(new PrivilegedAction<String>() {

        public String run() {
          return System.getenv("HOME");
        }
      });
      if (!(home == null || home.length() == 0)) {
        return resolve(new LocalFileEntry(new File("."), this), home);
      }
    }
    final String home = AccessController.doPrivileged(new PrivilegedAction<String>() {

      public String run() {
        return System.getProperty("user.home");
      }
    });
    if (home == null || home.length() == 0) {
      return null;
    }
    return new LocalFileEntry(new File(home).getAbsoluteFile(), this);
  }

  public enum Platform {

    WIN32_CYGWIN(false),
    WIN32(false),
    POSIX_JAVA5(false),
    POSIX_JAVA6(true);
    private boolean executableSupproted;

    private Platform(boolean executableSupproted) {
      this.executableSupproted = executableSupproted;
    }

    public boolean isExecutableSupproted() {
      return executableSupproted;
    }

    public static Platform detect() {
      final String osDotName = AccessController.doPrivileged(new PrivilegedAction<String>() {

        public String run() {
          return System.getProperty("os.name");
        }
      });
      if (osDotName != null &&
          osDotName.toLowerCase().indexOf("windows") != -1) {
        final String path = AccessController.doPrivileged(new PrivilegedAction<String>() {

          public String run() {
            return System.getProperty("java.library.path");
          }
        });
        if (path == null) {
          return WIN32;
        }
        for (final String p : path.split(";")) {
          final File e = new File(p, "cygpath.exe");
          if (e.isFile()) {
            cygpath = e.getAbsolutePath();
            return WIN32_CYGWIN;
          }
        }
        return WIN32;
      }
      else {
        canExecute = needMethod(File.class, "canExecute");
        setExecute = needMethod(File.class, "setExecutable",
                Boolean.TYPE);
        if (canExecute != null && setExecute != null) {
          return POSIX_JAVA6;
        }
        else {
          return POSIX_JAVA5;
        }
      }
    }

    private static Method needMethod(final Class<?> on,
                                     final String name,
                                     final Class<?>... args) {
      try {
        return on.getMethod(name, args);
      }
      catch (SecurityException e) {
        return null;
      }
      catch (NoSuchMethodException e) {
        return null;
      }
    }
  }
}
