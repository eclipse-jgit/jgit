/*
 * Copyright (C) 2008-2013, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.merge;

import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.diff.DiffEntry;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;


public class RenameEntry {

  public RenameEntry(DiffEntry src, DiffEntry dst) {

    this.baseId = src.getOldId().toObjectId();
    this.oldId = src.getNewId().toObjectId();
    this.oldMode = src.getNewMode();
    this.oldPath = src.getNewPath();
    this.oldAttributes = src.getAttributes();

    this.newId = dst.getNewId().toObjectId();
    this.newMode = dst.getNewMode();
    this.newPath = dst.getNewPath();

    this.newAttributes = dst.getAttributes();
  }


  protected Attributes oldAttributes;
  protected Attributes newAttributes;
  /**
   * File name of the old (pre-image).
   */
  protected String oldPath;

  /**
   * File name of the new (post-image).
   */
  protected String newPath;


  /**
   * Old mode of the file, if described by the patch, else null.
   */
  protected FileMode oldMode;

  /**
   * New mode of the file, if described by the patch, else null.
   */
  protected FileMode newMode;


  /**
   * ObjectId listed on the index line for the old (pre-image)
   */
  protected ObjectId baseId;

  /**
   * ObjectId listed on the index line for the old (pre-image)
   */
  protected ObjectId oldId;

  /**
   * ObjectId listed on the index line for the new (post-image)
   */
  protected ObjectId newId;

  public String getOldPath() {
    return oldPath;
  }

  public String getNewPath() {
    return newPath;
  }


  /**
   * Get the old file mode
   *
   * @return the old file mode, if described in the patch
   */
  public FileMode getOldMode() {
    return oldMode;
  }

  /**
   * Get the new file mode
   *
   * @return the new file mode, if described in the patch
   */
  public FileMode getNewMode() {
    return newMode;
  }


  public ObjectId getBaseId() {
    return baseId;
  }
  /**
   * Get the old object id from the <code>index</code>.
   *
   * @return the object id; null if there is no index line
   */
  public ObjectId getOldId() {
    return oldId;
  }

  /**
   * Get the new object id from the <code>index</code>.
   *
   * @return the object id; null if there is no index line
   */
  public ObjectId getNewId() {
    return newId;
  }

  public Attributes getOldAttributes() {
    return this.oldAttributes;
  }

  public Attributes getNewAttributes() {
    return this.newAttributes;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("nls")
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("RenameEntry[");
    buf.append(" ");
    buf.append(oldPath + "->" + newPath);

    buf.append("]");
    return buf.toString();
  }
}
