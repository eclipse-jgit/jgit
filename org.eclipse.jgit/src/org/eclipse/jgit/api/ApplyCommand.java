/*
 * Copyright (C) 2011, 2021 IBM Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.InputStream;
import java.util.List;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.Applier;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.InCoreSupport;

/**
 * Apply a patch to files and/or to the index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-apply.html" >Git documentation
 * about apply</a>
 * @since 2.0
 */
public class ApplyCommand extends GitCommand<ApplyResult> {

  private InputStream in;

  private boolean inCore = false;

  @Nullable
  protected final Repository db;

  /**
   * Reader to support {@link #revWalk} and other object loading.
   */
  protected ObjectReader reader;

  /**
   * A RevWalk for computing merge bases, or listing incoming commits.
   */
  protected RevWalk revWalk;

  /**
   * Constructs the command.
   *
   * @param local
   */
  ApplyCommand(Repository local) {
    super(local);
    this.inCore = false;
    if (local == null) {
      throw new NullPointerException(JGitText.get().repositoryIsRequired);
    }
    db = local;
    ObjectInserter inserter = local.newObjectInserter();
    reader = inserter.newReader();
    revWalk = new RevWalk(reader);
  }

  ApplyCommand(Repository remote, RevWalk base) {
    super(null);
    db = null;
    inCore = true;
    revWalk = base;
    reader = base.getObjectReader();
  }

  /**
   * Set patch
   *
   * @param in the patch to apply
   * @return this instance
   */
  public ApplyCommand setPatch(InputStream in) {
    checkCallable();
    this.in = in;
    return this;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Executes the {@code ApplyCommand} command with all the options and parameters collected by the
   * setter methods (e.g. {@link #setPatch(InputStream)} of this class. Each instance of this class
   * should only be used for one invocation of the command. Don't call this method twice on an
   * instance.
   */
  @Override
  public ApplyResult call() throws GitAPIException, PatchFormatException,
      PatchApplyException {
    checkCallable();
    setCallable(false);
    ApplyResult r = new ApplyResult();
    // DO NOT SUBMIT - handle output.
//    try {
      Applier applier = new Applier(repo);
      applier.applyPatch(in);
//      for (DirCacheEntry entry : appliedEntries) {
//        r.addUpdatedFile(f);
//      }
//    } catch (IOException e) {
//      throw new PatchApplyException(MessageFormat.format(
//          JGitText.get().patchApplyException, e.getMessage()), e);
//    }
    return r;
  }
}
