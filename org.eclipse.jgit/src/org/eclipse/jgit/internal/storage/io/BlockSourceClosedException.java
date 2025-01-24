package org.eclipse.jgit.internal.storage.io;

import java.io.File;

public class BlockSourceClosedException extends IllegalStateException {

	public BlockSourceClosedException(File file) {
		super("Block source channel for input file " + file.getAbsolutePath() + " is closed");
	}
}
