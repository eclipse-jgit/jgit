package org.eclipse.jgit.errors;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;

/**
 * Exception for when the index file cannot be locked
 */
public class CannotLockDirCache extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param indexLocation
	 */
	public CannotLockDirCache(File indexLocation) {
		super(MessageFormat.format(JGitText.get().cannotLock, indexLocation));
	}

}
