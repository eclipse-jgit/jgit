/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.util.RefList;

/**
 * Writes out refs to {@link org.eclipse.jgit.lib.Constants#PACKED_REFS} file by
 * computing and applying all the missing traits.
 *
 * @since 7.7
 */
public abstract class PackedRefsWriter extends RefWriter {
  private final RefDirectory refDir;

  /**
   * <p>Constructor for RefWriter.</p>
   *
   * @param refs
   *            the complete set of references. This should have been computed
   *            by applying updates to the advertised refs already discovered.
   * @param traits
   *            traits that are applicable to the refs. If any traits are missing,
   *            they would be applied before writing, but the specified traits
   *            are not re-computed/checked.
   * @param refDir
   *            refDir where the packed-refs file should be written.
   *
   * @throws IOException
   *            if it is not able to peel any of the tags.
   * @since 7.7
   */
  public PackedRefsWriter(
      Collection<Ref> refs,
      EnumSet<PackedRefsTrait> traits,
      RefDirectory refDir) throws IOException {
    super(refs, traits);
    this.refDir = refDir;
    applyMissingTraits();
  }

  private void applyMissingTraits() throws IOException {
    if (!traits.contains(PackedRefsTrait.SORTED)) {
      this.refs = RefComparator.sort(refs);
      traits.add(PackedRefsTrait.SORTED);
    }

    if (!traits.contains(PackedRefsTrait.PEELED)) {
      Collection<Ref> peeledRefs = new ArrayList<>();
      for (Ref ref: refs) {
        if (!ref.isPeeled() && ref.getName().startsWith(R_TAGS)) {
          ref = refDir.peel(ref);
        }
        peeledRefs.add(ref);
      }
      this.refs = peeledRefs;
      this.traits.add(PackedRefsTrait.PEELED);
    }
  }

  /**
   * Access the new refs after all the necessary traits were applied.
   *
   * @return refs with all the traits applied
   */
  public RefList<Ref> asRefList() {
    RefList.Builder<Ref> builder = new RefList.Builder<>(this.refs.size());
    for(Ref each: refs) {
      builder.add(each);
    }
    return builder.toRefList();
  }
}
