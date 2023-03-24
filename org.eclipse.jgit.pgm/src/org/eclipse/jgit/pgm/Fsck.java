/*
 * Copyright (C) 2023, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.FsckError;
import org.eclipse.jgit.errors.FsckError.CorruptIndex;
import org.eclipse.jgit.errors.FsckError.CorruptObject;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;

/**
 * @since 5.13.2
 */
@Command(common = true, usage = "usage_Fsck")
public class Fsck extends TextBuiltin {

	@Override
	protected void run() throws Exception {
		org.eclipse.jgit.lib.Fsck fsck = db.newFsck();
		FsckError errors = fsck.check(new TextProgressMonitor(errw));
		for (CorruptIndex idx : errors.getCorruptIndices()) {
			outw.println(MessageFormat.format(CLIText.get().corruptIndex,
					idx.getFileName()));
		}
		for (CorruptObject obj : errors.getCorruptObjects()) {
			outw.println(MessageFormat.format(CLIText.get().corruptObject,
					obj.getId().getName(), getTypeName(obj.getType()),
					obj.getErrorType()));
		}
		for (ObjectId obj : errors.getMissingObjects()) {
			outw.println(MessageFormat.format(CLIText.get().missingObject,
					obj.getName()));
		}
		for (String head : errors.getNonCommitHeads()) {
			outw.println(
					MessageFormat.format(CLIText.get().nonCommitHead, head));
		}
		outw.flush();
	}

	private Object getTypeName(int type) {
		switch (type) {
			case Constants.OBJ_BLOB:
				return "blob"; //$NON-NLS-1$
			case Constants.OBJ_COMMIT:
				return "commit"; //$NON-NLS-1$
			case Constants.OBJ_EXT:
				return "extended type"; //$NON-NLS-1$
			case Constants.OBJ_OFS_DELTA:
				return "offset delta"; //$NON-NLS-1$
			case Constants.OBJ_REF_DELTA:
				return "reference delta"; //$NON-NLS-1$
			case Constants.OBJ_TAG:
				return "tag"; //$NON-NLS-1$
			case Constants.OBJ_TREE:
				return "tree"; //$NON-NLS-1$
			case Constants.OBJ_TYPE_5:
				return "reserved for future use"; //$NON-NLS-1$
			case Constants.OBJ_BAD:
			default:
				return "unknown or invalid"; //$NON-NLS-1$
	}
}
}
