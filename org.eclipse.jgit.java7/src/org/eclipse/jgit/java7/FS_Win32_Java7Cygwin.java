package org.eclipse.jgit.java7;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.internal.FS_Win32_Cygwin;

/**
 * FS for Java7 on Windows with Cygwin
 */
public class FS_Win32_Java7Cygwin extends FS_Win32_Cygwin {

	FS_Win32_Java7Cygwin(FS src) {
		super(src);
	}

	FS_Win32_Java7Cygwin() {
	}

	@Override
	public FS newInstance() {
		return new FS_Win32_Java7Cygwin(this);
	}

	@Override
	public boolean supportsSymlinks() {
		return true;
	}

	@Override
	public boolean isSymLink(File path) throws IOException {
		return FileUtil.isSymlink(path);
	}

	@Override
	public long lastModified(File path) throws IOException {
		return FileUtil.lastModified(path);
	}

	@Override
	public void setLastModified(File path, long time) throws IOException {
		FileUtil.setLastModified(path, time);
	}

	@Override
	public void delete(File path) throws IOException {
		FileUtil.delete(path);
	}

	@Override
	public long length(File f) throws IOException {
		return FileUtil.getLength(f);
	}

	@Override
	public boolean exists(File path) {
		return FileUtil.exists(path);
	}

	@Override
	public boolean isDirectory(File path) {
		return FileUtil.isDirectory(path);
	}

	@Override
	public boolean isFile(File path) {
		Path nioPath = path.toPath();
		return Files.isRegularFile(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	@Override
	public boolean isHidden(File path) throws IOException {
		return FileUtil.isHidden(path);
	}

	@Override
	public void setHidden(File path, boolean hidden) throws IOException {
		FileUtil.setHidden(path, hidden);
	}

	@Override
	public String readSymLink(File path) throws IOException {
		return FileUtil.readSymlink(path);
	}

	@Override
	public void createSymLink(File path, String target) throws IOException {
		FileUtil.createSymLink(path, target);
	}
}
