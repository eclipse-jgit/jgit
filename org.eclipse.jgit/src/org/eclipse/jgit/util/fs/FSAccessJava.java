package org.eclipse.jgit.util.fs;

import java.io.File;

/**
 * Java implementation of FSAccess, used when no native implementation is around
 */
public class FSAccessJava extends FSAccess {

	FSAccessJava() {
		// empty default constructor
	}

	@Override
	public LStat lstat(File file) throws NoSuchFileException,
			NotDirectoryException {
		// TODO add determination of file mode
		return new LStat(file.lastModified(), (int) file.length());
	}

}
