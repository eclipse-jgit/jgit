package org.eclipse.jgit.internal.storage.io;

import java.io.File;

public class BlockSourceFileNotFoundException extends IllegalStateException {

	public BlockSourceFileNotFoundException(File file) {
		super("Block source input file " + file.getAbsolutePath() + " not found");
	}
}
