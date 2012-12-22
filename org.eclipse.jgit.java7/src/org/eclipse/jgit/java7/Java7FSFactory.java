package org.eclipse.jgit.java7;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.FSFactory;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.internal.FS_Win32_Cygwin;

/**
 * A factory for creating FS instances on Java7
 */
public class Java7FSFactory extends FSFactory {
	@Override
	public FS detect(Boolean cygwinUsed) {
		if (SystemReader.getInstance().isWindows()) {
			if (cygwinUsed == null)
				cygwinUsed = Boolean.valueOf(FS_Win32_Cygwin.isCygwin());
			if (cygwinUsed.booleanValue())
				return new FS_Win32_Java7Cygwin();
			else
				return new FS_Win32_Java7();
		} else
			return new FS_POSIX_Java7();
	}
}
