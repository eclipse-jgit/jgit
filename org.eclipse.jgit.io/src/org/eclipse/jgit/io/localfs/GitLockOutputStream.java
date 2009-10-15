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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import org.eclipse.jgit.io.utils.IOUtils;

/**
 * Output stream for locked files. Will handle append mode and will also copy
 * to the original file when the write is done.
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public class GitLockOutputStream
        extends OutputStream {

  private File mainFile;
  private File lockFile;
  private boolean appendMode;
  private FileLock fileLock;
  private FileOutputStream lockFileOutputStream;

  /**
   * An output stream specialized in handling git atomic file lock mechanism. It
   * does not write to the mainFile directly but instead writes to the temp file
   * and upon completion of the writing operations renames the lock file to the
   * main file. End of writing operation should be signified by {@link #close()}.
   * If appended mode is true and mainFile exists it will copy the content of
   * mainFile into lockFile.
   * @param mainFile Main file to attain the lock against
   * @param lockFile The temporary file to write the content into first
   * @param appendMode Whether the content should be appended to the mainFile or
   *                   not.
   * @throws IOException If any error in the output process.
   * @throws IllegalArgumentException If any argument is null
   */
  public GitLockOutputStream(File mainFile,
                             File lockFile,
                             boolean appendMode)
          throws IOException,
                 IllegalArgumentException {

    this.mainFile = mainFile;
    this.lockFile = lockFile;
    this.appendMode = appendMode;
    init();
  }

  /**
   * Will release the channel lock, close the output stream and rename the lock
   * file to the original file to signify the end of writing.
   * @throws IOException If any error in the process
   */
  @Override
  public void close()
          throws IOException {
    if (fileLock != null) {
      fileLock.release();
    }
    lockFileOutputStream.close();
    if (fileLock != null) {
      if (!lockFile.renameTo(mainFile)) {
        if (!mainFile.exists() || mainFile.delete()) {
          if (lockFile.renameTo(mainFile)) {
            throw new IOException(new StringBuilder("Could not rename ").append(lockFile.
                    getAbsolutePath()).append(" to ").append(mainFile.
                    getAbsolutePath()).toString());
          }
        }
      }
    }
  }

  @Override
  public void flush()
          throws IOException {
    lockFileOutputStream.flush();
  }

  @Override
  public void write(int b)
          throws IOException {
    lockFileOutputStream.write(b);
  }

  @Override
  public void write(byte[] b)
          throws IOException {
    lockFileOutputStream.write(b);
  }

  @Override
  public void write(byte[] b,
                    int off,
                    int len)
          throws IOException {
    lockFileOutputStream.write(b, off, len);
  }

  protected void init()
          throws IOException {
    if (mainFile.exists() && appendMode) {
      FileInputStream inputStream = new FileInputStream(mainFile);
      FileOutputStream outputStream = new FileOutputStream(lockFile);
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
      outputStream.close();
    }
    lockFileOutputStream = new FileOutputStream(lockFile, appendMode);
    try {
      fileLock = lockFileOutputStream.getChannel().lock();
    }
    catch (IOException ex) {
      close();
      throw ex;
    }
    catch (Exception ex) {
      close();
      throw new IOException(ex);
    }
    if (fileLock == null) {
      close();
      throw new OverlappingFileLockException();
    }
  }
}
